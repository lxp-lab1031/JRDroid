# 参照ソース調査と実装方針

調査日: 2026-10-24。コードの存在・API・ビルド可能性と、実電波の受信成功は区別します。

## JR 受信実装

参照: https://github.com/soltia48/jr-conventional-line-radio-receiver

固定したコミット: `a20abbc17ae84d0393753099529bfc9c26c85e8b`（README の版表記 v1.7.0）。`native/vendor/UPSTREAM_COMMIT` にも記録しています。

プロジェクト全体は MIT、Copyright (c) 2026 KIRISHIKI Yudai。`jr-voice/src/celp` が流用する mcelp-codec 部分も MIT で、同ディレクトリの LICENSE を保持しました。今回取り込んだ各 crate のソースには変更を加えていません。Android 向けの接続コードは `native/bridge` に分離しています。上流の Tauri UI・SoapySDR・デスクトップ音声出力は組み込みません。

| ソース | 内容と Android での利用 |
|---|---|
| `jr-conventional-dsp` 1.2.0 | 純 Rust。IQ の帯域処理・受信フィルター・タイミング回復・搬送波補正・π/4-DQPSK の判定。`receiver::Dsp` が使える。二つの等化・検出経路にも対応 |
| `receiver-core/src/frame.rs` | 384 bit / 40 ms の無線フレーム。3セグメントのマーカー `3d22/e867/e867`、複数フレームによる同期取得、シンボルずれ追従、二経路のフレーム結合 |
| `receiver-core/src/voice_frame.rs` | デスクランブル、畳み込み符号/Viterbi、ソフト判定、CRC・Reed–Solomon 検査と修復を経て受理した158 bitの音声ペイロードを生成 |
| `jr-voice` 1.3.0 | 純 Rust の Modified RL-CELP 音声合成。1入力40 ms → 320 samples / 8 kHz / i16、7フレーム＝280 ms の先読み。固定モデルとコードブックを `include_bytes!` 等で組み込み、外部推論不要 |
| `receiver-core/src/pipeline.rs` | 上記をつなぎ、同期・フレーム・音声カウンター、PCM と診断値を返す。`default-features = false` で SoapySDR 依存を外せる |

今回の JNI は上流の個別 C ABI を直接寄せ集めず、Rust の `Pipeline` を1チャンネル分所有します。入力は unsigned interleaved IQ、出力は PCM16 配列。所有権は1本の DSP スレッドに限定し、Java の UI スレッドからハンドルへ直接触りません。NDK r28c の `aarch64-linux-android28-clang` をリンカーにして `cdylib` を作れました。Android OS でのロード・実行確認は別途必要です。

### 音声品質について

上流の音声ライブラリは規範的なビット割当全体が確定した実装ではなく、一部のパラメーター解釈に仮説と学習済み推定モデルを使うことを README に明記しています。モデルは前後文脈、ピッチ、スペクトル、ゲイン等から合成します。280 ms はアルゴリズムの先読みであり、USB・IQ・演算・AudioTrack の遅延が加わります。

合成したテストペイロードから PCM が得られることは、実際の JR 放送が明瞭に聞こえることの証明にはなりません。上流にも実際の無線・音声の試験収録は付属しません。実機で同期数と音声数を確認し、Windows の受信と同じ条件で聞き取りやすさを比較する必要があります。

## SDR ドライバーと方式比較

参照: https://github.com/martinmarinov/rtl_tcp_andro-

調査コミット: `bf421f0d6983d665158fbc4729831ec88c244fac`。README と Android 側の Intent 処理、`SdrTcpArguments`、C の `tcp_commands.h`・`sdrtcp.c` を調べました。

| 方式 | 利点 | 課題・今回の判断 |
|---|---|---|
| 既存 SDR アプリのアドオン | 既存受信画面を利用できる可能性 | 候補アプリに共通して使える Android 向け JR 復号アドオン API を確認できず。SDR# の Windows .NET プラグインを Android にそのまま移植する経路はないため、初版に採用せず |
| 専用アプリ＋既存 Android ドライバー | 公開された `iqsrc://` Intent と標準 RTL-TCP、USB 許可・RTL2832U 制御は既存ドライバーが担当。端末内で完結 | 初回に別アプリの導入が必要。アプリ間連携・OS の省電力制御は Galaxy で要試験。**初版に採用** |
| 専用アプリに USB ドライバー内蔵 | 1 APK での運用が可能 | libusb の FD 引き渡し、JNI、USB の異常系、GPL 対応、ドライバー保守が増える。今回未実装。既存連携が実機で機能しなければ次に検討 |

利用する契約は次のとおりです。

- `Intent.ACTION_VIEW`、`iqsrc://-a 127.0.0.1 -p 12345 -s 240000 -f 352625000 -g 40 -P 0`。対象パッケージを `marto.rtl_tcp_andro` に限定。
- `RESULT_OK` と `supportedTcpCommands` を受けて localhost へ接続。エラー時は `detailed_exception_message` を表示。
- `RTL0` + tuner ID + gain count の12 byteヘッダーを検査。R820T=5 / R828D=6 のみ受理し、想定外の IQ 形式を持つ別ドライバーを拒否。
- コマンドは1 byte opcode＋big-endian 32 bit値。240 kS/s、352625000 Hz、0 ppm、digital AGC off、manual gain、402（0.1 dB）を送信。
- Intent のゲイン引数は整数なので40 dBで起動後、TCP で40.2 dBを要求。設定直後の先頭0.25秒分の IQ を捨ててから復調。
- 停止時は Android 拡張コマンド `0x7e` でドライバー終了を要求し、ソケットを閉じる。
- ドライバーの USB 許可・設定受理・再接続・終了は実機未確認。旧版ではコマンド能力配列が返らないため、標準 RTL-SDR コマンドを仮定する。

外部ドライバーは GPL 系のソフトウェアです。今回のアプリは公開プロトコルで連携し、そのバイナリ・ソースは配布物に含めません。将来内蔵する場合には、組み込む全ソースと対応するライセンス条件を改めて扱います。

## Windows の比較対象

参照: https://unset-histfile.booth.pm/items/8843954

このクラウド環境からのアクセスは HTTP 403 で、配布物・ソース・ライセンス・API を確認できませんでした。コードの流用・バイナリの組み込みはしていません。Windows で受信できているという情報と 2.4 MSPS・40.2 dB・0 ppm・NFM・8 kHz は、ユーザー提供の比較条件として扱っています。

## 残る主要課題

1. Galaxy S26 の USB 許可と既存ドライバーの起動・終了、ドングルへの OTG 給電を検証する。
2. 同じアンテナ・放送・受信条件で実際のフレーム同期と音声受理を確認し、ppm・ゲイン調整が必要か確認する。初版の設定は固定。
3. Android の AudioTrack、スピーカー経路、音声フォーカス、画面消灯・バックグラウンド・通知停止を検証する。
4. 連続受信で処理速度、欠落、発熱、電池、遅延、実際の音声品質を測る。RTL-TCP ではドライバー内のサンプル欠落を検知できない点が残る。
5. 実機での結果を受けて音声の改善と必要な設定 UI を追加し、その後にデータ電文表示へ進む。
