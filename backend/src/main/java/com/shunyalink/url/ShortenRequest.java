package com.shunyalink.url;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public class ShortenRequest {

    @NotBlank(message = "URL must not be blank")
    @Pattern(
            regexp = "^https?://.*",
            message = "URL must start with http:// or https://"
    )
    private String longUrl;

    @Size(min = 3, max = 20, message = "Custom alias must be 3-20 characters")
    @Pattern(
            regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?$",
            message = "Alias must use lowercase letters, numbers, or hyphens. Cannot start/end with hyphen"
    )
    private String customAlias;

    @Min(value = 1, message = "Expiry must be at least 1 day")
    @Max(value = 365, message = "Expiry cannot exceed 365 days")
    private Integer expiryDays;

    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    @Size(min = 4, max = 20, message = "Password must be 4-20 characters")
    private String password;

    private boolean useAutoTitle = false;

    private Set<String> tags;

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }

    public Integer getExpiryDays() {
        return expiryDays;
    }

    public void setExpiryDays(Integer expiryDays) {
        this.expiryDays = expiryDays;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public boolean isUseAutoTitle() { return useAutoTitle; }

    public void setUseAutoTitle(boolean useAutoTitle) { this.useAutoTitle = useAutoTitle; }

    public Set<String> getTags() { return tags; }

    public void setTags(Set<String> tags) { this.tags = tags; }

}
