package com.shunyalink.url;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.shunyalink.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import com.shunyalink.analytics.AnalyticsService;

@RestController
public class RedirectController {

    private final UrlService urlService;
    private final UrlRepository urlRepository;
    private final AnalyticsService analyticsService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public RedirectController(UrlService urlService, UrlRepository urlRepository, AnalyticsService analyticsService) {
        this.urlService = urlService;
        this.urlRepository = urlRepository;
        this.analyticsService = analyticsService;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/{shortId}")
    public ResponseEntity<?> redirect(@PathVariable String shortId, HttpServletRequest request) {
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("URL not found"));
        
        // If password is set, don't redirect yet. Tell the frontend to ask for a password.
        if (entity.getPassword() != null) {
            // Redirect to the frontend password challenge page
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/p/" + shortId))
                    .build();
        }
        
        // NEW: Record Analytics with IP (Only for non-password links here)
        // Password links are recorded in UrlController.resolvePassword instead
        analyticsService.recordClick(shortId, getClientIp(request));

        // No password -> Proceed with normal redirect
        String longUrl = urlService.getLongUrl(shortId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}