package com.shunyalink.auth;

import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String googleClientId;
    private final VerificationTokenRepository verificationTokenRepo;
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.google.client-id}") String googleClientId,
                       VerificationTokenRepository verificationTokenRepo,
                       PasswordResetTokenRepository passwordResetTokenRepo,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleClientId = googleClientId;
        this.verificationTokenRepo = verificationTokenRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.emailService = emailService;
    }

    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }

        // Create user with hashed password
        UserEntity user = new UserEntity();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword())); // BCrypt hash
        user.setName(request.getName());
        userRepository.save(user);

        // Send verification email
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationTokenRepo.save(verificationToken);
        emailService.sendVerificationEmail(user.getEmail(), verificationToken.getToken());

        // Generate JWT and return
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getName());
    }

    public AuthResponse login(LoginRequest request) {
        // Find user by email
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid email or password");
        }

        // Generate JWT and return
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, user.getEmail(), user.getName());
    }

    public AuthResponse googleLogin(String idTokenString) {
        try {
            // Verify the Google ID token
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Invalid Google ID token");
            }

            // Extract user info from Google token
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            // Find existing user or create new one
            UserEntity user = userRepository.findByEmail(email)
                    .orElseGet(() -> {
                        UserEntity newUser = new UserEntity();
                        newUser.setEmail(email);
                        newUser.setName(name != null ? name : email);
                        newUser.setAuthProvider("GOOGLE");
                        // No password for Google users
                        return userRepository.save(newUser);
                    });

            // Generate our JWT and return
            String token = jwtService.generateToken(user.getId(), user.getEmail());
            return new AuthResponse(token, user.getEmail(), user.getName());

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Google authentication failed");
        }
    }

    public String verifyEmail(String token) {
        VerificationToken vToken = verificationTokenRepo.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if (vToken.isExpired()) {
            throw new BadRequestException("Verification token has expired");
        }

        UserEntity user = vToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        // Dispatch Welcome Email asynchronously
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

        return "Email verified successfully!";
    }

    public String requestPasswordReset(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found with that email"));

        if ("GOOGLE".equals(user.getAuthProvider())) {
            throw new BadRequestException("Google accounts cannot reset password. Use Google login.");
        }

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        passwordResetTokenRepo.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());

        return "Password reset email sent!";
    }

    public String resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepo.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (resetToken.isExpired()) {
            throw new BadRequestException("Reset token has expired");
        }
        if (resetToken.isUsed()) {
            throw new BadRequestException("Reset token has already been used");
        }

        UserEntity user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepo.save(resetToken);

        return "Password reset successfully!";
    }

}
