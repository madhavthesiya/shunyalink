package com.shunyalink.url;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportServiceTest {

    private final CsvExportService service = new CsvExportService();

    private static UrlEntity link(String longUrl, String title) {
        UrlEntity entity = new UrlEntity("abc", longUrl, false);
        entity.setTitle(title);
        return entity;
    }

    @Test
    void commasQuotesAndNewlines_areQuotedPerRfc4180() {
        String csv = service.exportToCsv(List.of(
                link("https://example.com/a,b?x=1", "Hello, \"World\"\nline two")));

        assertThat(csv).contains("\"https://example.com/a,b?x=1\"");
        assertThat(csv).contains("\"Hello, \"\"World\"\"\nline two\"");
    }

    @Test
    void cellsASpreadsheetWouldExecute_areNeutralized() {
        String csv = service.exportToCsv(List.of(
                link("https://example.com", "=HYPERLINK(\"https://evil.example\",\"Click me\")")));

        // Leading apostrophe makes Excel/Sheets treat the cell as text, not a formula
        assertThat(csv).contains("\"'=HYPERLINK(");
        assertThat(csv).doesNotContain(",\"=HYPERLINK(");
    }

    @Test
    void headerRow_isUnchanged() {
        assertThat(service.exportToCsv(List.of()))
                .isEqualTo("Short ID,Original URL,Click Count,Created At,Title,Category,Tags\n");
    }
}
