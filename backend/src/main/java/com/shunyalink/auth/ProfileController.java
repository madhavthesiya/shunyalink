package com.shunyalink.auth;

import com.shunyalink.url.DbUrlService;
import com.shunyalink.url.UrlStatsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final AuthService authService;
    private final DbUrlService dbUrlService;
    private final UserRepository userRepository;

    public ProfileController(AuthService authService, DbUrlService dbUrlService, UserRepository userRepository) {
        this.authService = authService;
        this.dbUrlService = dbUrlService;
        this.userRepository = userRepository;
    }

    // Private Endpoint - Load current user's profile for settings
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
            baseProfile.getBioText(),
            baseProfile.getThemeColor(),
            bioLinks
        );
        
        return ResponseEntity.ok(completeProfile);
    }

    // Authenticated Endpoint - Secure User Updates
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
}
