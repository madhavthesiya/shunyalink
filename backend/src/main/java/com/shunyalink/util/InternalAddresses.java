package com.shunyalink.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Classifies IP addresses as internal (loopback, private, link-local, reserved, ...) and parses
 * IP literals. Shared by client-IP resolution and the SSRF guard so both agree on what "internal" means.
 */
public final class InternalAddresses {

    private static final Pattern IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private InternalAddresses() {
    }

    /**
     * Parses an IPv4/IPv6 literal, or returns null if the value is not one. Never performs a DNS
     * lookup: the pattern check guarantees {@link InetAddress#getByName} only sees literals.
     */
    public static InetAddress parseLiteral(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        boolean ipv4 = IPV4.matcher(candidate).matches();
        boolean ipv6 = candidate.indexOf(':') >= 0 && IPV6_CHARS.matcher(candidate).matches();
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** True for any address that must never be treated as a public internet host. */
    public static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet6Address v6) {
            if (v6.isIPv4CompatibleAddress()) {
                return isInternalIpv4(new byte[]{b[12], b[13], b[14], b[15]});
            }
            return (b[0] & 0xFE) == 0xFC; // fc00::/7 unique local
        }
        return isInternalIpv4(b);
    }

    private static boolean isInternalIpv4(byte[] b) {
        int b0 = b[0] & 0xFF;
        int b1 = b[1] & 0xFF;
        int b2 = b[2] & 0xFF;
        return b0 == 0                                  // 0.0.0.0/8 "this network"
                || b0 == 10 || b0 == 127                // private, loopback (for IPv4-compatible form)
                || (b0 == 169 && b1 == 254)             // link-local
                || (b0 == 172 && (b1 & 0xF0) == 16)     // 172.16.0.0/12
                || (b0 == 192 && b1 == 168)             // 192.168.0.0/16
                || (b0 == 100 && (b1 & 0xC0) == 64)     // 100.64.0.0/10 carrier-grade NAT
                || (b0 == 192 && b1 == 0 && b2 == 0)    // 192.0.0.0/24 IETF protocol assignments
                || (b0 == 198 && (b1 & 0xFE) == 18)     // 198.18.0.0/15 benchmarking
                || b0 >= 224;                           // multicast, reserved, broadcast
    }
}
