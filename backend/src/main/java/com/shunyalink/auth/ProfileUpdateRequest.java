package com.shunyalink.auth;

public class ProfileUpdateRequest {
    private String username;
    private String name;
    private String bioText;
    private String themeColor;
    private String profileType;
    private String githubUsername;
    private String leetcodeUsername;
    private String codeforcesUsername;
    private String codeChefHandle;
    private String atCoderHandle;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBioText() { return bioText; }
    public void setBioText(String bioText) { this.bioText = bioText; }

    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; }

    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }

    public String getGithubUsername() { return githubUsername; }
    public void setGithubUsername(String githubUsername) { this.githubUsername = githubUsername; }

    public String getLeetcodeUsername() { return leetcodeUsername; }
    public void setLeetcodeUsername(String leetcodeUsername) { this.leetcodeUsername = leetcodeUsername; }

    public String getCodeforcesUsername() { return codeforcesUsername; }
    public void setCodeforcesUsername(String codeforcesUsername) { this.codeforcesUsername = codeforcesUsername; }

    public String getCodeChefHandle() { return codeChefHandle; }
    public void setCodeChefHandle(String codeChefHandle) { this.codeChefHandle = codeChefHandle; }

    public String getAtCoderHandle() { return atCoderHandle; }
    public void setAtCoderHandle(String atCoderHandle) { this.atCoderHandle = atCoderHandle; }
}
