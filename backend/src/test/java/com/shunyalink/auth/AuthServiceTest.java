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
