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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
    /** Compared against when no account matches, so login takes the same time either way. */
    private final String dummyPasswordHash;

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{3,30}$");
    private static final Pattern THEME_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern PLATFORM_HANDLE = Pattern.compile("^[A-Za-z0-9_.-]{1,40}$");
    private static final Set<String> PROFILE_TYPES = Set.of("NORMAL", "PROGRAMMER");
    /** Names that would let a profile pass as the site itself or its staff. */
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "administrator", "root", "system", "support", "help", "security", "official",
            "shunyalink", "staff", "moderator", "api", "www", "login", "register", "dashboard", "settings");

    /** Same response whether or not the account exists, so this endpoint can't discover accounts. */
    static final String RESET_REQUESTED_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";

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
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    /** Emails are case-insensitive identifiers; store and compare them in one canonical form. */
    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        // Check if email already exists (in any letter case)
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }

        // Create user with hashed password
        UserEntity user = new UserEntity();
        user.setEmail(email);
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
        Optional<UserEntity> found =
                userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizeEmail(request.getEmail()));
        String storedHash = found.map(UserEntity::getPassword).orElse(null);

        // Always run exactly one BCrypt comparison: an early return for unknown (or Google-only)
        // accounts would be measurably faster and reveal which emails are registered.
        boolean matches = passwordEncoder.matches(request.getPassword(),
                storedHash != null ? storedHash : dummyPasswordHash);
        if (storedHash == null || !matches) {
            throw new BadRequestException("Invalid email or password");
        }
        UserEntity user = found.get();

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
        Optional<UserEntity> found = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizeEmail(email));
        // Silent for unknown or already-verified addresses: the caller can't tell them apart
        if (found.isEmpty() || found.get().isEmailVerified()) {
            return;
        }
        UserEntity user = found.get();

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
        Optional<UserEntity> found = userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(normalizeEmail(email));
        // Google-only accounts have no password to reset; they get the same answer as unknown emails
        if (found.isEmpty() || "GOOGLE".equals(found.get().getAuthProvider())) {
            return RESET_REQUESTED_MESSAGE;
        }
        UserEntity user = found.get();

        // Only the newest reset link works
        passwordResetTokenRepo.deleteByUser(user);
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        passwordResetTokenRepo.save(resetToken);
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken.getToken());

        return RESET_REQUESTED_MESSAGE;
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
        // Following the emailed link proved control of the inbox
        user.setEmailVerified(true);
        userRepository.save(user);

        // Consume this and any other outstanding links, and sign out every existing session:
        // a reset is often a response to a compromised password.
        passwordResetTokenRepo.deleteByUser(user);
        jwtBlacklistService.revokeAllForUser(user.getId());

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

        // Every field is validated only when it changes: the settings form re-sends the whole
        // profile, and values stored before these rules existed must not block unrelated edits.
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            String newUsername = request.getUsername().toLowerCase().trim();
            if (!newUsername.equals(user.getUsername())) {
                if (!USERNAME.matcher(newUsername).matches()) {
                    throw new BadRequestException("Username must be 3-30 characters: lowercase letters, numbers or underscores");
                }
                if (RESERVED_USERNAMES.contains(newUsername)) {
                    throw new BadRequestException("That username is reserved");
                }
                // Check if username is already taken by SOMEONE ELSE
                Optional<UserEntity> existing = userRepository.findByUsername(newUsername);
                if (existing.isPresent() && !existing.get().getId().equals(userId)) {
                    throw new ConflictException("Username is already taken");
                }
            }
            user.setUsername(newUsername);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            String name = request.getName().trim();
            if (name.length() > 100) {
                throw new BadRequestException("Name cannot exceed 100 characters");
            }
            user.setName(name);
        }

        if (request.getBioText() != null) {
            String bio = request.getBioText().trim();
            if (bio.length() > 500) {
                throw new BadRequestException("Bio cannot exceed 500 characters");
            }
            user.setBioText(bio);
        }
        if (request.getThemeColor() != null) {
            String color = request.getThemeColor().trim();
            // Rendered into inline CSS on public profile pages
            if (!color.equals(user.getThemeColor()) && !THEME_COLOR.matcher(color).matches()) {
                throw new BadRequestException("Theme color must be a hex color like #1a2b3c");
            }
            user.setThemeColor(color);
        }
        if (request.getProfileType() != null) {
            String type = request.getProfileType().trim();
            if (!type.equals(user.getProfileType()) && !PROFILE_TYPES.contains(type)) {
                throw new BadRequestException("Profile type must be NORMAL or PROGRAMMER");
            }
            user.setProfileType(type);
        }
        // Platform handles are placed into external API URLs and the scraper path
        user.setGithubUsername(platformHandle(request.getGithubUsername(), user.getGithubUsername()));
        user.setLeetcodeUsername(platformHandle(request.getLeetcodeUsername(), user.getLeetcodeUsername()));
        user.setCodeforcesUsername(platformHandle(request.getCodeforcesUsername(), user.getCodeforcesUsername()));
        user.setCodeChefHandle(platformHandle(request.getCodeChefHandle(), user.getCodeChefHandle()));
        user.setAtCoderHandle(platformHandle(request.getAtCoderHandle(), user.getAtCoderHandle()));

        userRepository.save(user);
    }

    /** null = field not sent (keep current); blank = disconnect the platform. */
    private static String platformHandle(String requested, String current) {
        if (requested == null) {
            return current;
        }
        String handle = requested.trim();
        if (handle.isEmpty()) {
            return null;
        }
        if (!handle.equals(current) && !PLATFORM_HANDLE.matcher(handle).matches()) {
            throw new BadRequestException("Platform usernames may only contain letters, numbers, '.', '_' and '-'");
        }
        return handle;
    }

    public boolean isUsernameAvailable(String username, Long currentUserId) {
        String cleanUsername = username.toLowerCase().trim();
        Optional<UserEntity> existing = userRepository.findByUsername(cleanUsername);
        
        // Available if no one has it OR if the current user already owns it
        return existing.isEmpty() || existing.get().getId().equals(currentUserId);
    }
}
