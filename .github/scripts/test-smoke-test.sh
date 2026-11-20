#!/usr/bin/env bash
# Runs smoke-test.sh against a local stub of the backend (needs python3, curl and jq, as on GitHub's runners).
#
# The health check must read the top-level "status" whatever the order of the properties: Jackson 3 (Spring Boot 4)
# sorts them, so the body starts with "components". A text match on '{"status":"UP"' rejected a healthy build and
# rolled back the deploy of ecf9d0c (Nov 21, 2026).
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
stub=$(mktemp -d)

python3 - "$stub" <<'PY' &
import http.server, pathlib, sys

root = pathlib.Path(sys.argv[1])

class Backend(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/actuator/info":
            self.reply(200, (root / "info.json").read_bytes())
        elif self.path == "/actuator/health":
            self.reply(int((root / "health.code").read_text()), (root / "health.json").read_bytes())
        elif self.path == "/abc":
            self.send_response(302)
            self.send_header("Location", "https://example.com/")
            self.send_header("Content-Length", "0")
            self.end_headers()
        else:
            self.reply(404, b"{}")

    def reply(self, code, body):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass

server = http.server.HTTPServer(("127.0.0.1", 0), Backend)
(root / "port").write_text(str(server.server_port))
server.serve_forever()
PY
server_pid=$!
trap 'kill "$server_pid" 2>/dev/null || true; rm -rf "$stub"' EXIT

for _ in $(seq 50); do
  [ -s "$stub/port" ] && break
  sleep 0.1
done
[ -s "$stub/port" ] || { echo "The stub backend did not start" >&2; exit 1; }
base="http://127.0.0.1:$(cat "$stub/port")"
printf '{"app":{"commit":"c0ffee"}}' > "$stub/info.json"

check() {
  local expected=$1 code=$2 body=$3 name=$4 actual
  printf '%s' "$code" > "$stub/health.code"
  printf '%s' "$body" > "$stub/health.json"
  if SMOKE_TIMEOUT_SECONDS=5 SMOKE_POLL_SECONDS=1 SMOKE_HEALTH_SECONDS=2 SMOKE_HEALTH_POLL_SECONDS=1 \
      bash "$script_dir/smoke-test.sh" "$base" c0ffee abc > "$stub/output" 2>&1; then
    actual=pass
  else
    actual=fail
  fi
  if [ "$actual" != "$expected" ]; then
    echo "FAILED: $name: expected $expected, got $actual" >&2
    sed 's/^/    /' "$stub/output" >&2
    exit 1
  fi
  echo "ok: $name ($actual)"
}

check pass 200 '{"status":"UP","components":{"db":{"status":"UP"}}}' 'Spring Boot 3 order, status first'
check pass 200 '{"components":{"db":{"status":"UP"}},"status":"UP"}' 'Spring Boot 4 order, properties sorted'
check fail 503 '{"components":{"db":{"status":"DOWN"}},"status":"DOWN"}' 'a component is down'
check fail 200 '{"components":{"db":{"status":"UP"}},"status":"UNKNOWN"}' 'only a component says UP'
check fail 200 'not json' 'a body that is not JSON'
echo "smoke-test.sh: all checks passed"
