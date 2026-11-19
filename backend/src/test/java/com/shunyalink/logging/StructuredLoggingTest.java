package com.shunyalink.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The container turns on JSON logs with LOGGING_STRUCTURED_FORMAT_CONSOLE (Dockerfile.backend). An unknown format
 * would stop the application at startup, so this runs Spring Boot's structured encoder with the Dockerfile's value.
 *
 * <p>It tests the encoder directly: Spring Boot configures logging once per JVM, so starting another application
 * here would silently keep whatever an earlier test configured.
 */
class StructuredLoggingTest {

    @Test
    void containerLogLines_areOneJsonObjectEach_andCarryTheRequestId() throws Exception {
        var context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment());
        var encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat(containerLogFormat());
        encoder.start();

        var event = new LoggingEvent("probe", context.getLogger("com.shunyalink.probe"), Level.WARN,
                "probe line\nsecond line", null, null);
        event.setMDCPropertyMap(Map.of(RequestIdFilter.MDC_KEY, "req-structured-1"));
        String output = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(output.strip().lines()).as("one entry is one line, even with a line break in the message").hasSize(1);
        JsonNode json = new ObjectMapper().readTree(output);
        assertThat(json.path("message").asText()).isEqualTo("probe line\nsecond line");
        assertThat(json.path("requestId").asText()).isEqualTo("req-structured-1");
        assertThat(json.has("@timestamp")).isTrue();
    }

    /** The value the deployed image really uses, read from the Dockerfile (tests run in the backend directory). */
    private static String containerLogFormat() throws Exception {
        var setting = Pattern.compile("^ENV LOGGING_STRUCTURED_FORMAT_CONSOLE=(\\S+)$", Pattern.MULTILINE)
                .matcher(Files.readString(Path.of("Dockerfile.backend")));
        assertThat(setting.find()).as("Dockerfile.backend sets LOGGING_STRUCTURED_FORMAT_CONSOLE").isTrue();
        return setting.group(1);
    }
}
