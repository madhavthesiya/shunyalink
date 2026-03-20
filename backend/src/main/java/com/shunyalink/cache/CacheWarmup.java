package com.shunyalink.cache;

import com.shunyalink.url.UrlEntity;
import com.shunyalink.url.UrlRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class CacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmup.class);

    private final UrlRepository urlRepository;
    private final RedisTemplate<String,String>  redisTemplate;

    public CacheWarmup(UrlRepository urlRepository, RedisTemplate<String, String> redisTemplate) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void preloadCache() {

        List<UrlEntity> urls =
                urlRepository.findTop1000ByOrderByClickCountDesc();

        for(UrlEntity url : urls) {

            String key = "url:" + url.getShortId();

            redisTemplate.opsForValue().set(key,url.getLongUrl(),24,TimeUnit.HOURS);
        }

        log.info("Cache warmup completed loaded={}", urls.size());
    }
}
