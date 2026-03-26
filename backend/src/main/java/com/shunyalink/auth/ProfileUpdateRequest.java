package com.shunyalink.auth;

public class ProfileUpdateRequest {
    private String username;
    private String name;
    private String bioText;
    private String themeColor;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBioText() { return bioText; }
    public void setBioText(String bioText) { this.bioText = bioText; }

    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; }
}
