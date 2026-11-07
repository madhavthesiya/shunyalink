package com.shunyalink.analytics;

import com.shunyalink.util.InternalAddresses;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/** Produces link- and day-scoped pseudonyms; these are not anonymous identifiers. */
public final class VisitorHasher {
    private final byte[] key;

    public VisitorHasher(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("Analytics hash key must be 32 bytes");
        }
        this.key = key.clone();
    }

    public Optional<String> hash(long urlId, LocalDate day, String ip) {
        if (urlId <= 0) throw new IllegalArgumentException("urlId must be positive");
        Objects.requireNonNull(day, "day");
        InetAddress address = InternalAddresses.parseLiteral(ip);
        if (address == null) return Optional.empty();
        byte[] bytes = address.getAddress();
        ByteBuffer input = ByteBuffer.allocate(1 + Long.BYTES * 2 + bytes.length);
        input.put((byte) 1).putLong(urlId).putLong(day.toEpochDay()).put(bytes);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Optional.of(HexFormat.of().formatHex(mac.doFinal(input.array())));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
