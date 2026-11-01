package com.shunyalink.auth;

import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, data recovery, and email verification")
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;
    private final com.shunyalink.security.JwtService jwtService;
    private final com.shunyalink.security.JwtBlacklistService blacklistService;
    private final ClientIpResolver clientIpResolver;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthController(AuthService authService, RateLimiterService rateLimiterService,
                          com.shunyalink.security.JwtService jwtService,
                          com.shunyalink.security.JwtBlacklistService blacklistService,
                          ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
        this.jwtService = jwtService;
        this.blacklistService = blacklistService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account and sends a verification email.")
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        rateLimiterService.checkLimit("rate:register:" + ip, 5, 60);
        return authService.register(request);
    }

    @Operation(summary = "Authenticate user", description = "Returns a JWT token if credentials are valid.")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        rateLimiterService.checkLimit("rate:login:" + ip, 10, 60);
        return authService.login(request);
    }

    @Operation(summary = "Google OAuth login", description = "Authenticates user using a Google ID Token.")
    @PostMapping("/google")
    public AuthResponse googleLogin(@Valid @RequestBody GoogleTokenRequest request) {
        return authService.googleLogin(request.getIdToken());
    }

    @Operation(summary = "Verify email address", description = "Activates user account using the token sent via email.")
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/login?verified=true"))
                .build();
    }

    @Operation(summary = "Resend verification email", description = "Trigger a new verification email for a non-verified account.")
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        limitEmailSending("resend", request.email(), httpRequest);
        authService.resendVerificationEmail(request.email());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Request password reset", description = "Sends a password reset link if an account exists. The response is identical either way.")
    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        limitEmailSending("forgot", request.email(), httpRequest);
        return authService.requestPasswordReset(request.email());
    }

    @Operation(summary = "Reset password", description = "Updates password using the secret token from the reset email and signs out all existing sessions.")
    @PostMapping("/reset-password")
    public String resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return authService.resetPassword(request.token(), request.newPassword());
    }

    /**
     * Endpoints that send email: limited per client (one caller spamming many addresses) and per
     * address (many callers flooding one inbox), protecting both users and the mail quota.
     */
    private void limitEmailSending(String action, String email, HttpServletRequest httpRequest) {
        rateLimiterService.checkLimit("rate:" + action + ":ip:" + clientIpResolver.resolve(httpRequest), 5, 3600);
        rateLimiterService.checkLimit("rate:" + action + ":email:" + AuthService.normalizeEmail(email), 3, 3600);
    }

    @Operation(summary = "Logout user", description = "Invalidates the current JWT token by adding it to the Redis blacklist.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.isTokenValid(token)) {
                long remainingTimeMs = jwtService.getRemainingTimeMs(token);
                blacklistService.blacklistToken(token, remainingTimeMs);
            }
        }
        return ResponseEntity.ok().build();
    }
}
