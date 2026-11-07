package com.shunyalink.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCountryResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void looksUpIpv4AndIpv6Locally() throws Exception {
        Path database = Path.of(getClass().getResource("/geoip/GeoIP2-Country-Test.mmdb").toURI());
        try (LocalCountryResolver resolver = new LocalCountryResolver(database)) {
            assertThat(resolver.resolve("2.125.160.216")).isEqualTo("United Kingdom");
            assertThat(resolver.resolve("2001:218::1")).isEqualTo("Japan");
            assertThat(resolver.resolve("10.0.0.1")).isEqualTo("Local/Dev");
            assertThat(resolver.resolve("not-an-ip")).isEqualTo("Unknown");
        }
    }

    @Test
    void missingOrCorruptDatabaseDoesNotPreventLookups() throws Exception {
        Path missing = tempDir.resolve("missing.mmdb");
        Path corrupt = tempDir.resolve("corrupt.mmdb");
        Files.writeString(corrupt, "not a database");
        try (LocalCountryResolver absent = new LocalCountryResolver(missing);
             LocalCountryResolver invalid = new LocalCountryResolver(corrupt)) {
            assertThat(absent.resolve("8.8.8.8")).isEqualTo("Unknown");
            assertThat(invalid.resolve("8.8.8.8")).isEqualTo("Unknown");
            assertThat(absent.resolve("127.0.0.1")).isEqualTo("Local/Dev");
        }
    }
}
