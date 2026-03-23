package com.shunyalink.auth;

public class AuthResponse {

    private String token;
    private String email;
    private String name;

    public AuthResponse(String token, String email, String name) {
        this.token = token;
        this.email = email;
        this.name = name;
    }

    public String getToken() { return token; }
    public String getEmail() { return email; }
    public String getName() { return name; }
}