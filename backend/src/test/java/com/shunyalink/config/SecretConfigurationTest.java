package com.shunyalink.config;

import com.shunyalink.security.JwtService;
import com.shunyalink.util.EncryptionUtils;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Secrets have no defaults: the application must refuse to start when one is
 * missing or unusable instead of falling back to a value published in the repo.
 */
class SecretConfigurationTest {

    // PropertyPlaceholderAutoConfiguration gives the same strict placeholder
    // resolution as the real application (unresolvable -> startup failure).
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class));

    @Test
    void encryption_missingSecret_failsStartup() {
        runner.withUserConfiguration(EncryptionUtils.class)
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("app.encryption.secret"));
    }

    @Test
    void encryption_blankSecret_failsStartup() {
        runner.withUserConfiguration(EncryptionUtils.class)
                .withPropertyValues("app.encryption.secret=   ")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("ENCRYPTION_SECRET"));
    }

    @Test
    void encryption_validSecret_starts() {
        runner.withUserConfiguration(EncryptionUtils.class)
                .withPropertyValues("app.encryption.secret=test-only-encryption-key-32chars")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EncryptionUtils.class));
    }

    @Test
    void jwt_missingSecret_failsStartup() {
        runner.withUserConfiguration(JwtService.class)
                .withPropertyValues("app.jwt.expiration-ms=60000")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .hasMessageContaining("app.jwt.secret"));
    }

    @Test
    void jwt_secretShorterThan256Bits_failsStartup() {
        runner.withUserConfiguration(JwtService.class)
                .withPropertyValues("app.jwt.secret=too-short", "app.jwt.expiration-ms=60000")
                .run(context -> assertThat(context).getFailure().rootCause()
                        .isInstanceOf(WeakKeyException.class));
    }

    @Test
    void jwt_validSecret_starts() {
        runner.withUserConfiguration(JwtService.class)
                .withPropertyValues(
                        "app.jwt.secret=test-only-jwt-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
                        "app.jwt.expiration-ms=60000")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtService.class));
    }
}
