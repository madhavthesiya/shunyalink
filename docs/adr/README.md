# Architecture Decision Records

Short records of decisions that had real alternatives, written when the decision was made.
Each one states the context, the options considered, what was chosen and what it costs.

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-encrypt-link-passwords-with-aes-gcm.md) | Link passwords are encrypted with AES-256-GCM, not hashed | Accepted |
| [0002](0002-client-ip-from-rightmost-public-hop.md) | Client IP = right-most public `X-Forwarded-For` hop | Accepted |
| [0003](0003-ssrf-guard-validates-every-redirect-hop.md) | Server-side URL fetches validate every redirect hop | Accepted |
| [0004](0004-user-wide-session-revocation.md) | User-wide JWT revocation with an "issued-before" cutoff | Accepted |
| [0005](0005-failure-policy-per-dependency.md) | Fail fast on secrets, fail open on rate limiting, fail closed on revocation | Accepted |
| [0006](0006-gated-deploys-with-smoke-test-and-rollback.md) | Deploys are gated on CI, verified by a smoke test, and rolled back automatically | Accepted |
| [0007](0007-supply-chain-controls-for-the-pipeline.md) | Pin actions to SHAs, cool down new releases, gate images on fixable criticals | Accepted |
| [0008](0008-ratchet-quality-gates.md) | Quality gates are ratchets on today's baseline, not fixed targets | Accepted |
| [0009](0009-upgrade-frontend-security-dependencies.md) | Upgrade frontend dependencies to address production advisories | Accepted |
| [0010](0010-manual-deploys-use-existing-main-images.md) | Manual deployments reuse images from main history | Accepted |
| [0011](0011-preserve-unknown-analytics-countries.md) | Keep unresolved click countries unknown | Accepted |
| [0012](0012-classify-analytics-bots-by-user-agent.md) | Classify analytics bots using User-Agent markers | Accepted for Phase 3 |
| [0013](0013-local-country-lookup.md) | Resolve countries from a local DB-IP database | Accepted for Phase 3 |
| [0014](0014-transactional-analytics-rollups.md) | Apply analytics events transactionally with stable identities | Accepted for Phase 3 |
| [0015](0015-scope-visitor-pseudonyms-by-link-and-day.md) | Scope visitor pseudonyms to a link and UTC day | Accepted for Phase 3 |
| [0016](0016-transactional-daily-unique-sketches.md) | Keep daily unique-visitor sketches with transactional rollups | Accepted for Phase 3 |
| [0017](0017-bound-analytics-labels.md) | Bound analytics labels before queueing events | Accepted for Phase 3 |
| [0018](0018-phishing-check-outside-the-transaction.md) | Run the phishing check before the transaction, and fail open visibly | Accepted |
| [0019](0019-request-ids-and-structured-logs.md) | Request IDs, JSON logs in production, and what is never logged | Accepted |
| [0020](0020-redirect-cache-ownership-negative-caching-jitter.md) | One owner for the redirect cache, negative caching and jittered expiry | Accepted |
| [0021](0021-virtual-threads-bulkheads-and-portfolio-deadlines.md) | Virtual threads for waiting, separate pools for background work, deadlines for the portfolio | Accepted |
| [0022](0022-platform-failures-are-thrown-not-cached.md) | Platform failures are thrown, never returned, so they are never cached | Accepted |
| [0023](0023-timeouts-and-circuit-breakers-per-upstream.md) | A timeout and a circuit breaker per external service, and no retries | Accepted |
| [0024](0024-safe-browsing-before-the-ai-check.md) | Google Safe Browsing first, the AI check second | Accepted |
| [0025](0025-shared-anonymous-links-under-concurrency.md) | Let the database decide which identical link wins, then return the winner | Accepted |
| [0026](0026-problem-details-and-validation-at-the-edge.md) | Problem Details for errors (without breaking clients), and validation at the edge | Accepted |
| [0027](0027-instants-in-code-utc-in-storage-schema-later.md) | Instants in the code and the API now; column types later, in a rollback-safe way | Accepted |
| [0028](0028-spring-boot-4-upgrade-proven-by-tests.md) | Spring Boot 4.1 as a version bump on its own, proven by the tests rather than by the compiler | Accepted |

Format: *Context → Options → Decision → Consequences*. A superseded decision stays in place with
its status changed, so the history of *why* is never lost.
