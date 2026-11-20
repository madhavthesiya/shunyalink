package com.shunyalink.url;

import com.shunyalink.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** CSV exports of a user's own links. Formatting lives in {@link CsvExportService}; this decides what may be exported. */
@Service
public class LinkExportService {
    private final UrlRepository urls;
    private final CsvExportService csv;

    public LinkExportService(UrlRepository urls, CsvExportService csv) {
        this.urls = urls;
        this.csv = csv;
    }

    /**
     * One link's row, for its owner only. Anyone else gets 404, as for viewing its stats, so nobody can probe which
     * links exist (this export used to answer 403).
     */
    public String linkCsv(String shortId, Long userId) {
        UrlEntity link = urls.findByShortId(shortId)
                .filter(found -> Objects.equals(found.getUserId(), userId))
                .orElseThrow(() -> new NotFoundException("URL not found"));
        return csv.exportToCsv(List.of(link));
    }

    /** Every link the user owns, newest first. */
    public String allLinksCsv(Long userId) {
        return csv.exportToCsv(urls.findByUserIdOrderByCreatedAtDesc(userId));
    }
}
