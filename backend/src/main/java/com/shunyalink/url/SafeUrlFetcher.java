package com.shunyalink.url;

import com.shunyalink.util.InternalAddresses;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Fetches user-supplied URLs (for link titles) without letting them reach internal services: an
 * SSRF guard. Only http(s) on ports 80/443 to hosts whose every resolved address is public, and
 * redirects are followed manually so each hop is re-validated (a public page redirecting to
 * 169.254.169.254 is the classic bypass).
 *
 * <p>Known limitation: Jsoup resolves the host again when connecting, so a DNS-rebinding attacker
 * who flips the record between the check and the connection is not covered. Closing that requires
 * connecting to the validated IP directly.
 */
@Component
public class SafeUrlFetcher {

    static final int MAX_REDIRECTS = 3;
    static final int MAX_BODY_BYTES = 512 * 1024;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Predicate<InetAddress> addressBlocked;
    private final IntPredicate portAllowed;
    private final HostResolver resolver;

    public SafeUrlFetcher() {
        this(InternalAddresses::isInternal, SafeUrlFetcher::isStandardWebPort, InetAddress::getAllByName);
    }

    SafeUrlFetcher(Predicate<InetAddress> addressBlocked, IntPredicate portAllowed, HostResolver resolver) {
        this.addressBlocked = addressBlocked;
        this.portAllowed = portAllowed;
        this.resolver = resolver;
    }

    static boolean isStandardWebPort(int port) {
        return port == -1 || port == 80 || port == 443;
    }

    public Document fetch(String url, int timeoutMs) throws IOException {
        URI current = validate(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            Connection.Response response = Jsoup.connect(current.toString())
                    .userAgent(USER_AGENT)
                    .timeout(timeoutMs)
                    .maxBodySize(MAX_BODY_BYTES)
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .execute();
            int status = response.statusCode();
            if (status >= 300 && status < 400 && response.hasHeader("Location")) {
                current = validate(resolveRedirect(current, response.header("Location")));
                continue;
            }
            if (status >= 400) {
                throw new IOException("HTTP " + status + " from " + current.getHost());
            }
            return response.parse();
        }
        throw new IOException("Too many redirects (max " + MAX_REDIRECTS + ")");
    }

    URI validate(String url) throws BlockedUrlException {
        URI uri;
        try {
            uri = new URI(url).normalize();
        } catch (URISyntaxException e) {
            throw new BlockedUrlException("Malformed URL");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BlockedUrlException("Only http(s) URLs can be fetched");
        }
        if (uri.getRawUserInfo() != null) {
            throw new BlockedUrlException("URLs with embedded credentials are not fetched");
        }
        if (!portAllowed.test(uri.getPort())) {
            throw new BlockedUrlException("Only standard web ports can be fetched");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BlockedUrlException("URL has no host");
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new BlockedUrlException("Host does not resolve");
        }
        for (InetAddress address : addresses) {
            if (addressBlocked.test(address)) {
                throw new BlockedUrlException("URL resolves to an internal address");
            }
        }
        return uri;
    }

    private static String resolveRedirect(URI current, String location) throws IOException {
        try {
            return current.resolve(location).toString();
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid redirect location");
        }
    }
}
