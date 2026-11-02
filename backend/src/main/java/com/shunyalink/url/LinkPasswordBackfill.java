package com.shunyalink.url;

import com.shunyalink.security.LinkPasswordCipher;
import com.shunyalink.util.EncryptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time conversion of legacy AES/ECB link passwords to AES-256-GCM, run at startup.
 *
 * <p>Legacy values are decryptable with the old key, so they can all be converted now instead of
 * waiting for each link to be unlocked. The job is idempotent (it only selects values not yet in
 * the current format, so later startups are no-ops), safe across replicas (two instances
 * converting the same row both write valid ciphertext), and never blocks startup.
 * Once it logs converted=0 in production, EncryptionUtils and ENCRYPTION_SECRET can be removed.
 */
@Component
public class LinkPasswordBackfill {

    private static final Logger log = LoggerFactory.getLogger(LinkPasswordBackfill.class);
    private static final String CURRENT_FORMAT_PATTERN = "v1:%";

    private final UrlRepository urlRepository;
    private final LinkPasswordCipher linkPasswordCipher;
    private final EncryptionUtils encryptionUtils;

    public LinkPasswordBackfill(UrlRepository urlRepository, LinkPasswordCipher linkPasswordCipher,
                                EncryptionUtils encryptionUtils) {
        this.urlRepository = urlRepository;
        this.linkPasswordCipher = linkPasswordCipher;
        this.encryptionUtils = encryptionUtils;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            migrateLegacyPasswords();
        } catch (Exception e) {
            log.error("Link password backfill failed; it will run again on the next startup", e);
        }
    }

    /** @return the number of links converted */
    int migrateLegacyPasswords() {
        List<UrlEntity> candidates = urlRepository.findWithLegacyPasswordFormat(CURRENT_FORMAT_PATTERN);
        int converted = 0;
        int skipped = 0;
        for (UrlEntity entity : candidates) {
            String stored = entity.getPassword();
            // BCrypt hashes (oldest links) are one-way: still verifiable, nothing to convert
            String plaintext = stored.startsWith("$2") ? null : encryptionUtils.decrypt(stored);
            if (plaintext == null) {
                skipped++;
                continue;
            }
            try {
                entity.setPassword(linkPasswordCipher.encrypt(plaintext, entity.getShortId()));
                urlRepository.save(entity);
                converted++;
            } catch (Exception e) {
                skipped++;
                log.warn("Could not convert link password for shortId={}", entity.getShortId(), e);
            }
        }
        if (!candidates.isEmpty()) {
            log.info("Link password backfill: converted={} skipped={}", converted, skipped);
        }
        return converted;
    }
}
