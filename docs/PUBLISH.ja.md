# ソースと APK の公開

公開先: https://github.com/lxp-lab1031/JRDroid

## v0.2.3-alpha: 他アプリとの音声ミックス

2026-11-03 に音声フォーカスの要求をやめ、Androidのメディア出力に従うミックス再生構成へ変更しました。停止修正版の終了処理を継承しています。生成物は `JRDroid-0.2.3-alpha-debug.apk` / `JRDroid-0.2.3-alpha-standalone.apk` / `JRDroid-0.2.3-alpha-source.zip` です。相手アプリとの実機併用は未確認です。[操作手順](AUDIO_MIX.ja.md) を参照してください。

## v0.2.2-alpha: 停止処理の修正版

2026-11-03 に、停止時の終了処理と音声出力の所有スレッドを修正し、クラッシュ診断画面を追加しました。生成物は `JRDroid-0.2.2-alpha-debug.apk` / `JRDroid-0.2.2-alpha-standalone.apk` / `JRDroid-0.2.2-alpha-source.zip` です。ホスト回帰試験とAPKビルドは成功し、実機での解消は未確認です。[停止処理の修正](STOP_FIX.ja.md) を参照してください。

## v0.2.1-alpha: 省電力処理版

2026-11-02 に音声出力の待機・休止、IQバッファー再利用、画面非表示中の診断削減を追加しました。生成物は `JRDroid-0.2.1-alpha-debug.apk` / `JRDroid-0.2.1-alpha-standalone.apk` / `JRDroid-0.2.1-alpha-source.zip` です。実機での電池消費改善率は未測定です。[省電力処理と比較試験](POWER.ja.md) を参照してください。

## v0.2.0-alpha: D チャンネル選局版

2026-11-02 に、停止中のDチャンネル選択・MHz直接入力・6.25 kHzステップ・選択保存を追加しました。通常版と別インストール版を引き続きビルドでき、生成物は `JRDroid-0.2.0-alpha-debug.apk` / `JRDroid-0.2.0-alpha-standalone.apk` / `JRDroid-0.2.0-alpha-source.zip` です。周波数選択の実機試験は未実施です。詳細は [選局手順](D_CHANNELS.ja.md) と [検証記録](VALIDATION.ja.md) を参照してください。

## v0.1.0-alpha: 音声受信確認版の保存

このソース一式は、このチャットで作成・配布し、2026-10-28 にユーザーが Galaxy S26 で実際の放送音声と画面消灯中の継続再生を確認したプロジェクトの保存版です。2026-10-31 に公開用として整理しました。受信・復号・再生のコードは、確認後に変更していません。アプリ内のバージョン表記 `0.1.0-alpha` も保持しています。

通常版 `jp.jrdroid` と、既存版への上書きを避ける別インストール版 `jp.jrdroid.standalone` の両方を同じソースからビルドできます。ユーザーが実機で使用したパッケージ種別は記録されていませんが、両版の受信処理は共通です。

## ZIP に含むもの

- Android Studio で開くプロジェクト設定と Gradle wrapper。
- 日本語 UI、RTL-TCP クライアント、受信サービス、AudioTrack 再生、JNI のソース。
- Rust の DSP、フレーム同期、誤り訂正、音声合成と組み込みモデル8ファイル。
- Rust のツールチェーン指定と `Cargo.lock`。
- ビルド・検証・クラウド導入スクリプト、既存テスト。
- MIT ライセンス、参照コードの著作権表示、依存ライブラリのライセンス通知。
- README、調査結果、インストール手順、実機の検証記録。
- 公開用 ZIP 内の `SOURCE_SHA256SUMS.txt`。各ソースファイルの SHA-256 を記録します（この一覧自身は対象外）。

APK、ビルド済みのネイティブライブラリ、SDK/NDK/JDK、依存キャッシュ、Git 管理情報、署名鍵、端末設定は含めません。Rust の音声モデル `.bin` と Gradle wrapper `.jar` はビルドに必要なため含めています。参照先の JR 受信コードは同梱済みで、別リポジトリの clone や submodule の取得は不要です。

RTL-SDR driver アプリは同梱していません。端末への初回導入は [インストール手順](INSTALL.ja.md) に従ってください。

## GitHub への登録

1. ZIP を展開します。中の `JRDroid` フォルダー直下に `README.md`、`settings.gradle`、`app`、`native` があることを確認します。
2. GitHub Desktop などで `lxp-lab1031/JRDroid` を clone します。リポジトリに既存ファイルがある場合は、その内容と履歴を確認してから取り込んでください。
3. 展開した `JRDroid` フォルダーの**中身**を clone 先のルートにコピーします。隠しファイル `.gitignore` も含めます。clone 先の `.git` はそのまま保持します。
4. 追加・変更されるファイルを確認し、音声受信確認版として commit して push します。リポジトリ直下に `JRDroid/JRDroid` のような余分な階層を作らないでください。
5. GitHub 上で README と `app` / `native` が表示されることを確認します。

この ZIP の作成だけでは GitHub への push や Release の公開は行われません。ZIP 自体を保存するだけでなく、上記のように展開したソースを登録すると Android Studio で clone して開けます。

## 再ビルド

README の JDK・Android SDK/NDK・Rust の準備後、展開先で次を実行します。

```bash
./gradlew assembleDebug
# 既存の JRDroid と別アプリとして入れる場合
./gradlew assembleStandalone
```

通常版の APK: `app/build/outputs/apk/debug/app-debug.apk`

別インストール版の APK: `app/build/outputs/apk/standalone/app-standalone.apk`

`gradlew` の実行権限が展開ツールで失われた場合は `chmod +x gradlew`、または `bash gradlew assembleDebug` を使ってください。

2026-10-31 に公開用 ZIP を別ディレクトリへ展開し、既存の開発ツールと依存キャッシュを使って通常版・別インストール版の両方の APK を再ビルドできることを確認しました。

署名鍵はソース ZIP に含めていません。別の環境で生成したデバッグ APK は既存 APK と署名が異なることがあるため、そのまま上書き更新できるとは限りません。公開用 APK を継続配布する場合は、署名鍵を GitHub に入れずに管理する必要があります。

## 確認済みの範囲

ユーザーの実機試験で受信・音声復号・スピーカー再生・画面消灯中の継続再生が確認されています。データ電文表示は未実装です。長時間の安定性、通信オフ、音声品質の定量比較などは別の確認項目として残しています。詳細は [検証記録](VALIDATION.ja.md) を参照してください。
