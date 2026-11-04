# JRDroid

Android スマートフォンと RTL2832U（R820T）で列車無線の受信を開発する **実機評価中の α 版**です。v0.3.2-alpha は、JR Dタイプ22波の選局機能に加え、ユーザー提供の基地局設定写真にある146 MHz帯16波の**試験受信**と5秒相当のIQ保存に対応しています。旧142 MHz帯の移動局16設定は削除し、旧版の保存設定は同じチャンネルの基地局側へ引き継ぎます。初期周波数は旅客一斉の352.62500 MHzです。受信は1波ずつです。

v0.3.2-alphaで「IQ をダウンロードに保存（ZIP）」を追加しました。Galaxyでは `ダウンロード/JRDroid` へ保存し、ChatGPTをCodexへ切り替えた後にファイル選択からZIPを添付できます。[保存・添付の手順](docs/IQ_EXPORT.ja.md) を参照してください。受信・音声復号処理は変更していません。

**2026-10-28、ユーザーから Galaxy S26 のスピーカーで実際の放送音声が聞こえ、画面消灯中も音声が継続しているとの報告を受けました。最優先の受信・音声復号・再生の経路と、画面消灯中の継続再生はユーザーの実機試験で確認されました。2026-10-26 の録画では R820T 認識・IQ 入力・フレーム同期を確認しています。長時間の安定性、音声品質の比較、通信オフや長時間の省電力状態での動作は引き続き評価が必要です。**

最初の実機報告は v0.1.0-alpha の旅客一斉受信に対するものです。その後、選局機能を追加した版についてD4音声第1波の受信報告も得ています。全22波の実電波を確認したものではありません。

無音時の音声出力休止、IQ バッファー再利用、画面非表示中の診断削減を継続しています。v0.2.2-alpha の音声出力の所有スレッド・非同期停止の修正を継承しており、2026-11-03 にユーザーから停止警告の件は解決済みとの回答を受けました。音声書き込みは非ブロッキングで、満杯のときだけ割り込み可能な20 ms待機を使います。IQ は約68 ms単位にまとめるため入力待ちの遅延が増える場合があります。**電池持ちの改善率は未測定**です。[停止処理の修正](docs/STOP_FIX.ja.md) と [省電力処理の記録](docs/POWER.ja.md) を参照してください。

v0.2.3-alpha は音声フォーカスを要求せず、他のメディアアプリの再生をJRDroidから止めない構成です。ユーザーから **D4音声第1波・旅客一斉の受信、TikTokとの音声ミックス、Bluetoothイヤホンへの出力、電池消費の低減**を確認したとの報告を受けました。YouTubeと音楽アプリとの同時再生、電池消費の定量比較は未確認です。[同時再生の手順](docs/AUDIO_MIX.ja.md) を参照してください。

**京浜急行等の音声復号は未確認です。146 MHz帯では既存のJR互換復号を試すだけで、私鉄用のフレーム・コーデックを実装済みとするものではありません。** 今回の変更は基地局側の選局設定で、実電波の音声復号を確認したものではありません。[146 MHz基地局試験版の使い方](docs/PRIVATE_BASE_STATION_TEST.ja.md) と [調査状況](docs/KEIKYU_RESEARCH.ja.md) を参照してください。

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
| 周波数 | JR Dタイプ22波 / 写真の146 MHz帯基地局16設定、1波ずつ |
| 選局 | 受信対象からJRまたは146 MHz基地局試験を選択。直接入力は登録周波数のみ |
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
3. 停止中に「受信対象・周波数を選択」でJRまたは146 MHz基地局試験を選び、一覧から設定します。JRは±6.25 kHz、146 MHz試験は画像の設定順で前後へ移動します。選択は保存されます。
4. JRDroid の「受信開始」を押し、ドライバーの USB アクセスを許可します。
5. IQ 数、フレーム同期、有効音声・PCM 数が進むか確認します。
6. 端末のメディア音量を調整します。「停止」または通知から停止できます。周波数を変える際はいったん停止してください。

D1〜D7 の名称は同梱の上流プリセットにある **JR東日本の標準割当**です。路線ごとに変則割当があるため MHz の値を確認してください。146 MHz帯はユーザー提供写真のK1/K2/K3/北総の基地局16設定のみ受け付けます。データ波と音声波を区別して表示します。会社・区間ごとの運用と復号方式の互換性は未確定です。データ波の電文表示は未対応です。[JR周波数一覧](docs/D_CHANNELS.ja.md) / [146 MHz基地局設定](docs/PRIVATE_BASE_STATION_TEST.ja.md) を参照してください。

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

- Rust の選択した受信・DSP・音声テスト: **94 成功、1 ignored、失敗 0**。ignored は実収録 IQ と環境変数が必要な上流の 22波性能試験で、1波の実機受信確認の代用にはしていません。
- 新規テストには、奇数バイト境界の IQ 保持、不正入力、合成した π/4-DQPSK IQ からの同期・音声合成、終端 flush を含みます。
- JVM/JNI のホスト検証25項目: 成功。JR22波の既存検証と200回の停止・再開に加え、146 MHz帯基地局16設定の周波数受け渡し・旧移動局の拒否・保存設定の移行、JR合成信号のPCM出力、5秒相当のIQ上限・途中停止の区別、共有データの不変性とパス制限を含みます。私鉄の実電波では試験していません。IQのZIP出力は、バイト列の保持・元の一時取得からの独立性・保存失敗時のロールバックも検証しています。
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
