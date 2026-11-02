package com.shunyalink.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "::1",                         // loopback
            "0.0.0.0", "::",                            // unspecified
            "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", // RFC 1918
            "169.254.169.254", "fe80::1",               // link-local (cloud metadata lives here)
            "100.64.0.1", "100.127.255.255",            // carrier-grade NAT
            "198.18.0.1", "192.0.0.8", "240.0.0.1", "255.255.255.255",
            "fc00::1", "fd12:3456:789a::1",             // IPv6 unique local
            "224.0.0.1", "ff02::1",                     // multicast
            "::ffff:10.0.0.1", "::ffff:127.0.0.1"       // IPv4-mapped IPv6
    })
    void internalAddresses_areDetected(String literal) {
        assertThat(InternalAddresses.isInternal(InternalAddresses.parseLiteral(literal))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "203.0.113.7", "172.32.0.1", "100.128.0.1", "2001:db8::1", "2606:4700::1111"})
    void publicAddresses_areNotInternal(String literal) {
        assertThat(InternalAddresses.isInternal(InternalAddresses.parseLiteral(literal))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "localhost", "evil.example.com", "1.2.3", "999.1.1.1", "1.2.3.4.5", "abc:xyz", "1.2.3.4%eth0"})
    void nonLiterals_areRejectedWithoutAnyDnsLookup(String value) {
        assertThat(InternalAddresses.parseLiteral(value)).isNull();
    }

    @Test
    void null_isRejected() {
        assertThat(InternalAddresses.parseLiteral(null)).isNull();
    }
}
