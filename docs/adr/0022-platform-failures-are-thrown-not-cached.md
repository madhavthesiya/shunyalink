# ADR 0022: Platform failures are thrown, never returned, so they are never cached

- **Status:** Accepted (roadmap item 3.6, first part)
- **Code:** `PlatformUnavailableException`, `GithubService`, `LeetCodeService`, `CodeforcesService`, `AtCoderService`,
  `CodeChefService`, `CpController.ask`, `RoastService`, `PlatformFailuresAreNotCachedTest`, `CpControllerTest`

## Context

Each coding platform's stats are cached in Redis for an hour (`@Cacheable`). On a failure, every service caught the
exception and **returned** a value: `{"error": "Could not fetch ..."}`, or for CodeChef a placeholder with "N/A".
Spring caches whatever a `@Cacheable` method returns. So one failed call (LeetCode rate-limiting us for a second, a
Codeforces 503 during a contest) hid that user's stats for the next hour of views. Only `CodeforcesService` had the
`unless = "... containsKey('error')"` rule that prevents this; the other four did not. A test that fails each platform
once and then lets it answer reproduced it for GitHub, LeetCode, AtCoder and CodeChef.

AtCoder also used the generic cache name `cpStats`, which any future service could have collided with.

## Options

1. **Add the `unless` rule to the four services that lack it.** The smallest change. But the rule is a string of SpEL
   that each new service must remember to copy, and that is exactly how four of five services ended up without it.
   It also cannot work for CodeChef, whose failure placeholder looks like data (a real reply can also lack a rating).
2. **Throw on failure.** Spring's cache never stores what a method throws. The rule is then built into the contract
   instead of being remembered per service.

## Decision

Option 2.

- **A failure throws `PlatformUnavailableException`:** a timeout, connection failure, 5xx, rate limiting, or a reply
  that can't be read.
- **A definitive answer is returned and cached like data.** GitHub's 404 ("no such user") is cached. GitHub allows
  60 anonymous requests an hour, so an unknown user must not cost one per view. A LeetCode reply for an unknown user
  is an ordinary reply and is cached too.
- **Codeforces 400 is not treated as "no such user".** Codeforces uses 400 for more than unknown handles, and caching
  a misread error for an hour is the bug being fixed here. Every Codeforces failure is thrown, so behaviour is
  unchanged.
- **AtCoder has two independent sources** (the rating from AtCoder, the solved count from Kenkoooo). If either
  answers, its part is returned and cached, as before. It throws only if neither answers because of a failure.
- **The portfolio controller** already turned an exception into "left out and listed in `unavailablePlatforms`"
  (ADR 0021). It now also takes a per-platform fallback. CodeChef's is its "N/A" placeholder, because the owner
  chose that the CodeChef widget stays visible while the scraper is down. The 3.7 deadline had made a slow CodeChef
  disappear instead; the placeholder now covers both a failure and a timeout.
- **The roast** names an unavailable platform as "unavailable" in its prompt. Before, the error map itself went
  into the prompt.
- AtCoder's cache is renamed `atcoder-stats`.

The frontend needs no change. It already hides a widget whose stats are missing or carry an `error` key, so
"left out" and the old error map look the same to it.

## Consequences

- (+) A platform blip costs one view, not an hour of views. A new platform service gets this right by default.
- (+) Logs name the real cause ("Portfolio: leetcode unavailable (ResourceAccessException)"), not a generic message.
- (−) While a platform is down, every uncached view calls it again. Each call is bounded by the portfolio deadline
  (5 s), and a circuit breaker per platform is the next step (3.6, second part).
- (−) An AtCoder result with one source missing can stay cached for an hour. This keeps today's behaviour, because
  Kenkoooo may block server requests, and treating that as a whole-platform failure could hide AtCoder for good.
- (−) The roast is still cached for 24 hours even when it was written while a platform was unavailable. Not caching
  such roasts would cost more paid AI calls; it can change if users notice.
