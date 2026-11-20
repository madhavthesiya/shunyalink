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
#
# Responses are parsed as JSON (jq), never matched as text: the order of JSON properties is not part of the
# contract. Jackson 3 (Spring Boot 4) sorts them, and a match on '{"status":"UP"' rolled back a healthy build
# (ecf9d0c, Nov 21, 2026). Tested by test-smoke-test.sh.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <base-url> <expected-commit> <short-id>" >&2
  exit 2
fi
command -v jq >/dev/null || { echo "::error::smoke-test.sh needs jq"; exit 2; }
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

# Prints the string at a jq path of the JSON on stdin, or nothing (missing field, or not JSON)
json_string() {
  jq -r "($1) // empty | strings" 2>/dev/null || true
}

echo "1/3 Waiting up to ${timeout_seconds}s for ${base_url} to serve commit ${expected_commit}"
deadline=$((SECONDS + timeout_seconds))
serving=""
while :; do
  info=$(curl -fsS --max-time 10 -A "$user_agent" "${base_url}/actuator/info" 2>/dev/null || true)
  serving=$(printf '%s' "$info" | json_string .app.commit)
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
  # The overall status is the top-level one; every component also has a "status" of its own.
  if [ "$status" = "200" ] && [ "$(printf '%s' "$body" | json_string .status)" = "UP" ]; then
    echo "    UP"
    break
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
