#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cargo test --manifest-path native/Cargo.toml --locked --release -p jrdroid -p jr_conventional_dsp -p jr_conventional_voice -p receiver-core --no-default-features -j 3
mkdir -p app/build/host-tests
javac -encoding UTF-8 -d app/build/host-tests app/src/main/java/jp/jrdroid/{DChannels,RadioChannels,RtlTcp,NativeReceiver,IqBlockReader,IqCaptureBuffer,IqCaptureFiles,PcmPlayback,ReceiverSession,AudioPump}.java tests/java/jp/jrdroid/*.java
java -cp app/build/host-tests jp.jrdroid.DChannelsTest
python3 - <<'PY'
import json,subprocess
upstream=json.load(open('native/vendor/ui/radio-presets.json'))
channels=next(s['channels'] for s in upstream['channel_sets'] if s['id']=='east-downlink')
actual=dict(line.split('\t',1) for line in subprocess.check_output(['java','-cp','app/build/host-tests','jp.jrdroid.DChannelsTest','--dump'],text=True).splitlines())
assert set(actual)=={str(c['frequency_hz']) for c in channels}
for c in channels:
    expected='旅客一斉' if c['service']=='common' else c['label']
    assert actual[str(c['frequency_hz'])]==expected
print('PASS: all 22 channel labels/frequencies match the vendored upstream assignments')
PY
java -cp app/build/host-tests jp.jrdroid.RtlTcpProtocolTest
cargo build --manifest-path native/Cargo.toml --locked --release -p jrdroid --features host-jni -j 3
cargo run --manifest-path native/Cargo.toml --locked --release -p jrdroid --example synthetic_iq -- app/build/host-tests/synthetic.u8
java -Djava.library.path=native/target/release -cp app/build/host-tests jp.jrdroid.JniSmokeTest app/build/host-tests/synthetic.u8
java -Djava.library.path=native/target/release -cp app/build/host-tests jp.jrdroid.PowerPathTest
java -cp app/build/host-tests jp.jrdroid.SessionLifecycleTest
java -Djava.library.path=native/target/release -cp app/build/host-tests jp.jrdroid.Private142Test app/build/host-tests/synthetic.u8 app/build/host-tests/private-synthetic.jrd-iq
python3 scripts/inspect-capture.py app/build/host-tests/private-synthetic.jrd-iq
