package com.shunyalink.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/auth/reset-password. BCrypt ignores input beyond 72 bytes, hence the cap. */
public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String newPassword) {
}
