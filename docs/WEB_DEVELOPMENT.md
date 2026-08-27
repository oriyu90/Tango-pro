# Web版の開発・公開手順

## 構成

- `shared`: Android/Web共通のmodel、学習規則、回答正規化、CSV、組み込み単語帳catalog
- `webApp`: Kotlin/Wasm + Compose Multiplatform UI、Room 3 repository、PWA資産
- `sqliteWasmWorker`: AndroidX SQLite Web driverと`@sqlite.org/sqlite-wasm`を接続するWorker
- Web DB: `tango-pro.db`をSQLite WasmのOPFS VFSへ保存
- 公開URL: `https://studio-rizi.pages.dev/projects/tango-pro/web/`

AndroidのRoom 2 schema、applicationId、既存migrationは変更しない。macOS版はSwift/SwiftUIのままとし、3版の移行契約はCSVと学習記録ZIP format version 1である。

## 対応ブラウザ

Chrome / Edgeの現行安定版をTier 1とする。Kotlin/WasmGC、OPFS SyncAccessHandle、Web Worker、Web Locks、Web Cryptoが必要である。非対応ブラウザやprivate modeの永続性は保証しない。

同時に複数タブから同じOPFS databaseを開かない。`browser-bridge.js`のWeb Locks APIで最初のタブだけがwriter lockを保持し、2つ目は案内画面を表示する。

## ローカル検証

```bash
bash scripts/run_web_tests.sh
```

このスクリプトはAndroid/Wasm共通test、Web archive validation、Room 3 repository contract test、外部リンクとWeb Speech bridgeのJavaScript回帰test、production distributionを実行する。非ASCIIのworkspace pathでKotlin/Wasm linkerが失敗する既知問題を避けるため、root Gradle scriptは該当時だけOSの一時directoryへbuild outputを移す。

ブラウザではCOOP / COEPを返すHTTP serverから起動し、最低限次を確認する。

1. 17冊が初回だけ投入される
2. 4択とタイピングで成績が更新され、再読込後も残る
3. CSV import時に単語帳名と言語を選べる
4. CSV / 学習記録ZIPを書き出し・読み戻せる
5. 2タブ目はdatabaseを開かない
6. 390×844、960×480、desktopで操作要素が欠けない
7. 日本語・簡体中国語が同梱Noto Sans SCで欠けない
8. 通常／シンプル、ライト／ダークのダッシュボード末尾に2つの外部リンクボタンがあり、正しいURLを新規タブで開いて元タブを保持する
9. TTSで指定localeのvoiceが選ばれ、voice一覧の遅延読込後も発声し、別言語voiceへ誤フォールバックしない
10. consoleにuncaught errorがない

## Studio Riziへstage

```bash
bash scripts/stage_web_for_pages.sh \
  /absolute/path/to/studio-rizi/website/projects/tango-pro/web
```

stage scriptはproduction bundleをbuildし、source mapを除外して同期し、全公開assetからcontent-derived build IDを作る。`build-info.json`へsource commit、build ID、base path、precache対象を記録し、同じIDをService Workerへ埋め込む。source treeがdirtyなら`sourceCommit`へ`-dirty`を付ける。公開commit確定後に再stageし、dirty表示のないbuildを公開する。

`studio-rizi/website/_headers`では `/projects/tango-pro/web/*` だけに次を指定する。

```text
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
Cross-Origin-Resource-Policy: same-origin
X-Content-Type-Options: nosniff
```

`index.html`、`sw.js`、`build-info.json`は`no-cache`、content-hash付きWasmはimmutableとする。Service Workerのactivate処理はCache Storageの旧Tango Web cacheだけを削除し、OPFS、localStorage、Room dataを削除しない。

## Studio Rizi側の検証

```bash
npm ci
npm run build
npm run validate
npm run count-files
```

公開後はread-onlyで次を確認する。

```bash
curl -I https://studio-rizi.pages.dev/projects/tango-pro/web/
curl -I https://studio-rizi.pages.dev/projects/tango-pro/web/sw.js
curl -s https://studio-rizi.pages.dev/projects/tango-pro/web/build-info.json
```

実際のWasm名は`build-info.json`から取得し、HTTP 200、`Content-Type: application/wasm`、COOP / COEP、ブラウザの`window.crossOriginIsolated === true`を確認する。

## ロールバック

Studio RiziのWeb bundleだけを直前の既知正常commitへ戻す。OPFSやlocalStorageを消すmigrationをService Workerへ入れない。DB schema変更が必要な場合はRoom migrationと旧bundle読込試験を先に追加し、単なるbundle rollbackで新schemaを壊さないことを確認する。
