#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
repo=$(mktemp -d)
trap 'rm -rf "$repo"' EXIT
git -C "$repo" init -q
git -C "$repo" -c user.name=Test -c user.email=test@example.invalid commit -q --allow-empty -m first
first=$(git -C "$repo" rev-parse HEAD)
git -C "$repo" -c user.name=Test -c user.email=test@example.invalid commit -q --allow-empty -m second
head=$(git -C "$repo" rev-parse HEAD)
git -C "$repo" checkout -q --orphan unrelated
git -C "$repo" -c user.name=Test -c user.email=test@example.invalid commit -q --allow-empty -m unrelated
other=$(git -C "$repo" rev-parse HEAD)

check() {
  local expected=$1 event=$2 ref=$3 tag=$4
  if (cd "$repo" && bash "$script_dir/validate-deploy-inputs.sh" "$event" "$ref" "$tag" "$head" >/dev/null 2>&1); then
    actual=pass
  else
    actual=fail
  fi
  if [ "$actual" != "$expected" ]; then
    echo "Expected $expected, got $actual: event=$event ref=$ref tag=$tag" >&2
    exit 1
  fi
}

check pass push refs/heads/main ''
check fail push refs/heads/feature ''
check fail push refs/heads/main "$first"
check pass workflow_dispatch refs/heads/main "$first"
check pass workflow_dispatch refs/heads/main "$head"
check fail workflow_dispatch refs/heads/main ''
check fail workflow_dispatch refs/heads/main "$other"
check fail workflow_dispatch refs/heads/feature "$first"
check fail workflow_dispatch refs/heads/main 'latest'
check fail workflow_dispatch refs/heads/main 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
check fail pull_request refs/heads/main "$first"
echo 'Deployment input validation: 11 cases passed'

# Keep the mock rehearsal tied to the expression used by the real deployment.
workflow_dir="$script_dir/../workflows"
real_condition=$(grep -F 'if: ${{ !cancelled() && steps.previous.outputs.image' "$workflow_dir/deploy-backend-azure.yml" | sed 's/^[[:space:]]*//')
test -n "$real_condition"
count=0
while IFS= read -r condition; do
  [ "$condition" = "$real_condition" ] || { echo 'Rollback rehearsal condition differs from deploy workflow' >&2; exit 1; }
  count=$((count + 1))
done < <(grep -F 'if: ${{ !cancelled() && steps.previous.outputs.image' "$workflow_dir/rollback-rehearsal.yml" | sed 's/^[[:space:]]*//')
[ "$count" -eq 4 ] || { echo "Expected 4 rehearsal predicates, got $count" >&2; exit 1; }
echo 'Rollback rehearsal predicates match deployment workflow'
