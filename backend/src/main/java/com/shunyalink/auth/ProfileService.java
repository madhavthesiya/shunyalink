package com.shunyalink.auth;

import com.shunyalink.exception.NotFoundException;
import com.shunyalink.url.UrlService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Public bio profiles and profile pictures. Moved out of {@code ProfileController}, which now only maps HTTP. */
@Service
public class ProfileService {
    private final AuthService authService;
    private final UserRepository users;
    private final UrlService urlService;
    private final CloudinaryService cloudinary;

    public ProfileService(AuthService authService, UserRepository users, UrlService urlService, CloudinaryService cloudinary) {
        this.authService = authService;
        this.users = users;
        this.urlService = urlService;
        this.cloudinary = cloudinary;
    }

    /** A public bio page: the profile without private fields (email, verification status), plus the bio links. */
    public ProfileResponse publicProfile(String username) {
        ProfileResponse profile = authService.getProfile(username);
        UserEntity user = users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return new ProfileResponse(
                profile.getUsername(),
                profile.getName(),
                null,  // email is never public
                false, // nor is verification status
                profile.getBioText(),
                profile.getThemeColor(),
                profile.getProfileType(),
                profile.getGithubUsername(),
                profile.getLeetcodeUsername(),
                profile.getCodeforcesUsername(),
                profile.getCodeChefHandle(),
                profile.getAtCoderHandle(),
                profile.getProfilePictureUrl(),
                urlService.getBioLinks(user.getId()).stream().map(PublicBioLink::of).toList());
    }

    /**
     * Uploads the picture and stores its URL on the user. Throws {@link IllegalArgumentException} for a file the
     * upload rejects (type, size) and {@link IOException} if the upload itself fails; the controller maps both.
     */
    public String updateProfilePicture(Long userId, MultipartFile file) throws IOException {
        String secureUrl = cloudinary.uploadProfilePicture(file);
        UserEntity user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setProfilePictureUrl(secureUrl);
        users.save(user);
        return secureUrl;
    }
}
