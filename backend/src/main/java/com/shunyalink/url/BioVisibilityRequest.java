package com.shunyalink.url;

import jakarta.validation.constraints.NotNull;

/** Show or hide a link on the owner's public bio page. */
public record BioVisibilityRequest(
        @NotNull(message = "showOnBio is required")
        Boolean showOnBio) {
}
