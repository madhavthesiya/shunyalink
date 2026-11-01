package com.shunyalink.cp;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(RoastServiceTest.CachingConfig.class)
class RoastServiceTest {

    @Configuration
    @EnableCaching
    @Import(RoastService.class)
    static class CachingConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private RoastService roastService;
    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private UserRepository userRepository;
    @MockBean
    private LeetCodeService leetCodeService;
    @MockBean
    private CodeforcesService codeforcesService;
    @MockBean
    private GithubService githubService;
    @MockBean
    private LlmIntegrationService llm;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        UserEntity user = new UserEntity();
        user.setGithubUsername("octocat");
        when(userRepository.findByUsername("madhav")).thenReturn(Optional.of(user));
    }

    @Test
    void successfulRoast_isCachedSoRepeatViewsDoNotCallTheLlm() {
        when(llm.roastProfile(anyString())).thenReturn(Optional.of("Ouch."));

        assertThat(roastService.roast("madhav")).isEqualTo("Ouch.");
        assertThat(roastService.roast("madhav")).isEqualTo("Ouch.");

        verify(llm, times(1)).roastProfile(anyString());
    }

    @Test
    void failedRoast_isNotCachedSoTheNextViewRetries() {
        when(llm.roastProfile(anyString())).thenReturn(Optional.empty(), Optional.of("Second try."));

        assertThat(roastService.roast("madhav")).isNull();
        assertThat(roastService.roast("madhav")).isEqualTo("Second try.");

        verify(llm, times(2)).roastProfile(anyString());
    }
}
