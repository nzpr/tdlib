#!/usr/bin/env bash
set -euo pipefail

# -------------------------------
# Paths
# -------------------------------
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
TD_SRC="$ROOT_DIR/td"
NATIVE_LIBS_FOLDER="$ROOT_DIR/src/main/resources/native"

# -------------------------------
# Detect Zig
# -------------------------------
ZIG_BIN="$(command -v zig || true)"
if [[ -z "$ZIG_BIN" ]]; then
  echo "ERROR: zig not found. Install Zig first."
  exit 1
fi
echo "Using Zig: $ZIG_BIN"

# Export compilers for CMake
export CC="$ZIG_BIN cc"
export CXX="$ZIG_BIN c++"

# -------------------------------
# Host OS + Arch
# -------------------------------
HOST_OS=$(uname -s | tr '[:upper:]' '[:lower:]' | tr -d '\r')
HOST_ARCH=$(uname -m | tr -d '\r')

case "$HOST_ARCH" in
arm64 | aarch64) HOST_ARCH="aarch64" ;;
x86_64 | amd64) HOST_ARCH="x86_64" ;;
*)
  echo "Unsupported arch: $HOST_ARCH"
  exit 1
  ;;
esac

case "$HOST_OS" in
darwin) HOST_OS="macos" ;;
linux) HOST_OS="linux" ;;
mingw* | msys*) HOST_OS="windows" ;;
*)
  echo "Unsupported OS: $HOST_OS"
  exit 1
  ;;
esac

echo "Detected OS: '$HOST_OS'"
echo "Detected Arch: '$HOST_ARCH'"
echo "Host detected: $HOST_OS-$HOST_ARCH"

# -------------------------------
# Only build host target locally
# -------------------------------
ALL_TARGETS=(
  # "linux:x86_64:x86_64-linux-gnu"
  # "macos:x86_64:x86_64-macos-gnu"
  "macos:aarch64:aarch64-macos-gnu"
  # "windows:x86_64:x86_64-windows-gnu"
)

# Only build all platforms on CI, else only host
if [[ "${CI:-false}" == "true" ]]; then
  BUILD_TARGETS=("${ALL_TARGETS[@]}")
  echo "CI detected → building ALL targets"
else
  echo "Local dev detected → building only host target"
  BUILD_TARGETS=()
  for row in "${ALL_TARGETS[@]}"; do
    IFS=":" read -r os arch triple <<<"$row"
    if [[ "$os" == "$HOST_OS" && "$arch" == "$HOST_ARCH" ]]; then
      BUILD_TARGETS+=("$row")
      break
    fi
  done
fi

# -------------------------------
# Java include paths
# -------------------------------
JAVA_INCLUDE="$JAVA_HOME/include"
JAVA_INCLUDE_OS="$JAVA_INCLUDE/$(uname | tr '[:upper:]' '[:lower:]')"

# -------------------------------
# Detect num of cores
# -------------------------------
if command -v nproc >/dev/null 2>&1; then
  NUM_CORES=$(nproc)
elif [[ "$(uname)" == "Darwin" ]]; then
  NUM_CORES=$(sysctl -n hw.ncpu)
else
  NUM_CORES=2 # fallback
fi

echo "Using $NUM_CORES cores"

# -------------------------------
# Build loop
# -------------------------------
for row in "${BUILD_TARGETS[@]}"; do
  IFS=":" read -r OS ARCH TARGET <<<"$row"
  echo "==== Building for $OS-$ARCH ===="

  JNI_BUILD_FOLDER="$ROOT_DIR/td/example/java"

  # -------------------------------
  # Step 1: Build TDLib
  # -------------------------------
  rm -Rf .build && mkdir .build
  pushd ".build" >/dev/null

  cmake "$TD_SRC" \
    -G Ninja \
    --debug-trycompile \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ROOT_DIR/toolchains/zig-$OS-$ARCH.cmake" \
    -DCMAKE_INSTALL_PREFIX="$JNI_BUILD_FOLDER/td" \
    -DTD_ENABLE_JNI=ON \
    -DCMAKE_C_FLAGS="-O3 -march=native -flto --target=$TARGET" \
    -DCMAKE_CXX_FLAGS="-O3 -march=native -flto --target=$TARGET -I$JAVA_INCLUDE -I$JAVA_INCLUDE_OS" \
    -DTDLIB_ENABLE_DOWNLOAD=ON
  cmake --build . -j "$NUM_CORES" --target install
  popd >/dev/null

  # -------------------------------
  # Step 2: Build JNI
  # -------------------------------
  pushd "td/example/java" >/dev/null
  rm -Rf .build && mkdir .build
  pushd ".build" >/dev/null
  cmake "$JNI_BUILD_FOLDER" \
    -G Ninja \
    --debug-trycompile \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$JNI_BUILD_FOLDER/td_jni" \
    -DTd_DIR:PATH="$JNI_BUILD_FOLDER/td/lib/cmake/Td" \
    -DCMAKE_TOOLCHAIN_FILE="$ROOT_DIR/toolchains/zig-$OS-$ARCH.cmake"
  cmake --build . -j "$NUM_CORES" --target install
  popd >/dev/null
  popd >/dev/null

  INSTALL_DIR="$NATIVE_LIBS_FOLDER/$OS-$ARCH"

  # Copy native lib
  mkdir -p "$INSTALL_DIR"
  cp "$JNI_BUILD_FOLDER/td_jni/bin/lib"* "$INSTALL_DIR"

  echo "✔ Output stored at: $INSTALL_DIR"
done

mkdir -p src/main/java/org/drinkless/tdlib
cp -Rf "$JNI_BUILD_FOLDER/org/drinkless/tdlib/TdApi.java" \
  "$JNI_BUILD_FOLDER/org/drinkless/tdlib/Client.java" \
  src/main/java/org/drinkless/tdlib

echo "🎉 Build finished!"
