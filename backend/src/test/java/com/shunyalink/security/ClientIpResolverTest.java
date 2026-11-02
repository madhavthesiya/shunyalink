package com.shunyalink.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    @Test
    void noForwardedHeader_usesTheSocketAddress() {
        assertThat(resolver.resolve(request("203.0.113.7", null))).isEqualTo("203.0.113.7");
    }

    @Test
    void azureStyleHopWithPort_returnsTheIpWithoutThePort() {
        // Without stripping, every new TCP connection would get its own rate-limit bucket.
        assertThat(resolver.resolve(request("169.254.129.1", "203.0.113.7:51234"))).isEqualTo("203.0.113.7");
    }

    @Test
    void clientSuppliedEntries_areIgnored() {
        // The client wrote "1.1.1.1"; our proxy appended the real peer on the right.
        assertThat(resolver.resolve(request("169.254.129.1", "1.1.1.1, 203.0.113.7:51234"))).isEqualTo("203.0.113.7");
    }

    @Test
    void spoofedHeaderAlone_cannotChangeTheBucketPerRequest() {
        String a = resolver.resolve(request("169.254.129.1", "11.11.11.11, 203.0.113.7:1000"));
        String b = resolver.resolve(request("169.254.129.1", "22.22.22.22, 203.0.113.7:2000"));
        assertThat(a).isEqualTo(b).isEqualTo("203.0.113.7");
    }

    @Test
    void internalProxyHopsOnTheRight_areSkipped() {
        assertThat(resolver.resolve(request("10.0.0.9", "203.0.113.7, 10.0.0.4, 172.16.0.2"))).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv6WithBracketsAndPort_isParsedAndNormalized() {
        assertThat(resolver.resolve(request("169.254.129.1", "[2001:db8::1]:443"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        // Different spellings of the same address map to one key
        assertThat(resolver.resolve(request("169.254.129.1", "2001:0db8:0000::0001")))
                .isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void garbageEntries_areIgnoredAndHostnamesAreNeverResolved() {
        assertThat(resolver.resolve(request("169.254.129.1", "not-an-ip, 203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(resolver.resolve(request("10.0.0.9", "evil.example.com"))).isEqualTo("10.0.0.9");
    }

    @Test
    void localDockerStack_allPrivate_returnsTheNearestClient() {
        // nginx appended the Docker gateway; a client-supplied private value on the left is ignored
        assertThat(resolver.resolve(request("172.18.0.5", "10.9.9.9, 172.18.0.1"))).isEqualTo("172.18.0.1");
    }
}
