package com.shunyalink.auth;

import jakarta.validation.constraints.NotBlank;

public class GoogleTokenRequest {

    @NotBlank(message = "ID token is required")
    private String idToken;

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }
}
