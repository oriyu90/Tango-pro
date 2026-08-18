# v2.0.0 静的解析・テストレポート

対象: 標準CSV刷新、長い単語帳名、シンプルモード、レスポンシブ表示、問題文字サイズ、可変高4択、周回表示、v1.2.x互換

## 1. 標準CSVと移行

- Android / macOSが同じASCII物理名17ファイルを参照
- 内訳は中国語基本単語1冊、Lv.1〜Lv.8英単語8冊、英熟語8冊
- 全17ファイルをRFC 4180パーサーで読込み、有効な2列以上の行が存在することを自動テスト
- 旧標準英語資産 `basic_english_words.csv` / `common_test_words.csv` 等がAPK資産に存在しないことを確認
- Androidは新安定IDごとの投入キー、macOSは `bundledCatalogVersion = 2` で移行
- macOS Core self-testで17冊初期投入、全削除後の再起動で再生成しないことを確認
- 既存単語帳の削除処理は追加していないため、アップデート時のユーザーデータを保持

結果: 正常。

## 2. 周回と表示ロジック

- 周回は空の単語帳を1、その他を `min(studyCount) + 1` として算出
- 一部だけ学習済みの場合は1周目に留まり、全語1回で2周目となる境界を単体テスト
- 4周目まで専用visual tier、5周目以降はtier 0となることを確認
- Android DAOはグループ単位の `MIN(studyCount)` をFlowで購読し、左パネルを更新
- macOSは `VocabGroup.currentRound` で同じ式を使用

結果: 正常。

## 3. シンプルモード、文字サイズ、長い名前

- シンプルモード設定をAndroid SharedPreferences / macOS optional JSON fieldへ保存
- 問題文字倍率を `0.8 / 1.0 / 1.2 / 1.4` へ正規化して保存
- Android 360×640dp描画で、長い選択中名の末尾省略、50%吹き出し、閉じた出題設定、開始ボタンを確認
- 問題・4択は同じ倍率を使用し、4択は固定高を廃止して最大3行＋可変高とした
- macOS旧JSONに新設定fieldがない場合は `false / 1.0` を使用し、保存後の再読込をCore self-testで確認

結果: 正常。

## 4. アスペクト比・UI描画

- Android 360×640dp: シンプルダッシュボードと縦積み学習画面
- Android 960×480dp: 問題／回答2ペイン、特大問題文、長い4択4件
- 縦長画面の最終選択肢へ `performScrollTo` でき、表示領域へ到達することを確認
- タブレット向けにダッシュボード本文最大幅1,000dp、ドロワー最大幅360dpを設定
- macOSアプリを最小680×500対応でコンパイルし、4択を利用幅に応じて1列／2列へ切替

結果: 正常。

## 5. 自動テストと静的解析

- Android unit / Robolectric / Compose UI test: 31件、失敗0
- `./gradlew testDebugUnitTest lintDebug`: 成功
- Android Lint: errors 0、warnings 26
- warning内訳: 依存関係更新通知21件、既存launcher icon重複5件
- Roborazzi: 狭幅ダッシュボードと横長特大文字学習画面を記録して目視確認
- macOS Core self-test: PASS
- macOS app: SwiftUIを含むarm64 / x86_64コンパイル成功
- `git diff --check`: 成功

結果: 今回の変更に起因する静的解析エラーなし。依存関係の一括更新は機能変更と分離するため実施しない。

## 6. 最終成果物検証

- Android release APK: versionCode 6 / versionName 2.0.0
- v1.2.0公開APKと同一の署名証明書を使用し、更新互換性を維持
- APK: v2署名検証、ZIP整合性、同梱CSV数を確認
- macOS app: CFBundleVersion 6 / CFBundleShortVersionString 2.0.0
- macOS executable: x86_64 / arm64 universal binary
- macOS app: ad-hoc署名を `codesign --verify --deep --strict` で確認
- DMG: `hdiutil verify` と内包appの版・署名・architectureを確認

結果: 最終ビルド後に確認済み。SHA-256は `CHECKSUMS_v2.0.0.txt` に記録する。

## 7. 残存リスク

- Android正式署名鍵が提供されていないため、正式公開鍵でのupgrade installは未検証
- Android実機のTTS音声データ有無とメーカー固有UIは端末依存
- macOS成果物はad-hoc署名・未NotarizeのためGatekeeper警告が表示される場合がある
- 自動UI検証は代表的な360×640dp / 960×480dpであり、全端末の実機検証を代替しない
- 既存ユーザーの旧標準単語帳はデータ保護のため自動削除しない

## 総合評価

自動テスト、静的解析、旧保存形式移行、代表アスペクト比描画、両OS build、成果物検証の範囲でv2.0.0候補は合格。
