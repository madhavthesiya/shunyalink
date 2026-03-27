package com.shunyalink.url;
import com.shunyalink.exception.BadRequestException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.auth.UserEntity;
import com.shunyalink.exception.ForbiddenException;

import java.util.List;


@RestController
@RequestMapping("/api/v1/url")
public class UrlController {

    private final UrlService urlService;
    private final RateLimiterService rateLimiterService;
    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CsvExportService csvExportService;

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
        if (auth != null && auth.getPrincipal() instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    public UrlController(UrlService urlService, RateLimiterService rateLimiterService,
                         UrlRepository urlRepository, UserRepository userRepository,
                         BCryptPasswordEncoder passwordEncoder, CsvExportService csvExportService) {
        this.urlService = urlService;
        this.rateLimiterService = rateLimiterService;
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.csvExportService = csvExportService;
    }

    @PostMapping("/shorten")
    public ShortenResponse shorten(@Valid @RequestBody ShortenRequest request, HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        String key = "rate:shorten:" + ip;

        // rate limit 10 request per 60 seconds
        rateLimiterService.checkLimit(key,10,60);

        Long userId = getCurrentUserId();

        // Strict Email Verification Check for Logged-In Users
        if (userId != null) {
            UserEntity user = userRepository.findById(userId).orElse(null);
            if (user != null && !user.isEmailVerified() && !"GOOGLE".equals(user.getAuthProvider())) {
                throw new ForbiddenException("You must verify your email address before you can create links.");
            }
        }

        UrlEntity entity = urlService.shortenUrl(request.getLongUrl(),request.getCustomAlias(), request.getExpiryDays(), userId, request.getTitle(),request.getPassword());
        return new ShortenResponse(
                entity.getShortId(),
                baseUrl + "/" + entity.getShortId(),
                entity.getLongUrl(),
                entity.getCreatedAt(),
                entity.getTitle(),
                entity.getPassword() != null
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
        return urlService.getMyLinks(userId);
    }

    @DeleteMapping("/{shortId}")
    public void deleteUrl(@PathVariable String shortId) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");
        urlService.deleteUrl(shortId, userId);
    }

    @PutMapping("/{shortId}/bio-visibility")
    public void toggleBioVisibility(@PathVariable String shortId, @RequestBody java.util.Map<String, Boolean> request) {
        Long userId = getCurrentUserId();
        if (userId == null) throw new BadRequestException("Authentication required");
        Boolean showOnBio = request.get("showOnBio");
        if (showOnBio == null) throw new BadRequestException("showOnBio parameter is required");
        urlService.toggleShowOnBio(shortId, userId, showOnBio);
    }

    @GetMapping("/stats/public")
    public ResponseEntity<PublicStatsResponse> getPublicStats() {
        long totalLinks = urlRepository.count();
        long totalUsers = userRepository.count();
        long totalClicks = urlRepository.sumTotalClicks();
        
        return ResponseEntity.ok(new PublicStatsResponse(totalLinks, totalUsers, totalClicks));
    }

    @PostMapping("/resolve/{shortId}")
    public ResponseEntity<?> resolvePassword(@PathVariable String shortId, @RequestBody java.util.Map<String, String> body) {
        String password = body.get("password");
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new com.shunyalink.exception.NotFoundException("URL not found"));

        // Check password using Bcrypt
        if (entity.getPassword() == null || passwordEncoder.matches(password, entity.getPassword())) {
            urlService.incrementClickCount(shortId); // Increment click analytics!
            return ResponseEntity.ok(java.util.Map.of("longUrl", entity.getLongUrl()));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Incorrect password");
        }
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv() {
        Long userId = getCurrentUserId();
        if (userId == null) throw new com.shunyalink.exception.BadRequestException("Authentication required");

        List<UrlEntity> urls = urlRepository.findByUserIdOrderByCreatedAtDesc(userId);
        String csvContent = csvExportService.exportToCsv(urls);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=links.csv")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
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
