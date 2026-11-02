package com.shunyalink.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkPasswordCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private final LinkPasswordCipher cipher = new LinkPasswordCipher(KEY);

    @Test
    void encryptThenDecrypt_returnsTheOriginalPassword() {
        String stored = cipher.encrypt("s3cret-code", "promo");

        assertThat(stored).startsWith("v1:");
        assertThat(cipher.decrypt(stored, "promo")).isEqualTo("s3cret-code");
    }

    @Test
    void samePasswordTwice_producesDifferentCiphertext() {
        // Random IV per value: unlike ECB, the database no longer reveals which links share a password
        assertThat(cipher.encrypt("same", "a")).isNotEqualTo(cipher.encrypt("same", "a"));
    }

    @Test
    void tamperedCiphertext_isRejected() {
        String stored = cipher.encrypt("s3cret-code", "promo");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 0x01;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered, "promo")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ciphertextCopiedToAnotherLink_doesNotDecrypt() {
        // The short id is authenticated associated data
        String stored = cipher.encrypt("s3cret-code", "promo");

        assertThatThrownBy(() -> cipher.decrypt(stored, "other")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ciphertextFromAnotherKey_doesNotDecrypt() {
        String stored = new LinkPasswordCipher(OTHER_KEY).encrypt("s3cret-code", "promo");

        assertThatThrownBy(() -> cipher.decrypt(stored, "promo")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void currentFormatDetection() {
        assertThat(LinkPasswordCipher.isCurrentFormat(cipher.encrypt("x1234", "a"))).isTrue();
        assertThat(LinkPasswordCipher.isCurrentFormat("bGVnYWN5")).isFalse();
        assertThat(LinkPasswordCipher.isCurrentFormat("$2a$10$hash")).isFalse();
        assertThat(LinkPasswordCipher.isCurrentFormat(null)).isFalse();
    }

    @Test
    void keyThatIsNotExactly32Bytes_isRejectedAtStartup() {
        String shortKey = Base64.getEncoder().encodeToString("too-short".getBytes());

        assertThatThrownBy(() -> new LinkPasswordCipher(shortKey)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LinkPasswordCipher("not base64 !!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LinkPasswordCipher(" ")).isInstanceOf(IllegalStateException.class);
    }
}
