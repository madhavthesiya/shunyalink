package com.shunyalink.auth;

import com.shunyalink.exception.BadRequestException;
import com.shunyalink.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** HTTP only: who is asking, what they sent, and how the answer is shaped. The work happens in the services. */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "User Profile", description = "Endpoints for managing user display names, bios, and theme colors")
public class ProfileController {

    private final AuthService authService;
    private final ProfileService profileService;

    public ProfileController(AuthService authService, ProfileService profileService) {
        this.authService = authService;
        this.profileService = profileService;
    }

    @Operation(summary = "Get your own profile", description = "Returns full profile details for the authenticated user.")
    @GetMapping("/me")
    public ProfileResponse getMyProfile(@CurrentUser Long userId) {
        return authService.getProfileById(userId);
    }

    @Operation(summary = "Get public bio profile", description = "Returns public display details and bio-links for a specific username.")
    @GetMapping("/{username}")
    public ProfileResponse getPublicProfile(@PathVariable String username) {
        return profileService.publicProfile(username);
    }

    @Operation(summary = "Update profile settings", description = "Updates display name, bio, and theme preferences for the authenticated user.")
    @PostMapping("/settings")
    public Map<String, String> updateProfileSettings(@RequestBody ProfileUpdateRequest request, @CurrentUser Long userId) {
        authService.updateProfile(userId, request);
        return Map.of("message", "Profile updated successfully");
    }

    @Operation(summary = "Check username availability", description = "Checks if a username is available; a signed-in user's own username counts as available.")
    @GetMapping("/username-check")
    public Map<String, Boolean> checkUsername(@RequestParam String username, @CurrentUser(required = false) Long userId) {
        return Map.of("available", authService.isUsernameAvailable(username, userId));
    }

    @Operation(summary = "Upload profile picture", description = "Uploads a profile picture to Cloudinary and saves the secure URL.")
    @PostMapping(value = "/picture", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadProfilePicture(@RequestParam("file") MultipartFile file,
                                                                    @CurrentUser Long userId) {
        try {
            return ResponseEntity.ok(Map.of("profilePictureUrl", profileService.updateProfilePicture(userId, file)));
        } catch (IllegalArgumentException rejected) {
            throw new BadRequestException(rejected.getMessage()); // wrong type or too large: the message is for the user
        } catch (IOException uploadFailed) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload failed. Please try again."));
        }
    }
}
