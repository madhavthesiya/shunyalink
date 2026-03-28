package com.shunyalink.url;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ForbiddenException;
import com.shunyalink.rate.RateLimiterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/url")
@Tag(name = "URL Management", description = "Endpoints for creating, managing, and tracking shortened links")
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;
    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CsvExportService csvExportService;
    private final com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public UrlController(UrlService urlService, RateLimiterService rateLimiterService,
                         UrlRepository urlRepository, UserRepository userRepository,
                         PasswordEncoder passwordEncoder, CsvExportService csvExportService,
                         com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository) {
        this.urlService = urlService;
        this.rateLimiterService = rateLimiterService;
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.csvExportService = csvExportService;
        this.globalStatsRepository = globalStatsRepository;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private Long getUserIdFromPrincipal(Object principal) {
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Integer) return ((Integer) principal).longValue();
        return null;
    }

    @Operation(summary = "Create a short URL", description = "Generates a unique short ID for a long URL. Supports custom aliases and password protection.")
    @PostMapping("/shorten")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        rateLimiterService.checkLimit("rate:shorten:" + ip, 10, 60);

        Long userId = getCurrentUserId();
        if (userId != null) {
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user != null && !user.isEmailVerified() && !"GOOGLE".equals(user.getAuthProvider())) {
                throw new ForbiddenException("You must verify your email address before you can create links.");
            }
        }

        UrlEntity entity = urlService.shortenUrl(request.getLongUrl(), request.getCustomAlias(), request.getExpiryDays(), userId, request.getTitle(), request.getPassword());
        return new ShortenResponse(
                entity.getShortId(),
                baseUrl + "/" + entity.getShortId(),
                entity.getLongUrl(),
                entity.getCreatedAt(),
                entity.getTitle(),
                entity.getPassword() != null
        );
    }

    @Operation(summary = "Get user's links", description = "Returns a paginated list of links created by the authenticated user.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/my-links")
    public Page<UrlStatsResponse> getMyLinks(
            @AuthenticationPrincipal Object principal,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = getUserIdFromPrincipal(principal);
        return urlService.getMyLinks(userId, pageable);
    }

    @Operation(summary = "Get link stats", description = "Returns click analytics and metadata for a specific short ID.")
    @GetMapping("/stats/{shortId}")
    public UrlStatsResponse getStats(@PathVariable String shortId) {
        return urlService.getStats(shortId);
    }

    @Operation(summary = "Export link stats to CSV", description = "Downloads analytics for a specific short ID as CSV.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/stats/{shortId}/export")
    public ResponseEntity<byte[]> exportStats(@PathVariable String shortId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");

        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new com.shunyalink.exception.NotFoundException("URL not found"));

        if (!userId.equals(entity.getUserId())) throw new ForbiddenException("Not authorized");

        String csvContent = csvExportService.exportToCsv(List.of(entity));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stats-" + shortId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvContent.getBytes());
    }

    @Operation(summary = "Delete a link", description = "Permanently removes a short link from the system.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @DeleteMapping("/{shortId}")
    public void deleteUrl(@PathVariable String shortId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");
        urlService.deleteUrl(shortId, userId);
    }

    @Operation(summary = "Toggle Bio Link visibility", description = "Shows or hides a link on your public Bio Profile.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/{shortId}/bio-visibility")
    public void toggleBioVisibility(@PathVariable String shortId, @RequestBody Map<String, Boolean> request) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");
        Boolean showOnBio = request.get("showOnBio");
        if (showOnBio == null) throw new BadRequestException("showOnBio parameter is required");
        urlService.toggleShowOnBio(shortId, userId, showOnBio);
    }

    @Operation(summary = "Get public system stats", description = "Returns total links, users, and clicks for landing pages.")
    @GetMapping("/stats/public")
    public ResponseEntity<PublicStatsResponse> getPublicStats() {
        com.shunyalink.analytics.GlobalStatsEntity stats = globalStatsRepository.getStats();
        return ResponseEntity.ok(new PublicStatsResponse(
                stats.getTotalLinks(),
                stats.getTotalUsers(),
                stats.getTotalClicks()));
    }

    @Operation(summary = "Resolve password-protected link", description = "Verifies password and returns the original long URL.")
    @PostMapping("/resolve/{shortId}")
    public ResponseEntity<?> resolvePassword(@PathVariable String shortId, @RequestBody Map<String, String> body) {
        String password = body.get("password");
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new com.shunyalink.exception.NotFoundException("URL not found"));

        if (entity.getPassword() == null || passwordEncoder.matches(password, entity.getPassword())) {
            urlService.incrementClickCount(shortId);
            return ResponseEntity.ok(Map.of("longUrl", entity.getLongUrl()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password");
        }
    }

    @Operation(summary = "Export all user links to CSV", description = "Downloads all user links as a CSV file.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");

        List<UrlEntity> urls = urlRepository.findByUserIdOrderByCreatedAtDesc(userId);
        String csvContent = csvExportService.exportToCsv(urls);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=links.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvContent.getBytes());
    }

    public static class PublicStatsResponse {
        public long totalLinks;
        public long totalUsers;
        public long totalClicks;

        public PublicStatsResponse(long totalLinks, long totalUsers, long totalClicks) {
            this.totalLinks = totalLinks;
            this.totalUsers = totalUsers;
            this.totalClicks = totalClicks;
        }
    }
}
