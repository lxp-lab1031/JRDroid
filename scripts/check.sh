#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cargo test --manifest-path native/Cargo.toml --locked --release -p jrdroid -p jr_conventional_dsp -p jr_conventional_voice -p receiver-core --no-default-features -j 3
mkdir -p app/build/host-tests
javac -encoding UTF-8 -d app/build/host-tests app/src/main/java/jp/jrdroid/{RtlTcp,NativeReceiver}.java tests/java/jp/jrdroid/*.java
java -cp app/build/host-tests jp.jrdroid.RtlTcpProtocolTest
cargo build --manifest-path native/Cargo.toml --locked --release -p jrdroid --features host-jni -j 3
cargo run --manifest-path native/Cargo.toml --locked --release -p jrdroid --example synthetic_iq -- app/build/host-tests/synthetic.u8
java -Djava.library.path=native/target/release -cp app/build/host-tests jp.jrdroid.JniSmokeTest app/build/host-tests/synthetic.u8
