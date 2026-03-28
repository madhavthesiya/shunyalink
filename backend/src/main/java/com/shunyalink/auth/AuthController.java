package com.shunyalink.auth;

import com.shunyalink.rate.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, login, data recovery, and email verification")
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account and sends a verification email.")
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        rateLimiterService.checkLimit("rate:register:" + ip, 5, 60);
        return authService.register(request);
    }

    @Operation(summary = "Authenticate user", description = "Returns a JWT token if credentials are valid.")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
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
    public ResponseEntity<Void> resendVerification(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        authService.resendVerificationEmail(email);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Request password reset", description = "Sends a password reset link to the specified email address.")
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestBody Map<String, String> request) {
        return authService.requestPasswordReset(request.get("email"));
    }

    @Operation(summary = "Reset password", description = "Updates password using the secret token from the reset email.")
    @PostMapping("/reset-password")
    public String resetPassword(@RequestBody Map<String, String> request) {
        return authService.resetPassword(request.get("token"), request.get("newPassword"));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
