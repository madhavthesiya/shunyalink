package com.shunyalink.auth;

import java.util.List;

public class ProfileResponse {
    private String username;
    private String name;
    private String email;
    private boolean emailVerified;
    private String bioText;
    private String themeColor;
    private String profileType;
    private String githubUsername;
    private String leetcodeUsername;
    private String codeforcesUsername;
    private String codeChefHandle;
    private String atCoderHandle;
    private String profilePictureUrl;
    /** Only on a public profile, and only what the bio page shows (see PublicBioLink). */
    private List<PublicBioLink> links;

    public ProfileResponse(String username, String name, String email, boolean emailVerified,
                          String bioText, String themeColor, String profileType,
                          String githubUsername, String leetcodeUsername, String codeforcesUsername,
                          String codeChefHandle, String atCoderHandle, String profilePictureUrl,
                          List<PublicBioLink> links) {
        this.username = username;
        this.name = name;
        this.email = email;
        this.emailVerified = emailVerified;
        this.bioText = bioText;
        this.themeColor = themeColor;
        this.profileType = profileType;
        this.githubUsername = githubUsername;
        this.leetcodeUsername = leetcodeUsername;
        this.codeforcesUsername = codeforcesUsername;
        this.codeChefHandle = codeChefHandle;
        this.atCoderHandle = atCoderHandle;
        this.profilePictureUrl = profilePictureUrl;
        this.links = links;
    }

    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getBioText() { return bioText; }
    public String getThemeColor() { return themeColor; }
    public String getProfileType() { return profileType; }
    public String getGithubUsername() { return githubUsername; }
    public String getLeetcodeUsername() { return leetcodeUsername; }
    public String getCodeforcesUsername() { return codeforcesUsername; }
    public String getCodeChefHandle() { return codeChefHandle; }
    public String getAtCoderHandle() { return atCoderHandle; }
    public String getProfilePictureUrl() { return profilePictureUrl; }
    public List<PublicBioLink> getLinks() { return links; }
}
