package com.shunyalink.logging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSafeTest {

    @Test
    void lineBreaksAndControlCharacters_cannotForgeOrSplitALogLine() {
        // A short link alias such as "x\r\nINFO Admin logged in" would otherwise print a fake second line.
        // \r\n counts as one line break, so it becomes one '_'.
        assertThat(LogSafe.value("abc\r\nINFO forged\tline\u0000")).isEqualTo("abc_INFO forged_line_");
        assertThat(LogSafe.value("a\nb\rc d")).isEqualTo("a_b_c_d");
    }

    @Test
    void nullIsPrintable_andHugeValuesAreCapped() {
        assertThat(LogSafe.value(null)).isEqualTo("null");
        assertThat(LogSafe.value("x".repeat(500))).hasSize(203).endsWith("...");
    }

    @Test
    void urlsAreReducedToTheirHost_becausePathsAndQueriesCarrySecrets() {
        assertThat(LogSafe.host("https://docs.example.com/d/abc/edit?token=SECRET#frag")).isEqualTo("docs.example.com");
        assertThat(LogSafe.host("http://user:pass@example.org:8080/x")).isEqualTo("example.org");
    }

    @Test
    void unparsableUrls_areNotEchoed() {
        assertThat(LogSafe.host("not a url\r\nINFO forged")).isEqualTo("<invalid-url>");
        assertThat(LogSafe.host(null)).isEqualTo("<invalid-url>");
    }
}
