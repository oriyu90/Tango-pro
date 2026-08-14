# Tango pro v1.2.1

> [!WARNING]
> Tango proは試験的なアプリです。Android版はデバッグ鍵、macOS版はad-hoc署名・未Notarizeで配布します。更新前に学習記録ZIPまたはJSONバックアップを作成してください。

## 単語帳ごとの出題設定

- 出題方向、4択／タイピング、出題対象、問題数を単語帳ごとに保存
- Androidはタグ、範囲、絞り込みの有効状態も単語帳ごとに保存
- 単語帳を切り替えたときとアプリ再起動後に、その単語帳で前回使った設定を復元
- Android v1.2.0以前の共通設定は、各単語帳の初回選択時に初期値として引き継ぎ
- タイピングは従来どおり「日本語 → 対象言語」に固定

## フランス語・ポルトガル語TTS

- CSV追加時の対象言語にフランス語とポルトガル語を追加
- Androidは単語帳編集、macOSは単語帳メニューから追加後も言語を変更可能
- Androidは `fr-FR` / `pt-BR` localeを使用
- macOSは `fr-FR` / `fr-CA`、`pt-BR` / `pt-PT` の順にインストール済みvoiceを探索
- 順方向の問題表示、自動読み上げ、手動読み上げ、逆方向の回答確定後読み上げに対応
- 学習記録ZIPのformat version 1を維持したまま、言語metadata `fr` / `pt` をAndroid / macOS双方で受け入れ
- `fr` / `pt` を含むZIPを別端末へ移す場合は、受け側もv1.2.1以上が必要

## 互換性

- Android applicationId `com.aistudio.vocabstudier.xwqnzy` とRoom schema version 4を維持
- macOS保存形式version 1を維持し、単語帳別設定がないv1.2.0 JSONを既定値で読込
- CSV列、既存の英語・中国語・読み上げなし、学習記録ZIP、Android JSONバックアップを継続利用可能

## 配布物と注意事項

- `Tango-pro-1.2.1-android-debug.apk`: Androidデバッグ鍵で署名した試験用APK
- `Tango-pro-1.2.1-universal.dmg`: Apple Silicon / Intel対応、アプリはad-hoc署名、未Notarize
- デバッグ鍵が異なるAndroid旧版には上書きできません。バックアップ後に旧版をアンインストールして導入してください
- macOSではGatekeeperの警告が表示される場合があります
- 端末に対象言語のTTSデータ／音声がない場合は、OSの音声設定から追加してください

## 検証

- Android unit / Robolectric test 26件成功、Lint 0 errors、debug APK / release APK build成功
- 単語帳別設定の移行・分離・再読込テスト
- `fr` / `pt` locale mappingと学習記録ZIP round-trip
- Android生成ZIP → macOS、macOS生成ZIP → Androidの双方向codecテスト
- macOS旧JSON互換、Core self-test、universal build、ad-hoc署名、DMG検証

正式配布へ移行する場合は、Android正式署名鍵でのupgrade install、実端末TTS、macOS Developer ID署名・Notarizationを別途確認してください。
