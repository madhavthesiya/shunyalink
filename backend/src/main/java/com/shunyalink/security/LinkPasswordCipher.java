package com.shunyalink.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Reversible encryption for link passwords.
 *
 * <p>Link passwords are shared access codes, not account credentials: the owner hands the same code
 * to many people and must be able to look it up again, so they are encrypted rather than hashed.
 * AES-256-GCM gives a fresh random IV per value (equal passwords look different) and an
 * authentication tag (tampering is detected). The link's short id is bound in as associated data,
 * so a ciphertext copied into another link's row will not decrypt.
 *
 * <p>Stored format: {@code v1:base64(iv || ciphertext+tag)}. The version prefix allows a future key
 * rotation: add a v2 key, encrypt with it, and re-encrypt v1 values in the backfill.
 */
@Component
public class LinkPasswordCipher {

    private static final String VERSION_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public LinkPasswordCipher(@Value("${app.link-password.key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key == null ? "" : base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LINK_PASSWORD_KEY must be base64 (generate with: openssl rand -base64 32)");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("LINK_PASSWORD_KEY must decode to exactly 32 bytes (openssl rand -base64 32)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public static boolean isCurrentFormat(String stored) {
        return stored != null && stored.startsWith(VERSION_PREFIX);
    }

    public String encrypt(String plaintext, String shortId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(shortId.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Link password encryption failed", e);
        }
    }

    /** @throws IllegalStateException if the value is malformed, tampered with, or belongs to another link/key */
    public String decrypt(String stored, String shortId) {
        if (!isCurrentFormat(stored)) {
            throw new IllegalStateException("Unsupported link password format");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            if (in.length <= IV_BYTES) {
                throw new IllegalStateException("Malformed link password ciphertext");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            cipher.updateAAD(shortId.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Link password could not be decrypted", e);
        }
    }
}
