package com.shunyalink.url;

import com.shunyalink.exception.GoneException;
import com.shunyalink.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    // ─── GET /{shortId} ──────────────────────────────────────────────

    @Test
    void redirect_validShortId_redirectsToLongUrl() throws Exception {
        when(urlService.getLongUrl("abc123")).thenReturn("https://google.com");

        mockMvc.perform(get("/abc123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "https://google.com"));
    }

    @Test
    void redirect_notFound_returns404() throws Exception {
        when(urlService.getLongUrl("xyz"))
                .thenThrow(new NotFoundException("Short URL not found"));

        mockMvc.perform(get("/xyz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redirect_expiredUrl_returns410() throws Exception {
        when(urlService.getLongUrl("oldlink"))
                .thenThrow(new GoneException("Short URL has expired"));

        mockMvc.perform(get("/oldlink"))
                .andExpect(status().isGone());
    }
}