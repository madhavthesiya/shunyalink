#!/usr/bin/env bash
# Reject accidental manual builds and images unrelated to the selected main revision.
set -euo pipefail
if [ "$#" -ne 4 ]; then
  echo 'usage: validate-deploy-inputs.sh <event> <ref> <image-tag> <main-sha>' >&2
  exit 2
fi
event=$1 ref=$2 tag=$3 main_sha=$4
[ "$ref" = refs/heads/main ] || { echo 'Deployments must use main' >&2; exit 1; }
case "$event" in
  push)
    [ -z "$tag" ] || { echo 'Automatic deployments must build the current commit' >&2; exit 1; }
    ;;
  workflow_dispatch)
    [[ "$tag" =~ ^[0-9a-f]{40}$ ]] || { echo 'Manual deployment requires a full image commit SHA' >&2; exit 1; }
    git cat-file -e "${tag}^{commit}" 2>/dev/null && git merge-base --is-ancestor "$tag" "$main_sha" \
      || { echo 'Image commit must be in main history' >&2; exit 1; }
    ;;
  *) echo 'Unsupported deployment event' >&2; exit 1 ;;
esac
