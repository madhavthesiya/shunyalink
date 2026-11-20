# ADR 0023: A timeout and a circuit breaker per external service, and no retries

- **Status:** Accepted (roadmap item 3.6, second part)
- **Code:** `upstream/Upstreams`, `upstream/Upstream`, the five platform services, `LlmIntegrationService`,
  `UpstreamTest`, `BlockedPlatformPortfolioTest`, `LlmIntegrationServiceTest`

## Context

The backend calls eight external services: GitHub, LeetCode, Codeforces, AtCoder, Kenkoooo (AtCoder solved counts),
the CodeChef scraper, Gemini and Groq. All but the scraper shared one `RestTemplate` with a 3 s connect and 3 s read
timeout. Two problems:

- **A service that is down was asked again on every request, and each request waited the full timeout.** Measured
  with LeetCode replaced by a server that accepts connections and never answers, every portfolio view took about
  3 s: **[3949, 3010, 3020, 3016, 3020, 3014, 3005, 3013] ms**. Worse, the phishing check runs while a user waits
  for their short link. With Gemini down, every shorten would pay Gemini's timeout before Groq was asked.
- **One timeout did not fit every service.** Codeforces' `user.status` reply lists every submission and can be
  several MB. The scraper drives a headless browser and needs 15 s. And `HttpURLConnection`'s read timeout limits
  each read, not the whole response, so a slow trickle of bytes was never cut off.

## Decision

- **One class lists every external service** (`Upstreams`): its address, its read timeout, and its own circuit
  breaker. Link previews are not on the list. They fetch user-supplied URLs through `SafeUrlFetcher`, which has its
  own limits and SSRF checks (ADR 0003).
- **Spring's `RestClient` on the JDK `HttpClient`**, replacing `RestTemplate`:
  - One client, so one connection pool, with a 2 s connect timeout.
  - A read timeout per service that covers the whole response, body included:
    - 3 s for the platform APIs, below the portfolio's 5 s deadline;
    - 5 s for Kenkoooo, which took 2 s to answer even from a home connection. In production it had
      returned no solved count at all. It was not blocked, only slow: with the 5 s timeout, the count appeared
      (checked Nov 19);
    - 10 s for Codeforces;
    - 15 s for the scraper;
    - 3 s for each AI provider.
  - The JDK client does not hold an OS thread while a virtual thread waits on it (ADR 0021).
  - URIs are templates (`/users/{username}`), so a handle is always encoded as a single path segment or query
    value. Before, the handle was concatenated into the URL.
- **A circuit breaker per service** (Resilience4j, as for Redis in ADR 0005). It opens when at least half of the
  last 10 calls failed (judged after 5), fails calls at once for 30 s, then lets 2 probe calls through.
  - **Only signs of an unhealthy service count:** no answer in time, a connection failure, a 5xx, or a 429. A 404
    or 400 is an answer about the request, not about the service, so it can never open the breaker.
  - **Per service, not shared:** Kenkoooo being down must not stop AtCoder ratings, and Gemini being down must not
    stop Groq.
  - The breaker logs one line when it opens and one when it closes. Its state and skipped calls are metrics
    (`shunyalink.upstream.breaker.open{upstream}`, `shunyalink.upstream.breaker.skipped{upstream}`).

Measured with the same hanging LeetCode: **[3220, 3017, 3006, 3006, 3015, 2, 2, 2] ms**. LeetCode received 5
connections, and none after its breaker opened. The other platforms were returned on every view.

## Options considered and rejected

- **Retries with backoff.** Rejected for now:
  - A platform result is cached for an hour once it succeeds, and a failure is never cached (ADR 0022), so the next
    view already is the retry.
  - The AI calls fall back to a second provider, which is a better retry than asking the failed one again.
  - Inside a 5 s deadline, a retry after a 3 s timeout cannot finish anyway, and retries add load to a service that
    is already struggling (the "retry storm").
  - A retry is worth adding when a caller cannot retry by itself and the call is idempotent. A background job is an
    example.
- **Resilience4j's Spring Boot starter with annotations (`@CircuitBreaker`, `@Retry`).** Not used:
  - It would bring AOP proxies that interact with `@Cacheable`'s proxy, and their order decides whether a cache
    hit counts as a call.
  - Configuration would be spread over YAML.
  - The programmatic breaker is the same library already used for Redis, testable without a Spring context, and
    one class shows every external service.
  - Cost: no `/actuator/circuitbreakers` endpoint. The two metrics above cover it.
- **One shared breaker for all services.** Rejected: one service's outage would stop the healthy ones.
- **Lower timeouts so that even the first views stay under 3 s.** The first five views of a hanging service take
  its 3 s read timeout. Going lower risks cutting off slow but healthy replies (a cold TLS connection, a busy
  platform). The breaker removes the repeated wait instead.

## Consequences

- (+) A service that is down costs five slow calls per 30 s, not one per request.
- (+) Every external dependency and its limits are in one file.
- (−) While a breaker is open, a service that has recovered is not used for up to 30 s.
- (−) The breakers are per instance and in memory. With several instances, each would learn separately. That is
  acceptable: each instance only protects its own threads.
- (−) Low traffic means a breaker may never see 5 calls in a short outage. Then the timeouts and the portfolio
  deadline are the protection, which is fine at low traffic.
