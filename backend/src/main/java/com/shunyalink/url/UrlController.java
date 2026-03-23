package com.shunyalink.url;
import com.shunyalink.exception.BadRequestException;
import com.shunyalink.rate.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/url")
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;

    @Value("${app.base-url}")
    private String baseUrl;

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim(); // first IP = real user
        }
        return request.getRemoteAddr(); // fallback for local dev
    }

    // Helper to get userId from JWT (returns null for anonymous users)
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return null;
    }

    public UrlController(UrlService urlService, RateLimiterService rateLimiterService) {

        this.urlService = urlService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/shorten")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        String key = "rate:shorten:" + ip;

        // rate limit 10 request per 60 seconds
        rateLimiterService.checkLimit(key,10,60);

        Long userId = getCurrentUserId();
        UrlEntity entity = urlService.shortenUrl(request.getLongUrl(),request.getCustomAlias(), request.getExpiryDays(), userId);
        return new ShortenResponse(
                entity.getShortId(),
                baseUrl + "/" + entity.getShortId(),
                entity.getLongUrl(),
                entity.getCreatedAt()
        );
    }

    @GetMapping("/stats/{shortId}")
    public UrlStatsResponse getStats(@PathVariable String shortId){

        return urlService.getStats(shortId);
    }

    @GetMapping("/my-links")
    public List<UrlStatsResponse> getMyLinks() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");
        return urlService.getMyLinks(userId).stream()
                .map(e -> new UrlStatsResponse(e.getShortId(), e.getLongUrl(), e.getClickCount(), e.getLastAccessedTime(), e.getCreatedAt()))
                .toList();
    }

    @DeleteMapping("/{shortId}")
    public void deleteUrl(@PathVariable String shortId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");
        urlService.deleteUrl(shortId, userId);
    }
}
