# v1.2.1 静的解析・テストレポート

対象: 単語帳別出題設定、旧共通設定移行、英語／中国語／フランス語／ポルトガル語TTS、言語変更、学習記録ZIP互換、既存機能回帰

## 1. 単語帳別設定

- Androidは `studySettings.<groupId>.*` の名前空間で方向、回答形式、条件、タグ、範囲、問題数を保存
- 設定が未作成の単語帳はv1.2.0以前の共通値を正規化して一度だけ移行
- 単語帳Aの変更が単語帳Bを上書きしないことをRobolectricで確認
- タイピング時の逆方向固定、不正な問題数・範囲・filterの正規化を単体テスト
- 削除した単語帳の設定キーも削除
- macOSはoptionalな `VocabGroup.studySettings` として保存し、2冊の異なる設定を再起動後に復元
- `studySettings` がないv1.2.0形式JSONを既定値で読み込むself-testに成功

結果: 正常。

## 2. TTSと言語metadata

- 対応コードを `en` / `zh` / `fr` / `pt` / `none` に一元化
- Android locale mapping: `en-US` / `zh-CN` / `fr-FR` / `pt-BR`
- macOSは音声属性のlocaleを `_` / `-` 正規化し、対象言語のインストール済みvoiceのみ選択
- 自動再生は順方向の対象語、逆方向は回答確定後の対象語、手動再生は常にCSV第1列を読む
- CSV追加UIと追加後の編集UIを両OSで拡張
- 学習記録ZIPで `fr` / `pt` を書出・読込できるround-tripを両codecで確認

結果: 正常。初回macOS app buildでVoiceAttributeKey名とlocale区切り差を検出し、正式APIキーと正規化処理へ修正後に再検証した。

## 3. クロスプラットフォーム互換

- Android生成fixtureをmacOSで読込・永続化・再読込
- macOS生成fixtureをAndroidで読込
- format `tango-pro-study-archive` version 1、CSV SHA-256、progress対応を維持
- 英語とフランス語を含むfixture、およびフランス語・ポルトガル語metadata round-tripを確認

結果: 正常。

## 4. 静的解析とビルド

- `git diff --check`: 成功
- Android unit / Robolectric test: 26件、失敗0
- `./gradlew clean test lint assembleRelease stageDebugApk`: 成功
- Android Lint: errors 0、warnings 26
- warning内訳: 依存関係更新通知21件、意図的なlauncher icon重複5件
- macOS Core self-test: PASS
- universal app: x86_64 / arm64
- `codesign --verify --deep --strict`: 成功
- `plutil -lint`: 成功
- debug APK: versionCode 5 / versionName 1.2.1、v2署名検証成功、ZIP整合性正常
- DMG: `hdiutil verify`、read-only mount、内包appのversion・署名・architecture検証成功

結果: 機能追加に起因するLint error / warningなし。依存関係の一括更新は回帰範囲外のため実施しない。

## 5. 残存リスク

- Android正式署名鍵がないため、正式公開鍵でのupgrade installは未検証
- Android実メーカー端末でのフランス語・ポルトガル語TTSデータ有無は端末依存
- macOS成果物はad-hoc署名・未Notarize
- macOSで対象voiceが未導入の場合は誤言語で代替せず無音となり、OS設定から音声追加が必要
- 出題設定はローカルUI履歴であり、学習記録ZIPには含めない

## 総合評価

自動テスト、静的解析、旧保存形式、双方向archive、両OS build、最終成果物検証の範囲でv1.2.1候補は合格。
