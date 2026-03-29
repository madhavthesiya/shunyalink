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
@Tag(name = "Redirect & Previews", description = "Endpoints for handling short URL redirects and social media rich previews (OG tags)")
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

    @Operation(summary = "Redirect or Social Preview", description = "Handles the core redirection logic. Detects social media bots (WhatsApp, Twitter, FB) and returns OG tags for rich previews. Otherwise, redirects to the target URL (or password challenge).")
    @GetMapping("/{shortId}")
    public ResponseEntity<?> redirect(@PathVariable String shortId, HttpServletRequest request) {
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("URL not found"));
        
        // NEW: Social Bot Detection for Rich Previews (WhatsApp, Twitter, FB, etc.)
        String userAgent = request.getHeader("User-Agent");
        if (isSocialBot(userAgent)) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(generateSocialHtml(entity, request));
        }

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

    private boolean isSocialBot(String ua) {
        if (ua == null) return false;
        String lowerUa = ua.toLowerCase();
        return lowerUa.contains("whatsapp") || 
               lowerUa.contains("facebookexternalhit") || 
               lowerUa.contains("twitterbot") || 
               lowerUa.contains("slackbot") || 
               lowerUa.contains("linkedinbot") || 
               lowerUa.contains("discordbot") ||
               lowerUa.contains("telegrambot");
    }

    private String generateSocialHtml(UrlEntity entity, HttpServletRequest request) {
        String title = entity.getTitle() != null && !entity.getTitle().isBlank() ? entity.getTitle() : "ShunyaLink | Shorten & Brand";
        String description = "This link was shortened and secured by ShunyaLink. Build your own professional Bio-Link profile for free.";
        
        // Dynamically detect the host (sl.madhavv.me vs shunyalink.madhavv.me)
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        String url = scheme + "://" + serverName + "/" + entity.getShortId();
        
        // Branded image remains on the main frontend domain
        String imageUrl = "https://shunyalink.madhavv.me/placeholder-logo.png"; 

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>" + title + "</title>\n" +
                "    <meta name=\"description\" content=\"" + description + "\">\n" +
                "    <meta property=\"og:title\" content=\"" + title + "\">\n" +
                "    <meta property=\"og:description\" content=\"" + description + "\">\n" +
                "    <meta property=\"og:image\" content=\"" + imageUrl + "\">\n" +
                "    <meta property=\"og:url\" content=\"" + url + "\">\n" +
                "    <meta property=\"og:type\" content=\"website\">\n" +
                "    <meta name=\"twitter:card\" content=\"summary_large_image\">\n" +
                "    <meta name=\"twitter:title\" content=\"" + title + "\">\n" +
                "    <meta name=\"twitter:description\" content=\"" + description + "\">\n" +
                "    <meta name=\"twitter:image\" content=\"" + imageUrl + "\">\n" +
                "    <meta http-equiv=\"refresh\" content=\"0;url=" + entity.getLongUrl() + "\">\n" +
                "</head>\n" +
                "<body>\n" +
                "    <p>Redirecting to <a href=\"" + entity.getLongUrl() + "\">" + entity.getLongUrl() + "</a>...</p>\n" +
                "</body>\n" +
                "</html>";
    }
}