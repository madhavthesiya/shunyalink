#!/usr/bin/env bash
# Post-deploy smoke test for the backend.
#
#   smoke-test.sh <base-url> <expected-commit> <short-id>
#
# 1. Waits until <base-url>/actuator/info reports <expected-commit>. A healthy response alone is not
#    enough: until Azure switches containers, the OLD build keeps answering and would pass.
# 2. Checks /actuator/health reports UP overall.
# 3. Checks that /<short-id>, a real link, still answers 302 with a Location. This exercises routing,
#    PostgreSQL or the Redis cache, and the redirect itself.
#
# Exits non-zero on the first failed check. SMOKE_TIMEOUT_SECONDS (default 900) bounds step 1: cold
# starts on the App Service plan have taken up to ~5 minutes.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <base-url> <expected-commit> <short-id>" >&2
  exit 2
fi
base_url=${1%/}
expected_commit=$2
short_id=$3
timeout_seconds=${SMOKE_TIMEOUT_SECONDS:-900}
poll_seconds=${SMOKE_POLL_SECONDS:-15}
user_agent="ShunyaLink-DeploySmoke/1.0"

fail() {
  echo "::error::$*"
  exit 1
}

# Prints the value of a top-level-unique JSON string field, or nothing
json_field() {
  grep -o "\"$1\":\"[^\"]*\"" | head -n 1 | cut -d '"' -f 4
}

echo "1/3 Waiting up to ${timeout_seconds}s for ${base_url} to serve commit ${expected_commit}"
deadline=$((SECONDS + timeout_seconds))
serving=""
while :; do
  info=$(curl -fsS --max-time 10 -A "$user_agent" "${base_url}/actuator/info" 2>/dev/null || true)
  serving=$(printf '%s' "$info" | json_field commit || true)
  if [ "$serving" = "$expected_commit" ]; then
    break
  fi
  if [ "$SECONDS" -ge "$deadline" ]; then
    fail "Timed out after ${timeout_seconds}s: serving commit '${serving:-<no answer>}', expected '${expected_commit}'"
  fi
  sleep "$poll_seconds"
done
echo "    serving ${serving} after ~$((SECONDS))s"

echo "2/3 Checking health"
health=$(curl -fsS --max-time 10 -A "$user_agent" "${base_url}/actuator/health") \
  || fail "/actuator/health did not answer 2xx"
case "$health" in
  '{"status":"UP"'*) echo "    UP" ;;
  *) fail "/actuator/health is not UP: ${health}" ;;
esac

echo "3/3 Checking that /${short_id} redirects"
result=$(curl -sS --max-time 10 -A "$user_agent" -o /dev/null -w '%{http_code} %{redirect_url}' "${base_url}/${short_id}") \
  || fail "/${short_id} did not answer"
code=${result%% *}
location=${result#* }
if [ "$code" != "302" ] || [ -z "$location" ]; then
  fail "/${short_id} answered ${code} (Location: '${location}'), expected 302 with a Location"
fi
echo "    302 -> ${location}"

echo "Smoke test passed"
