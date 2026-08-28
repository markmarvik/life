#!/usr/bin/env bash
#
# scripts/fetch-needle.sh
# Download Needle 2's arm64 static lib (model baked in, ~20MB).
# Not committed. Pixel 8 Pro is arm64-v8a.
#
# Usage:  ./scripts/fetch-needle.sh
# Env:    NEEDLE_LIB_URL  override the Hugging Face URL

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/prebuilt/arm64-v8a"
OUT="$DEST/libneedle.a"
URL="${NEEDLE_LIB_URL:-https://huggingface.co/Cactus-Compute/needle2/resolve/main/android-arm64/libneedle.a}"

mkdir -p "$DEST"

if [[ -f "$OUT" && -s "$OUT" ]]; then
  echo "[fetch-needle] already have $OUT ($(wc -c < "$OUT") bytes)"
  exit 0
fi

echo "[fetch-needle] downloading $URL"
tmp="$OUT.part"
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 --retry-delay 2 -o "$tmp" "$URL"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$tmp" "$URL"
else
  echo "[fetch-needle] need curl or wget" >&2
  exit 1
fi

if [[ ! -s "$tmp" ]]; then
  rm -f "$tmp"
  echo "[fetch-needle] download empty" >&2
  exit 1
fi

mv "$tmp" "$OUT"
echo "[fetch-needle] wrote $OUT ($(wc -c < "$OUT") bytes)"
