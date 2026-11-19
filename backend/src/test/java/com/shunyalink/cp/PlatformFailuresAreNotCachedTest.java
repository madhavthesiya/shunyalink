package com.shunyalink.cp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Platform stats are cached for an hour. A failed call must not be: otherwise one LeetCode blip hides a user's
 * LeetCode stats for the next hour of views. Each test fails the platform once, then lets it answer, and expects the
 * second view to get the real answer. A definitive "no such user" is an answer, and is cached.
 */
@SpringJUnitConfig(PlatformFailuresAreNotCachedTest.Platforms.class)
class PlatformFailuresAreNotCachedTest {

    @Configuration
    @EnableCaching
    @Import({GithubService.class, LeetCodeService.class, CodeforcesService.class, AtCoderService.class})
    static class Platforms {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        MockRestServiceServer upstream(RestTemplate restTemplate) {
            return MockRestServiceServer.bindTo(restTemplate).build();
        }

        @Bean
        CodeChefService codeChefService(RestTemplate restTemplate) {
            return new CodeChefService(restTemplate, "http://scraper:3001", "key");
        }
    }

    @Autowired private CacheManager caches;
    @Autowired private MockRestServiceServer upstream;
    @Autowired private GithubService github;
    @Autowired private LeetCodeService leetCode;
    @Autowired private CodeforcesService codeforces;
    @Autowired private AtCoderService atCoder;
    @Autowired private CodeChefService codeChef;

    @BeforeEach
    void freshCachesAndUpstream() {
        caches.getCacheNames().forEach(name -> caches.getCache(name).clear());
        upstream.reset();
    }

    @Test
    void github_aFailureIsNotCached() {
        upstream.expect(requestTo("https://api.github.com/users/octo")).andRespond(withServerError());
        upstream.expect(requestTo("https://api.github.com/users/octo"))
                .andRespond(withSuccess("{\"public_repos\":3,\"followers\":1}", APPLICATION_JSON));

        catchThrowable(() -> github.getStats("octo"));

        assertThat(github.getStats("octo")).containsEntry("publicRepos", 3);
        upstream.verify();
    }

    @Test
    void leetCode_aFailureIsNotCached() {
        upstream.expect(requestTo("https://leetcode.com/graphql")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        upstream.expect(requestTo("https://leetcode.com/graphql")).andRespond(withSuccess("""
                {"data":{"matchedUser":{"submitStats":{"acSubmissionNum":[
                  {"difficulty":"All","count":5},{"difficulty":"Easy","count":3},{"difficulty":"Medium","count":2}]}},
                 "userContestRanking":null,"userContestRankingHistory":null}}""", APPLICATION_JSON));

        catchThrowable(() -> leetCode.getStats("lc"));

        assertThat(leetCode.getStats("lc")).containsEntry("totalSolved", 5);
        upstream.verify();
    }

    @Test
    void codeforces_aFailureIsNotCached() {
        upstream.expect(requestTo("https://codeforces.com/api/user.info?handles=cf")).andRespond(withServerError());
        upstream.expect(requestTo("https://codeforces.com/api/user.info?handles=cf")).andRespond(withSuccess(
                "{\"status\":\"OK\",\"result\":[{\"rating\":1500,\"maxRating\":1600}]}", APPLICATION_JSON));
        upstream.expect(requestTo("https://codeforces.com/api/user.rating?handle=cf"))
                .andRespond(withSuccess("{\"status\":\"OK\",\"result\":[]}", APPLICATION_JSON));
        upstream.expect(requestTo("https://codeforces.com/api/user.status?handle=cf"))
                .andRespond(withSuccess("{\"status\":\"OK\",\"result\":[]}", APPLICATION_JSON));

        catchThrowable(() -> codeforces.getStats("cf"));

        assertThat(codeforces.getStats("cf")).containsEntry("rating", 1500);
        upstream.verify();
    }

    @Test
    void atCoder_aFailureIsNotCached() {
        upstream.expect(requestTo("https://atcoder.jp/users/ac/history/json")).andRespond(withServerError());
        upstream.expect(requestTo("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=ac"))
                .andRespond(withServerError());
        upstream.expect(requestTo("https://atcoder.jp/users/ac/history/json"))
                .andRespond(withSuccess("[]", APPLICATION_JSON));
        upstream.expect(requestTo("https://kenkoooo.com/atcoder/atcoder-api/v3/user/ac_rank?user=ac"))
                .andRespond(withSuccess("{\"count\":42}", APPLICATION_JSON));

        catchThrowable(() -> atCoder.getStats("ac"));

        assertThat(atCoder.getStats("ac")).containsEntry("totalSolved", 42);
        upstream.verify();
    }

    @Test
    void codeChef_aFailureIsNotCached() {
        upstream.expect(requestTo("http://scraper:3001/codechef/cc")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        upstream.expect(requestTo("http://scraper:3001/codechef/cc"))
                .andRespond(withSuccess("{\"rating\":2100,\"totalSolved\":500}", APPLICATION_JSON));

        catchThrowable(() -> codeChef.getStats("cc"));

        assertThat(codeChef.getStats("cc")).containsEntry("rating", 2100);
        upstream.verify();
    }

    @Test
    void aFailure_isReportedAsUnavailable_notReturnedAsData() {
        upstream.expect(requestTo("https://api.github.com/users/octo")).andRespond(withServerError());

        assertThatThrownBy(() -> github.getStats("octo"))
                .isInstanceOf(PlatformUnavailableException.class)
                .hasMessage("GitHub is unavailable");
    }

    @Test
    void github_noSuchUser_isAnAnswer_andIsCached() {
        // GitHub allows 60 anonymous requests an hour; an unknown user must not cost one per portfolio view.
        upstream.expect(requestTo("https://api.github.com/users/ghost")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        Map<String, Object> first = github.getStats("ghost");
        Map<String, Object> second = github.getStats("ghost");

        assertThat(first).containsKey("error");
        assertThat(second).isEqualTo(first);
        upstream.verify(); // exactly one request
    }
}
