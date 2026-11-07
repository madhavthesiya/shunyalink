# ADR 0006: Deploys are gated on CI, verified by a smoke test, and rolled back automatically

- **Status:** Accepted (2026-11-03)
- **Code:** `.github/workflows/ci.yml`, `.github/workflows/deploy-backend-azure.yml`, `.github/scripts/smoke-test.sh`

## Context

The backend deployed on every push to `main` that touched `backend/`. The workflow built the JAR
with `-DskipTests`, pushed an image and reported success as soon as Azure accepted it. Nothing ran
the tests, and nothing checked whether the new version actually started. A broken build, a missing
environment variable or a failing migration became an outage that someone had to notice by hand.

The backend is a single Azure App Service instance. During a deploy, the old container keeps
serving until the new one responds, so "health is UP right after deploy" can just mean the old
build is still running.

## Options

| Option | For | Against |
|---|---|---|
| Run tests inside the deploy workflow | Small change | Tests run twice on PRs and pushes; PRs still not gated |
| Deployment slots (blue-green swap with warm-up) | Zero-downtime swap, instant swap-back | Needs a Standard-tier App Service plan or higher: extra monthly cost at this project's scale |
| **CI gate + post-deploy smoke test + redeploy previous image on failure** | Free; every image is immutable and tagged by commit, so rollback is just a redeploy | A bad build can serve errors for the few minutes before the rollback lands |

## Decision

1. **Gate.** Deploy is a job in the CI workflow that `needs` the backend test job, and runs only on
   `main` automatically. Branch protection requires PR checks and applies to administrators. Manual
   runs are restricted to existing image tags from `main` history (ADR 0010).
2. **Build once, tag by commit.** The image is tagged only with the commit SHA. `latest` is no longer
   pushed, because a moving tag makes "what is running?" and "what do we roll back to?" ambiguous.
3. **Prove the new build is serving.** The image carries its commit (`APP_COMMIT`), published at
   `/actuator/info`. The smoke test waits until that equals the deployed commit, then requires
   `/actuator/health` UP and a real short link to answer `302`.
4. **Roll back automatically when possible.** Before deploying, the workflow attempts to record
   the commit-tagged image App Service is running. If available, it redeploys that image and
   smoke-tests it after a deploy or smoke-test failure.
5. **Manual rollback without a rebuild.** The deploy workflow can be run by hand with
   `image_tag = <earlier commit SHA>`.

## Consequences

- (+) Automatic deploys through CI require backend tests; a failed deploy can restore a known
  previous image when one was recorded. Manual redeployment still needs operational care.
- (+) "Which commit is live?" has a one-request answer: `curl https://sl.madhavv.me/actuator/info`.
- (−) Not zero-downtime. If the new build is broken, users can see errors until the rollback finishes.
  Deployment slots would fix this if the plan is ever upgraded.
- (−) **Rollback does not undo database migrations.** Flyway migrations must therefore stay
  backward compatible with the previous release (expand first, contract in a later release), or
  rolling back the code would break against the newer schema.
- (−) Each deploy adds one click to the smoke-test link (`SMOKE_SHORT_ID`, default `/ce`). Its User-Agent,
  `ShunyaLink-DeploySmoke`, is filtered out once bot filtering lands in the analytics pipeline.
- Images built before this pipeline do not publish their commit, so verifying a rollback to one of
  them times out; that needs a manual health check (once, during the transition).
