#!/usr/bin/env bash
# Renders every appstore/screenshots/src/<locale>/*.html to a 1290 x 2796 JPEG
# (iPhone 6.9") in fastlane/screenshots/<locale>/, the layout `fastlane deliver` reads.
#
# Usage: appstore/screenshots/render.sh [locale ...]   (no argument = every locale)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
src="$here/src"
out="$root/fastlane/screenshots"

chrome="${CHROME:-}"
if [ -z "$chrome" ]; then
  for candidate in "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" google-chrome chromium chromium-browser; do
    if [ -x "$candidate" ] || command -v "$candidate" >/dev/null 2>&1; then chrome="$candidate"; break; fi
  done
fi
[ -n "$chrome" ] || { echo "Chrome not found. Set CHROME to its path." >&2; exit 1; }

to_jpeg() { # $1 png, $2 jpg. JPEG has no alpha channel, which App Store Connect refuses.
  if command -v sips >/dev/null 2>&1; then
    sips -s format jpeg -s formatOptions 95 "$1" --out "$2" >/dev/null
  else
    python3 -c 'import sys; from PIL import Image; Image.open(sys.argv[1]).convert("RGB").save(sys.argv[2], quality=95)' "$1" "$2"
  fi
}

size_of() { # prints "WIDTHxHEIGHT"
  if command -v sips >/dev/null 2>&1; then
    sips -g pixelWidth -g pixelHeight "$1" | awk '/pixelWidth/{w=$2} /pixelHeight/{h=$2} END{print w "x" h}'
  else
    python3 -c 'import sys; from PIL import Image; print("%dx%d" % Image.open(sys.argv[1]).size)' "$1"
  fi
}

if [ "$#" -gt 0 ]; then locales=("$@"); else
  locales=()
  for dir in "$src"/*/; do
    name="$(basename "$dir")"
    [ "$name" = "assets" ] || locales+=("$name")
  done
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
count=0
for locale in "${locales[@]}"; do
  [ -d "$src/$locale" ] || { echo "Unknown locale: $locale" >&2; exit 1; }
  mkdir -p "$out/$locale"
  for page in "$src/$locale"/*.html; do
    name="$(basename "$page" .html)"
    "$chrome" --headless=new --disable-gpu --no-sandbox --hide-scrollbars \
      --force-device-scale-factor=1 --window-size=1290,2796 --virtual-time-budget=8000 \
      --user-data-dir="$tmp/profile" --screenshot="$tmp/shot.png" "file://$page" >/dev/null 2>&1
    [ -s "$tmp/shot.png" ] || { echo "Chrome produced no image for $page" >&2; exit 1; }
    to_jpeg "$tmp/shot.png" "$out/$locale/$name.jpg"
    got="$(size_of "$out/$locale/$name.jpg")"
    [ "$got" = "1290x2796" ] || { echo "$locale/$name is $got, expected 1290x2796" >&2; exit 1; }
    rm -f "$tmp/shot.png"
    count=$((count + 1))
  done
  echo "rendered $locale"
done
echo "$count screenshots in $out"
