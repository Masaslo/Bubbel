#!/usr/bin/env bash
set -euo pipefail

tool_root="$(cd "$(dirname "$0")" && pwd)"
args=()
for arg in "$@"; do
  if [[ "$arg" != "--target=x86_64-unknown-linux-gnu" ]]; then
    args+=("$arg")
  fi
done
exec "$tool_root/zig-x86_64-linux-0.16.0/zig" cc -target x86_64-linux-gnu "${args[@]}"
