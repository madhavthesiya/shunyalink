# ADR 0005: Fail fast on secrets, fail open on rate limiting, fail closed on revocation

- **Status:** Accepted (2026-11-01)
- **Code:** `application.properties` (no secret defaults), `SecretConfigurationTest`, `RedisGuard`, `AnalyticsFallbackBuffer`,
  `RateLimiterService`, `JwtAuthenticationFilter`, `LinkPasswordBackfill`, `ApplicationContextWiringTest`

## Context

Every check that depends on configuration or on Redis has to decide what happens when that
dependency is missing. The answers were previously accidental: secrets silently fell back to values
published in this repository, while other checks crashed or skipped unpredictably.

## Decision

Pick the policy per dependency by asking **"what is the worst outcome if this check is skipped?"**

| Dependency | If skipped… | Policy |
|---|---|---|
| `JWT_SECRET`, `ENCRYPTION_SECRET`, `LINK_PASSWORD_KEY` | Forged tokens / readable passwords with a public key: catastrophic and silent | **Fail fast**: no defaults; the app (and `docker compose`) refuses to start; blank or malformed values are rejected |
| Rate limiter (Redis) | A few minutes of weaker abuse protection: bounded, visible | **Fail open**: log and allow, so a cache outage doesn't take down login and shortening |
| JWT blacklist / revocation (Redis) | A logged-out or revoked session keeps working: a security failure | **Fail closed**: the request is rejected, never served as authenticated (today as a 500; returning 503 is a planned refinement) |
| Link password backfill at startup | Legacy values stay in the old format a little longer; unlock still works | **Never block startup**: log, retry on next start |
| Optional integrations (AI keys, scraper key, Google client ID) | One feature degrades | **Degrade gracefully** |
| Redis on the **redirect path** (cache, analytics `XADD`) | Cache misses go to PostgreSQL; a click goes to a buffer | **Circuit breaker, fail fast** (added 2026-11-18, below) |
| **Click analytics** | Some clicks are not counted | **Redirect first**: analytics never blocks, slows or fails a redirect; losses are counted, not prevented |

### Redis on the request path (2026-11-18)

Measured with the Redis container *paused*, so every command hangs until its 1 s timeout: each redirect took
**about 3,050 ms**, made of three timeouts (cache read, cache write, `XADD`) plus a synchronous database
fallback that took a row lock and one of the pool's **2** connections. With analytics ingestion enabled, a
Redis outage would therefore have become a whole-app outage.

- **Circuit breaker** (`RedisGuard`, Resilience4j): it opens when at least 50% of the last 10 calls fail
  (minimum 5 calls), skips Redis for 10 s, then lets 2 probe calls through. Only *unavailability*
  (connection failure, timeout) counts; a data error such as WRONGTYPE takes the fallback without
  tripping the breaker. With the same test, redirect latency went 3,084 → 2,027 → **5–65 ms** from the third redirect on.
- **No database work on request threads.** Fallback click events go to a bounded in-memory buffer
  (10,000 events, about 3 MB), drained by one background writer (`analytics-fallback-writer`) that uses
  at most one connection and backs off when the database fails. Event UUIDs make retries idempotent.
  When the buffer is full, events are **dropped and counted**. On shutdown it makes one final write
  attempt, and anything left is counted as lost.
- **Legacy recorder:** the analytics thread pool used `CallerRunsPolicy`. When saturated, it ran the
  click task, about 15 Redis commands, *on the redirect thread*. It is now `AbortPolicy` (rejected tasks
  are counted), and the work isn't queued at all while the breaker is open.

Considered and rejected:
- *A bigger pool or longer timeouts*: these move the cliff, they don't remove it.
- *Unbounded buffering*: this turns a Redis outage into an out-of-memory crash.
- *Writing to the database synchronously but faster*: the pool has 2 connections shared with every request.

Deploy safety follows from this: because missing secrets now stop the app, every merge to `main`
(which auto-deploys) is preceded by confirming the production settings, and
`ApplicationContextWiringTest` boots the full application with only infrastructure mocked, so
wiring errors are caught before deploy (run locally today; it becomes a required CI check when the
test gate is added). The test was verified by deliberately breaking a bean and watching it fail.

## Consequences

- (+) No silent insecure state: misconfiguration is loud and immediate.
- (+) A Redis outage degrades rate limiting rather than causing a full outage.
- (+) A Redis outage costs redirects about two slow requests, not a 3 s penalty on every request.
- (−) Clicks can be lost: the buffer is in memory (lost on a crash) and bounded (dropped when full).
  Both are counted (`shunyalink.analytics.fallback{outcome=dropped|lost_on_shutdown}`), so the loss is
  visible, not silent.
- (−) While the breaker is open, every redirect is a database lookup. That is fine at this traffic level;
  at much higher traffic, an in-process cache of hot links would carry the load during a Redis outage.
- (−) Adding a required secret is a deployment step that must happen *before* the code ships;
  the PR description and roadmap carry an explicit checklist for it.
