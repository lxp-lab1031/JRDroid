# v0.3.1-alpha — 146 MHz帯の基地局設定

142 MHz帯の移動局16波を削除し、提供写真の146 MHz帯基地局16波へ置き換えました。K1/K2/K3/北総について、音声・データ・基地局を一覧に表示します。**京急等の音声復号は引き続き未確認の試験版です。**

通常版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/base-station-146mhz/downloads/JRDroid-0.3.1-alpha-debug.apk)

別インストール版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/base-station-146mhz/downloads/JRDroid-0.3.1-alpha-standalone.apk)

ソース一式: [ZIP](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/base-station-146mhz/downloads/JRDroid-0.3.1-alpha-source.zip)

チェックサム: [SHA-256](SHA256SUMS-0.3.1-alpha.txt)

現在と同じ種類の旧版へ更新してください。署名・アプリIDを維持し、versionCodeは7です。旧移動局設定を保存していた場合、同じチャンネルの基地局側へ自動的に引き継ぎます。JR設定・音声ミックス・停止修正・省電力処理・IQ保存は継続しています。

停止 → 「受信対象・周波数を選択」 → 「京急・京成・北総 基地局 · 146 MHz帯（試験）」 → 音声波を選択 → 受信開始。

例: **K1-1 音声 基地局は146.48125 MHz**です。K1データの146.49375 MHzとは別の波です。写真の146.470000 MHzは広帯域受信の中心で、本アプリは選択した1波へ合わせます。

同期しない・音声が出ない場合は、発話中に「調査用 IQ を5秒取得」を押し、完了後「保存した IQ を共有」から取得データを送れます。自動送信はしません。

[基地局16波の一覧と手順](../docs/PRIVATE_BASE_STATION_TEST.ja.md) / [検証記録](../docs/VALIDATION.ja.md)

過去の配布物は版を明示して保持しています。v0.3.0-alphaの142 MHz帯は移動局側の旧設定です。
