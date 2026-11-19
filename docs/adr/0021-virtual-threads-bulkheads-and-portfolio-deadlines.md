# ADR 0021: Virtual threads for waiting, separate pools for background work, deadlines for the portfolio

- **Status:** Accepted (roadmap item 3.7)
- **Code:** `spring.threads.virtual.enabled`, `AsyncConfig.applicationTaskExecutor`, `CpController.ask`,
  `VirtualThreadsIntegrationTest`, `AsyncExecutorsTest`, `CpControllerTest`

## Context

- **Requests mostly wait.** They wait on PostgreSQL in Sydney (about 120–150 ms per round trip), Redis, the AI phishing
  check (seconds), and competitive-programming platforms. Each waiting request held an OS thread from Tomcat's pool.
- **The portfolio fan-out was neither parallel nor bounded.** It sent five platform calls to the common ForkJoinPool,
  which is sized to the CPU count (one or two cores here), so blocking calls could run one after another. It had no
  overall deadline, so the CodeChef headless-browser scraper (10–15 s) held up the whole page, and one exception
  returned 500 for the whole portfolio.
- **Background work shared the analytics pool by accident.** No default `@Async` executor existed, because Spring Boot
  backs off when any executor bean is defined and `analyticsExecutor` was one. So emails and link previews ran on the
  analytics pool, which rejects work when full (ADR 0005). A click flood would have failed sign-up emails and link
  creation, because the preview is submitted inside the shorten transaction.

## Decision

- **Virtual threads for request handling and `@Scheduled` jobs** (`spring.threads.virtual.enabled=true`). A waiting
  virtual thread holds no OS thread. Database concurrency is now limited only by the 2-connection Hikari pool,
  whose 2 s connection timeout fails fast. Before, Tomcat's 200 threads were a second, hidden limit.
- **Portfolio:**
  - Each platform call runs on its own virtual thread, with request IDs carried over.
  - Each call has a deadline (`app.portfolio.platform-timeout`, 5 s) and its own fallback.
  - A platform that is slow or fails is left out and listed in `unavailablePlatforms`, and the rest are returned.
  - A call that misses the deadline keeps running, and its `@Cacheable` result serves the next view.
- **Bulkheads:** a separate pool for plain `@Async` work (`applicationTaskExecutor`, alias `taskExecutor`):
  - 4 **platform** threads, a queue of 1,000, and request IDs carried over.
  - It finishes queued emails on a normal shutdown.
  - It deliberately doesn't use virtual threads. This work is fire-and-forget, and a small fixed pool caps how much
    runs at once (each preview ends with a write to the 2-connection database pool). A pool of virtual threads would
    defeat their purpose.

## Options considered

| Option | Why not |
|---|---|
| A bigger Tomcat thread pool | Each thread still costs memory and a kernel thread. It moves the limit instead of removing it. |
| Reactive (WebFlux) | Rewrites every layer, and JDBC is still blocking. Virtual threads keep the simple blocking style. |
| `SimpleAsyncTaskExecutor` with virtual threads for `@Async` | Its concurrency limit **blocks the caller** when the limit is reached. A shorten would wait behind previews instead of queueing them. |
| One timeout for the whole portfolio | One slow platform would still cost the others their answer. A deadline per platform keeps every answer that arrived. |

## Consequences

- (+) Waiting no longer costs OS threads, and a test proves real Tomcat requests run on virtual threads.
- (+) The portfolio answers within about 5 s, whatever any one platform does, and says which platforms are missing.
- (+) Analytics overload can't fail emails or link creation.
- (−) **Pinning on Java 21:** a virtual thread blocked inside `synchronized` holds its carrier OS thread. The code paths
  here (Lettuce/Netty, the PostgreSQL driver 42.7, HikariCP, Logback 1.5) use `java.util.concurrent` locks, and the
  app's own `synchronized` method (`AnalyticsFallbackBuffer.drain`) runs on a dedicated platform thread. Java 24
  removes this limitation (JEP 491). Diagnose with `-Djdk.tracePinnedThreads=short` if needed.
- (−) **The database pool is the real limit.** With unlimited cheap threads, a database-heavy spike now fails fast at
  Hikari's 2 s timeout instead of queueing at Tomcat. A load test belongs to Phase 8 (k6).
- (−) **The frontend doesn't show `unavailablePlatforms` yet.** The field was added to the JSON and nothing breaks
  without it.
