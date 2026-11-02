package com.shunyalink.cp;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.exception.BadRequestException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Generates the AI roast for a public portfolio. Each roast is a paid LLM call, so successful
 * roasts are cached per user (24h, see RedisConfig); failures return null and are not cached.
 */
@Service
public class RoastService {

    private final UserRepository userRepository;
    private final LeetCodeService leetCodeService;
    private final CodeforcesService codeforcesService;
    private final GithubService githubService;
    private final LlmIntegrationService llmIntegrationService;

    public RoastService(UserRepository userRepository, LeetCodeService leetCodeService,
                        CodeforcesService codeforcesService, GithubService githubService,
                        LlmIntegrationService llmIntegrationService) {
        this.userRepository = userRepository;
        this.leetCodeService = leetCodeService;
        this.codeforcesService = codeforcesService;
        this.githubService = githubService;
        this.llmIntegrationService = llmIntegrationService;
    }

    @Cacheable(value = "roasts", key = "#username", unless = "#result == null")
    public String roast(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User not found"));

        StringBuilder context = new StringBuilder();
        if (user.getLeetcodeUsername() != null) {
            context.append("LeetCode: ").append(leetCodeService.getStats(user.getLeetcodeUsername())).append("; ");
        }
        if (user.getCodeforcesUsername() != null) {
            context.append("Codeforces: ").append(codeforcesService.getStats(user.getCodeforcesUsername())).append("; ");
        }
        if (user.getGithubUsername() != null) {
            context.append("GitHub: ").append(githubService.getStats(user.getGithubUsername())).append("; ");
        }

        return llmIntegrationService.roastProfile(context.toString()).orElse(null);
    }
}
