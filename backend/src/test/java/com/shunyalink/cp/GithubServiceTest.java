package com.shunyalink.cp;

import com.shunyalink.upstream.Upstreams;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GithubServiceTest {

    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer github = MockRestServiceServer.bindTo(http).build();

    @Test
    void aUsernameIsOnePathSegment_itCannotChangeWhichEndpointIsCalled() {
        // Usernames come from user profiles. Before, they were concatenated into the URL, so "../orgs/x" was a path.
        github.expect(requestTo("https://api.github.com/users/..%2Forgs%2Fx%3Fper_page%3D1"))
                .andRespond(withSuccess("{\"public_repos\":1}", MediaType.APPLICATION_JSON));

        assertThat(new GithubService(Upstreams.using(http)).getStats("../orgs/x?per_page=1")).containsEntry("publicRepos", 1);
        github.verify();
    }
}
