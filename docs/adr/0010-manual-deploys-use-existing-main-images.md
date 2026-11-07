# ADR 0010: Manual deployments reuse images from main history

- **Status:** Accepted (2026-11-07)
- **Code:** `.github/workflows/deploy-backend-azure.yml`, `.github/scripts/validate-deploy-inputs.sh`, `.github/workflows/rollback-rehearsal.yml`

## Context

Automatic deployment follows the backend test job on `main`, but a manual run could build a selected
branch without that gate. Manual rollback must still be possible if the current release fails. A
production rollback has not been exercised, and testing it by intentionally breaking production would
interrupt users.

## Options

| Option | For | Against |
|---|---|---|
| Allow a manual build from any branch | Convenient for emergency fixes | Can bypass tests and PR review |
| Disable manual deployments | Smallest attack surface | No direct rollback path |
| **Redeploy an existing image from main history only** | Preserves rollback without an untested manual rebuild | Image availability and database compatibility still need operator judgment |

## Decision

Manual dispatch requires selecting `main` and providing a full commit SHA that is an ancestor of the
selected main revision; it redeploys an existing registry image without building. Automatic `main`
pushes build the current commit only after the backend test job. Validate inputs before Azure login.
Run a GitHub Actions rehearsal using mock steps for deploy failure, smoke failure, successful deploy
and missing rollback target. The rehearsal does not call Azure, so it tests the workflow's rollback
conditions, not registry access or the actual Azure deployment action.

The checks guard normal use of this workflow, not a malicious actor who can change workflow code or
registry images. Historical image tags are not proof that an image passed today's tests. A rollback
does not undo database migrations and must be compatible with the live schema.

## Consequences

- (+) Operators retain a rollback path without accidentally building an untested selected branch.
- (+) Failure and success conditions are exercised without disrupting production.
- (−) Manual fixes must follow the normal PR and CI path before a new image can be deployed.
- (−) Full rollback on Azure remains unproven until exercised in a suitable non-production environment.
