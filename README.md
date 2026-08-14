# Tango pro v1.2.1

CSVで作った単語帳を取り込み、4択またはタイピングで学習するAndroid / macOS向けアプリです。英語・中国語・フランス語・ポルトガル語の読み上げ、単語帳別の出題設定履歴、学習履歴、単語帳の連結・並べ替え、CSV共有、セーブデータのバックアップに対応します。

> [!WARNING]
> Tango proは試験的なアプリです。v1.2.1で配布するAndroid APKはデバッグ鍵、macOS DMG内のアプリはad-hoc署名です。重要な学習データは、更新や移行の前に設定画面からバックアップしてください。

## v1.2.1の変更点

- 出題方向、回答形式、出題対象、問題数などの前回設定を単語帳ごとに保存・復元
- Androidではタグ・範囲指定も単語帳ごとに保持し、旧共通設定を初回に安全に引き継ぎ
- CSV追加時の言語選択にフランス語・ポルトガル語を追加
- 追加後も単語帳編集（macOSは単語帳メニュー）から読み上げ言語を変更可能
- Androidは `fr-FR` / `pt-BR`、macOSはインストール済み音声から対応voiceを選択
- 学習記録ZIPで `fr` / `pt` の言語metadataをAndroid / macOS間で相互利用可能

## v1.2.0の主な変更点

- 設定画面から、全単語帳CSVと学習記録を1つのZIPへ書き出し・読み込み可能
- ZIP内に人が直接読める `words.csv` と行ごとの `progress.json` を収録
- CSV内容が完全一致する既存単語帳には学習記録だけを統合
- CSVが存在しない、または1列・1行でも異なる場合は新しい単語帳として追加
- 未学習と学習済みの競合では学習済みを優先し、双方に履歴がある場合は最新結果を優先
- Android / macOS間で同じZIPを相互利用可能
- SHA-256、ZIP path、重複entry、symbolic link、展開サイズ、行数を検証
- AndroidはRoom transaction、macOSは検証完了後のatomic保存で部分適用を防止
- 学習完了後に同じ設定のまま次のセッションを開始できる「そのまま続ける」を追加
- 「おすすめ」は全語習得後も3周目以降を繰り返せるようにし、初期選択へ変更
- 出題条件を整理し、「うろ覚えをランダム」を追加
- 単語の文字部分をタップしてもTTS再生可能

詳細は [リリースノート](RELEASE_NOTES_v1.2.1.md)、[学習記録ZIP仕様](docs/STUDY_ARCHIVE_FORMAT.md)、[静的解析レポート](docs/STATIC_ANALYSIS_REPORT_v1.2.1.md) を参照してください。

## 主な機能

- 組み込み単語帳8冊、またはUTF-8 CSVのファイル／テキスト取り込み
- 英語・中国語・フランス語・ポルトガル語、4択・タイピング、出題方向、タグ・範囲指定
- 単語帳ごとの前回出題設定の保存・復元
- おすすめ／未学習／うろ覚え＆ミス／うろ覚えランダム／学習済ランダムの出題条件
- TTS読み上げ、音量調整、正誤効果音、ライト／ダークテーマ
- 習得済み・うろ覚え・未学習／要復習の進捗表示
- 単語帳の編集、並べ替え、連結、進捗リセット、削除
- CSVのコピー／共有、学習記録ZIPの相互移行、従来JSONバックアップ／復元

## 学習記録ZIP

設定画面の「学習記録ZIP」から操作します。書き出したZIPには全単語帳のCSVと、その行に対応する学習回数・直近正誤・直近学習日時が入ります。

- 同一CSVあり: CSVは変更せず、学習記録を統合
- 同一CSVなし: ZIP内の名前・言語・学習記録で新しい単語帳を追加
- 完全一致の対象: 行順、対象語、日本語訳、タグ、発音の4列すべて
- 名前や言語だけが同じでもCSVが異なれば新規追加

ZIPの内部構造と制限は [学習記録ZIP形式仕様](docs/STUDY_ARCHIVE_FORMAT.md) に記載しています。

## 学習状態

| 状態 | 条件 |
| --- | --- |
| 習得済み | 学習2回以上かつ直近が正解 |
| うろ覚え | 学習1回かつ直近が正解 |
| 未学習／要復習 | 未学習、または直近が不正解 |

## CSV

1行につき `対象言語,日本語訳,タグ,発音` の順です。タグと発音は省略できます。

```csv
apple,りんご,名詞,æpl
"take care of",世話をする,熟語,
```

UTF-8、LF/CRLF、引用符付きのカンマ・改行・二重引用符に対応し、上限は100,000レコードです。ヘッダー行は不要です。詳細は [CSV形式仕様](docs/CSV_FORMAT.md) を参照してください。

## Androidでビルドする

必要環境はJDK 17以上（JDK 21で検証済み）とAndroid SDKです。プロジェクト直下で実行します。

```bash
./gradlew test lint
./gradlew stageDebugApk
```

検証用APKは `dist-android/Tango-pro-1.2.1-android-debug.apk` に生成されます。v1.2.1では、試験的な配布物であることを明示したうえで、このデバッグ署名APKをGitHub Releaseに掲載します。デバッグ鍵が異なる既存インストールには上書きできないため、その場合はバックアップ後に旧版をアンインストールしてください。

## macOSでビルドする

macOS 13以上とXcode Command Line Toolsが必要です。

```bash
macos/build_macos.sh
macos/package_dmg.sh
```

ローカルビルドはad-hoc署名です。配布時はDeveloper ID署名とNotarizationを別途行ってください。

## 開発資料

- [設計書](DESIGN_DOC.md)
- [開発・検証手順](docs/DEVELOPMENT.md)
- [CSV形式仕様](docs/CSV_FORMAT.md)
- [学習記録ZIP形式仕様](docs/STUDY_ARCHIVE_FORMAT.md)
- [静的解析レポート](docs/STATIC_ANALYSIS_REPORT_v1.2.1.md)
- [v1.2.1 リリースノート](RELEASE_NOTES_v1.2.1.md)
- [リリースチェックリスト](docs/RELEASE_CHECKLIST.md)
- [ローカル成果物のSHA-256](CHECKSUMS_v1.2.1.txt)

## ライセンス

GNU General Public License v3.0。Copyright (C) 2026 Yuki Orita。免責事項と主な第三者ライブラリ情報は [NOTICE.md](NOTICE.md) を参照してください。
