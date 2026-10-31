#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_NDK_HOME:=$ANDROID_HOME/ndk/28.2.13676358}"
if [[ ! -d "$ANDROID_NDK_HOME" ]]; then
  echo 'Android NDK 28.2.13676358 が必要です。ANDROID_HOME または ANDROID_NDK_HOME を設定してください。' >&2
  exit 1
fi
case "$(uname -s)" in
  Linux) ndk_host=linux-x86_64 ;;
  Darwin) ndk_host=darwin-x86_64 ;;
  *) echo 'Rust ビルドは Linux/macOS または WSL で実行してください。' >&2; exit 1 ;;
esac
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$ndk_host/bin/aarch64-linux-android28-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
cd native
cargo build --locked --release --target aarch64-linux-android -p jrdroid -j 3
cd ..
mkdir -p app/src/main/jniLibs/arm64-v8a
cp native/target/aarch64-linux-android/release/libjrdroid.so app/src/main/jniLibs/arm64-v8a/
