package com.shunyalink.scheduler;

import com.shunyalink.url.UrlRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ExpiredLinkCleanupScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(ExpiredLinkCleanupScheduler.class);

    private final UrlRepository urlRepository;

    public ExpiredLinkCleanupScheduler(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Scheduled(fixedRate = 3600000) // every 1 hour
    public void cleanupExpiredLinks() {

        int deleted = urlRepository.deleteExpiredUrls();

        log.info("EXPIRED LINK CLEANUP removed={}", deleted);
    }
}