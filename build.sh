#!/usr/bin/env bash
#
# build.sh - CLI wrapper for building Life on Artix Linux
# Usage:
#   ./build.sh                  # clean + assembleDebug (default)
#   ./build.sh assembleDebug
#   ./build.sh test
#   ./build.sh installDebug

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${BLUE}[build.sh]${NC} $*"; }
warn()  { echo -e "${YELLOW}[build.sh]${NC} $*"; }
error() { echo -e "${RED}[build.sh]${NC} $*" >&2; }
success() { echo -e "${GREEN}[build.sh]${NC} $*"; }

show_help() {
  cat <<EOF
Life build helper (pure terminal — no Android Studio needed)

Usage:
  ./build.sh [gradle args...]

Examples:
  ./build.sh
  ./build.sh test
  ./build.sh clean assembleDebug
  ./build.sh installDebug
EOF
}

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  show_help
  exit 0
fi

find_or_install_java() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    info "Using existing JAVA_HOME=$JAVA_HOME"
    return 0
  fi

  local candidates=(
    "/usr/lib/jvm/java-17-openjdk"
    "/usr/lib/jvm/default"
    "/usr/lib/jvm/default-runtime"
    "/opt/java-17-openjdk"
    "$HOME/.sdkman/candidates/java/current"
  )

  for cand in "${candidates[@]}"; do
    if [[ -x "$cand/bin/java" ]]; then
      if "$cand/bin/java" -version 2>&1 | grep -q 'version "17'; then
        export JAVA_HOME="$cand"
        info "Detected JDK 17 at $JAVA_HOME"
        return 0
      fi
    fi
  done

  if command -v pacman >/dev/null 2>&1; then
    warn "Java 17 not found. Installing jdk17-openjdk via pacman..."
    sudo pacman -S --needed --noconfirm jdk17-openjdk
    if [[ -x /usr/lib/jvm/java-17-openjdk/bin/java ]]; then
      export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
      success "Installed and using JAVA_HOME=$JAVA_HOME"
      return 0
    fi
  fi

  if command -v java >/dev/null 2>&1; then
    local java_bin
    java_bin="$(command -v java)"
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$java_bin")")")"
    export JAVA_HOME
    warn "Falling back to JAVA_HOME=$JAVA_HOME (from PATH)"
    return 0
  fi

  error "Could not find or install Java 17."
  echo "On Artix Linux, run: sudo pacman -S jdk17-openjdk"
  exit 1
}

find_or_install_java
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -f gradlew && ! -x gradlew ]]; then
  chmod +x gradlew
fi

if [[ $# -eq 0 ]]; then
  info "No arguments given — defaulting to: clean assembleDebug"
  set -- clean assembleDebug
fi

# Needle native lib is only needed for APKs. Unit tests use the keyword fallback.
want_native=1
for arg in "$@"; do
  case "$arg" in
    test|testDebugUnitTest|*UnitTest) want_native=0 ;;
  esac
done
# Mixed `test assembleDebug` still wants the .so in the APK.
for arg in "$@"; do
  case "$arg" in
    assemble*|install*|bundle*) want_native=1 ;;
  esac
done

if [[ "$want_native" == "1" && "${LIFE_SKIP_NEEDLE:-}" != "1" ]]; then
  if ! bash "$SCRIPT_DIR/scripts/fetch-needle.sh"; then
    warn "Needle native lib missing — APK will use keyword fallback"
  fi
fi

native_prop=false
if [[ "$want_native" == "1" && -f app/src/main/cpp/prebuilt/arm64-v8a/libneedle.a ]]; then
  native_prop=true
  info "Needle native: on (arm64-v8a)"
else
  info "Needle native: off (keyword fallback)"
fi

info "JAVA_HOME=$JAVA_HOME"
info "Running: ./gradlew $* -Plife.needle.native=$native_prop"

exec ./gradlew "$@" "-Plife.needle.native=$native_prop"
