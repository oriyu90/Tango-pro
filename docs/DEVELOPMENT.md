# 開発・検証手順

## 前提

- Android: JDK 17以上（JDK 21で検証済み）、Android SDK、zshまたはbash
- macOS: macOS 13以上、Xcode Command Line Tools
- 秘密鍵、パスワード、`local.properties` はコミットしない

## Android

### 標準チェック

```bash
./gradlew test lint
./gradlew assembleDebug assembleRelease
```

`assembleRelease` は署名環境変数がなければ未署名の成果物を作るだけであり、そのまま公開できません。

### 検証用APK

```bash
./gradlew stageDebugApk
```

`dist-android/Tango-pro-1.2.0-android-debug.apk` が生成されます。ローカルのAndroidデバッグ鍵による署名です。

### 正式署名

既存ユーザーへ更新配布するには過去の公開版と同じ署名鍵が必要です。値をシェル履歴やログへ出さず、CIのSecretまたは一時的な環境変数から渡します。

```text
KEYSTORE_PATH=/absolute/path/to/release.keystore
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

署名後は `apksigner verify --verbose --print-certs` で署名者を過去版と照合し、`aapt dump badging` または端末のpackage情報でversionCode 4 / versionName 1.2.0を確認します。

### 起動スモークテスト

1. 旧アプリが必要ならJSONバックアップを取得
2. 検証端末へ新規インストールし初回起動
3. 組み込み8冊が読めることを確認
4. アプリを強制停止して再起動
5. 組み込み単語帳を1冊削除し、再起動後に復活しないことを確認
6. CSV import/export、4択、タイピング、成績更新、JSON復元を確認
7. 設定から学習記録ZIPを書き出し、同じ端末へ読み戻して単語帳数が増えないことを確認
8. 1列変更したCSVを含むZIPでは新しい単語帳が増えることを確認
9. `adb logcat` に対象packageのFatal/ANRがないことを確認
10. 初期出題条件が「おすすめ」で、全語習得後も「そのまま続ける」から次の周回を開始できることを確認
11. 問題語とスピーカーアイコンのどちらをタップしても手動読み上げされることを確認
12. 旧版で全問／前回ミス／1回のみを選択していた場合、「おすすめ」へ移行することを確認

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
/tmp/tango-core-self-test \
  --android-fixture /tmp/Tango-pro-android-v1.2.0.zip \
  --write-fixture /tmp/Tango-pro-macos-v1.2.0.zip
TANGO_ARCHIVE_FIXTURE=/tmp/Tango-pro-macos-v1.2.0.zip \
  ./gradlew testDebugUnitTest \
  --tests 'com.example.StudyArchiveCodecTest.macOS archive fixture is Android compatible when supplied'
```

### AppとDMG

```bash
macos/build_macos.sh
macos/package_dmg.sh
```

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
