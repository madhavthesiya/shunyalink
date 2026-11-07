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
