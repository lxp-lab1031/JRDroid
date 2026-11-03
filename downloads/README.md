# v0.3.0-alpha — 142 MHz帯の試験受信・IQ保存

**京急・京成・北総の音声復号は未確認です。** Windowsの設定画像にある16周波数の選局と、調査用の5秒相当IQ保存・共有を追加した試験版です。既存のJR復号処理との互換性を試す段階で、私鉄専用の方式を実装済みとはしていません。

通常版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/private-142mhz-experimental/downloads/JRDroid-0.3.0-alpha-debug.apk)

別インストール版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/private-142mhz-experimental/downloads/JRDroid-0.3.0-alpha-standalone.apk)

ソース一式: [ZIP](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/private-142mhz-experimental/downloads/JRDroid-0.3.0-alpha-source.zip)

チェックサム: [SHA-256](SHA256SUMS-0.3.0-alpha.txt)

同じ種類の旧版へ更新してください。v0.2.3-alphaと署名・アプリIDを維持し、versionCodeを6に増やしています。JRの選局・停止修正・省電力処理・他アプリとの音声ミックスを継承しています。

停止 → 「受信対象・周波数を選択」 → 「142 MHz帯 · 私鉄設定の試験受信」 → 周波数を選択 → 受信開始。

音声が出ないときは、放送中に「調査用 IQ を5秒取得」を押し、保存完了後に「保存した IQ を共有」を使って取得データを保存・共有できます。アプリが自動で送信することはありません。

[142 MHz帯の設定一覧と試験手順](../docs/PRIVATE_142_TEST.ja.md) / [検証記録](../docs/VALIDATION.ja.md)

旧版のAPK・ソース・チェックサムもこのフォルダーに保持しています。ユーザーから実機報告のあったJR音声ミックス版は `JRDroid-0.2.3-alpha-*.apk` です。
