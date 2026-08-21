# Tango pro v2.1.0

> [!WARNING]
> Android版は既公開版と互換の署名証明書、macOS版はad-hoc署名・未Notarizeです。更新前に学習記録ZIPを保存してください。

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

## 確認済み項目

- Android unit test（配置モードの正規化を追加）
- macOS Core self-test
- Android / macOS build、署名、成果物整合性

正式配布へ移行する場合は、Android正式署名鍵での更新インストール、実端末TTS、macOS Developer ID署名・Notarizationを別途確認してください。
