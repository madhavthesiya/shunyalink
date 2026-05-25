package com.shunyalink.url;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.shunyalink.exception.NotFoundException;
import com.shunyalink.exception.GoneException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.shunyalink.analytics.AnalyticsService;

@RestController
@Tag(name = "Redirect & Previews", description = "Endpoints for handling short URL redirects and social media rich previews (OG tags)")
public class RedirectController {

    private final UrlService urlService;
    private final UrlRepository urlRepository;
    private final AnalyticsService analyticsService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public RedirectController(UrlService urlService, UrlRepository urlRepository,
                              AnalyticsService analyticsService,
                              RedisTemplate<String, String> redisTemplate) {
        this.urlService = urlService;
        this.urlRepository = urlRepository;
        this.analyticsService = analyticsService;
        this.redisTemplate = redisTemplate;
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

        String cacheKey = "url:entity:" + shortId;
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(cacheKey);

        String longUrl;
        boolean hasPassword;
        String title;

        if (!cached.isEmpty()) {
            // ✅ Cache HIT — zero DB hit on the hot path
            longUrl     = (String) cached.get("longUrl");
            hasPassword = "true".equals(cached.get("hasPassword"));
            title       = (String) cached.get("title");

            // Check expiry from cache
            String expiryStr = (String) cached.get("expiryTime");
            if (expiryStr != null && !expiryStr.isEmpty()
                    && LocalDateTime.parse(expiryStr).isBefore(LocalDateTime.now())) {
                throw new GoneException("Short URL has expired");
            }
        } else {
            // Cache MISS — hit DB, then populate cache for next time
            UrlEntity entity = urlRepository.findByShortId(shortId)
                    .orElseThrow(() -> new NotFoundException("URL not found"));

            if (entity.getExpiryTime() != null && entity.getExpiryTime().isBefore(LocalDateTime.now())) {
                throw new GoneException("Short URL has expired");
            }

            longUrl     = entity.getLongUrl();
            hasPassword = entity.getPassword() != null;
            title       = entity.getTitle();

            // Cache the redirect-critical fields as a Redis Hash
            cacheEntityForRedirect(cacheKey, entity);
        }

        // Social Bot Detection for Rich Previews (WhatsApp, Twitter, FB, etc.)
        String userAgent = request.getHeader("User-Agent");
        if (isSocialBot(userAgent)) {
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html; charset=UTF-8")
                    .body(generateSocialHtml(title, longUrl, shortId, request));
        }

        // If password is set, don't redirect yet. Tell the frontend to ask for a password.
        if (hasPassword) {
            // Redirect to the frontend password challenge page
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/p/" + shortId))
                    .build();
        }

        // Record Analytics with IP (Only for non-password links here)
        // Password links are recorded in UrlController.resolvePassword instead
        analyticsService.recordClick(shortId, getClientIp(request), request.getHeader("User-Agent"), request.getHeader("Referer"));

        // No password -> Proceed with normal redirect (Fix #3: use longUrl directly, no redundant getLongUrl call)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    /**
     * Caches the redirect-critical fields of a UrlEntity as a Redis Hash.
     * This allows subsequent redirect requests to skip the database entirely.
     */
    private void cacheEntityForRedirect(String cacheKey, UrlEntity entity) {
        Map<String, String> fields = new HashMap<>();
        fields.put("longUrl", entity.getLongUrl());
        fields.put("hasPassword", String.valueOf(entity.getPassword() != null));
        fields.put("title", entity.getTitle() != null ? entity.getTitle() : "");
        fields.put("expiryTime", entity.getExpiryTime() != null
                ? entity.getExpiryTime().toString() : "");

        redisTemplate.opsForHash().putAll(cacheKey, fields);
        redisTemplate.expire(cacheKey, 24, TimeUnit.HOURS);
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

    private String generateSocialHtml(String title, String longUrl, String shortId, HttpServletRequest request) {
        String displayTitle = (title != null && !title.isBlank()) ? title : "ShunyaLink | Smart URL Shortener & Digital Identity";
        String description = "AI-secured short link by ShunyaLink — smart URL shortener with analytics and Bio-Link profiles.";
        
        // Dynamically detect the host (sl.madhavv.me vs shunyalink.madhavv.me)
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        String url = scheme + "://" + serverName + "/" + shortId;
        
        // Branded social image remains on the main frontend domain
        String imageUrl = "https://shunyalink.madhavv.me/logo-social.png"; 

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>" + displayTitle + "</title>\n" +
                "    <meta name=\"description\" content=\"" + description + "\">\n" +
                "    <meta property=\"og:title\" content=\"" + displayTitle + "\">\n" +
                "    <meta property=\"og:description\" content=\"" + description + "\">\n" +
                "    <meta property=\"og:image\" content=\"" + imageUrl + "\">\n" +
                "    <meta property=\"og:url\" content=\"" + url + "\">\n" +
                "    <meta property=\"og:type\" content=\"website\">\n" +
                "    <meta name=\"twitter:card\" content=\"summary_large_image\">\n" +
                "    <meta name=\"twitter:title\" content=\"" + displayTitle + "\">\n" +
                "    <meta name=\"twitter:description\" content=\"" + description + "\">\n" +
                "    <meta name=\"twitter:image\" content=\"" + imageUrl + "\">\n" +
                "    <meta http-equiv=\"refresh\" content=\"0;url=" + longUrl + "\">\n" +
                "</head>\n" +
                "<body>\n" +
                "    <p>Redirecting to <a href=\"" + longUrl + "\">" + longUrl + "</a>...</p>\n" +
                "</body>\n" +
                "</html>";
    }
}