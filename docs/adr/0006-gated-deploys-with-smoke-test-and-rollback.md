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

## Update (2026-11-20): health is retried for up to 60 s, and a failure says why

The deploy of `6ef00a4` was rolled back although the new build was fine. It was serving the right commit, but
`/actuator/health` answered 503 once. The container logs showed why: `DataSource health check failed:
CannotGetJdbcConnectionException`, three health requests at the same moment, each giving up at the pool's 2 s
connection timeout. In the first seconds after a start, the 2-connection pool to the database in Sydney is still
warming up while the cold JVM serves the first requests. The logs of the previous, successful deploy show the same
pool exhaustion right after startup; it just didn't coincide with the check.

A gate that fails good builds teaches people to ignore it, so the check is now retried for up to 60 s
(`SMOKE_HEALTH_SECONDS`), and on failure it prints the HTTP status and the body, which names the component that is
down. A real outage is still DOWN at the end of the window, so a broken build is still rolled back. Tested against a
fake backend: health that stays DOWN fails with the body shown; health that is DOWN for two checks and then UP passes.

Rejected: removing the database from the health check (it is the one dependency the app cannot work without), and
raising the pool's connection timeout (it protects every request, not only the health check).

### Update (Nov 21, 2026): a healthy build rolled back, because the gate read JSON as text

The first Spring Boot 4 deploy (`ecf9d0c`) answered health with HTTP 200 and everything UP, but Jackson 3 sorts JSON
properties, so the body began with `"components"`, not `"status"`. The smoke test matched the prefix
`{"status":"UP"` and rolled the build back. It now parses responses with `jq` (the top-level `status`, and
`app.commit`), and `test-smoke-test.sh` runs it in CI against a stub backend. The gate is code that decides
whether a deploy stays, so it gets tests like any other code. Details in ADR 0028.
