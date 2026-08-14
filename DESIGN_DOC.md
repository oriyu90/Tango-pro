# Tango pro v1.2.1 設計書

## 1. 目的と対象

Tango proは、CSV単語帳を取り込んで反復学習するローカルファーストのAndroid / macOSアプリである。本書はv1.2.1の実装を正とし、データ構造、責務、失敗時の扱い、互換性を定義する。

設計原則は次のとおり。

- 学習データは端末内で完結し、ネットワークを必須にしない
- インポートや連結は全件成功または全件失敗とし、部分データを残さない
- 表示名と物理リソース名を分離し、OSやビルドツールの差を受けにくくする
- 学習状態の判定を一箇所に集約し、表示と出題条件を一致させる
- 保存形式にバージョンを持たせ、未知の形式を黙って解釈しない

## 2. 対応環境とバージョン

| 項目 | Android | macOS |
| --- | --- | --- |
| アプリ版 | 1.2.1 | 1.2.1 |
| ビルド番号 | 5 | 5 |
| 最低OS | API 24 | macOS 13 |
| 対象SDK | API 36 | macOS SDK |
| UI | Jetpack Compose / Material 3 | SwiftUI |
| 永続化 | Room SQLite + SharedPreferences | JSONファイル（atomic write） |

AndroidのapplicationIdは既存ユーザーの更新互換性を守るため `com.aistudio.vocabstudier.xwqnzy` を維持する。コードnamespaceは現状 `com.example` であり、変更には移行作業が必要なためv1.2.1では維持する。

## 3. Androidアーキテクチャ

```text
MainActivity
  └─ MainAppContent
       ├─ EmptyStateScreen
       ├─ DashboardScreen
       ├─ StudySessionScreen
       └─ QuizSummaryScreen
            │
            ▼
       MainViewModel
       ├─ domain: 状態判定・回答正規化・問題生成・組み込み台帳
       ├─ service: TTS・効果音
       └─ data: Room DAO・CSV・バックアップモデル
```

### UI層

- `MainActivity.kt`: Activity生成、共有Intent受け取り、テーマ適用のみ
- `MainAppContent.kt`: 画面遷移、ダイアログ、ファイル選択、共有処理
- `DashboardScreen.kt`: 進捗と学習条件
- `StudySessionScreen.kt`: 出題・回答UI
- `QuizSummaryScreen.kt`: セッション結果
- `EmptyStateScreen.kt`: 単語帳がない場合の導線

### 状態・業務ロジック層

- `MainViewModel`: RoomとUI状態の調停、インポート、連結、セッション制御
- `StudyProgress`: 習得状態の唯一の判定元
- `AnswerNormalizer`: Unicode NFKCと小文字化によるタイピング照合
- `QuizQuestionFactory`: 4択の重複排除とフォールバック生成
- `BundledGroupCatalog`: 組み込みCSVの安定ID、物理名、表示名、言語
- `StudyLanguage`: 対応言語コード、表示名、Android TTS localeの一元定義
- `StudySettings`: 単語帳別出題設定の正規化規則

### データ層

- `AppDatabase`: Room schema version 4、v1→v4の明示Migration
- `WordDao`: Flowによる購読とsuspend query、原子的な成績更新
- `CsvParser` / `CsvExporter`: RFC 4180相当の入出力
- `SaveDataModels`: JSONバックアップ形式version 1
- `StudyArchiveCodec`: ZIP構造、SHA-256、CSV・進捗対応、展開量の検証
- `StudyArchiveService`: Room snapshot exportとtransaction import、完全一致判定、統合
- `StudySettingsPreferences`: Androidの単語帳ID別出題設定と旧共通設定の移行

## 4. Androidデータモデル

### study_groups

| 列 | 型 | 説明 |
| --- | --- | --- |
| id | Long / PK | 自動採番 |
| name | String | 表示名 |
| createdAt | Long | 作成時刻（epoch ms） |
| language | String | `en` / `zh` / `fr` / `pt` / `none` |
| sortOrder | Int | 昇順表示 |

### words

| 列 | 型 | 説明 |
| --- | --- | --- |
| id | Long / PK | 自動採番 |
| groupId | Long / indexed | 所属グループID |
| english | String | 学習対象語。歴史的名称のため中国語でもこの列を使う |
| japanese | String | 日本語訳 |
| tag | String | 任意タグ |
| pronunciation | String | 任意の発音情報 |
| studyCount | Int | 回答確定回数 |
| isCorrectLast | Boolean | 直近回答の正誤 |
| lastStudiedAt | Long | 直近回答時刻（epoch ms） |

現スキーマは外部キー制約を宣言していない。グループ削除はDAOの `@Transaction` でwordsを先に削除してからgroupを削除する。将来外部キーを追加する場合はRoom schema versionを上げ、既存孤児データの検査を含むMigrationを用意する。

## 5. 学習状態と成績更新

| 状態 | 条件 |
| --- | --- |
| 習得済み | `studyCount >= 2 && isCorrectLast` |
| うろ覚え | `studyCount == 1 && isCorrectLast` |
| 未学習／要復習 | 上記以外 |

不正解の初回回答は「うろ覚え」ではなく「要復習」である。Androidは `StudyProgress`、macOSは同じ条件のcomputed logicを使用する。

回答確定時は、読み出したWordを上書き保存せず次のSQLを実行する。

```sql
UPDATE words
SET studyCount = studyCount + 1,
    isCorrectLast = :isCorrect,
    lastStudiedAt = :studiedAt
WHERE id = :wordId
```

これにより、短時間の連続更新でも古い `studyCount` によるlost updateを防ぐ。

## 6. 出題

出題条件は次の5種類を扱い、初期値は `recommend` とする。

| ID | 表示 | 候補 |
| --- | --- | --- |
| `recommend` | おすすめ | 全語。要復習、うろ覚え、習得済みの順で優先 |
| `unstudied` | 未学習のみ | `studyCount == 0` |
| `weak` | うろ覚え＆ミスのみ | うろ覚え、または学習歴があり直近不正解 |
| `vague_random` | うろ覚えをランダム | うろ覚え |
| `learned_random` | 学習済をランダム | 習得済み |

旧版で保存された `all`、`incorrect`、`learned_once` および未知のIDは、Android起動時に `recommend` へ移行する。タグ・範囲条件は成績条件で候補を絞った後、ランダム化・おすすめ優先順位付けの前に適用する。

出題方向、回答形式、出題対象、問題数は単語帳ごとに保持する。Androidはさらにタグ、範囲、絞り込み有効状態を単語帳ID付きSharedPreferencesへ保存する。v1.2.0以前の共通キーは各単語帳が初めて選択されたときの初期値として一度だけ移行し、その後は相互に上書きしない。macOSは `VocabGroup.studySettings` に任意値として保存し、旧JSONでフィールドが欠ける場合は既定値へフォールバックする。

`recommend` は習得済みも候補から除外しない。そのため全語が習得済みになった後も、3周目、4周目以降を同じ単語帳で繰り返せる。セッション終了時には最後に使用した単語帳、方向、回答形式、条件、問題数、タグ・範囲を保持し、「そのまま続ける」で同じ設定の新規セッションを開始する。

手動TTSはスピーカーアイコンだけでなく問題語全体を操作領域とする。自動読み上げ設定が無効でも手動再生は利用できる。対応言語は英語 `en-US`、中国語 `zh-CN`、フランス語 `fr-FR`、ポルトガル語 `pt-BR` である。macOSは同言語のインストール済みvoiceを優先locale順で探索する。順方向では問題文、逆方向では回答確定後の対象語を読み上げる。

4択問題は正解1件と誤答3件を必要とする。誤答候補は正解文字列を除外し、文字列単位で重複排除する。実データが不足する場合のみ、正解と重ならない表示用フォールバックを追加する。

タイピング回答は前後空白を除去し、Unicode NFKC正規化後に `Locale.ROOT` で小文字化して比較する。このため英字の大文字・小文字と全角・半角の差を許容するが、綴りや単語間空白の差までは許容しない。

## 7. CSVと組み込み単語帳

CSVの公開仕様は [docs/CSV_FORMAT.md](docs/CSV_FORMAT.md) を参照する。AndroidのパーサーはReaderから逐次処理し、閉じていない引用符や100,000件超過では例外を返す。登録前に全体を解析するため、不正CSVで部分グループを作らない。

組み込みCSVは次の対応を持つ。

| 安定ID | 物理ファイル名 | 表示名 |
| --- | --- | --- |
| basic_phrases | basic_english_phrases.csv | 英語基本フレーズ |
| basic_words | basic_english_words.csv | 英語基本単語 |
| basic_chinese | basic_chinese_words.csv | 中国語基本単語 |
| common_test_words | common_test_words.csv | 共テ用英単語 |
| common_test_phrases | common_test_phrases.csv | 共テ用英熟語 |
| advanced_words | advanced_words.csv | 難関大用英単語 |
| advanced_phrases | advanced_phrases.csv | 難関大用英熟語 |
| pre_high_school | pre_high_school_vocab.csv | 高校レベル未満の単熟語 |

Androidは安定IDごとのSharedPreferencesキー、macOSは保存状態の `bundledImportCompleted` で初期投入完了を記録する。完了後にユーザーが削除した組み込み単語帳は再生成しない。旧Android版の初期投入フラグは、既公開の3冊についてのみ互換判定に使用する。

## 8. 複数レコード操作

- CSVインポート: 解析完了後、groupとwordsをRoom transactionで登録
- グループ連結: 対象取得後、新groupと複製wordsをRoom transactionで登録
- セーブデータ復元: version検証後、全groupをRoom transactionでmergeまたは追加
- グループ削除: wordsとgroupをDAO transactionで削除
- 学習記録ZIP import: 全entryを検証後、完全一致groupへのmergeと新規group追加を単一Room transactionで実施

同名グループのバックアップ復元では、語句集合が完全一致するときだけ学習時刻と回数を比較して新しい成績を採用する。内容が異なる場合は連番付きの別グループとして追加し、既存データを破壊しない。

## 9. 学習記録ZIP

ZIP format identifierは `tango-pro-study-archive`、format versionは1とする。ルートの `manifest.json` が単語帳metadataと各entryのSHA-256を保持し、単語帳ごとに次の2ファイルを置く。

```text
manifest.json
groups/group-0001/words.csv
groups/group-0001/progress.json
```

`words.csv` はアプリ内部の4列をRFC 4180形式、UTF-8、LF、末尾改行ありで正規出力する。「CSV完全一致」はこの正規CSVの文字列一致であり、単語帳名、内部ID、作成日時は判定に含めない。行順、対象語、日本語訳、タグ、発音のいずれかが異なれば別単語帳である。

`progress.json` はCSV行番号、学習回数、直近正誤、直近学習時刻（epoch milliseconds）を保持する。CSVのSHA-256と行数が一致しない記録は拒否する。

統合規則は次のとおり。

1. 一方のみ `studyCount > 0` なら学習済み側を採用
2. 双方が学習済みなら大きい `studyCount` を保持
3. 直近正誤は `lastStudiedAt` が新しい側を採用
4. 同日時なら `studyCount` が大きい側、完全同値なら端末側を採用

安全制限は500冊、1冊100,000語、1 entry 64 MiB、総展開量256 MiB、最大1,001 file entryとする。危険path、重複名、未知entry、非UTF-8、symlink、hash不一致、非連続行番号、未知versionを登録前に拒否する。macOSはsystem `zipinfo` でcentral directoryを展開前検査し、`ditto`で一時directoryへ展開する。

完全な形式仕様は [docs/STUDY_ARCHIVE_FORMAT.md](docs/STUDY_ARCHIVE_FORMAT.md) を参照する。

## 10. 設定とバックアップ

AndroidのUI設定はSharedPreferences、単語帳と成績はRoomに保存する。テーマ・TTS・音量・効果音はアプリ共通、出題設定は単語帳ID別である。音量は0.0〜1.0、問題数は有効範囲に丸め、壊れた設定値をCompose Sliderや `take()` に渡さない。

AndroidのJSONセーブデータversion 1はグループ名、言語、語句、タグ、発音、学習回数、直近正誤、直近時刻を含む。UI設定とグループ順は含めない。未知のversionは拒否する。

学習記録ZIPを通常のプラットフォーム間移行形式とする。従来JSONはAndroid旧版との互換用として設定画面に残す。

Android Auto BackupではデータベースとSharedPreferencesを対象とする。端末移行前には明示的なJSON書き出しも推奨する。

## 11. macOS版

macOS版はSwiftUIの `TangoStore` が画面状態と業務処理を保持する。保存先JSONは一時ファイルを介したatomic writeで更新し、専用serial queueで競合を避ける。保存形式version 1以外はエラーとして読み込みを停止する。単語帳ごとの `studySettings` はoptionalとして追加し、v1.2.0以前のJSONを形式version変更なしで読み込める。

AndroidとCSV列、100,000件制限、学習状態、組み込み8冊を合わせる。UUIDを内部IDに用いるため、Android Roomの数値IDとは相互変換しない。プラットフォーム間の成績移行は学習記録ZIPを用い、内部IDではなく正規CSVで対応付ける。

## 12. ビルド、署名、検証

Androidの標準検証は `./gradlew test lint stageDebugApk assembleRelease`。`stageDebugApk` はデバッグ署名APKを `dist-android` に複製する。Release署名は `KEYSTORE_PATH`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` の環境変数で与え、鍵をリポジトリへ置かない。

macOSは `macos/build_macos.sh` でuniversal app、`macos/package_dmg.sh` でDMGを作る。ローカル候補はad-hoc署名であり、公開時はDeveloper ID署名、Notarization、Gatekeeper確認が必要である。

詳細な手順と公開前チェックは [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) を参照する。

## 13. 既知の保留事項

- Androidのnamespace `com.example` は既存コード由来。applicationIdを変えずに段階的移行する計画が必要
- AndroidとmacOSの従来JSON形式は共通ではないが、v1.2.0以降は学習記録ZIPを共通形式とする
- Release署名・macOS Notarizationはローカル検証の範囲外
- 依存関係の一括更新は回帰範囲が大きいためv1.2.1では行わず、別版で段階的に実施する
