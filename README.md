# JRDroid

Android端末とRTL-SDRを使い、JRデジタル列車無線の受信・音声再生を目指す個人開発プロジェクトです。
**現在は開発の土台のみです。無線受信・復調・復号・音声再生はできません。**

## 前提

| 項目 | 現在わかっている条件 |
| --- | --- |
| 対象 | JRデジタル列車無線（利用者申告：旅客一斉） |
| 周波数 | 352.6250 MHz（352,625,000 Hz） |
| 端末 | Android S26（利用者呼称。型番・OS・USBホスト動作は実機確認待ち） |
| SDR | RTL-SDR / RTL2832U（チューナー型式・VID/PIDは確認待ち） |
| 開発PC | Windows 11。ブラウザーはChrome |

周波数は利用者指定の初期値で、通信方式を確定する情報ではありません。
サンプルレート、変調、フレーム形式、音声コーデック、必要帯域、対応ライブラリは未確定です。
Windowsでの受信実績はありますが、Androidへの移植可否は未検証です。

## この版でできること

- 起動画面に対象周波数と未実装状態を表示。
- 接続中のUSB機器のVID/PIDを表示し、再確認。
- USB機器の列挙のみ。アクセス許可の要求・デバイスのオープンは行いません。

## 最小構成

```text
app/                       Androidアプリ（Java、標準UI、外部依存なし）
  src/main/java/jp/lxplab/jrdroid/
    MainActivity.java      設定表示・USB機器一覧
    receiver/              ハードウェア非依存の受信設定
  src/main/res/values/     表示文字列
  src/main/AndroidManifest.xml
docs/IMPLEMENTATION.md     実装順序・未確認事項・受入条件
gradle/README.md           Wrapper生成手順
```

当面は単一appモジュールとし、ドライバーや復号器が決まる前に多くのモジュールを作りません。

## Windows 11で開く・ビルドする

ChromeだけではAndroidアプリをビルドできません。Android Studio、JDK 17、Android SDK Platform 35が必要です。
固定構成はAGP 8.7.3 / Gradle 8.9 / Java 17、minSdk 26、compileSdk・targetSdk 35です。
最新構成を追う目的ではなく、初期開発の基準として固定しています。S26での動作は未確認です。

1. このリポジトリをclone、またはGitHubの **Code → Download ZIP** から取得・展開します。
2. Gradle 8.9を用意し、プロジェクトのルートで以下を実行します（詳細は[gradle/README.md](gradle/README.md)）。
3. Android Studioの **Open** でルートフォルダーを開きます。SDK 35を入れ、Gradle JDKを17に設定します。
4. SDK位置はAndroid Studioが生成する`local.properties`で指定します。このファイルはコミットしません。

```powershell
gradle wrapper --gradle-version 8.9 --distribution-type bin
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Wrapper一式はまだ同梱していません。初回のみインストール済みGradle 8.9を使用してください。
生成後は`gradlew`、`gradlew.bat`、`gradle/wrapper/`一式をコミットします。
初回の同期・ビルドには依存ファイルのダウンロードが必要です。
APKは`app/build/outputs/apk/debug/app-debug.apk`に出力されます。

## 実装方針と確認

[実装方針](docs/IMPLEMENTATION.md)を参照してください。最初の実機確認は、USB未接続時の案内と接続後のVID/PID表示です。
この初期構成を作成した環境にはAndroid SDKとGradleがなく、APKビルド・lint・実機試験は未実施です。

## 参考資料

- [Android USB host](https://developer.android.com/develop/connectivity/usb/host)
- [Android Gradle Plugin 8.7の互換性](https://developer.android.com/build/releases/past-releases/agp-8-7-0-release-notes)
- [Gradle Wrapper](https://docs.gradle.org/8.9/userguide/gradle_wrapper.html)

外部の受信ソフトや復号ライブラリは同梱していません。採用時にAPI・ライセンス・Android対応を確認します。
受信音声や個人の録音は、この公開リポジトリへコミットしないでください。
