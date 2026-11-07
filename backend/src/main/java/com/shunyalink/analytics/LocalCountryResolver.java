package com.shunyalink.analytics;

import com.maxmind.db.Reader;
import com.shunyalink.util.InternalAddresses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Resolves IP literals from a local MMDB file without sending them to a remote service. */
public final class LocalCountryResolver implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LocalCountryResolver.class);
    private final Reader reader;

    public LocalCountryResolver(Path database) {
        Reader opened = null;
        try {
            if (database != null) {
                // Avoid a live file mapping that can prevent replacement or cleanup on Windows.
                opened = new Reader(database.toFile(), Reader.FileMode.MEMORY);
            }
        } catch (IOException | IllegalArgumentException e) {
            // Do not log visitor IPs; an unavailable database degrades to Unknown.
            log.warn("Local country database unavailable; lookups will be unknown");
        }
        this.reader = opened;
    }

    public String resolve(String ip) {
        InetAddress address = InternalAddresses.parseLiteral(ip);
        if (address == null) return "Unknown";
        if (InternalAddresses.isInternal(address)) return "Local/Dev";
        if (reader == null) return "Unknown";
        try {
            Map<?, ?> record = reader.get(address, Map.class);
            if (record == null || !(record.get("country") instanceof Map<?, ?> country)) return "Unknown";
            if (country.get("names") instanceof Map<?, ?> names && names.get("en") instanceof String name && !name.isBlank()) {
                return name;
            }
            if (country.get("iso_code") instanceof String code && code.matches("[A-Z]{2}")) {
                String name = new Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH);
                return name.equals(code) ? "Unknown" : name;
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Local country lookup failed");
        }
        return "Unknown";
    }

    @Override
    public void close() throws IOException {
        if (reader != null) reader.close();
    }
}
