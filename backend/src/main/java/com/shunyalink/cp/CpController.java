package com.shunyalink.cp;

import com.shunyalink.auth.UserEntity;
import com.shunyalink.auth.UserRepository;
import com.shunyalink.exception.BadRequestException;
import com.shunyalink.rate.RateLimiterService;
import com.shunyalink.security.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.shunyalink.logging.MdcTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "CP Portfolio", description = "Endpoints for fetching competitive programming statistics")
public class CpController {

    static final String ROAST_UNAVAILABLE =
            "The AI roast engine is taking a break. Try again in a few minutes.";

    private final UserRepository userRepository;
    private final LeetCodeService leetCodeService;
    private final CodeforcesService codeforcesService;
    private final CodeChefService codeChefService;
    private final AtCoderService atCoderService;
    private final GithubService githubService;
    private final RoastService roastService;
    private final RateLimiterService rateLimiterService;
    private final ClientIpResolver clientIpResolver;

    private static final Logger log = LoggerFactory.getLogger(CpController.class);

    /**
     * One virtual thread per platform call. These calls only wait on the network, so a virtual thread costs almost
     * nothing while waiting. The old code used the common ForkJoinPool, which is sized to the CPU count (one or two
     * on this App Service plan), so the five blocking calls could run one after another. Request IDs are carried over.
     */
    private final Executor platformCalls;
    private final Duration platformTimeout;

    public CpController(UserRepository userRepository,
                        LeetCodeService leetCodeService,
                        CodeforcesService codeforcesService,
                        CodeChefService codeChefService,
                        AtCoderService atCoderService,
                        GithubService githubService,
                        RoastService roastService,
                        RateLimiterService rateLimiterService,
                        ClientIpResolver clientIpResolver,
                        @Value("${app.portfolio.platform-timeout:5s}") Duration platformTimeout) {
        ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
        MdcTaskDecorator requestContext = new MdcTaskDecorator();
        this.platformCalls = task -> virtualThreads.execute(requestContext.decorate(task));
        this.platformTimeout = platformTimeout;
        this.userRepository = userRepository;
        this.leetCodeService = leetCodeService;
        this.codeforcesService = codeforcesService;
        this.codeChefService = codeChefService;
        this.atCoderService = atCoderService;
        this.githubService = githubService;
        this.roastService = roastService;
        this.rateLimiterService = rateLimiterService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "Get AI roast for CP portfolio", description = "Generates a sarcastic roast based on the user's coding stats. Cached per user for 24h.")
    @GetMapping("/{username}/roast")
    public ResponseEntity<Map<String, String>> getRoast(@PathVariable String username, HttpServletRequest request) {
        // Each uncached roast is a paid LLM call
        rateLimiterService.checkLimit("rate:roast:" + clientIpResolver.resolve(request), 5, 3600);

        String roast = roastService.roast(username);
        return ResponseEntity.ok(Map.of("roast", roast != null ? roast : ROAST_UNAVAILABLE));
    }

    @Operation(summary = "Get full CP portfolio stats", description = "Aggregates stats from all connected CP platforms for a user.")
    @GetMapping("/{username}")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable String username, HttpServletRequest request) {
        // Fans out to five external services (one is a headless browser) per uncached view
        rateLimiterService.checkLimit("rate:portfolio:" + clientIpResolver.resolve(request), 30, 60);

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("User not found"));

        // Every platform is asked at once, each on its own virtual thread, with the same deadline. A slow or failing
        // platform is left out (CodeChef shows its placeholder instead) and listed in unavailablePlatforms; it never
        // holds up or fails the whole page. A call that misses the deadline keeps running, and its @Cacheable result
        // serves the next view. A failure is never cached (PlatformUnavailableException), so the next view asks again.
        Set<String> unavailable = ConcurrentHashMap.newKeySet();
        var leetcode = ask("leetcode", user.getLeetcodeUsername(), leetCodeService::getStats, unavailable);
        var codeforces = ask("codeforces", user.getCodeforcesUsername(), codeforcesService::getStats, unavailable);
        var codechef = ask("codechef", user.getCodeChefHandle(), codeChefService::getStats, unavailable,
                CodeChefService::unavailablePlaceholder);
        var atcoder = ask("atcoder", user.getAtCoderHandle(), handle -> {
            Map<String, Object> result = new HashMap<>(atCoderService.getStats(handle));
            result.put("handle", handle);
            return result;
        }, unavailable);
        var github = ask("github", user.getGithubUsername(), githubService::getStats, unavailable);
        CompletableFuture.allOf(leetcode, codeforces, codechef, atcoder, github).join();

        Map<String, Object> stats = new HashMap<>();
        int totalGloballySolved = 0;
        for (var platform : List.of(Map.entry("leetcode", leetcode), Map.entry("codeforces", codeforces),
                Map.entry("codechef", codechef), Map.entry("atcoder", atcoder))) {
            Map<String, Object> result = platform.getValue().join();
            if (result != null) {
                stats.put(platform.getKey(), result);
                totalGloballySolved += toInt(result.get("totalSolved"));
            }
        }
        Map<String, Object> githubStats = github.join();
        if (githubStats != null) {
            stats.put("github", githubStats);
        }

        stats.put("totalGloballySolved", totalGloballySolved);
        stats.put("unavailablePlatforms", unavailable.stream().sorted().toList());
        stats.put("profileType", user.getProfileType());

        return ResponseEntity.ok(stats);
    }

    /** One platform's stats; null if the user has no handle there, the call failed, or it missed the deadline. */
    private CompletableFuture<Map<String, Object>> ask(String platform, String handle,
                                                       Function<String, Map<String, Object>> call, Set<String> unavailable) {
        return ask(platform, handle, call, unavailable, () -> null);
    }

    /** As above, but a failed or late call is replaced by {@code fallback} (still listed as unavailable). */
    private CompletableFuture<Map<String, Object>> ask(String platform, String handle,
                                                       Function<String, Map<String, Object>> call, Set<String> unavailable,
                                                       Supplier<Map<String, Object>> fallback) {
        if (handle == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> call.apply(handle), platformCalls)
                .orTimeout(platformTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(failure -> {
                    unavailable.add(platform);
                    log.warn("Portfolio: {} unavailable ({})", platform, describe(failure));
                    return fallback.get();
                });
    }

    /** The underlying failure's type, such as TimeoutException or ResourceAccessException. Messages can echo URLs. */
    private static String describe(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof PlatformUnavailableException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}

