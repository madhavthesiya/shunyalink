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

Format: *Context → Options → Decision → Consequences*. A superseded decision stays in place with
its status changed, so the history of *why* is never lost.
