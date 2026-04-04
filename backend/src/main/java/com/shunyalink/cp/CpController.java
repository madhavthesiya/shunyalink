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
        int totalGloballySolved = 0;

        if (user.getLeetcodeUsername() != null) {
            Map<String, Object> lc = leetCodeService.getStats(user.getLeetcodeUsername());
            stats.put("leetcode", lc);
            totalGloballySolved += (Integer) lc.getOrDefault("totalSolved", 0);
        }

        if (user.getCodeforcesUsername() != null) {
            Map<String, Object> cf = codeforcesService.getStats(user.getCodeforcesUsername());
            stats.put("codeforces", cf);
            totalGloballySolved += (Integer) cf.getOrDefault("totalSolved", 0);
        }

        if (user.getCodeChefHandle() != null) {
            Map<String, Object> cc = codeChefService.getStats(user.getCodeChefHandle());
            stats.put("codechef", cc);
            Object ccSolved = cc.get("totalSolved");
            if (ccSolved instanceof String) {
                totalGloballySolved += Integer.parseInt((String) ccSolved);
            } else if (ccSolved instanceof Integer) {
                totalGloballySolved += (Integer) ccSolved;
            }
        }

        if (user.getAtCoderHandle() != null) {
            Map<String, Object> ac = new java.util.HashMap<>(atCoderService.getStats(user.getAtCoderHandle()));
            ac.put("handle", user.getAtCoderHandle()); // needed by frontend for Kenkoooo client-side fetch
            stats.put("atcoder", ac);
            Object acSolved = ac.get("totalSolved");
            if (acSolved instanceof String) {
                totalGloballySolved += Integer.parseInt((String) acSolved);
            } else if (acSolved instanceof Integer) {
                totalGloballySolved += (Integer) acSolved;
            }
        }

        if (user.getGithubUsername() != null) {
            stats.put("github", githubService.getStats(user.getGithubUsername()));
        }

        stats.put("totalGloballySolved", totalGloballySolved);
        stats.put("profileType", user.getProfileType());

        return ResponseEntity.ok(stats);
    }
}
