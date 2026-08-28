#!/usr/bin/env bash
# Build Life for iPhone. Must run on a Mac with Xcode 15+.
# After a successful build, prints install steps (also in INSTALL.md).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

usage() {
  cat <<'EOF'
Usage: ./ios/build.sh [build|test|install|open]

  build     Compile Life.app for generic iOS (default)
  test      Run the JVM-style domain tests on the iPhone simulator
  install   Build and install onto a plugged-in iPhone
  open      Open Life.xcodeproj in Xcode

Requires macOS + Xcode 15 or newer. Signing uses your Apple ID
(free account is enough for your own phone).
EOF
}

print_install() {
  echo
  echo "======== install on iPhone ========"
  cat "$ROOT/INSTALL.md"
}

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Life for iPhone has to be built on a Mac with Xcode 15+."
  echo "This machine is $(uname -s) $(uname -m); there is no iOS toolchain here."
  echo
  echo "On a Mac, from the repo:"
  echo "  ./ios/build.sh            # compile"
  echo "  ./ios/build.sh install    # compile + put it on a plugged-in iPhone"
  echo "  ./ios/build.sh open       # or just open ios/Life.xcodeproj"
  print_install
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "xcodebuild not found. Install Xcode from the App Store, then:"
  echo "  sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
  echo "  sudo xcodebuild -license accept"
  print_install
  exit 1
fi

cmd="${1:-build}"
PROJECT=(-project "$ROOT/Life.xcodeproj" -scheme Life)

case "$cmd" in
  -h|--help|help)
    usage
    exit 0
    ;;
  open)
    open "$ROOT/Life.xcodeproj"
    echo "Opened Life.xcodeproj. Pick your Team under Signing & Capabilities, plug in the iPhone, press Run."
    print_install
    ;;
  test)
    DEST="${DESTINATION:-platform=iOS Simulator,name=iPhone 16}"
    xcodebuild "${PROJECT[@]}" \
      -destination "$DEST" \
      -configuration Debug \
      test
    echo
    echo "Tests finished."
    print_install
    ;;
  install)
    echo "Looking for a connected iPhone…"
    # Prefer CoreDevice (Xcode 15+); fall back to xctrace.
    UDID="${DEVICE_UDID:-}"
    if [[ -z "$UDID" ]] && command -v xcrun >/dev/null; then
      if xcrun devicectl list devices 2>/dev/null | grep -qi iphone; then
        UDID="$(xcrun devicectl list devices 2>/dev/null | awk 'BEGIN{IGNORECASE=1} /iPhone/ && /available|connected/ {print $3; exit}')"
      fi
      if [[ -z "$UDID" ]]; then
        UDID="$(xcrun xctrace list devices 2>/dev/null | awk '/iPhone/ && /\\(/ && $0 !~ /Simulator/ { if (match($0, /\\(([A-F0-9-]+)\\)/, a)) { print a[1]; exit } }')"
      fi
    fi
    if [[ -z "$UDID" ]]; then
      echo "No iPhone UDID found. Plug the phone in, unlock it, tap Trust."
      echo "Then list devices with:"
      echo "  xcrun xctrace list devices"
      echo "and rerun:"
      echo "  DEVICE_UDID=<udid> ./ios/build.sh install"
      echo
      echo "Or open Xcode and press Run:"
      echo "  ./ios/build.sh open"
      print_install
      exit 1
    fi
    echo "Installing to $UDID (needs a Signing Team in Xcode if this is the first time)."
    xcodebuild "${PROJECT[@]}" \
      -destination "id=$UDID" \
      -configuration Debug \
      -allowProvisioningUpdates \
      build
    echo
    echo "If xcodebuild installed the app, unlock the phone and open Life."
    echo "First launch on a free Apple ID: Settings → General → VPN & Device Management → trust your Apple ID."
    print_install
    ;;
  build)
    mkdir -p "$ROOT/build"
    xcodebuild "${PROJECT[@]}" \
      -destination 'generic/platform=iOS' \
      -configuration Debug \
      -derivedDataPath "$ROOT/build/DerivedData" \
      CODE_SIGNING_ALLOWED=NO \
      build
    APP="$(find "$ROOT/build/DerivedData" -name 'Life.app' -type d | head -n 1 || true)"
    echo
    echo "Build succeeded."
    if [[ -n "$APP" ]]; then
      echo "App bundle: $APP"
      echo "(This unsigned generic build cannot be copied onto a phone. Use ./ios/build.sh install or Xcode Run.)"
    fi
    print_install
    ;;
  *)
    usage
    exit 1
    ;;
esac
