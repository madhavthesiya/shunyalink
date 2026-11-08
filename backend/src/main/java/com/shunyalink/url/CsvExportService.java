package com.shunyalink.url;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CsvExportService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.shunyalink.analytics.DurableAnalyticsReader durableAnalyticsReader;

    private static final String HEADER = "Short ID,Original URL,Click Count,Created At,Title,Category,Tags\n";

    public String exportToCsv(List<UrlEntity> urls) {
        var collected = durableAnalyticsReader == null ? java.util.Map.<Long, UrlStatsResponse>of()
                : durableAnalyticsReader.summaries(urls);
        StringBuilder csv = new StringBuilder(HEADER);
        for (UrlEntity url : urls) {
            String category = url.getCategory() != null ? url.getCategory() : "GENERAL";
            String tags = url.getTags() != null ? String.join("|", url.getTags()) : "";
            csv.append(String.join(",",
                    cell(url.getShortId()),
                    cell(url.getLongUrl()),
                    cell(String.valueOf(durableAnalyticsReader == null ? url.getClickCount()
                            : collected.get(url.getId()).getClickCount())),
                    cell(url.getCreatedAt() != null ? url.getCreatedAt().toString() : ""),
                    cell(url.getTitle()),
                    cell(category),
                    cell(tags)))
                    .append('\n');
        }
        return csv.toString();
    }

    /**
     * RFC 4180 quoting (commas, quotes and newlines stay inside one cell), plus a leading
     * apostrophe on values a spreadsheet would execute as a formula (CSV injection): titles and
     * tags are user-controlled and the owner opens this file in Excel or Sheets.
     */
    static String cell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String text = value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
