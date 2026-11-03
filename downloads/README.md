# v0.2.2-alpha — 停止処理修正版

通常版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/stop-fix-20261103/downloads/JRDroid-0.2.2-alpha-debug.apk)

別インストール版: [APK](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/stop-fix-20261103/downloads/JRDroid-0.2.2-alpha-standalone.apk)

ソース: [ZIP](https://github.com/lxp-lab1031/JRDroid/raw/refs/heads/stop-fix-20261103/downloads/JRDroid-0.2.2-alpha-source.zip)

同じ種類の旧版へ更新してください。停止ボタンで音声出力と通信を直接停止する構造を改め、音声出力の所有スレッドと解放完了待ちを修正しました。実機での解消はまだ確認していません。

停止→選局→受信開始を確認してください。まだ落ちる場合は、再起動後に画面下の「停止・クラッシュの診断情報」を開き、「コピー」した内容を使って原因を確認できます。Androidが過去の終了履歴を残していれば更新前の異常も読める場合があります。自動送信はしません。

[変更内容と確認手順](../docs/STOP_FIX.ja.md)
