package com.shunyalink.url;

import jakarta.validation.constraints.Size;

/**
 * Edit a link's title or password. A field left out (null) is unchanged; the service decides what an empty value
 * means. The limits match {@link ShortenRequest}.
 */
public record UpdateMetadataRequest(
        @Size(max = 100, message = "Title cannot exceed 100 characters")
        String title,

        @Size(max = 20, message = "Password cannot exceed 20 characters")
        String password) {
}
