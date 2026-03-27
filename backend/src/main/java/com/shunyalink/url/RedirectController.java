package com.shunyalink.url;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import com.shunyalink.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.net.URI;

@RestController
public class RedirectController {

    private final UrlService urlService;
    private final UrlRepository urlRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public RedirectController(UrlService urlService, UrlRepository urlRepository) {
        this.urlService = urlService;
        this.urlRepository = urlRepository;
    }

    @GetMapping("/{shortId}")
    public ResponseEntity<?> redirect(@PathVariable String shortId) {
        UrlEntity entity = urlRepository.findByShortId(shortId)
                .orElseThrow(() -> new NotFoundException("URL not found"));
        // If password is set, don't redirect. Tell the frontend to ask for a password.
        if (entity.getPassword() != null) {
            // Redirect to the frontend password challenge page
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(frontendUrl + "/p/" + shortId))
                    .build();
        }
        // No password -> Proceed with normal redirect
        String longUrl = urlService.getLongUrl(shortId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }
}