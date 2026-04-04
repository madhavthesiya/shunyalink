package com.shunyalink.url;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CsvExportService {
    public String exportToCsv(List<UrlEntity> urls) {
        StringBuilder csv = new StringBuilder();
        csv.append("Short ID,Original URL,Click Count,Created At,Title,Category,Tags\n");
        for (UrlEntity url : urls) {
            String title = url.getTitle() != null ? url.getTitle().replace(",", " ") : "";
            String category = url.getCategory() != null ? url.getCategory() : "GENERAL";
            String tags = url.getTags() != null ? String.join("|", url.getTags()) : "";
            csv.append(String.format("%s,%s,%d,%s,%s,%s,%s\n",
                    url.getShortId(), url.getLongUrl(), url.getClickCount(), url.getCreatedAt(), title, category, tags));
        }
        return csv.toString();
    }
}
