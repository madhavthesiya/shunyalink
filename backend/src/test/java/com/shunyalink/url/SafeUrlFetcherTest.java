package com.shunyalink.url;

import com.shunyalink.util.InternalAddresses;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeUrlFetcherTest {

    /** Fake DNS so hostname cases don't depend on the network. Literals resolve normally (no lookup). */
    private static final Map<String, String> FAKE_DNS = Map.of(
            "public.example", "93.184.215.14",
            "internal.example", "10.0.0.5",
            "metadata.example", "169.254.169.254");

    private static InetAddress[] fakeResolve(String host) throws UnknownHostException {
        String ip = FAKE_DNS.get(host);
        if (ip != null) {
            return new InetAddress[]{InetAddress.getByName(ip)};
        }
        InetAddress literal = InternalAddresses.parseLiteral(host);
        if (literal != null) {
            return new InetAddress[]{literal};
        }
        if (host.equals("localhost")) {
            return InetAddress.getAllByName("localhost");
        }
        throw new UnknownHostException(host);
    }

    private final SafeUrlFetcher productionPolicy = new SafeUrlFetcher(
            InternalAddresses::isInternal, SafeUrlFetcher::isStandardWebPort, SafeUrlFetcherTest::fakeResolve);

    // ─── URL validation (production policy) ──────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://localhost:80/admin",
            "http://169.254.169.254/latest/meta-data/",   // cloud instance metadata
            "http://10.0.0.5/",
            "http://[::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://internal.example/",                   // public-looking name, private address
            "http://metadata.example/",
            "http://0.0.0.0/",
            "file:///etc/passwd",
            "ftp://public.example/",
            "gopher://public.example/",
            "http://public.example:6379/",                // non-web port (e.g. Redis)
            "http://user:pass@public.example/",           // embedded credentials
            "http:///no-host",
            "not a url"
    })
    void validate_rejectsInternalTargetsAndNonWebUrls(String url) {
        assertThatThrownBy(() -> productionPolicy.validate(url)).isInstanceOf(BlockedUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://public.example/page?q=1", "http://public.example:80/", "https://93.184.215.14/"})
    void validate_allowsPublicWebUrls(String url) throws Exception {
        assertThat(productionPolicy.validate(url)).isNotNull();
    }

    // ─── Redirect handling against a real HTTP server ─────────────────

    private HttpServer server;
    private SafeUrlFetcher fetcher;
    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/page", exchange -> {
            byte[] body = "<html><head><title>Hello</title></head></html>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        redirect("/to-page", "/page");
        redirect("/to-metadata", "http://169.254.169.254/latest/meta-data/");
        redirect("/to-private", "http://10.0.0.5/admin");
        redirect("/loop", "/loop");
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        // Test policy: only the loopback test server itself is exempt, and any port is allowed
        fetcher = new SafeUrlFetcher(a -> InternalAddresses.isInternal(a) && !a.isLoopbackAddress(),
                port -> true, SafeUrlFetcherTest::fakeResolve);
    }

    private void redirect(String path, String location) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetch_followsSafeRedirects() throws Exception {
        assertThat(fetcher.fetch(base + "/to-page", 5000).title()).isEqualTo("Hello");
    }

    @Test
    void fetch_redirectToCloudMetadata_isBlockedBeforeConnecting() {
        assertThatThrownBy(() -> fetcher.fetch(base + "/to-metadata", 5000))
                .isInstanceOf(BlockedUrlException.class);
    }

    @Test
    void fetch_redirectToPrivateNetwork_isBlocked() {
        assertThatThrownBy(() -> fetcher.fetch(base + "/to-private", 5000))
                .isInstanceOf(BlockedUrlException.class);
    }

    @Test
    void fetch_redirectLoop_stopsAfterTheLimit() {
        assertThatThrownBy(() -> fetcher.fetch(base + "/loop", 5000))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("redirects");
    }
}
