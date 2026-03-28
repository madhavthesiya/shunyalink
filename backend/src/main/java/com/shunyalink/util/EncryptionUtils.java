package com.shunyalink.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class EncryptionUtils {

    private final String secretKey;

    public EncryptionUtils(@Value("${app.encryption.secret:my-ultra-secret-key-32-chars-long}") String secretKey) {
        // Ensure key is 32 bytes for AES-256
        if (secretKey.length() < 32) {
            this.secretKey = String.format("%-32s", secretKey).substring(0, 32);
        } else {
            this.secretKey = secretKey.substring(0, 32);
        }
    }

    public String encrypt(String strToEncrypt) {
        if (strToEncrypt == null || strToEncrypt.isEmpty()) return null;
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Error while encrypting: " + e.toString());
        }
    }

    public String decrypt(String strToDecrypt) {
        if (strToDecrypt == null || strToDecrypt.isEmpty()) return null;
        try {
            // Check if it's a BCrypt hash (starts with $2a$ or $2b$)
            if (strToDecrypt.startsWith("$2a$") || strToDecrypt.startsWith("$2b$") || strToDecrypt.startsWith("$2y$")) {
                return null; // Cannot decrypt BCrypt
            }
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If decryption fails, it might be an old hash or corrupted data
            return null;
        }
    }
}
