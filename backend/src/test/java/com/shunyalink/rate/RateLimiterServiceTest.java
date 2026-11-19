package com.shunyalink.rate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    @Test
    void theLoggedLimitName_neverContainsTheClientIpOrEmail() {
        // Keys end with who is being limited; logs say which limit fired, and the request ID links to the request.
        assertThat(RateLimiterService.limitName("rate:login:203.0.113.7")).isEqualTo("rate:login");
        assertThat(RateLimiterService.limitName("rate:forgot:email:someone@example.com")).isEqualTo("rate:forgot");
        assertThat(RateLimiterService.limitName("rate:login:2001:db8::1")).isEqualTo("rate:login");
        assertThat(RateLimiterService.limitName("rate:resolve:abc:198.51.100.2")).isEqualTo("rate:resolve");
    }
}
