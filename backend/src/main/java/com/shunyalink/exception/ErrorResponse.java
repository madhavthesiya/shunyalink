package com.shunyalink.exception;

import com.shunyalink.logging.RequestIdFilter;
import org.slf4j.MDC;

import java.time.LocalDateTime;

public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    /** Same value as the X-Request-Id header: a user can quote it, and it finds every log line of the request. */
    private final String requestId;

    public ErrorResponse(LocalDateTime timestamp, int status, String message) {
        this.timestamp = timestamp;
        this.status = status;
        this.message = message;
        this.requestId = MDC.get(RequestIdFilter.MDC_KEY);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getRequestId() {
        return requestId;
    }
}
