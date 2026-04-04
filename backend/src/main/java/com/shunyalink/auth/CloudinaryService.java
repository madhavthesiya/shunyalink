package com.shunyalink.auth;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB in bytes

    public CloudinaryService(@Value("${CLOUDINARY_URL}") String cloudinaryUrl) {
        this.cloudinary = new Cloudinary(cloudinaryUrl);
        this.cloudinary.config.secure = true;
    }

    /**
     * Uploads a profile picture to Cloudinary with smart compression.
     * - c_fill: crops to fill the square (face-aware)
     * - w_400, h_400: shrinks to 400x400 pixels
     * - q_auto: auto-selects the best quality level
     * - f_auto: converts to WebP/AVIF for smallest size
     *
     * @param file the uploaded image file
     * @return the secure CDN URL of the uploaded image
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if the file is too large or not an image
     */
    public String uploadProfilePicture(MultipartFile file) throws IOException {
        // Guard 1: Reject non-image content types
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed (JPEG, PNG, WEBP).");
        }

        // Guard 2: Reject files over 5MB before streaming to Cloudinary
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Profile picture must be smaller than 5MB.");
        }

        // Upload to Cloudinary with auto-compression transformations
        Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", "shunyalink/profile_pictures",
                "transformation", "c_fill,w_400,h_400,q_auto,f_auto",
                "resource_type", "image"
        ));

        return (String) result.get("secure_url");
    }
}
