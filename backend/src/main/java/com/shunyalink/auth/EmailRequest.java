package com.shunyalink.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of the forgot-password and resend-verification endpoints. */
public record EmailRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email) {
}
