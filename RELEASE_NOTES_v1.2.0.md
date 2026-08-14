# Tango pro v1.2.0

> [!WARNING]
> Tango proは試験的なアプリです。Android版はデバッグ鍵、macOS版はad-hoc署名で配布します。インストール前に下記の「配布物と注意事項」を確認してください。

## 新機能: 学習記録ZIP

Android版とmacOS版の設定画面から、すべての単語帳CSVと学習記録を1つのZIPへ書き出し、別端末へ読み込めるようになりました。

- ZIPには単語帳ごとの `words.csv` と `progress.json` を収録
- 同じCSVが端末にある場合は単語帳を重複追加せず、学習記録を統合
- 同じCSVがない場合、または1行・1列でも内容が違う場合は新しい単語帳として追加
- 未学習と学習済みが競合した場合は学習済みを優先
- 双方に履歴がある場合は最新の正誤状態と大きい学習回数を保持
- Androidで書き出したZIPをmacOSへ、macOSで書き出したZIPをAndroidへ読み込み可能

## データ安全性

- CSVをSHA-256で検証し、manifest・学習記録との対応を確認
- 危険なZIP path、重複entry、未知file、非UTF-8、行番号不整合を拒否
- ZIP bomb対策としてentry数、1 file、総展開量を制限
- macOSは展開前にcentral directoryとsymbolic linkを検査
- Androidは単一Room transactionで適用し、失敗時に部分データを残さない
- macOSは検証・統合・atomic保存が完了するまで成功扱いにせず、保存失敗時はメモリ状態も復元

## 学習フローの改善

- 学習完了画面に「そのまま続ける」を追加し、直前の単語帳・方向・回答形式・出題条件・問題数・タグ／範囲を引き継いで再開
- 「おすすめ」を初期選択に変更し、要復習→うろ覚え→習得済みの順で優先
- おすすめでは習得済みの単語も候補に残るため、全語習得後も3周目以降を継続可能
- 出題条件を「おすすめ」「未学習のみ」「うろ覚え＆ミスのみ」「うろ覚えをランダム」「学習済をランダム」に整理
- 旧版の削除済み出題条件は起動時に「おすすめ」へ安全に移行
- スピーカーアイコンに加え、問題語全体のクリック／タップで手動読み上げ

## 互換性

- Android: applicationIdとRoom schema version 4を維持
- 学習記録ZIP: format `tango-pro-study-archive`、version 1
- 従来のCSV import/exportとAndroid JSONバックアップは継続利用可能
- v1.1.2候補までのAndroid起動停止、成績更新、組み込み単語帳、macOS保存修正をすべて含む

## 配布物と注意事項

- `Tango-pro-1.2.0-android-debug.apk`: Androidデバッグ鍵で署名した試験用APK
- `Tango-pro-1.2.0-universal.dmg`: Apple Silicon / Intel対応、アプリはad-hoc署名、未Notarize
- Android APKは、署名鍵が異なる旧版へ上書きインストールできません。必要な場合は学習記録ZIPまたはJSONを書き出し、旧版をアンインストールしてから導入してください
- macOSではGatekeeperの警告が表示される場合があります
- 重要な学習データは更新前にバックアップしてください

## 検証結果

- Android unit test: 22件成功
- Android Lint: 0 errors / 26 warnings（更新通知21件、意図的なicon重複5件）
- Android API 36.1 emulator: 8冊・9,666語のZIP export/import成功
- 同一ZIP import後: 8冊のままで全単語帳が統合扱い、再起動成功、対象appのFatal/ANRなし
- Android→macOS: 8冊・9,666語のimportと永続化後の再読込成功
- macOS→Android: 2冊fixtureのimport成功
- macOS core self-test、universal build、ad-hoc署名整合性検証、app起動成功

正式なストア配布へ移行する場合は、Android正式署名での更新確認、Android実端末確認、macOS Developer ID署名・Notarizationが別途必要です。
