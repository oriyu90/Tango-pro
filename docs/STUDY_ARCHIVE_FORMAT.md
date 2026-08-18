# 学習記録ZIP形式仕様

Tango pro v1.2.0で導入し、v1.2.1でフランス語・ポルトガル語metadataへ拡張したAndroid / macOS共通の移行形式である。v2.0.0でもformat version 1を維持する。

## 識別情報

- 拡張子: `.zip`
- MIME type: `application/zip`
- format: `tango-pro-study-archive`
- format version: `1`
- 文字コード: JSON、CSV、entry名のすべてUTF-8

## ディレクトリ構造

```text
manifest.json
groups/
  group-0001/
    words.csv
    progress.json
  group-0002/
    words.csv
    progress.json
```

単語帳IDは出力順の `group-0001` 形式であり、アプリ内部のRoom IDやUUIDを含めない。

## manifest.json

```json
{
  "format": "tango-pro-study-archive",
  "version": 1,
  "exportedAtEpochMillis": 1786439904581,
  "appVersion": "2.0.0",
  "groups": [
    {
      "id": "group-0001",
      "name": "英語基本単語",
      "language": "en",
      "csvPath": "groups/group-0001/words.csv",
      "progressPath": "groups/group-0001/progress.json",
      "csvSha256": "64桁の小文字16進SHA-256"
    }
  ]
}
```

`language` は `en`（英語）、`zh`（中国語）、`fr`（フランス語）、`pt`（ポルトガル語）、`none`（読み上げなし）のいずれか。`exportedAtEpochMillis` はUnix epoch millisecondsである。

## words.csv

アプリが保持する単語帳を次の4列で正規出力する。

```csv
対象語,日本語訳,タグ,発音
```

- UTF-8、BOMなし
- LF改行
- RFC 4180相当の引用符処理
- 常に4列
- 最終行の後にもLFを付加
- ヘッダー行なし

これはインポート前の元ファイルbyte列ではなく、アプリが保持している値の可逆な正規表現である。元CSVが2列だった場合も、ZIP内では空のタグ・発音を含む4列になる。

## progress.json

```json
{
  "version": 1,
  "csvSha256": "対応するwords.csvのSHA-256",
  "records": [
    {
      "row": 0,
      "studyCount": 3,
      "isCorrectLast": true,
      "lastStudiedAt": 1786439000000
    }
  ]
}
```

`records` はCSVと同数で、`row` は0から始まる重複のない連続値でなければならない。未学習は `studyCount = 0`、`lastStudiedAt = 0`。学習時刻はUnix epoch millisecondsである。

## 完全一致判定

既存単語帳を同じ4列・行順で正規CSV化し、ZIP内の正規CSVと文字列比較する。次のすべてが一致した場合だけ同一CSVとする。

- レコード数と行順
- 対象語
- 日本語訳
- タグ
- 発音

単語帳名、言語設定、作成日時、内部ID、学習記録は一致条件に含めない。同名でもCSVが異なれば新しい単語帳として追加する。異なる名前でもCSVが完全一致すれば既存単語帳へ記録を統合する。

## 学習記録の統合

単語ごとに次の規則を適用する。

1. 片方だけが学習済み（`studyCount > 0`）なら学習済み側を採用
2. 双方が学習済みなら `studyCount` は大きい値を保持
3. `isCorrectLast` は `lastStudiedAt` が新しい側を採用
4. 日時が同じなら `studyCount` が大きい側を採用
5. 日時と回数が同じなら現在端末側を維持

この方式は回答回数を加算しない。別端末で重複して数えた回答を二重加算するのを防ぐため、最大値を採用する。

## 新規追加

完全一致するCSVがなければ、ZIPのCSV、単語帳名、言語、学習記録から新しい単語帳を作る。同名が存在する場合は `名前 2`、`名前 3` のように連番を付ける。

## 検証と制限

| 項目 | 上限／規則 |
| --- | --- |
| 単語帳数 | 500冊 |
| 1冊の単語数 | 100,000語 |
| file entry数 | 1,001 |
| 1 fileの展開サイズ | 64 MiB |
| 全fileの展開サイズ | 256 MiB |
| path | 相対pathのみ。空要素、`.`、`..`、backslashは禁止 |
| entry | 重複、未知file、symlinkは禁止 |
| integrity | manifestとprogress両方のSHA-256がCSVと一致すること |

全entry、JSON、CSV、hash、行番号を検証してからデータを変更する。Androidは単一Room transactionで統合・追加し、macOSは一時状態で統合後にJSONをatomic writeする。失敗した場合は変更前の状態を維持する。

## 互換性方針

version 1のreaderは未知のformat versionを推測して読み込まない。将来フィールドを変更する場合はversionを上げ、旧version readerまたは明示変換を用意する。

v1.2.1は既存の文字列fieldを拡張して `fr` / `pt` を追加した。英語・中国語・読み上げなしだけのZIPはv1.2.0と相互利用できるが、`fr` / `pt` を含むZIPはそれらを認識しないv1.2.0へは読み込めない。両端末をv1.2.1以上へ更新してから移行する。
