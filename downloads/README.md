# v0.3.2-alpha — IQをダウンロードへZIP保存

ChatGPTからCodexへの切り替えで添付が消える場合に、IQをスマホへ保存してから添付できる版です。新しい「IQ をダウンロードに保存（ZIP）」で、Galaxyの「ダウンロード/JRDroid」へ保存します。

通常版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/iq-download-export/downloads/JRDroid-0.3.2-alpha-debug.apk)

別インストール版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/iq-download-export/downloads/JRDroid-0.3.2-alpha-standalone.apk)

ソース一式: [ZIP](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/iq-download-export/downloads/JRDroid-0.3.2-alpha-source.zip)

チェックサム: [SHA-256](SHA256SUMS-0.3.2-alpha.txt)

現在と同じ種類の旧版へ更新してください。署名・アプリIDは維持し、versionCodeは8です。受信・音声復号のバイナリはv0.3.1-alphaと同一です。146 MHz帯基地局16波・JR22波・音声ミックス・停止修正・省電力処理・IQ取得と共有を継続しています。

1. 「調査用 IQ を5秒取得」で取得完了を待ちます。
2. 「IQ をダウンロードに保存（ZIP）」を押し、「保存しました」の表示を待ちます。
3. ChatGPTで**先にCodexへ切り替えます**。
4. 添付のファイル選択から「ダウンロード」→「JRDroid」のZIPを選びます。

保存したZIPは元の一時IQと独立して残り、添付前に解凍する必要はありません。既存の「保存した IQ を共有」も利用できます。Android9では保存先選択画面からダウンロード等を選びます。

[保存・添付の詳しい手順](../docs/IQ_EXPORT.ja.md) / [基地局の選局](../docs/PRIVATE_BASE_STATION_TEST.ja.md) / [検証記録](../docs/VALIDATION.ja.md)

ビルドとホスト試験は成功。Galaxyでの保存・Codexへの添付操作は実機確認が必要です。京急の実電波による音声復号も未確認です。過去の配布物は各バージョン名で保持しています。
