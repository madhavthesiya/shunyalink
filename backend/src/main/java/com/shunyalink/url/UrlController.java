package com.shunyalink.url;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.exception.BadRequestException;
import com.shunyalink.exception.ForbiddenException;
import com.shunyalink.exception.UnauthorizedException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.ClientIpResolver;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final CsvExportService csvExportService;
    private final com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository;
    private final com.shunyalink.analytics.AnalyticsService analyticsService;
    private final CsvImportService csvImportService;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.base-url}")
    private String baseUrl;

    public UrlController(UrlService urlService, RateLimiterService rateLimiterService,
                         UrlRepository urlRepository, UserRepository userRepository,
                         CsvExportService csvExportService,
                         com.shunyalink.analytics.GlobalStatsRepository globalStatsRepository,
                         com.shunyalink.analytics.AnalyticsService analyticsService,
                         CsvImportService csvImportService,
                         ClientIpResolver clientIpResolver) {
        this.urlService = urlService;
        this.rateLimiterService = rateLimiterService;
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.csvExportService = csvExportService;
        this.globalStatsRepository = globalStatsRepository;
        this.analyticsService = analyticsService;
        this.csvImportService = csvImportService;
        this.clientIpResolver = clientIpResolver;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private Long requireUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new UnauthorizedException("Authentication required");
        return userId;
    }

    private Long getUserIdFromPrincipal(Object principal) {
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof Integer) return ((Integer) principal).longValue();
        return null;
    }

    @Operation(summary = "Create a short URL", description = "Generates a unique short ID for a long URL. Supports custom aliases and password protection.")
    @PostMapping("/shorten")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {
        String ip = clientIpResolver.resolve(httpRequest);
        rateLimiterService.checkLimit("rate:shorten:" + ip, 10, 60);

        Long userId = getCurrentUserId();
        if (userId != null) {
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user != null && !user.isEmailVerified() && !"GOOGLE".equals(user.getAuthProvider())) {
                throw new ForbiddenException("You must verify your email address before you can create links.");
            }
        }

        UrlEntity entity = urlService.shortenUrl(request.getLongUrl(), request.getCustomAlias(), request.getExpiryDays(), userId, request.getTitle(), request.getPassword(), request.isUseAutoTitle(), request.getTags());
        return new ShortenResponse(
                entity.getShortId(),
                baseUrl + "/" + entity.getShortId(),
                entity.getLongUrl(),
                entity.getCreatedAt(),
                entity.getTitle(),
                entity.getPassword() != null,
                entity.getCategory(),
                entity.getTags()
        );
    }

    @Operation(summary = "Get user's links", description = "Returns a paginated list of links created by the authenticated user.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/my-links")
    public Page<UrlStatsResponse> getMyLinks(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = getUserIdFromPrincipal(principal);
        return urlService.getMyLinks(userId, search, pageable);
    }

    @Operation(summary = "Reorder links", description = "Updates the sequence of links for the authenticated user.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorderLinks(
            @AuthenticationPrincipal Object principal,
            @RequestBody ReorderRequest request) {
        Long userId = getUserIdFromPrincipal(principal);
        urlService.reorderLinks(userId, request.getShortIds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get link stats", description = "Returns click analytics and metadata for a specific short ID. Supports time ranges: '24h', '7d', and 'all'.")
    @GetMapping("/stats/{shortId}")
    public UrlStatsResponse getStats(
            @PathVariable String shortId,
            @RequestParam(defaultValue = "all") String range) {
        
        long lookbackMs = switch (range) {
            case "24h" -> 86400000L;
            case "7d" -> 604800000L;
            default -> 0L; // All time
        };
        
        return urlService.getStats(shortId, requireUserId(), lookbackMs);
    }

    @Operation(summary = "Export link stats to CSV", description = "Downloads analytics for a specific short ID as CSV.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/stats/{shortId}/export")
    public ResponseEntity<byte[]> exportStats(@PathVariable String shortId) {
        Long userId = requireUserId();

        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new com.shunyalink.exception.NotFoundException("URL not found"));

        if (!userId.equals(entity.getUserId())) throw new ForbiddenException("Not authorized");

        String csvContent = csvExportService.exportToCsv(List.of(entity));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=stats-" + shortId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvContent.getBytes());
    }

    @Operation(summary = "Delete a short URL", description = "Permanently removes a short link from the system.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @DeleteMapping("/{shortId}")
    public ResponseEntity<?> deleteUrl(@PathVariable String shortId, @AuthenticationPrincipal Long userId) {
        urlService.deleteUrl(shortId, userId);
        return ResponseEntity.ok(Map.of("message", "URL deleted successfully"));
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Bulk delete short URLs", security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<?> bulkDelete(@RequestBody List<String> shortIds, @AuthenticationPrincipal Long userId) {
        if (shortIds == null || shortIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No IDs provided"));
        }
        urlService.deleteUrls(shortIds, userId);
        return ResponseEntity.ok(Map.of("message", "URLs deleted successfully", "count", shortIds.size()));
    }

    @PostMapping("/bulk-import")
    @Operation(summary = "Bulk import short URLs via CSV", security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<?> bulkImport(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file, HttpServletRequest httpRequest) {
        Long userId = requireUserId();
        
        String ip = clientIpResolver.resolve(httpRequest);
        rateLimiterService.checkLimit("rate:import:" + ip, 5, 3600); // 5 imports per hour max

        Map<String, Object> result = csvImportService.importCsv(file, userId);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Toggle Bio Link visibility", description = "Shows or hides a link on your public Bio Profile.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/{shortId}/bio-visibility")
    public void toggleBioVisibility(@PathVariable String shortId, @RequestBody Map<String, Boolean> request) {
        Long userId = requireUserId();
        Boolean showOnBio = request.get("showOnBio");
        if (showOnBio == null) throw new BadRequestException("showOnBio parameter is required");
        urlService.toggleShowOnBio(shortId, userId, showOnBio);
    }

    @Operation(summary = "Update link metadata", description = "Allows editing the title and password of an existing link.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PatchMapping("/{shortId}/metadata")
    public void updateMetadata(@PathVariable String shortId, @RequestBody Map<String, String> request) {
        Long userId = requireUserId();
        
        String title = request.get("title");
        String password = request.get("password");
        
        urlService.updateUrlMetadata(shortId, userId, title, password);
    }

    @Operation(summary = "Update link tags", description = "Allows editing the tags of an existing link.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PatchMapping("/{shortId}/tags")
    public void updateTags(@PathVariable String shortId, @RequestBody Map<String, java.util.Set<String>> request) {
        Long userId = requireUserId();
        
        java.util.Set<String> tags = request.get("tags");
        urlService.updateTags(shortId, userId, tags);
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
    public Map<String, String> resolvePassword(@PathVariable String shortId, @Valid @RequestBody ResolveRequest body, HttpServletRequest request) {
        String ip = clientIpResolver.resolve(request);
        // The per-client limit stops a single guesser; the per-link ceiling bounds guessing
        // spread across many IPs. Both run before any password comparison.
        rateLimiterService.checkLimit("rate:resolve:" + shortId + ":" + ip, 5, 60);
        rateLimiterService.checkLimit("rate:resolve:" + shortId, 100, 3600);

        // Throws 401 on a wrong password; verification (and legacy upgrade) lives in the service
        String longUrl = urlService.resolveProtectedLink(shortId, body.password());
        analyticsService.recordClick(shortId, clientIpResolver.resolve(request), request.getHeader("User-Agent"), request.getHeader("Referer"));
        return Map.of("longUrl", longUrl);
    }

    @Operation(summary = "Export all user links to CSV", description = "Downloads all user links as a CSV file.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        Long userId = requireUserId();

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
