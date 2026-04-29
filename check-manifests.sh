#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq required" >&2
  exit 2
fi

STRIP='del(.permissions, .background, .web_accessible_resources, .browser_specific_settings)'

chrome=$(jq -S "$STRIP" manifest.chrome.json)
firefox=$(jq -S "$STRIP" manifest.firefox.json)

if [[ "$chrome" != "$firefox" ]]; then
  echo "manifest.chrome.json and manifest.firefox.json diverge outside known browser-specific fields:"
  diff <(echo "$chrome") <(echo "$firefox") || true
  exit 1
fi

echo "manifests in sync"
