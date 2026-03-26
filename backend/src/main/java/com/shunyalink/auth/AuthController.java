package com.shunyalink.auth;

import com.shunyalink.rate.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiterService rateLimiterService;

    public AuthController(AuthService authService, RateLimiterService rateLimiterService) {
        this.authService = authService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        rateLimiterService.checkLimit("rate:register:" + ip, 5, 60); // 5 attempts per minute
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        rateLimiterService.checkLimit("rate:login:" + ip, 10, 60); // 10 attempts per minute
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse googleLogin(@Valid @RequestBody GoogleTokenRequest request){
        return authService.googleLogin(request.getIdToken());
    }

    @GetMapping("/verify")
    public String verifyEmail(@RequestParam String token) {
        return authService.verifyEmail(token);
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        rateLimiterService.checkLimit("rate:forgot-password:" + ip, 3, 300); // 3 attempts per 5 minutes
        return authService.requestPasswordReset(request.get("email"));
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        rateLimiterService.checkLimit("rate:reset-password:" + ip, 5, 300); // 5 attempts per 5 minutes
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
