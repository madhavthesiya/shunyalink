package com.shunyalink.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clean() {
        MDC.clear();
    }

    /** The request ID that log lines written during the request would carry. */
    private String idSeenWhileHandling(MockHttpServletRequest request) throws Exception {
        var seen = new AtomicReference<String>();
        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));
        return seen.get();
    }

    @Test
    void generatesAnId_logsCarryIt_andTheCallerGetsItBack() throws Exception {
        String id = idSeenWhileHandling(new MockHttpServletRequest());

        assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).as("worker thread left clean").isNull();
    }

    @Test
    void keepsAWellFormedIdFromTheCaller_soOneRequestCanBeFollowedAcrossServices() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "frontend-7f3a9c21");

        assertThat(idSeenWhileHandling(request)).isEqualTo("frontend-7f3a9c21");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("frontend-7f3a9c21");
    }

    @Test
    void replacesACallerIdThatCouldForgeLogLines() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "abc\r\nINFO forged");

        String id = idSeenWhileHandling(request);
        assertThat(id).doesNotContain("forged").hasSize(36);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id);
    }

    @Test
    void clearsTheLogContext_evenWhenTheRequestFails() {
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
