#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

target="${1:-}"
mode="${2:-dev}"

if [[ "$target" != "chrome" && "$target" != "firefox" ]]; then
  echo "usage: $0 chrome|firefox [dev|zip]" >&2
  exit 1
fi
if [[ "$mode" != "dev" && "$mode" != "zip" ]]; then
  echo "second argument must be dev (default) or zip" >&2
  exit 1
fi

./check-manifests.sh

src="manifest.${target}.json"
if [[ ! -f "$src" ]]; then
  echo "missing $src" >&2
  exit 1
fi

cp "$src" manifest.json
echo "wrote manifest.json from $src"

if [[ "$mode" == "zip" ]]; then
  mkdir -p dist
  out="dist/${target}.zip"
  rm -f "$out"
  zip -rq "$out" manifest.json src/ icons/ _locales/
  echo "wrote $out"
fi
