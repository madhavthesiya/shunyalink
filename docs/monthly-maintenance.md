# Monthly supply-chain review

The repository owner performs this review once per calendar month and when a relevant security
alert arrives. Dependency alerts remain enabled; automatic update proposals remain disabled.
This is a manual checklist, not a scheduled job or a claim that dependencies are vulnerability-free.

Record the date, source commit, commands, exit codes, findings and follow-up PRs in the review notes.
Mark skipped or failed checks explicitly. A scanner error is not a clean result.

## Frontend dependencies

From `frontend`, run `npm audit --omit=dev` against the committed lockfile. Review the advisory and
affected dependency path before choosing an update. Open focused PRs and run typecheck, lint and
build after dependency changes. Apply the ordinary 14-day release cooldown; do not delay security
fixes. Review development dependencies and action pins too, since a production-only npm audit does
not cover build tools or backend dependencies.

## Backend image

Build and scan the reviewed source revision with the same pinned scanner and policy as deployment.
The Bash commands below run from the repository root with Docker available (for example, Linux or
WSL). Reports go to a temporary directory. The first scan deliberately includes reviewed exceptions;
the second applies the deployment gate. Neither scan deploys an image.

```bash
task_dir=$(mktemp -d)
source_commit=$(git rev-parse HEAD)
image="shunyalink-maintenance:$source_commit"
trivy_image=$(awk '$1 == "TRIVY_IMAGE:" {print $2; exit}' .github/workflows/deploy-backend-azure.yml)
test -n "$trivy_image" || exit 1
docker build -f backend/Dockerfile.backend --build-arg "APP_COMMIT=$source_commit" -t "$image" backend || exit 1

docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v shunyalink-trivy-cache:/root/.cache/trivy \
  "$trivy_image" image --timeout 20m --scanners vuln \
  --severity HIGH,CRITICAL --format table "$image" > "$task_dir/trivy-report.txt"
report_status=$?

docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -v shunyalink-trivy-cache:/root/.cache/trivy \
  -v "$PWD/.github/trivy-exceptions.yaml:/policy/exceptions.yaml:ro" \
  "$trivy_image" image --timeout 20m --scanners vuln --ignore-unfixed \
  --severity CRITICAL --exit-code 1 --ignorefile /policy/exceptions.yaml \
  "$image" > "$task_dir/trivy-gate.txt"
gate_status=$?
printf 'Report exit: %s; gate exit: %s; reports: %s\n' "$report_status" "$gate_status" "$task_dir"
test "$report_status" -eq 0 && test "$gate_status" -eq 0
```

Do not use `--skip-db-update` for the monthly review. Record the image/source revision, scanner
version and database freshness with the findings. Review HIGH findings and relevant unfixed issues
even though the deployment gate blocks only fixable CRITICAL findings. Read every exception's
package scope, applicability and expiry in `.github/trivy-exceptions.yaml`; do not extend an expiry
just to make a scan pass. The current Spring MVC exceptions expire on **2027-02-07**; plan their
removal through the framework migration described in ADR 0007.

## GeoIP data and pinned inputs

- Check whether a newer DB-IP Country Lite release should replace the pinned version. Follow
  [ADR 0013](adr/0013-local-country-lookup.md): retain a verified copy with attribution and license
  details on a new data release, update its asset ID and checksum in a PR, test lookups and build
  the image. Retain older assets; never replace bytes under an existing asset ID or skip checksums.
- Review security alerts for pinned GitHub actions, the scanner and image base layers. Review a new
  revision before changing its pin; a fixed SHA or digest does not prove the selected code is safe.
- Record findings as fixed, tracked with a follow-up, or accepted with a reason and review date.
  Keep unresolved findings visible. Repository-setting changes still require owner approval.

See [ADR 0007](adr/0007-supply-chain-controls-for-the-pipeline.md) for the gate and cooldown policy.
