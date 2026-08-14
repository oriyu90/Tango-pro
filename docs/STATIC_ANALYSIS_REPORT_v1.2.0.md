# v1.2.0 静的解析・テストレポート

対象: 学習記録ZIP、完全一致判定、成績統合、学習継続、出題条件、TTS操作、Android/macOS互換、既存機能回帰

## セクション1: archive形式と整合性

確認項目:

- manifest format/version、group ID、path、言語、SHA-256
- UTF-8・RFC 4180の正規CSV、4列、行順
- progressのCSV hash、行数、0始まり連続row
- 最大500冊、1冊100,000語、1 file 64 MiB、総展開量256 MiB
- 危険path、重複entry、未知file、symbolic link、改ざんCSVの拒否

結果: 正常。Android codec round-trip、SHA-256改ざん、危険pathテスト成功。macOSは展開前 `zipinfo` と展開後filesystem検証を実施。

## セクション2: 完全一致と統合

確認項目:

- 行順・対象語・訳・タグ・発音が全一致する場合だけmerge
- 名前が違ってもCSVが同一ならmerge
- 名前が同じでもCSVが異なれば連番名で新規追加
- 学習済み対未学習は学習済み優先
- 双方学習済みは回数最大値と最新日時側の正誤を採用

結果: 正常。Room integration testとSwift core self-testでmerge/addの双方を確認。

## セクション3: transactionと失敗時動作

AndroidはZIP全体をメモリ上で検証後、既存groupの成績更新と新規group追加を同一Room transactionで実施する。macOSは全検証・一時配列でのmerge後、保存queueをflushし、JSON atomic writeが成功してから完了する。保存失敗時はgroup・選択・sessionを復元する。

結果: 正常。部分適用経路なし。出力も一時file完成後に選択先へコピー／置換する。

## セクション4: Android UIと実ファイル

- 設定画面に「ZIPを読み込む」「ZIPを書き出す」を表示
- API 36.1 emulatorで8冊・9,666語、17 file entry、展開時約1.06 MBのZIPを生成
- `unzip -t`、manifest JSON、全entryを検証
- 同一ZIPをUIから再importし、Room DBが8冊のままであることを確認
- force-stop後のcold start成功、対象packageのFatal/ANRなし

結果: 正常。

## セクション5: macOS UI・永続化・相互互換

- SettingsにZIP import/export buttonを追加
- Android実ZIPを8冊・9,666語として読み込み、保存後にstoreを再生成して8冊を再読込
- Swift生成ZIPをAndroid codecで2冊として読込
- x86_64 / arm64 app build、`codesign --verify --deep --strict`、実app起動成功

結果: 正常。

## セクション6: 学習継続・出題条件・TTS操作

- Androidの削除済み条件 `all`、`incorrect`、`learned_once` と未知値を `recommend` へ移行
- おすすめが要復習・うろ覚えを優先しながら、全語習得後も候補を空にしないことを単体テスト
- うろ覚えランダムが「1回かつ直近正解」だけを、学習済ランダムが「2回以上かつ直近正解」だけを選ぶことを単体テスト
- Androidは最後のセッション要求を保持し、macOSはQuizSessionに継続設定を保持
- macOS core self-testで2回正解後の3周目開始と、問題数・条件を引き継いだindex 0の新規セッションを確認
- Android ComposeとmacOS SwiftUIの問題語全体を手動TTS操作領域に変更

結果: 正常。削除した条件の実行経路はUIとセッション処理から除去済み。

## 総合結果

- Android unit test: 22件、失敗0
- Android Lint: errors 0、warnings 26
- macOS core self-test: PASS
- Android↔macOS archive互換: PASS

## 残存リスク

- Android正式署名鍵がないため公開版署名でのupgrade installは未検証
- macOSはad-hoc署名で、Developer ID署名・Notarization前
- Android実メーカー端末のDocumentsUI差異はAPI 36.1 emulatorだけでは網羅できない
- format version 1は正規CSVの完全一致を採用するため、単語の並べ替えも別CSVとして扱う。これは仕様上の意図した動作
