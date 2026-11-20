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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import java.net.URI;
import com.shunyalink.util.UtcTimes;
import java.time.Instant;
import java.util.Map;
import com.shunyalink.analytics.AnalyticsClickRecorder;
import com.shunyalink.cache.RedirectCache;
import com.shunyalink.security.ClientIpResolver;

@RestController
@Tag(name = "Redirect & Previews", description = "Endpoints for handling short URL redirects and social media rich previews (OG tags)")
public class RedirectController {

    private static final String DEFAULT_TITLE = "ShunyaLink | Smart URL Shortener & Digital Identity";
    private static final String DEFAULT_DESCRIPTION =
            "AI-secured short link by ShunyaLink — smart URL shortener with analytics and Bio-Link profiles.";
    private static final String PROTECTED_TITLE = "Password-protected link | ShunyaLink";
    private static final String PROTECTED_DESCRIPTION =
            "This link is password-protected. Open it to enter the password.";

    private final UrlService urlService;
    private final UrlRepository urlRepository;
    private final AnalyticsClickRecorder analyticsService;
    private final RedirectCache redirectCache;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public RedirectController(UrlService urlService, UrlRepository urlRepository,
                              AnalyticsClickRecorder analyticsService,
                              RedirectCache redirectCache,
                              ClientIpResolver clientIpResolver) {
        this.urlService = urlService;
        this.urlRepository = urlRepository;
        this.analyticsService = analyticsService;
        this.redirectCache = redirectCache;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Redirect or Social Preview", description = "Handles the core redirection logic. Detects social media bots (WhatsApp, Twitter, FB) and returns OG tags for rich previews. Otherwise, redirects to the target URL (or password challenge).")
    @GetMapping("/{shortId}")
    public ResponseEntity<?> redirect(@PathVariable String shortId, HttpServletRequest request) {

        // Redis down or tripped breaker = cache miss; PostgreSQL stays authoritative
        Map<Object, Object> cached = redirectCache.entries(shortId);

        String longUrl;
        boolean hasPassword;
        String title;
        Long urlId = cachedUrlId(cached);

        if (urlId != null) {
            // Cache HIT — zero DB hit on the hot path
            longUrl     = (String) cached.get("longUrl");
            hasPassword = "true".equals(cached.get("hasPassword"));
            title       = (String) cached.get("title");

            // Check expiry from cache
            String expiryStr = (String) cached.get("expiryTime");
            if (expiryStr != null && !expiryStr.isEmpty()
                    && UtcTimes.parse(expiryStr).isBefore(Instant.now())) {
                throw new GoneException("Short URL has expired");
            }
        } else {
            // Cache MISS: unless PostgreSQL just said it doesn't exist, ask it, then cache the answer either way
            if (redirectCache.isKnownMissing(shortId)) {
                throw new NotFoundException("URL not found");
            }
            UrlEntity entity = urlRepository.findByShortId(shortId).orElseThrow(() -> {
                redirectCache.rememberMissing(shortId);
                return new NotFoundException("URL not found");
            });

            if (entity.getExpiryTime() != null && entity.getExpiryTime().isBefore(Instant.now())) {
                throw new GoneException("Short URL has expired");
            }

            longUrl     = entity.getLongUrl();
            hasPassword = entity.getPassword() != null;
            title       = entity.getTitle();
            urlId       = entity.getId();

            // Optional: skipped while Redis is failing.
            redirectCache.storeQuietly(entity);
        }

        // Social Bot Detection for Rich Previews (WhatsApp, Twitter, FB, etc.)
        boolean socialBot = isSocialBot(request.getHeader("User-Agent"));

        // Password check comes first: the User-Agent is client-controlled, so "being a bot"
        // must never unlock the destination of a protected link.
        if (hasPassword) {
            String challengeUrl = frontendUrl + "/p/" + shortId;
            if (socialBot) {
                return socialPreview(PROTECTED_TITLE, PROTECTED_DESCRIPTION, challengeUrl, shortId, request);
            }
            // Redirect to the frontend password challenge page
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(challengeUrl))
                    .build();
        }

        if (socialBot) {
            analyticsService.recordPreview(urlId, shortId, clientIpResolver.resolve(request),
                    request.getHeader("User-Agent"), request.getHeader("Referer"));
            String displayTitle = (title != null && !title.isBlank()) ? title : DEFAULT_TITLE;
            return socialPreview(displayTitle, DEFAULT_DESCRIPTION, longUrl, shortId, request);
        }

        // Record Analytics with IP (Only for non-password links here)
        // Password links are recorded in UrlController.resolvePassword instead
        analyticsService.recordClick(urlId, shortId, clientIpResolver.resolve(request), request.getHeader("User-Agent"), request.getHeader("Referer"));

        // No password -> Proceed with normal redirect (Fix #3: use longUrl directly, no redundant getLongUrl call)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    /** Old/incomplete hashes must reload the whole snapshot, never attach a new ID to an old URL. */
    private Long cachedUrlId(Map<Object, Object> cached) {
        if (cached == null || !(cached.get("longUrl") instanceof String destination) || destination.isBlank()
                || !("true".equals(cached.get("hasPassword")) || "false".equals(cached.get("hasPassword")))) return null;
        try {
            long id = Long.parseLong((String) cached.get("urlId"));
            Object expiry = cached.get("expiryTime");
            if (expiry != null && !"".equals(expiry)) UtcTimes.parse((String) expiry);
            return id > 0 ? id : null;
        } catch (RuntimeException invalid) {
            return null;
        }
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

    private ResponseEntity<String> socialPreview(String title, String description, String targetUrl,
                                                 String shortId, HttpServletRequest request) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(generateSocialHtml(title, description, targetUrl, shortId, request));
    }

    /**
     * Every interpolated value is HTML-escaped: titles come from users or scraped pages,
     * the target URL from users, and the host from the request's Host header.
     */
    private String generateSocialHtml(String rawTitle, String rawDescription, String rawTargetUrl,
                                      String shortId, HttpServletRequest request) {
        String displayTitle = HtmlUtils.htmlEscape(rawTitle);
        String description = HtmlUtils.htmlEscape(rawDescription);
        String targetUrl = HtmlUtils.htmlEscape(rawTargetUrl);

        // Dynamically detect the host (sl.madhavv.me vs shunyalink.madhavv.me)
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        String url = HtmlUtils.htmlEscape(scheme + "://" + serverName + "/" + shortId);

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
                "    <meta http-equiv=\"refresh\" content=\"0;url=" + targetUrl + "\">\n" +
                "</head>\n" +
                "<body>\n" +
                "    <p>Redirecting to <a href=\"" + targetUrl + "\">" + targetUrl + "</a>...</p>\n" +
                "</body>\n" +
                "</html>";
    }
}
