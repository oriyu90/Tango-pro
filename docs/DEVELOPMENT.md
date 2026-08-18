# 開発・検証手順

## 前提

- Android: JDK 17以上（JDK 21で検証済み）、Android SDK、zshまたはbash
- macOS: macOS 13以上、Xcode Command Line Tools
- 秘密鍵、パスワード、`local.properties` はコミットしない

## 一括検証

リポジトリ衛生、バージョン同期、サイト構造、Android/macOSの双方向互換性を次の順で確認する。

```bash
bash scripts/check_repository_hygiene.sh
bash scripts/check_version_lockstep.sh
python3 scripts/check_site.py
bash scripts/run_cross_platform_tests.sh
```

`run_cross_platform_tests.sh`はAndroidのテスト・lint・debug/release build、macOS Core self-test、一時fixtureによるAndroid→macOS→Androidの読み書き、`testdata/fixtures/`に保存した過去fixtureの読込を実行する。CIも同じスクリプトを使用する。

## Android

### 標準チェック

```bash
./gradlew test lint
./gradlew assembleDebug assembleRelease
```

`assembleRelease` は署名環境変数がなければ未署名の成果物を作るだけであり、そのまま公開できません。

### 検証用APK

```bash
./gradlew stageReleaseApk
```

`dist-android/Tango-pro-2.0.0-android.apk` が生成されます。署名環境変数がない状態では公開用APKを作成しないでください。

### 正式署名

既存ユーザーへ更新配布するには過去の公開版と同じ署名鍵が必要です。値をシェル履歴やログへ出さず、CIのSecretまたは一時的な環境変数から渡します。

```text
KEYSTORE_PATH=/absolute/path/to/release.keystore
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

署名後は `apksigner verify --verbose --print-certs` で署名者を過去版と照合し、`aapt dump badging` または端末のpackage情報でversionCode 6 / versionName 2.0.0を確認します。

### 起動スモークテスト

1. 旧アプリが必要ならJSONバックアップを取得
2. 検証端末へ新規インストールし初回起動
3. 中国語基本単語とLv.1〜Lv.8英単語・英熟語の組み込み17冊が読めることを確認
4. アプリを強制停止して再起動
5. 組み込み単語帳を1冊削除し、再起動後に復活しないことを確認
6. CSV import/export、4択、タイピング、成績更新、JSON復元を確認
7. 設定から学習記録ZIPを書き出し、同じ端末へ読み戻して単語帳数が増えないことを確認
8. 1列変更したCSVを含むZIPでは新しい単語帳が増えることを確認
9. `adb logcat` に対象packageのFatal/ANRがないことを確認
10. 初期出題条件が「おすすめ」で、全語習得後も「そのまま続ける」から次の周回を開始できることを確認
11. 問題語とスピーカーアイコンのどちらをタップしても手動読み上げされることを確認
12. 旧版で全問／前回ミス／1回のみを選択していた場合、「おすすめ」へ移行することを確認
13. 2冊で方向・回答形式・出題対象・問題数（必要ならタグ／範囲）を別々に変更し、切替・再起動後も各設定が復元されることを確認
14. CSV追加時にフランス語／ポルトガル語を選択し、順方向の自動TTS、逆方向回答後、手動再生を確認
15. 単語帳編集で言語を変更し、表示とTTSが即座に切り替わることを確認
16. 長い単語帳名が通常サイズ→80%サイズ→末尾省略の順で表示されることを確認
17. シンプルモードで進捗バーと%吹き出し、閉じた出題設定、常時表示の開始ボタンを確認
18. 問題文字サイズ4段階と、2〜3行の4択ボタンが欠けないことを確認
19. 360×640dp、960×480dp、タブレット相当の画面で操作要素が重ならないことを確認
20. 全語を同じ回数だけ学習し、2〜4周目の配色と5周目以降のラベルを確認

## macOS

### Core test

```bash
sdk_path=$(xcrun --sdk macosx --show-sdk-path)
swiftc -swift-version 5 -sdk "$sdk_path" \
  -framework AppKit -framework Combine -framework CryptoKit \
  macos/TangoProMac/TangoCore.swift \
  macos/TangoProMac/StudyArchive.swift \
  macos/TangoProMac/CoreSelfTest.swift \
  -o /tmp/tango-core-self-test
/tmp/tango-core-self-test
```

Androidで書き出した実ZIPをmacOSへ、macOS fixtureをAndroidへ渡す双方向確認も行う。

```bash
TANGO_ANDROID_FIXTURE_OUTPUT=/tmp/Tango-pro-android-v2.0.0.zip \
  ./gradlew testDebugUnitTest \
  --tests 'com.example.StudyArchiveCodecTest.producer output can be parsed again'
/tmp/tango-core-self-test \
  --android-fixture /tmp/Tango-pro-android-v2.0.0.zip \
  --write-fixture /tmp/Tango-pro-macos-v2.0.0.zip
TANGO_ARCHIVE_FIXTURE=/tmp/Tango-pro-macos-v2.0.0.zip \
  ./gradlew testDebugUnitTest \
  --tests 'com.example.StudyArchiveCodecTest.macOS archive fixture is Android compatible when supplied'
```

通常は上記をまとめた`bash scripts/run_cross_platform_tests.sh`を使用する。互換形式を意図的に変更する場合だけ、仕様書とテストを先に更新したうえで次を実行し、`testdata/fixtures/`を再生成する。

```bash
TANGO_FIXTURE_OUTPUT_DIR=testdata/fixtures bash scripts/run_cross_platform_tests.sh
```

fixture名にはアプリの現在バージョンが入り、Android生成とmacOS生成の2ファイルを保存する。既存fixtureは後方互換テストのため削除しない。

### AppとDMG

```bash
macos/build_macos.sh
macos/package_dmg.sh
```

追加確認として、2冊に異なる出題設定を保存して再起動後に復元されること、シンプルモードと文字倍率を復元できること、680×500と横長ウィンドウで欠けないこと、CSV追加時と単語帳メニューの双方でフランス語／ポルトガル語を選べること、対応voiceで読み上げることを確認する。

ローカルappはad-hoc署名です。公開時はDeveloper ID Applicationで署名し、Notarizationと `spctl --assess` を行います。

## バージョン更新箇所

- Android: `app/build.gradle.kts` の `appVersionName` と `versionCode`
- macOS: `macos/TangoProMac/Info.plist`
- DMG: `macos/package_dmg.sh`
- 文書: `README.md`、`DESIGN_DOC.md`、リリースノート

`versionCode` / bundle versionは公開ごとに必ず増やします。公開後の同じ番号を別内容で再利用しません。

## リリース前チェックリスト

- [ ] `./gradlew clean test lint assembleRelease` が成功
- [ ] Android実端末で新規導入と旧版からの更新を確認
- [ ] 正式APK/AABの署名証明書が過去版と一致
- [ ] macOS appをDeveloper ID署名しNotarization済み
- [ ] バックアップ復元とCSV round-tripを確認
- [ ] Android↔macOSの学習記録ZIP相互importを確認
- [ ] ZIP改ざん、危険path、CSV不一致、学習済み優先のテストが成功
- [ ] Release notesとSHA-256を確定
- [ ] Git差分に鍵、`local.properties`、生成物、一時ファイルがない
- [ ] ユーザーの明示承認後にだけcommit、tag、push、GitHub Releaseを行う
