package com.shunyalink.auth;

import java.util.List;
import com.shunyalink.url.UrlStatsResponse;

public class ProfileResponse {
    private String username;
    private String name;
    private String email;
    private boolean emailVerified;
    private String bioText;
    private String themeColor;
    private List<UrlStatsResponse> links;

    public ProfileResponse(String username, String name, String email, boolean emailVerified, String bioText, String themeColor, List<UrlStatsResponse> links) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.emailVerified = emailVerified;
        this.bioText = bioText;
        this.themeColor = themeColor;
        this.links = links;
    }

    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getBioText() { return bioText; }
    public String getThemeColor() { return themeColor; }
    public List<UrlStatsResponse> getLinks() { return links; }
}
