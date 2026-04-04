package com.shunyalink.url;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final UrlService urlService;

    public CsvImportService(UrlService urlService) {
        this.urlService = urlService;
    }

    public Map<String, Object> importCsv(MultipartFile file, Long userId) {
        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        if (file.isEmpty()) {
            return Map.of("success", 0, "failed", 0, "errors", List.of("File is empty"));
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return Map.of("success", 0, "failed", 0, "errors", List.of("File has no headers"));
            }

            // Expecting: longUrl, customAlias, title, tags
            // Let's do a simple heuristic matching for column indexes
            String[] headers = headerLine.toLowerCase().split(",");
            int longUrlIdx = -1, customAliasIdx = -1, titleIdx = -1, tagsIdx = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].trim();
                if (h.contains("long") || h.contains("url") || h.contains("link")) longUrlIdx = i;
                if (h.contains("alias") || h.contains("short")) customAliasIdx = i;
                if (h.contains("title") || h.contains("name")) titleIdx = i;
                if (h.contains("tag") || h.contains("folder")) tagsIdx = i;
            }

            // Fallback if no header explicitly named "longUrl", assume first column
            if (longUrlIdx == -1) longUrlIdx = 0;

            String line;
            int rowNumber = 1;

            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.trim().isEmpty()) continue;
                if (successCount >= 50) {
                    errors.add("Row " + rowNumber + ": Limit of 50 links per import reached.");
                    break;
                }

                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1); // Handles quotes
                if (columns.length <= longUrlIdx) {
                    failCount++;
                    errors.add("Row " + rowNumber + ": Missing URL column.");
                    continue;
                }

                String longUrl = columns[longUrlIdx].replace("\"", "").trim();
                if (longUrl.isEmpty()) {
                    failCount++;
                    continue;
                }

                // Add http protocol if missing for basic sanity
                if (!longUrl.startsWith("http://") && !longUrl.startsWith("https://")) {
                    longUrl = "https://" + longUrl;
                }

                String customAlias = null;
                if (customAliasIdx != -1 && columns.length > customAliasIdx) {
                    customAlias = columns[customAliasIdx].replace("\"", "").trim();
                }

                String title = null;
                if (titleIdx != -1 && columns.length > titleIdx) {
                    title = columns[titleIdx].replace("\"", "").trim();
                }

                Set<String> tags = new HashSet<>();
                if (tagsIdx != -1 && columns.length > tagsIdx) {
                    String tagsRaw = columns[tagsIdx].replace("\"", "").trim();
                    if (!tagsRaw.isEmpty()) {
                        String[] splitTags = tagsRaw.split("[|;]+"); // allow | or ; separated
                        for (String t : splitTags) {
                            if (!t.trim().isEmpty()) tags.add(t.trim());
                        }
                    }
                }

                try {
                    urlService.shortenUrl(longUrl, customAlias, null, userId, title, null, false, tags);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    errors.add("Row " + rowNumber + " ('" + longUrl + "'): " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error parsing CSV", e);
            errors.add("Failed to parse CSV file: " + e.getMessage());
        }

        return Map.of(
            "successCount", successCount,
            "failCount", failCount,
            "errors", errors
        );
    }
}
