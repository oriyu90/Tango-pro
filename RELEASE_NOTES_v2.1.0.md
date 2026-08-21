# Tango pro v2.1.0

> [!WARNING]
> macOS版はad-hoc署名・未Notarizeです。更新前に学習記録ZIPを保存してください。

> [!IMPORTANT]
> **Android署名鍵に関する重要なお知らせ**: 公開中のv2.0.0 APK（GitHub Release）は、本来の正規リリース鍵ではなく誤ってデバッグ鍵で署名された状態で公開されていました。v2.1.0はv1.1.1までと同じ正規のアップロード鍵で署名し、正しい署名系列に復帰しています。
>
> - v1.1.1以前から更新する場合、または新規インストールの場合は、通常どおり上書き更新・新規インストールできます。
> - **GitHub Releaseのv2.0.0 APKを実際にインストールしている場合のみ**、署名証明書の不一致によりv2.1.0への上書き更新ができません。設定画面から学習記録ZIPを書き出してから、v2.0.0をアンインストールし、v2.1.0を新規インストールしてください。

## 学習画面の配置モード

- 設定に「問題画面の配置」を追加（Android / macOS共通）
- 「上寄せ」: v2.0.0からの既定挙動。内容を自然な高さで上から詰めて表示し、内容が短い場合は下部に余白が生まれる
- 「均等配置」: v1.2.1以前の挙動。問題カードが残りの縦空間いっぱいに広がり、回答欄が画面下部に固定される
- 既定値はどちらのプラットフォームも現行の見た目を維持（Android: 上寄せ、macOS: 均等配置）。既存ユーザーの見え方は変わらない

## 範囲指定時の進捗バー

- Androidで範囲・タグ指定（出題対象・成績条件の絞り込み）を有効にしている間、単語帳本体の進捗カードの下に、指定範囲だけの学習済・うろ覚え・要復習の内訳を表示
- シンプルモードでも常に表示する
- 範囲・タグ指定はAndroid限定機能のため、この追加バーもAndroidのみ

## 互換性

- Android applicationId `com.aistudio.vocabstudier.xwqnzy`、Room schema version 4を維持
- macOS保存形式version 1、学習記録ZIP format version 1を維持
- 配置モードはアプリ共通設定として後方互換のある任意値で保存し、未知の値は既定へ安全に移行

## 配布物

- `Tango-pro-2.1.0-android.apk`: 正規アップロード鍵（v1.1.1までと同一証明書）で署名したrelease build
- `Tango-pro-2.1.0-universal.dmg`: Apple Silicon / Intel対応、ad-hoc署名、未Notarize
- `CHECKSUMS_v2.1.0.txt`: SHA-256

## 確認済み項目

- Android unit test 全件（配置モードの正規化を追加）、lint 0 errors
- Android / macOS の相互fixture互換性（`run_cross_platform_tests.sh`）
- macOS Core self-test
- Android release APKの署名証明書がv1.1.1と同一であることを`apksigner verify`で確認
- Android / macOS build、成果物整合性（SHA-256）

正式配布へ移行する場合は、実端末でのインストール・TTS動作確認、macOS Developer ID署名・Notarizationを別途行ってください。
