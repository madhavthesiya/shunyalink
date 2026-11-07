# ADR 0009: Upgrade frontend dependencies to address production advisories

- **Status:** Accepted (2026-11-07)
- **Code:** `frontend/package.json`, `frontend/package-lock.json`

## Context

The production dependency audit reported nine findings, including a critical Next.js advisory.
Leaving the versions unchanged retains known vulnerabilities; updating the whole dependency tree
would make it harder to attribute regressions. Zod's available fix crosses a major version.

## Options

| Option | For | Against |
|---|---|---|
| Leave versions unchanged | No upgrade risk | Known production advisories remain |
| Broad dependency refresh | Addresses more stale packages | Many unrelated changes to review and test |
| **Target security fixes and compatible lockfile updates** | Smaller review surface and a clean production audit | Zod still needs a major update; development advisories remain |

## Decision

Update Next.js and its ESLint configuration together to 16.4.5, and Zod to 4.6.7; apply the
non-breaking audit fixes to the lockfile. Source search found no direct Zod use, reducing but not
eliminating the risk of the major upgrade. Validate typecheck, lint, production build, clean install,
CI and Vercel preview. On 2026-11-07 `npm audit --omit=dev` reported zero vulnerabilities after
the updates. npm's full audit still reported eight moderate development-tool findings; its proposed
forced fix would downgrade `eslint-config-next` across a major version, so it was not applied.

## Consequences

- (+) The known production findings were cleared at the time of the audit.
- (−) A dependency scan is point-in-time evidence, not proof of safety; new advisories can appear.
- (−) Development tooling advisories remain for a compatible future fix and should be reviewed.
- (−) A major Zod upgrade can affect code added later; validate any future use against its API.
