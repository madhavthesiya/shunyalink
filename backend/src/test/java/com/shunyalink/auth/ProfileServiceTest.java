package com.shunyalink.auth;

import com.shunyalink.url.UrlService;
import com.shunyalink.url.UrlStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceTest {
    private final AuthService authService = mock(AuthService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UrlService urlService = mock(UrlService.class);
    private final CloudinaryService cloudinary = mock(CloudinaryService.class);
    private final ProfileService profiles = new ProfileService(authService, users, urlService, cloudinary);

    private static UserEntity user(long id) {
        UserEntity user = new UserEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id); // JPA assigns IDs; no setter
        user.setUsername("madhav");
        return user;
    }

    @Test
    void aPublicProfile_neverShowsTheEmailOrVerificationStatus_andCarriesTheBioLinks() {
        ProfileResponse full = new ProfileResponse("madhav", "Madhav", "me@example.com", true, "bio", "#fff",
                "PROGRAMMER", "gh", "lc", "cf", "cc", "ac", "https://img.example/p.png", null);
        List<UrlStatsResponse> bioLinks = List.of(mock(UrlStatsResponse.class));
        when(authService.getProfile("madhav")).thenReturn(full);
        when(users.findByUsername("madhav")).thenReturn(Optional.of(user(7L)));
        when(urlService.getBioLinks(7L)).thenReturn(bioLinks);

        ProfileResponse publicView = profiles.publicProfile("madhav");

        assertThat(publicView.getEmail()).isNull();
        assertThat(publicView.isEmailVerified()).isFalse();
        assertThat(publicView.getName()).isEqualTo("Madhav");
        assertThat(publicView.getLinks()).isSameAs(bioLinks);
    }

    @Test
    void aNewProfilePicture_isUploaded_thenStoredOnTheUser() throws Exception {
        var file = new MockMultipartFile("file", "me.png", "image/png", new byte[] {1, 2, 3});
        UserEntity me = user(7L);
        when(cloudinary.uploadProfilePicture(file)).thenReturn("https://img.example/new.png");
        when(users.findById(7L)).thenReturn(Optional.of(me));

        assertThat(profiles.updateProfilePicture(7L, file)).isEqualTo("https://img.example/new.png");
        assertThat(me.getProfilePictureUrl()).isEqualTo("https://img.example/new.png");
        verify(users).save(me);
    }
}
