#!/usr/bin/env bash
set -euo pipefail

target="${1:-}"
if [[ "$target" != "chrome" && "$target" != "firefox" ]]; then
  echo "usage: $0 chrome|firefox" >&2
  exit 1
fi

src="manifest.${target}.json"
if [[ ! -f "$src" ]]; then
  echo "missing $src" >&2
  exit 1
fi

cp "$src" manifest.json
echo "wrote manifest.json from $src"
