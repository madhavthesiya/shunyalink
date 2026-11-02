package com.shunyalink.url;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/url/resolve/{shortId}. */
public record ResolveRequest(
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password is too long")
        String password) {
}
