package com.shunyalink.cp;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "CP Portfolio", description = "Endpoints for fetching competitive programming statistics")
public class CpController {

    private final UserRepository userRepository;
    private final LeetCodeService leetCodeService;
    private final CodeforcesService codeforcesService;
    private final CodeChefService codeChefService;
    private final AtCoderService atCoderService;
    private final GithubService githubService;
    private final LlmIntegrationService llmIntegrationService;

    public CpController(UserRepository userRepository,
                        LeetCodeService leetCodeService,
                        CodeforcesService codeforcesService,
                        CodeChefService codeChefService,
                        AtCoderService atCoderService,
                        GithubService githubService,
                        LlmIntegrationService llmIntegrationService) {
        this.userRepository = userRepository;
        this.leetCodeService = leetCodeService;
        this.codeforcesService = codeforcesService;
        this.codeChefService = codeChefService;
        this.atCoderService = atCoderService;
        this.githubService = githubService;
        this.llmIntegrationService = llmIntegrationService;
    }

    @Operation(summary = "Get AI roast for CP portfolio", description = "Generates a sarcastic roast based on the user's coding stats.")
    @GetMapping("/{username}/roast")
    public ResponseEntity<Map<String, String>> getRoast(@PathVariable String username) {
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

        String roast = llmIntegrationService.roastProfile(context.toString());
        return ResponseEntity.ok(Map.of("roast", roast));
    }

    @Operation(summary = "Get full CP portfolio stats", description = "Aggregates stats from all connected CP platforms for a user.")
    @GetMapping("/{username}")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User not found"));

        Map<String, Object> stats = new HashMap<>();

        // Fire all platform calls in parallel — prevents Puppeteer's 10-15s latency
        // from blocking the other 4 fast API calls
        CompletableFuture<Map<String, Object>> lcFuture = (user.getLeetcodeUsername() != null)
                ? CompletableFuture.supplyAsync(() -> leetCodeService.getStats(user.getLeetcodeUsername()))
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Map<String, Object>> cfFuture = (user.getCodeforcesUsername() != null)
                ? CompletableFuture.supplyAsync(() -> codeforcesService.getStats(user.getCodeforcesUsername()))
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Map<String, Object>> ccFuture = (user.getCodeChefHandle() != null)
                ? CompletableFuture.supplyAsync(() -> codeChefService.getStats(user.getCodeChefHandle()))
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Map<String, Object>> acFuture = (user.getAtCoderHandle() != null)
                ? CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> ac = new HashMap<>(atCoderService.getStats(user.getAtCoderHandle()));
                    ac.put("handle", user.getAtCoderHandle());
                    return ac;
                })
                : CompletableFuture.completedFuture(null);

        CompletableFuture<Map<String, Object>> ghFuture = (user.getGithubUsername() != null)
                ? CompletableFuture.supplyAsync(() -> githubService.getStats(user.getGithubUsername()))
                : CompletableFuture.completedFuture(null);

        // Wait for all to complete
        CompletableFuture.allOf(lcFuture, cfFuture, ccFuture, acFuture, ghFuture).join();

        // Collect results
        int totalGloballySolved = 0;

        Map<String, Object> lc = lcFuture.join();
        if (lc != null) {
            stats.put("leetcode", lc);
            totalGloballySolved += (Integer) lc.getOrDefault("totalSolved", 0);
        }

        Map<String, Object> cf = cfFuture.join();
        if (cf != null) {
            stats.put("codeforces", cf);
            totalGloballySolved += (Integer) cf.getOrDefault("totalSolved", 0);
        }

        Map<String, Object> cc = ccFuture.join();
        if (cc != null) {
            stats.put("codechef", cc);
            totalGloballySolved += toInt(cc.get("totalSolved"));
        }

        Map<String, Object> ac = acFuture.join();
        if (ac != null) {
            stats.put("atcoder", ac);
            totalGloballySolved += toInt(ac.get("totalSolved"));
        }

        Map<String, Object> gh = ghFuture.join();
        if (gh != null) {
            stats.put("github", gh);
        }

        stats.put("totalGloballySolved", totalGloballySolved);
        stats.put("profileType", user.getProfileType());

        return ResponseEntity.ok(stats);
    }

    private int toInt(Object value) {
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}

