package com.shunyalink.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.shunyalink.analytics.GlobalStatsRepository;
import com.shunyalink.exception.BadRequestException;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final VerificationTokenRepository verificationTokens = mock(VerificationTokenRepository.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final EmailService emailService = mock(EmailService.class);
    private final GlobalStatsRepository globalStats = mock(GlobalStatsRepository.class);
    private final GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
    private final JwtBlacklistService blacklist = mock(JwtBlacklistService.class);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, googleVerifier,
                verificationTokens, resetTokens, emailService, globalStats, blacklist);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken(any(), any())).thenReturn("new-jwt");
    }

    private void googleSaysToken(String email, boolean emailVerified) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.set("name", "Real Owner");
        GoogleIdToken token = new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
        when(googleVerifier.verify("id-token")).thenReturn(token);
    }

    private UserEntity existingUser(long id, String email, String provider, boolean verified, String passwordHash) throws Exception {
        UserEntity user = new UserEntity();
        var idField = UserEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
        user.setEmail(email);
        user.setName("Someone");
        user.setAuthProvider(provider);
        user.setEmailVerified(verified);
        user.setPassword(passwordHash);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc(email)).thenReturn(Optional.of(user));
        return user;
    }

    // ─── Registration / login ────────────────────────────────────────

    @Test
    void register_duplicateEmailInDifferentCase_isRejected() {
        when(userRepository.existsByEmailIgnoreCase("madhav@gmail.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest();
        request.setEmail("Madhav@Gmail.com");
        request.setPassword("longenough");
        request.setName("Madhav");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(com.shunyalink.exception.ConflictException.class);
    }

    @Test
    void register_storesTheNormalizedEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("  New.User@Example.COM ");
        request.setPassword("longenough");
        request.setName("New");

        authService.register(request);

        verify(userRepository).save(argThat(u -> u.getEmail().equals("new.user@example.com")));
    }

    @Test
    void login_isCaseInsensitive() throws Exception {
        existingUser(9L, "madhav@gmail.com", "LOCAL", true, "$2a$10$hash");
        when(passwordEncoder.matches("pw", "$2a$10$hash")).thenReturn(true);
        LoginRequest request = new LoginRequest();
        request.setEmail("MADHAV@gmail.com");
        request.setPassword("pw");

        assertThat(authService.login(request).getToken()).isEqualTo("new-jwt");
    }

    @Test
    void login_unknownEmail_stillComparesAHashSoTimingDoesNotRevealAccounts() {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("ghost@example.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("pw");

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadRequestException.class);
        verify(passwordEncoder).matches(eq("pw"), any());
    }

    // ─── Password reset / verification emails ────────────────────────

    @Test
    void forgotPassword_unknownEmail_returnsTheGenericMessageAndSendsNothing() {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("ghost@example.com")).thenReturn(Optional.empty());

        String message = authService.requestPasswordReset("ghost@example.com");

        assertThat(message).isEqualTo(AuthService.RESET_REQUESTED_MESSAGE);
        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPassword_googleAccount_returnsTheSameGenericMessage() throws Exception {
        existingUser(3L, "g@gmail.com", "GOOGLE", true, null);

        assertThat(authService.requestPasswordReset("g@gmail.com")).isEqualTo(AuthService.RESET_REQUESTED_MESSAGE);
        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPassword_existingUser_invalidatesOlderLinksAndSendsOne() throws Exception {
        UserEntity user = existingUser(4L, "me@example.com", "LOCAL", true, "$2a$10$hash");

        assertThat(authService.requestPasswordReset("ME@example.com")).isEqualTo(AuthService.RESET_REQUESTED_MESSAGE);
        verify(resetTokens).deleteByUser(user);
        verify(emailService).sendPasswordResetEmail(eq("me@example.com"), anyString());
    }

    @Test
    void resendVerification_unknownEmail_isSilent() {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("ghost@example.com")).thenReturn(Optional.empty());

        authService.resendVerificationEmail("ghost@example.com");

        verifyNoInteractions(emailService);
    }

    @Test
    void resetPassword_success_revokesExistingSessionsAndRemainingLinks() throws Exception {
        UserEntity user = existingUser(8L, "me@example.com", "LOCAL", false, "$2a$10$old");
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        when(resetTokens.findByToken("reset-123")).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("brandnewpass")).thenReturn("$2a$10$new");

        authService.resetPassword("reset-123", "brandnewpass");

        assertThat(user.getPassword()).isEqualTo("$2a$10$new");
        assertThat(user.isEmailVerified()).isTrue(); // the reset link proved control of the inbox
        verify(blacklist).revokeAllForUser(8L);
        verify(resetTokens).deleteByUser(user);
    }

    // ─── Profile settings validation ─────────────────────────────────

    private UserEntity profileOwner() throws Exception {
        UserEntity user = existingUser(20L, "owner@example.com", "LOCAL", true, "$2a$10$hash");
        user.setUsername("madhav");
        user.setThemeColor("#000000");
        user.setProfileType("NORMAL");
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        return user;
    }

    private static ProfileUpdateRequest unchangedProfile() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setUsername("madhav");
        request.setThemeColor("#000000");
        request.setProfileType("NORMAL");
        return request;
    }

    @Test
    void updateProfile_themeColorThatIsNotAHexColor_isRejected() throws Exception {
        profileOwner();
        ProfileUpdateRequest request = unchangedProfile();
        request.setThemeColor("red; background-image: url(https://tracker.example/pixel)");

        assertThatThrownBy(() -> authService.updateProfile(20L, request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateProfile_unknownProfileType_isRejected() throws Exception {
        profileOwner();
        ProfileUpdateRequest request = unchangedProfile();
        request.setProfileType("ADMIN");

        assertThatThrownBy(() -> authService.updateProfile(20L, request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateProfile_reservedOrMalformedUsername_isRejected() throws Exception {
        profileOwner();
        ProfileUpdateRequest reserved = unchangedProfile();
        reserved.setUsername("admin");
        ProfileUpdateRequest malformed = unchangedProfile();
        malformed.setUsername("x!");

        assertThatThrownBy(() -> authService.updateProfile(20L, reserved)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> authService.updateProfile(20L, malformed)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateProfile_platformHandleWithPathCharacters_isRejected() throws Exception {
        profileOwner();
        ProfileUpdateRequest request = unchangedProfile();
        request.setGithubUsername("../orgs/private-org");

        assertThatThrownBy(() -> authService.updateProfile(20L, request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateProfile_blankHandle_clearsTheConnection() throws Exception {
        UserEntity user = profileOwner();
        user.setGithubUsername("octocat");
        ProfileUpdateRequest request = unchangedProfile();
        request.setGithubUsername("   ");

        authService.updateProfile(20L, request);

        assertThat(user.getGithubUsername()).isNull();
    }

    @Test
    void updateProfile_unchangedLegacyValues_stillSave() throws Exception {
        UserEntity user = profileOwner();
        // Stored before these rules existed; re-sending them must not block other edits
        user.setUsername("old.name");
        user.setThemeColor("black");
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setUsername("old.name");
        request.setThemeColor("black");
        request.setBioText("New bio");

        authService.updateProfile(20L, request);

        assertThat(user.getBioText()).isEqualTo("New bio");
    }

    // ─── Google sign-in / account pre-hijacking ──────────────────────

    @Test
    void googleLogin_onUnverifiedLocalAccount_removesTheUnprovenPasswordAndRevokesOldSessions() throws Exception {
        // Attacker pre-registered the victim's address with their own password (never verified)
        UserEntity account = existingUser(5L, "victim@gmail.com", "LOCAL", false, "$2a$10$attackersHash");
        googleSaysToken("victim@gmail.com", true);

        AuthResponse response = authService.googleLogin("id-token");

        assertThat(account.getPassword()).isNull();
        assertThat(account.getAuthProvider()).isEqualTo("GOOGLE");
        assertThat(account.isEmailVerified()).isTrue();
        verify(blacklist).revokeAllForUser(5L); // the attacker's registration token stops working
        assertThat(response.getToken()).isEqualTo("new-jwt");
    }

    @Test
    void googleLogin_onVerifiedLocalAccount_keepsThePasswordAndSessions() throws Exception {
        UserEntity account = existingUser(6L, "owner@gmail.com", "LOCAL", true, "$2a$10$ownersHash");
        googleSaysToken("owner@gmail.com", true);

        authService.googleLogin("id-token");

        assertThat(account.getPassword()).isEqualTo("$2a$10$ownersHash");
        verify(blacklist, never()).revokeAllForUser(anyLong());
    }

    @Test
    void googleLogin_withUnverifiedGoogleEmail_isRejected() throws Exception {
        googleSaysToken("someone@example.com", false);

        assertThatThrownBy(() -> authService.googleLogin("id-token")).isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void googleLogin_newUser_isCreatedVerifiedWithoutPassword() throws Exception {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByIdAsc("new@gmail.com")).thenReturn(Optional.empty());
        googleSaysToken("New@Gmail.com", true);

        authService.googleLogin("id-token");

        verify(userRepository).save(argThat(u ->
                u.getEmail().equals("new@gmail.com") && u.isEmailVerified()
                        && "GOOGLE".equals(u.getAuthProvider()) && u.getPassword() == null));
    }
}
