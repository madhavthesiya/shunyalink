package com.shunyalink.url;

import com.shunyalink.analytics.AnalyticsClickRecorder;
import com.shunyalink.analytics.PublicStatsService;
import com.shunyalink.analytics.PublicStatsService.PublicStats;
import com.shunyalink.auth.AuthService;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP only: who is asking ({@link CurrentUser}), what they sent (validated request types), request-level policy
 * such as rate limits, and the response shape. Business rules live in the services.
 */
@RestController
@RequestMapping("/api/v1/url")
@Tag(name = "URL Management", description = "Endpoints for creating, managing, and tracking shortened links")
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;
    private final AuthService authService;
    private final LinkExportService linkExportService;
    private final PublicStatsService publicStatsService;
    private final AnalyticsClickRecorder analyticsService;
    private final CsvImportService csvImportService;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.base-url}")
    private String baseUrl;

    public UrlController(UrlService urlService, RateLimiterService rateLimiterService, AuthService authService,
                         LinkExportService linkExportService, PublicStatsService publicStatsService,
                         AnalyticsClickRecorder analyticsService, CsvImportService csvImportService,
                         ClientIpResolver clientIpResolver) {
        this.urlService = urlService;
        this.rateLimiterService = rateLimiterService;
        this.authService = authService;
        this.linkExportService = linkExportService;
        this.publicStatsService = publicStatsService;
        this.analyticsService = analyticsService;
        this.csvImportService = csvImportService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Create a short URL", description = "Generates a unique short ID for a long URL. Supports custom aliases and password protection.")
    @PostMapping("/shorten")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest,
                                   @CurrentUser(required = false) Long userId) {
        rateLimiterService.checkLimit("rate:shorten:" + clientIpResolver.resolve(httpRequest), 10, 60);
        if (userId != null) {
            authService.requireVerifiedEmail(userId); // anonymous links are allowed; accounts must be verified
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
            @CurrentUser Long userId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return urlService.getMyLinks(userId, search, pageable);
    }

    @Operation(summary = "Reorder links", description = "Updates the sequence of links for the authenticated user.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorderLinks(@CurrentUser Long userId, @Valid @RequestBody ReorderRequest request) {
        urlService.reorderLinks(userId, request.getShortIds());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get link stats", description = "Returns click analytics and metadata for a specific short ID. Supports time ranges: '24h', '7d', '30d', and 'all'.")
    @GetMapping("/stats/{shortId}")
    public UrlStatsResponse getStats(
            @PathVariable String shortId,
            @RequestParam(defaultValue = "all") String range,
            @CurrentUser Long userId) {
        long lookbackMs = switch (range) {
            case "24h" -> 86400000L;
            case "7d" -> 604800000L;
            case "30d" -> 2592000000L;
            default -> 0L; // All time
        };
        return urlService.getStats(shortId, userId, lookbackMs);
    }

    @Operation(summary = "Export link stats to CSV", description = "Downloads analytics for a specific short ID as CSV.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/stats/{shortId}/export")
    public ResponseEntity<byte[]> exportStats(@PathVariable String shortId, @CurrentUser Long userId) {
        return csvDownload("stats-" + shortId + ".csv", linkExportService.linkCsv(shortId, userId));
    }

    @Operation(summary = "Delete a short URL", description = "Permanently removes a short link from the system.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @DeleteMapping("/{shortId}")
    public ResponseEntity<?> deleteUrl(@PathVariable String shortId, @CurrentUser Long userId) {
        urlService.deleteUrl(shortId, userId);
        return ResponseEntity.ok(Map.of("message", "URL deleted successfully"));
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Bulk delete short URLs", security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<?> bulkDelete(
            @RequestBody @Size(max = 200, message = "At most 200 links can be deleted at once") List<@NotBlank String> shortIds,
            @CurrentUser Long userId) {
        if (shortIds == null || shortIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "No IDs provided"));
        }
        urlService.deleteUrls(shortIds, userId);
        return ResponseEntity.ok(Map.of("message", "URLs deleted successfully", "count", shortIds.size()));
    }

    @PostMapping("/bulk-import")
    @Operation(summary = "Bulk import short URLs via CSV", security = @SecurityRequirement(name = "Bearer Authentication"))
    public ResponseEntity<?> bulkImport(@RequestParam("file") MultipartFile file, HttpServletRequest httpRequest,
                                        @CurrentUser Long userId) {
        authService.requireVerifiedEmail(userId);
        rateLimiterService.checkLimit("rate:import:" + clientIpResolver.resolve(httpRequest), 5, 3600); // 5 imports per hour max
        return ResponseEntity.ok(csvImportService.importCsv(file, userId));
    }

    @Operation(summary = "Toggle Bio Link visibility", description = "Shows or hides a link on your public Bio Profile.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PutMapping("/{shortId}/bio-visibility")
    public void toggleBioVisibility(@PathVariable String shortId, @Valid @RequestBody BioVisibilityRequest request,
                                    @CurrentUser Long userId) {
        urlService.toggleShowOnBio(shortId, userId, request.showOnBio());
    }

    @Operation(summary = "Update link metadata", description = "Allows editing the title and password of an existing link.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PatchMapping("/{shortId}/metadata")
    public void updateMetadata(@PathVariable String shortId, @Valid @RequestBody UpdateMetadataRequest request,
                               @CurrentUser Long userId) {
        urlService.updateUrlMetadata(shortId, userId, request.title(), request.password());
    }

    @Operation(summary = "Update link tags", description = "Allows editing the tags of an existing link.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @PatchMapping("/{shortId}/tags")
    public void updateTags(@PathVariable String shortId, @Valid @RequestBody UpdateTagsRequest request,
                           @CurrentUser Long userId) {
        urlService.updateTags(shortId, userId, request.tags());
    }

    @Operation(summary = "Get public system stats", description = "Returns total links, users, and clicks for landing pages.")
    @GetMapping("/stats/public")
    public PublicStats getPublicStats() {
        return publicStatsService.current();
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
        ResolvedLink resolved = urlService.resolveProtectedLinkWithIdentity(shortId, body.password());
        analyticsService.recordClick(resolved.urlId(), shortId, ip, request.getHeader("User-Agent"), request.getHeader("Referer"));
        return Map.of("longUrl", resolved.longUrl());
    }

    @Operation(summary = "Reveal link password", description = "Owner-only: returns the link's password so it can be shared again. Rate-limited per user.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/{shortId}/reveal-password")
    public Map<String, Object> revealPassword(@PathVariable String shortId, @CurrentUser Long userId) {
        // Bounds how many passwords a stolen session could extract
        rateLimiterService.checkLimit("rate:reveal:" + userId, 20, 3600);

        String password = urlService.revealLinkPassword(shortId, userId);
        Map<String, Object> body = new HashMap<>(); // Map.of rejects the null that marks an unrecoverable password
        body.put("password", password);
        body.put("recoverable", password != null);
        return body;
    }

    @Operation(summary = "Export all user links to CSV", description = "Downloads all user links as a CSV file.", security = @SecurityRequirement(name = "Bearer Authentication"))
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(@CurrentUser Long userId) {
        return csvDownload("links.csv", linkExportService.allLinksCsv(userId));
    }

    private static ResponseEntity<byte[]> csvDownload(String filename, String csv) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
