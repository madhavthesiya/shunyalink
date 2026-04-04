package com.shunyalink.auth;

import com.shunyalink.url.DbUrlService;
import com.shunyalink.url.UrlStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "User Profile", description = "Endpoints for managing user display names, bios, and theme colors")
public class ProfileController {

    private final AuthService authService;
    private final DbUrlService dbUrlService;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;

    public ProfileController(AuthService authService, DbUrlService dbUrlService, UserRepository userRepository, CloudinaryService cloudinaryService) {
        this.authService = authService;
        this.dbUrlService = dbUrlService;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
    }

    // Private Endpoint - Load current user's profile for settings
    @Operation(summary = "Get your own profile", description = "Returns full profile details for the authenticated user.")
    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(@AuthenticationPrincipal Object principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        Long userId = null;
        if (principal instanceof Long) {
            userId = (Long) principal;
        } else if (principal instanceof Integer) {
            userId = ((Integer) principal).longValue();
        }

        ProfileResponse profile = authService.getProfileById(userId);
        return ResponseEntity.ok(profile);
    }

    // Public Endpoint - No Authentication Required
    @Operation(summary = "Get public bio profile", description = "Returns public display details and bio-links for a specific username.")
    @GetMapping("/{username}")
    public ResponseEntity<ProfileResponse> getPublicProfile(@PathVariable String username) {
        // Fetch base profile data (throws 400 or 404 if not found)
        ProfileResponse baseProfile = authService.getProfile(username);
        
        // Find user entity to get User ID
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
            
        // Fetch only the URLs marked to "show on bio"
        List<UrlStatsResponse> bioLinks = dbUrlService.getBioLinks(user.getId());
        
        // Assemble complete response
        ProfileResponse completeProfile = new ProfileResponse(
            baseProfile.getUsername(),
            baseProfile.getName(),
            null,  // Don't expose email publicly
            false, // Don't expose verification status publicly
            baseProfile.getBioText(),
            baseProfile.getThemeColor(),
            baseProfile.getProfileType(),
            baseProfile.getGithubUsername(),
            baseProfile.getLeetcodeUsername(),
            baseProfile.getCodeforcesUsername(),
            baseProfile.getCodeChefHandle(),
            baseProfile.getAtCoderHandle(),
            baseProfile.getProfilePictureUrl(),
            bioLinks
        );
        
        return ResponseEntity.ok(completeProfile);
    }

    // Authenticated Endpoint - Secure User Updates
    @Operation(summary = "Update profile settings", description = "Updates display name, bio, and theme preferences for the authenticated user.")
    @PostMapping("/settings")
    public ResponseEntity<Map<String, String>> updateProfileSettings(
            @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal Object principal) {
                
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        Long userId = null;
        if (principal instanceof Long) {
            userId = (Long) principal;
        } else if (principal instanceof Integer) {
            userId = ((Integer) principal).longValue();
        } else {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid principal type"));
        }

        authService.updateProfile(userId, request);
        return ResponseEntity.ok(Map.of("message", "Profile updated successfully"));
    }

    @Operation(summary = "Check username availability", description = "Checks if a username is available for a given user ID.")
    @GetMapping("/username-check")
    public ResponseEntity<Map<String, Boolean>> checkUsername(
            @RequestParam String username,
            @AuthenticationPrincipal Object principal) {
        
        Long userId = null;
        if (principal instanceof Long) {
            userId = (Long) principal;
        } else if (principal instanceof Integer) {
            userId = ((Integer) principal).longValue();
        }

        boolean isAvailable = authService.isUsernameAvailable(username, userId);
        return ResponseEntity.ok(Map.of("available", isAvailable));
    }

    @Operation(summary = "Upload profile picture", description = "Uploads a profile picture to Cloudinary and saves the secure URL.")
    @PostMapping(value = "/picture", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadProfilePicture(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Object principal) {

        if (principal == null) return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));

        Long userId = null;
        if (principal instanceof Long) {
            userId = (Long) principal;
        } else if (principal instanceof Integer) {
            userId = ((Integer) principal).longValue();
        } else {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid session"));
        }

        try {
            String secureUrl = cloudinaryService.uploadProfilePicture(file);
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setProfilePictureUrl(secureUrl);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("profilePictureUrl", secureUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Upload failed. Please try again."));
        }
    }
}
