package com.shunyalink.auth;

import com.shunyalink.security.ClientIpResolver;
import com.shunyalink.security.CustomUserDetailsService;
import com.shunyalink.security.JwtBlacklistService;
import com.shunyalink.security.JwtService;
import com.shunyalink.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code @CurrentUser} through the real web layer: required on private endpoints, optional on public ones. */
@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, ClientIpResolver.class})
class ProfileControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuthService authService;
    @MockBean private ProfileService profileService;
    @MockBean private JwtService jwtService;
    @MockBean private JwtBlacklistService jwtBlacklistService;
    @MockBean private CustomUserDetailsService customUserDetailsService;

    private static RequestPostProcessor as(long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @Test
    void myProfile_isForTheSignedInUser() throws Exception {
        when(authService.getProfileById(7L)).thenReturn(new ProfileResponse("madhav", "Madhav", "me@example.com",
                true, null, null, null, null, null, null, null, null, null, null));

        mockMvc.perform(get("/api/v1/profile/me").with(as(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("madhav"));
    }

    @Test
    void myProfile_withoutSigningIn_is401_andTouchesNothing() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")).andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    void usernameCheck_worksForAnonymousVisitors_andCountsYourOwnNameAsAvailable() throws Exception {
        when(authService.isUsernameAvailable(eq("taken"), isNull())).thenReturn(false);
        when(authService.isUsernameAvailable("taken", 7L)).thenReturn(true); // it's the signed-in user's own name

        mockMvc.perform(get("/api/v1/profile/username-check").param("username", "taken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(get("/api/v1/profile/username-check").param("username", "taken").with(as(7L)))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void settings_updateTheSignedInUser() throws Exception {
        mockMvc.perform(post("/api/v1/profile/settings").with(as(7L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Madhav\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated successfully"));

        verify(authService).updateProfile(eq(7L), any(ProfileUpdateRequest.class));
    }
}
