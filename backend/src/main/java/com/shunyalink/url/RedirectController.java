package com.shunyalink.url;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    @GetMapping("/{shortId}")
    public void redirect(@PathVariable String shortId,
                         HttpServletResponse response) throws IOException {
        String longUrl = urlService.getLongUrl(shortId);
        response.sendRedirect(longUrl);
    }
}