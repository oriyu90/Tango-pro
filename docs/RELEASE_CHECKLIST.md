# リリースチェックリスト

Tango proをGitHubへ公開するときの確認事項です。生成物はGit履歴に含めず、GitHub Releaseへ添付します。

## ソース

- [ ] `git diff --check` が成功する
- [ ] 秘密鍵、パスワード、API token、`local.properties`、`.env`が追跡されていない
- [ ] `.gradle/`、`.kotlin/`、`build/`、`.local-archive/`が追跡されていない
- [ ] README、設計書、変更履歴、リリースノートのバージョンが一致する

## Android

- [ ] 署名環境変数を指定した `./gradlew test lint stageReleaseApk` が成功する
- [ ] APKのversionCode / versionNameを確認する
- [ ] `apksigner verify --verbose --print-certs` が成功する
- [ ] 公開済みAPKと署名証明書が一致することを確認する
- [ ] 署名証明書が異なる既存版には上書きできないことを明記する
- [ ] 組み込み17冊と旧標準英語資産の非同梱をテストする
- [ ] 狭幅、横長、特大文字、複数行4択のRobolectric UIテストが成功する

## macOS

- [ ] Core self-testが成功する
- [ ] `macos/build_macos.sh` と `macos/package_dmg.sh` が成功する
- [ ] `lipo -info` でx86_64 / arm64を確認する
- [ ] `codesign --verify --deep --strict` が成功する
- [ ] ad-hoc署名・未NotarizeであることをRelease本文に明記する
- [ ] 標準カタログ世代1→2の移行と、削除済み冊子を再生成しないことを確認する
- [ ] 680×500、横長ウィンドウ、文字サイズ4段階を確認する

## GitHub

- [ ] commitをpushする
- [ ] 同じcommitへバージョンtagを付ける
- [ ] ReleaseにAPK、DMG、checksumsを添付する
- [ ] Releaseページから添付物とSHA-256を確認する
- [ ] リポジトリがPublicで、説明・topics・既定branchが正しいことを確認する

## 正式配布へ移行する場合の追加確認

- Android正式署名鍵を安全なCI Secret等から指定し、過去の正式版と証明書を照合する
- 実端末で新規導入・更新・DocumentsUI・TTSを確認する
- macOS Developer ID署名、Notarization、Gatekeeper評価を行う
