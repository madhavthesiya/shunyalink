package com.shunyalink.auth;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true)
    private String password;  // stored as BCrypt hash  // null for Google users

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String authProvider = "LOCAL";  // LOCAL or GOOGLE

    @Column(nullable = false)
    private boolean emailVerified = false;

    // Link-In-Bio Profile Fields
    @Column(unique = true, length = 50)
    private String username;

    @Column(length = 500)
    private String bioText;

    @Column(length = 20)
    private String themeColor = "#000000";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getBioText() { return bioText; }
    public void setBioText(String bioText) { this.bioText = bioText; }
    
    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; }

}
