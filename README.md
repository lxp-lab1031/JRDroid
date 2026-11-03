# JRDroid

Android スマートフォンと RTL2832U（R820T）で JR 在来線デジタル列車無線を受信する **実機評価中の α 版**です。v0.2.2-alpha は停止時クラッシュの報告を受けて終了処理を修正した版で、352 MHz 帯の D タイプ22チャンネルを選局できます（1波ずつ受信）。初期周波数は旅客一斉の 352.62500 MHz です。

**2026-10-28、ユーザーから Galaxy S26 のスピーカーで実際の放送音声が聞こえ、画面消灯中も音声が継続しているとの報告を受けました。最優先の受信・音声復号・再生の経路と、画面消灯中の継続再生はユーザーの実機試験で確認されました。2026-10-26 の録画では R820T 認識・IQ 入力・フレーム同期を確認しています。長時間の安定性、音声品質の比較、通信オフや長時間の省電力状態での動作は引き続き評価が必要です。**

この実機報告は v0.1.0-alpha の旅客一斉受信に対するものです。v0.2.0-alpha の選局操作・設定保存・他チャンネルの受信は、合成信号とホスト試験・ビルドで検証しており、実機での確認が必要です。

無音時の音声出力休止、IQ バッファー再利用、画面非表示中の診断削減を継続しています。v0.2.2-alpha では音声出力を同じスレッドで作成・使用・解放し、停止ボタンではキャンセル通知だけを行います。音声書き込みは非ブロッキングへ戻し、満杯のときだけ割り込み可能な20 ms待機を使います。IQ は約68 ms単位にまとめるため入力待ちの遅延が増える場合があります。**電池持ちの改善率と停止クラッシュの実機での解消は未確認**です。[停止処理の修正](docs/STOP_FIX.ja.md) と [省電力処理の記録](docs/POWER.ja.md) を参照してください。

## 構成

専用の日本語 Android アプリ（Java）と、MIT ライセンスの既存 Rust 受信処理を JNI で接続しました。音声モデルは APK に組み込み、推論に外部サービスは使いません。

```text
RTL2832U / R820T — USB-C OTG — 同じスマートフォン上の RTL-SDR ドライバー
  → 127.0.0.1:12345 / unsigned 8-bit IQ
  → Rust: π/4-DQPSK 復調 → 同期 → FEC/CRC → Modified RL-CELP 音声合成
  → JNI: 8 kHz / 16-bit / mono PCM → Android AudioTrack → スピーカー
```

**別途「RTL-SDR driver」アプリ（`marto.rtl_tcp_andro`）のインストールが必要です。ドライバーをこの APK には内蔵していません。** 初回のアプリ導入・開発用依存関係の取得には通信が必要ですが、受信処理に PC・外部サーバー・インターネット接続は不要な設計です。

| 項目 | 初期設定 |
|---|---|
| 周波数 | 352.53750〜352.66875 MHz、6.25 kHz 刻み、1波ずつ |
| 選局 | D1〜D7 の標準割当・旅客一斉・データ波の22項目、直接入力、前後ステップ |
| 入力 | 240,000 complex samples/s、unsigned 8-bit I/Q |
| RF ゲイン | 手動 40.2 dB |
| 周波数補正 | 0 ppm |
| 復調 | π/4-DQPSK、4,800 symbols/s |
| 音声 | Modified RL-CELP、8 kHz、16-bit、mono |
| 対象 ABI | arm64-v8a、ELF の 16 KiB ページ整列対応 |
| Android | minSdk 28、compileSdk / targetSdk 36。主対象 Galaxy S26 |

240 kS/s は RTL2832U が扱える範囲で、1波の帯域を確保しながら 2.4 MSPS より入力負荷を抑えます。SDR# の NFM・8 kHz 音声出力は使わず、ドライバーの生 IQ を直接処理します。RTL-TCP に設定値の読み戻しや USB 側の欠落通知はなく、指定値が機器に適用されたことや欠落ゼロをアプリが保証するものではありません。

## 導入・受信

[インストールと実機試験](docs/INSTALL.ja.md) に APK のインストール、ドライバー導入、受信開始、エラーの切り分け、実機での受け入れ条件を記載しています。

旧版の「更新」が進まない場合に備え、アプリ ID を分けた「JRDroid 別インストール」版も用意しました。`./gradlew assembleStandalone` で生成でき、元の JRDroid を削除せずに導入できます。詳細は上記手順を参照してください。

1. スマートフォンに RTL-SDR driver と JRDroid の APK をインストールします。
2. RTL2832U とアンテナを OTG 接続します。
3. 停止中に「周波数・D チャンネルを選択」または ±6.25 kHz で選局します。MHz の直接入力もできます。選択は保存されます。
4. JRDroid の「受信開始」を押し、ドライバーの USB アクセスを許可します。
5. IQ 数、フレーム同期、有効音声・PCM 数が進むか確認します。
6. 端末のメディア音量を調整します。「停止」または通知から停止できます。周波数を変える際はいったん停止してください。

D1〜D7 の名称は同梱の上流プリセットにある **JR東日本の標準割当**です。路線ごとに変則割当があるため MHz の値を確認してください。範囲内の22波に限定しており、他帯域は受け付けません。データ波の電文表示は未対応です。[周波数一覧と操作手順](docs/D_CHANNELS.ja.md) を参照してください。

実際の放送がないと、有効音声や PCM のカウンターは増えません。USB の列挙・ドライバー接続・フレーム同期・音声生成は別の状態として表示します。

## ビルド

Linux または macOS で、JDK 21（AGP の最低要件は JDK 17）、Android SDK Platform 36、Build-tools 36.0.0、NDK 28.2.13676358、Rust 1.90.0 と `aarch64-linux-android` ターゲットを用意してください。Gradle 8.13 は同梱 wrapper が取得します。

```bash
rustup toolchain install 1.90.0 --profile minimal
rustup target add --toolchain 1.90.0 aarch64-linux-android
export ANDROID_HOME=/path/to/Android/Sdk
export JAVA_HOME=/path/to/jdk-21
./gradlew assembleDebug
```

成果物: `app/build/outputs/apk/debug/app-debug.apk`。Gradle の `preBuild` が Rust のクロスビルドを実行し、`libjrdroid.so` を APK に格納します。`Cargo.lock` を固定して使用します。ホスト用の `host-jni` 機能は通常の Android ビルドでは有効になりません。

Android Studio でこのディレクトリを開き、SDK Manager で上記 SDK・NDK を導入して Gradle Sync を実行できます。Rust の `cargo` / `rustup` が Android Studio の起動環境の PATH に必要です。macOS の例は `source "$HOME/.cargo/env"` 後に Android Studio を起動します。Windows は Rust ビルドスクリプトの対象外のため、WSL/Linux でビルドしてください。

このクラウド環境では以下を使用します。

```bash
bash scripts/setup-cloud.sh
source /workspace/toolchains/activate.sh
bash scripts/check.sh
./gradlew --no-daemon assembleDebug lintDebug
```

クラウド用スクリプトは `/workspace/toolchains` にツールを置きます。Debian の既存 OpenJDK 21 runtime を前提に、JDK の開発部分、SDK、NDK、Rust、Gradle を取得します。TLS と公式公開チェックサムを検証し、ソースの reset・コミット・push は行いません。Android SDK の標準ライセンスを受諾する処理を含みます。

## 検証と現在の限界

- Rust の選択した受信・DSP・音声テスト: **93 成功、1 ignored、失敗 0**。ignored は実収録 IQ と環境変数が必要な上流の 22波性能試験で、1波の実機受信確認の代用にはしていません。
- 新規テストには、奇数バイト境界の IQ 保持、不正入力、合成した π/4-DQPSK IQ からの同期・音声合成、終端 flush を含みます。
- JVM/JNI のホスト検証18項目: 成功。22波の選局・音声出力、IQ のまとめ読み、再生末尾を保った休止・再開、軽量ステータスに加え、ネットワーク停止の非同期化、設定中の終了コマンドの直列化、200回の停止・再開、音声出力の所有スレッドと解放完了待ちを含みます。旅客一斉の JNI 試験では合成 IQ から **28 有効音声フレーム・6,720 PCM サンプル**を確認しました。7フレームの先読み分はこのホスト JNI 試験の終了時に破棄しています。
- APK ビルド、Android lint、署名検証、16 KiB 整列検査: 実行結果は [検証記録](docs/VALIDATION.ja.md) を参照してください。
- ユーザー提供録画で **実機の R820T 接続・Android 上の JNI 受信処理・フレーム同期**を確認し、2026-10-28 にユーザーから **Galaxy S26 のスピーカーで実放送の音声を確認し、画面消灯中も音声が継続している**との報告を受けました。音声成功の記録はユーザー報告によるもので、クラウドで再現した実機試験ではありません。**長時間のリアルタイム性能、通信オフ、長時間の省電力状態・通話割り込み・抜き差しは未検証**です。
- データ電文の表示、ゲイン・ppm の編集、録音、同時複数波受信、受信中の選局、USB ドライバー内蔵は未実装です。
- 音声は参照実装の推定モデルを含む合成です。ビット完全な規範デコーダや聞き取り品質を保証しません。

## ソースとライセンス

GitHub の `lxp-lab1031/JRDroid` への登録方法と、音声受信確認版のソース ZIP の内容は [公開手順](docs/PUBLISH.ja.md) を参照してください。

[調査結果](docs/RESEARCH.ja.md) に参照コミット、ライセンス、DSP/同期/音声 API、方式比較、技術的な課題をまとめています。

- `app/`: Android UI・サービス・AudioTrack・RTL-TCP クライアント。
- `native/bridge/`: IQ の変換、受信パイプライン、JNI、合成信号テスト。
- `native/vendor/`: 上流の固定コミットから取り込んだ未改変ソースとライセンス。
- `scripts/`: ビルド、検証、クラウド導入、ライセンス文書生成。
- `docs/`: 調査、導入、実機評価と検証記録。

JRDroid の新規コードは MIT。上流の JR 受信処理と CELP も MIT で、元の著作権・ライセンスを保持しています。Rust の依存ライブラリのライセンス全文は APK 内の「オープンソースライセンス」と `app/src/main/assets/THIRD_PARTY_NOTICES.txt` に収録しています。別インストールの RTL-SDR ドライバーは GPL-2.0-or-later などのライセンスで、今回の APK にはソース・バイナリを同梱していません。
