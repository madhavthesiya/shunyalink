package com.shunyalink.security;

import com.shunyalink.util.InternalAddresses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Resolves the real client IP for rate limiting and analytics.
 *
 * <p>Trust model: every hop between the internet and this app (Azure App Service front end,
 * the local Nginx) <em>appends</em> the address it received the request from to
 * {@code X-Forwarded-For}, and those infrastructure hops have internal addresses. So the
 * right-most public entry is the client as seen by our edge; anything to its left was written by
 * the client and is ignored. Taking the left-most entry, as before, let any caller pick its own
 * rate-limit bucket.
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            String nearestInternal = null;
            for (int i = hops.length - 1; i >= 0; i--) {
                InetAddress address = InternalAddresses.parseLiteral(stripPort(hops[i].trim()));
                if (address == null) {
                    continue;
                }
                if (!InternalAddresses.isInternal(address)) {
                    return address.getHostAddress();
                }
                if (nearestInternal == null) {
                    // All-private chains happen in the local Docker stack (client = Docker gateway)
                    nearestInternal = address.getHostAddress();
                }
            }
            if (nearestInternal != null) {
                return nearestInternal;
            }
        }
        return request.getRemoteAddr();
    }

    /** "1.2.3.4:5678" and "[2001:db8::1]:443" carry a port (Azure adds one); bare IPv6 does not. */
    static String stripPort(String hop) {
        if (hop.startsWith("[")) {
            int end = hop.indexOf(']');
            return end > 0 ? hop.substring(1, end) : hop;
        }
        int colon = hop.indexOf(':');
        if (colon > 0 && colon == hop.lastIndexOf(':')) {
            return hop.substring(0, colon);
        }
        return hop;
    }
}
