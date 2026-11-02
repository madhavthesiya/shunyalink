# ADR 0005: Fail fast on secrets, fail open on rate limiting, fail closed on revocation

- **Status:** Accepted (2026-11-01)
- **Code:** `application.properties` (no secret defaults), `SecretConfigurationTest`,
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

Deploy safety follows from this: because missing secrets now stop the app, every merge to `main`
(which auto-deploys) is preceded by confirming the production settings, and
`ApplicationContextWiringTest` boots the full application with only infrastructure mocked, so
wiring errors are caught before deploy (run locally today; it becomes a required CI check when the
test gate is added). The test was verified by deliberately breaking a bean and watching it fail.

## Consequences

- (+) No silent insecure state: misconfiguration is loud and immediate.
- (+) A Redis outage degrades rate limiting rather than causing a full outage.
- (−) Adding a required secret is a deployment step that must happen *before* the code ships;
  the PR description and roadmap carry an explicit checklist for it.
