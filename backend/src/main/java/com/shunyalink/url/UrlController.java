package com.shunyalink.url;
import com.shunyalink.rate.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;


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

        UrlEntity entity = urlService.shortenUrl(request.getLongUrl(),request.getCustomAlias(), request.getExpiryDays());
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
}
