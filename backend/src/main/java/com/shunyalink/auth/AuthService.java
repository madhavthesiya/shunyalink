package com.shunyalink.auth;

import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ConflictException;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Locale;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final VerificationTokenRepository verificationTokenRepo;
    private final PasswordResetTokenRepository passwordResetTokenRepo;
    private final EmailService emailService;
    private final com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository;
    private final JwtBlacklistService jwtBlacklistService;

    public AuthService(UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            GoogleIdTokenVerifier googleIdTokenVerifier,
            VerificationTokenRepository verificationTokenRepo,
            PasswordResetTokenRepository passwordResetTokenRepo,
            EmailService emailService,
            com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository,
            JwtBlacklistService jwtBlacklistService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.verificationTokenRepo = verificationTokenRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.emailService = emailService;
        this.globalStatsRepository = globalStatsRepository;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    /** Emails are case-insensitive identifiers; store and compare them in one canonical form. */
    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
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

        // Denormalized counter for the public stats strip (avoids COUNT(*) per page view)
        globalStatsRepository.incrementUsers();

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
            // Verify the Google ID token (signature, audience, expiry)
            GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Invalid Google ID token");
            }

            // Extract user info from Google token
            GoogleIdToken.Payload payload = idToken.getPayload();
            // Google only vouches for addresses it has verified; an unverified one could be anyone's
            if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new BadRequestException("Your Google account's email address is not verified");
            }
            String email = normalizeEmail(payload.getEmail());
            String name = (String) payload.get("name");

            // Find existing user or create new one
            UserEntity user;
            Optional<UserEntity> existingUser = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(email);

            if (existingUser.isEmpty()) {
                UserEntity newUser = new UserEntity();
                newUser.setEmail(email);
                newUser.setName(name != null ? name : email);
                newUser.setAuthProvider("GOOGLE");
                newUser.setEmailVerified(true); // Google OAuth implies verified email
                user = userRepository.save(newUser);

                // Denormalized counter for the public stats strip (avoids COUNT(*) per page view)
                globalStatsRepository.incrementUsers();

                // Send Welcome Email to first-time Google users
                emailService.sendGoogleWelcomeEmail(user.getEmail(), user.getName());
            } else {
                user = existingUser.get();
                if (!user.isEmailVerified()) {
                    // Whoever registered this unverified account never proved they own the address;
                    // Google just proved this person does. The existing password (and any token
                    // issued at registration) may belong to an attacker who pre-registered it, so
                    // drop the password, make the account Google-only and revoke earlier sessions.
                    user.setPassword(null);
                    user.setAuthProvider("GOOGLE");
                    user.setEmailVerified(true);
                    userRepository.save(user);
                    jwtBlacklistService.revokeAllForUser(user.getId());
                    emailService.sendWelcomeEmail(user.getEmail(), user.getName());
                }
            }

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

        // Prevent duplicate Welcome Emails if user clicks the link multiple times
        if (user.isEmailVerified()) {
            // Delete the consumed token to keep DB clean
            verificationTokenRepo.delete(vToken);
            return "Email is already verified!";
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete the consumed token so it can't be reused
        verificationTokenRepo.delete(vToken);

        // Dispatch Welcome Email asynchronously
        emailService.sendWelcomeEmail(user.getEmail(), user.getName());

        return "Email verified successfully!";
    }

    public void resendVerificationEmail(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }

        // Invalidate earlier verification links so only the newest email works
        verificationTokenRepo.deleteByUser(user);

        // Generate a fresh token
        VerificationToken newToken = new VerificationToken();
        newToken.setUser(user);
        verificationTokenRepo.save(newToken);

        // Dispatch email
        emailService.sendVerificationEmail(user.getEmail(), newToken.getToken());
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

    public ProfileResponse getProfileById(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        return new ProfileResponse(
            user.getUsername(),
            user.getName(),
            user.getEmail(),
            user.isEmailVerified(),
            user.getBioText(),
            user.getThemeColor(),
            user.getProfileType(),
            user.getGithubUsername(),
            user.getLeetcodeUsername(),
            user.getCodeforcesUsername(),
            user.getCodeChefHandle(),
            user.getAtCoderHandle(),
            user.getProfilePictureUrl(),
            null
        );
    }

    public ProfileResponse getProfile(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User profile not found"));

        return new ProfileResponse(
            user.getUsername(),
            user.getName(),
            null, // Don't expose email publicly
            false,
            user.getBioText(),
            user.getThemeColor(),
            user.getProfileType(),
            user.getGithubUsername(),
            user.getLeetcodeUsername(),
            user.getCodeforcesUsername(),
            user.getCodeChefHandle(),
            user.getAtCoderHandle(),
            user.getProfilePictureUrl(),
            null
        );
    }

    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));

        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String newUsername = request.getUsername().toLowerCase().trim();
            // Check if username is already taken by SOMEONE ELSE
            Optional<UserEntity> existing = userRepository.findByUsername(newUsername);
            if (existing.isPresent() && !existing.get().getId().equals(userId)) {
                throw new ConflictException("Username is already taken");
            }
            user.setUsername(newUsername);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }

        if (request.getBioText() != null) {
            user.setBioText(request.getBioText().trim());
        }
        if (request.getThemeColor() != null) {
            user.setThemeColor(request.getThemeColor().trim());
        }
        if (request.getProfileType() != null) {
            user.setProfileType(request.getProfileType().trim());
        }
        if (request.getGithubUsername() != null) {
            user.setGithubUsername(request.getGithubUsername().trim());
        }
        if (request.getLeetcodeUsername() != null) {
            user.setLeetcodeUsername(request.getLeetcodeUsername().trim());
        }
        if (request.getCodeforcesUsername() != null) {
            user.setCodeforcesUsername(request.getCodeforcesUsername().trim());
        }
        if (request.getCodeChefHandle() != null) {
            user.setCodeChefHandle(request.getCodeChefHandle().trim());
        }
        if (request.getAtCoderHandle() != null) {
            user.setAtCoderHandle(request.getAtCoderHandle().trim());
        }

        userRepository.save(user);
    }

    public boolean isUsernameAvailable(String username, Long currentUserId) {
        String cleanUsername = username.toLowerCase().trim();
        Optional<UserEntity> existing = userRepository.findByUsername(cleanUsername);
        
        // Available if no one has it OR if the current user already owns it
        return existing.isEmpty() || existing.get().getId().equals(currentUserId);
    }
}
