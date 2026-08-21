# Tango pro v2.1.0

[![CI](https://github.com/oriyu90/Tango-pro/actions/workflows/ci.yml/badge.svg)](https://github.com/oriyu90/Tango-pro/actions/workflows/ci.yml)

CSVで作った単語帳を取り込み、4択またはタイピングで学習するAndroid / macOS向けアプリです。英語・中国語・フランス語・ポルトガル語の読み上げ、単語帳別の出題設定履歴、学習履歴、CSV共有、学習記録ZIPの相互移行に対応します。

[公式紹介サイト](https://studio-rizi.pages.dev/projects/tango-pro/) · [v2.1.0をダウンロード](https://github.com/oriyu90/Tango-pro/releases/tag/v2.1.0)

> [!WARNING]
> macOS DMG内のアプリはad-hoc署名・未Notarizeです。更新や移行の前に、設定画面から学習記録ZIPを保存してください。

> [!IMPORTANT]
> GitHub Release版のv2.0.0 APKはデバッグ鍵で誤って署名されていました。v2.1.0はv1.1.1までと同じ正規のアップロード鍵で署名し直しています。v2.0.0 APKから更新する場合は、学習記録ZIPを書き出してからアンインストール→v2.1.0の新規インストールが必要です（v1.1.1以前からの更新は通常どおり上書き可能）。詳細は [v2.1.0リリースノート](RELEASE_NOTES_v2.1.0.md) を参照してください。

## v2.1.0の変更点

- 学習画面の配置を「上寄せ」（v2.0.0からの既定）と「均等配置」（v1.2.1以前の挙動）から選べる設定を追加（Android / macOS共通）
- Androidで範囲・タグ指定を有効にしている間、指定範囲だけの学習済・うろ覚え・要復習バーを単語帳本体のバーの下へ追加表示（シンプルモードでも表示）
- 誤ってデバッグ鍵で公開されていたv2.0.0 APKから、v1.1.1までと同じ正規アップロード鍵での署名に復帰

詳細は [v2.1.0リリースノート](RELEASE_NOTES_v2.1.0.md)、[設計書](DESIGN_DOC.md)、[検証レポート](docs/STATIC_ANALYSIS_REPORT_v2.1.0.md) を参照してください。

## 主な機能

- 組み込み単語帳17冊、またはUTF-8 CSVのファイル／テキスト取り込み
- 英語・中国語・フランス語・ポルトガル語、4択・タイピング、出題方向、タグ・範囲指定
- 単語帳ごとの前回出題設定、周回数、習得済み・うろ覚え・要復習の進捗表示
- シンプル／通常表示、ライト／ダークテーマ、問題・4択文字サイズ変更
- TTS読み上げ、音量調整、正誤効果音
- 単語帳の編集、並べ替え、連結、進捗リセット、削除、CSV共有
- Android / macOS間で利用できる学習記録ZIP、Android従来JSONバックアップ

## 周回表示

現在周回は `全単語の最小学習回数 + 1` です。すべての単語を1回以上学習すると2周目、2回以上学習すると3周目になります。正誤にかかわらず回答を確定した回数を用いるため、周回数と習得状態は別々に確認できます。

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

UTF-8、LF/CRLF、引用符付きのカンマ・改行・二重引用符に対応し、上限は100,000レコードです。詳細は [CSV形式仕様](docs/CSV_FORMAT.md) を参照してください。

## 学習記録ZIP

設定画面の「学習記録ZIP」から、全単語帳のCSVと学習回数・直近正誤・直近学習日時を書き出せます。同一CSVには成績だけを統合し、内容が異なるCSVは新しい単語帳として追加します。詳細は [学習記録ZIP形式仕様](docs/STUDY_ARCHIVE_FORMAT.md) を参照してください。

## Androidでビルド

JDK 17以上とAndroid SDKが必要です。

```bash
./gradlew test lint
./gradlew stageReleaseApk
```

署名環境変数を指定した配布APKは `dist-android/Tango-pro-2.1.0-android.apk` に生成されます。署名証明書が異なるインストールには上書きできません。

## macOSでビルド

macOS 13以上とXcode Command Line Toolsが必要です。

```bash
macos/build_macos.sh
macos/package_dmg.sh
```

ローカルビルドはuniversal binaryのad-hoc署名です。正式配布時はDeveloper ID署名とNotarizationが必要です。

## 開発資料

- [リポジトリガイド](AGENTS.md)
- [コントリビューションガイド](CONTRIBUTING.md)
- [ドキュメント索引](docs/README.md)
- [設計書](DESIGN_DOC.md)
- [開発・検証手順](docs/DEVELOPMENT.md)
- [CSV形式仕様](docs/CSV_FORMAT.md)
- [学習記録ZIP形式仕様](docs/STUDY_ARCHIVE_FORMAT.md)
- [v2.1.0 静的解析・テストレポート](docs/STATIC_ANALYSIS_REPORT_v2.1.0.md)
- [v2.1.0 リリースノート](RELEASE_NOTES_v2.1.0.md)
- [リリースチェックリスト](docs/RELEASE_CHECKLIST.md)
- [成果物SHA-256](CHECKSUMS_v2.1.0.txt)

## ライセンス

GNU General Public License v3.0。Copyright (C) 2026 Yuki Orita。第三者ライブラリ情報は [NOTICE.md](NOTICE.md) を参照してください。
