#!/usr/bin/env bash
# Post-deploy smoke test for the backend.
#
#   smoke-test.sh <base-url> <expected-commit> <short-id>
#
# 1. Waits until <base-url>/actuator/info reports <expected-commit>. A healthy response alone is not
#    enough: until Azure switches containers, the OLD build keeps answering and would pass.
# 2. Checks /actuator/health reports UP overall, retrying for up to SMOKE_HEALTH_SECONDS (default 60).
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

# Retried, not checked once: in the first seconds after a start the 2-connection database pool can be busy,
# and the database health check gives up after its 2 s connection timeout (deploy of 6ef00a4, Nov 20). A real
# outage is still DOWN at the end of the window, so the deploy is still rolled back.
health_seconds=${SMOKE_HEALTH_SECONDS:-60}
health_poll_seconds=${SMOKE_HEALTH_POLL_SECONDS:-5}
echo "2/3 Checking health (up to ${health_seconds}s)"
health_deadline=$((SECONDS + health_seconds))
while :; do
  # No -f: a 503 body names the component that is down, which is what a failed deploy must show.
  response=$(curl -sS --max-time 10 -A "$user_agent" -w '\n%{http_code}' "${base_url}/actuator/health" 2>/dev/null || true)
  status=${response##*$'\n'}
  body=${response%$'\n'*}
  if [ "$status" = "200" ]; then
    case "$body" in
      '{"status":"UP"'*) echo "    UP"; break ;;
    esac
  fi
  if [ "$SECONDS" -ge "$health_deadline" ]; then
    fail "/actuator/health not UP after ${health_seconds}s: HTTP ${status:-<no answer>} ${body}"
  fi
  echo "    not UP yet: HTTP ${status:-<no answer>} ${body}"
  sleep "$health_poll_seconds"
done

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
