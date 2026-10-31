#!/usr/bin/env bash
# Reproducible Linux cloud toolchain. No root access and no repository reset.
set -euo pipefail
cd "$(dirname "$0")/.."
export RUSTUP_HOME=/workspace/toolchains/rustup
export CARGO_HOME=/workspace/toolchains/cargo
python3 scripts/setup-toolchains.py
source /workspace/toolchains/activate.sh
if ! command -v rustup >/dev/null; then
  curl -fLsS https://sh.rustup.rs -o /workspace/toolchains/downloads/rustup-init.sh
  bash /workspace/toolchains/downloads/rustup-init.sh -y --no-modify-path --profile minimal --default-toolchain 1.90.0
fi
rustup toolchain install 1.90.0 --profile minimal
rustup target add --toolchain 1.90.0 aarch64-linux-android
cargo fetch --manifest-path native/Cargo.toml --locked
python3 scripts/generate-notices.py
./gradlew --no-daemon assembleDebug
