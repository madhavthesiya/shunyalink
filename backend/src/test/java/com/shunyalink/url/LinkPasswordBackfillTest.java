package com.shunyalink.url;

import com.shunyalink.security.LinkPasswordCipher;
import com.shunyalink.util.EncryptionUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class LinkPasswordBackfillTest {

    private final UrlRepository urlRepository = mock(UrlRepository.class);
    private final EncryptionUtils encryptionUtils = mock(EncryptionUtils.class);
    private final LinkPasswordCipher cipher = new LinkPasswordCipher("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    private final LinkPasswordBackfill backfill = new LinkPasswordBackfill(urlRepository, cipher, encryptionUtils);

    private static UrlEntity link(String shortId, String storedPassword) {
        UrlEntity entity = new UrlEntity(shortId, "https://example.com/" + shortId, false);
        entity.setPassword(storedPassword);
        return entity;
    }

    @Test
    void legacyEcbValues_areReEncryptedWithGcmBoundToTheirLink() {
        UrlEntity a = link("a", "ecb-a");
        UrlEntity b = link("b", "ecb-b");
        when(urlRepository.findWithLegacyPasswordFormat("v1:%")).thenReturn(List.of(a, b));
        when(encryptionUtils.decrypt("ecb-a")).thenReturn("code-a");
        when(encryptionUtils.decrypt("ecb-b")).thenReturn("code-b");

        assertThat(backfill.migrateLegacyPasswords()).isEqualTo(2);

        assertThat(cipher.decrypt(a.getPassword(), "a")).isEqualTo("code-a");
        assertThat(cipher.decrypt(b.getPassword(), "b")).isEqualTo("code-b");
        verify(urlRepository).save(a);
        verify(urlRepository).save(b);
    }

    @Test
    void bcryptAndUnreadableValues_areLeftUntouched() {
        UrlEntity bcrypt = link("old", "$2a$10$hash");
        UrlEntity corrupt = link("bad", "not-decryptable");
        when(urlRepository.findWithLegacyPasswordFormat("v1:%")).thenReturn(List.of(bcrypt, corrupt));
        when(encryptionUtils.decrypt("not-decryptable")).thenReturn(null);

        assertThat(backfill.migrateLegacyPasswords()).isZero();

        assertThat(bcrypt.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(corrupt.getPassword()).isEqualTo("not-decryptable");
        verify(urlRepository, never()).save(any());
    }

    @Test
    void onStartup_failuresNeverStopTheApplication() {
        when(urlRepository.findWithLegacyPasswordFormat(anyString())).thenThrow(new IllegalStateException("db down"));

        backfill.onApplicationReady(); // must not throw
    }
}
