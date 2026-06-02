# Tango pro - 技術物理設計書・開発仕様書 (v1.0)
本ドキュメントは、「Tango pro」モバイルアプリケーションの内部アーキテクチャ、データベース設計、音声・オーディオスレッド制御、各種アルゴリズムを整理した技術設計書です。将来的にPC版（Desktop/Web）への移植を行う際の設計ガイダンスとしても活用できるように詳細に体系化しています。

---

## 1. プロジェクト全体像 & アーキテクチャ概要

本アプリは、Googleが推奨する推奨アプリ構造 (Recommended App Architecture - **MVVMモデル**) を採用し、UIとビジネスロジック、データアクセスを明確に分離しています。

```
       +---------------------------------------------+
       |             Jetpack Compose UI              |  (MainActivity.kt)
       +--------------------+------------------------+
                            | (State 監視)
                            | Observer Pattern
                            v
       +---------------------------------------------+
       |              MainViewModel                  |  (MainViewModel.kt)
       +--------------------+------------------------+
                            |
           +----------------+----------------+
           | (Data Access)                   | (Service Control)
           v                                 v
+----------------------+          +----------------------+
|  Room RoomDatabase   |          | SoundPlayer (自作)   | (SoundPlayer.kt)
|  & WordDao (SQLite)  |          | TtsService (Android) | (TtsService.kt)
+----------------------+          +----------------------+
```

### アーキテクチャ構成レイヤー
1. **コアUIレイヤー (Jetpack Compose / M3)**:
   単一Activity (`MainActivity.kt`) による Single-Activity 構成。
   画面のナビゲーションや各コンポーネント（ダッシュボード、設定ダイアログ、クイズ学習セッション画面、CSVインポート画面）は、再利用可能なComposablesにより構築され、画面のライフサイクルや回転などによる状態喪失から保護されています。
2. **ViewModelレイヤー (MainViewModel.kt)**:
   `AndroidViewModel`を継承し、アプリ起動からシャットダウンまでのライフサイクルを通じてデータの保持・状態操作・非同期処理を担当。
   SharedPreferencesへの永続書き込み（セッタープロパティ経由）とRoomのReactive Flows配信を管理。
3. **データ永続化レイヤー (Room / SQLite)**:
   `AppDatabase.kt` を中核とし、マルチスレッド環境に対応したトランザクション制御、自動インデックス管理、カスケード削除定義をSQLiteで実現。
4. **ハードウェア・サービス制御レイヤー**:
   - `TtsService`: クラウドを介さず端末ローカルのテキスト読上げエンジン（TTS API）を駆動させるバックエンド制御。
   - `SoundPlayer`: 再生遅延のないピコピコシンセサイザー効果音を、AudioTrackを利用してランタイム上でピュアPCM波形合成して即時再生。

---

## 2. データベース・スキーマ設計

データ永続化には SQLite を SQL書き込み不要で抽象化する Jetpack Room 永続ライブラリを使用しています。

### 1) グループテーブル (`study_groups` / Entity: `StudyGroup`)
CSVから取り込まれた単語帳ごとのメタデータを格納します。

| カラム名 (変数名) | データ型 (SQLite) | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` (`id`) | INTEGER (Long) | PRIMARY KEY, AUTOINCREMENT | 自動生成グループ識別子 |
| `name` (`name`) | TEXT (String) | NOT NULL | グループの表示名（CSVタイトルなど） |
| `createdAt` (`createdAt`) | INTEGER (Long) | NOT NULL | グループ作成日時（UNIXミリ秒タイムスタンプ） |

### 2) 単語テーブル (`words` / Entity: `Word`)
個別単語と、それに対する学習実績データを記録します。

| カラム名 (変数名) | データ型 (SQLite) | 制約 | 説明 |
| :--- | :--- | :--- | :--- |
| `id` (`id`) | INTEGER (Long) | PRIMARY KEY, AUTOINCREMENT | 自動生成単語識別子 |
| `groupId` (`groupId`) | INTEGER (Long) | NOT NULL, **FOREIGN KEY** | 外部キー。`study_groups.id` と紐付け、**On Delete CASCADE**設定 |
| `english` (`english`) | TEXT (String) | NOT NULL | 出題英語単語 |
| `japanese` (`japanese`) | TEXT (String) | NOT NULL | 日本語訳 |
| `tag` (`tag`) | TEXT (String) | NOT NULL (空文字許容) | カテゴリ、品詞分類タグなどのメタデータ |
| `studyCount` (`studyCount`) | INTEGER (Int) | DEFAULT 0, NOT NULL | 本単語に対する全回答学習回数 |
| `isCorrectLast` (`isCorrectLast`) | INTEGER (Boolean) | DEFAULT 0, NOT NULL | 前回の解答実績 (1:正解, 0:不正解) |
| `lastStudiedAt` (`lastStudiedAt`) | INTEGER (Long) | NOT NULL | 最終学習日時（UNIXタイムスタンプミリ秒） |

#### 外部キー定義とカスケード制約の重要性
```kotlin
@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = StudyGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE // グループ削除時に単語リストをデータベースレベルで高速カスケード一括消去
        )
    ],
    indices = [Index(value = ["groupId"])] // パフォーマンス高速化のための検索用インデックス
)
```

---

## 3. 学習進捗ロジック & 学習アルゴリズム

学習結果や習得度を効率よく記憶させるため、3つのステート（未学習・うろ覚え・習得済み）を動的に判定・分類しています。

### 1) 3段階習得度ステート定義
*   `🟢 習得済み (Studied)`:
    - 判定条件: `studyCount >= 2` かつ `isCorrectLast == true` (直近正解)
    - 本アプリが定義する「確実に定着した」ステート。
*   `🟡 うろ覚え (Vague)`:
    - 判定条件: `studyCount == 1`
    - 新しいグループから1回答した後の定着率評価期間のステート。
*   `🔴 未学習/要復習 (Unstudied/Review)`:
    - 判定条件: `studyCount == 0` (まだ未学習の段階) または、`studyCount > 0` かつ `isCorrectLast == false` (直近で間違えた単語)
    - 定着率が落ちた、あるいは未着手の単語を表示。

### 2) 出題フィルタリングロジック (`filterMode`)
学習者が選んだ条件によって、Room(SQL)でロードされた単語リストをメモリスレッド上で高速判定して絞り込みます。

- **`all` (全問対象)**: グループに属するすべての単語を出題対象とします。
- **`unstudied` (未学習のみ)**: `studyCount == 0` のみ抽出。
- **`incorrect` (前回ミスのみ)**: `studyCount > 0` かつ `isCorrectLast == false` のみ抽出。
- **`learned_once` (うろ覚えのみ)**: `studyCount == 1` のみ抽出。
- **`weak` (うろ覚え＆ミスのみ)**: `studyCount == 1` または ( `studyCount > 0` かつ `isCorrectLast == false` )。
- **`learned_random` (学習済からランダム)**: `studyCount > 0` の中から抽出し、シャフル。
- **`range` (範囲指定・タグ指定)**:
  - 画面で設定された `rangeStart` から `rangeEnd` までの順位部（1-based index）をシーケンシャルに取り出し。
  - さらに、`uniqueTags`から選択された品詞タグ（"すべて"以外）に完全一致する単語のみで自動絞り込み。

---

## 4. オーディオエンジン・音響工学設計 (自作高応答シンセ音響)

アプリの操作性と快感度を高めるため、遅延のある `.wav` などのメディアアセットファイルを読み込む方式を完全に排除し、**「再生遅延ゼロ（理論上最短時間、実測数ミリ秒以内）の自作PCM波形リアルタイム合成再生エンジン」** を搭載しています。

### 1) 効果音シンセサイザー (`SoundPlayer.kt`)
Androidの底层オーディオAPIである `AudioTrack` を駆動させ、周波数をその場で配列上に数値計算（サイン波、矩形波など）してダイレクトに送信します。

- **再生フォーマット仕様**:
  - サンプリング周波数: `44,100 Hz`
  - チャンネル: モノラル (`AudioFormat.CHANNEL_OUT_MONO`)
  - 量子化ビット数: 16bit PCM (`AudioFormat.ENCODING_PCM_16BIT`)
  - 動作スレッド: `Dispatchers.Default` (UIを絶対にブロッキングさせないバックグラウンド処理)

#### 周波数からPCM波形を生成する数式
$$x(t) = \text{Volume} \times A \times \sin(2\pi f t)$$
- サイン波（ピコピコ好感音用）: $\sin(\theta)$ で合成。
- 矩形波（重厚ブザー歪み音用）: $\theta$ の正負判定により $+1$ または $-1$ を出力して、低周波に歪みを加えた強烈なノイズ音を再現。

- **効果音(SE)音量コントロールの実装**:
  - 音声波形出力の最終段階で、各16Bit振幅サンプル(Short値)に `volumeMultiplier / 音量割合 (0.0f - 1.0f)` を乗算して音量のなめらかなスライド調整を実現。

### 2) テキスト読み上げ (`TtsService.kt`)
Android組み込みの `TextToSpeech` API をラップし、以下の挙動を担保。
- **言語設定**: `Locale.ENGLISH` による高精度な英語合成。音声エンジンの準備が整っていない状態での呼び出しを例外キャッチ。
- **音量コントロールの実装**:
  `TextToSpeech.QUEUE_FLUSH` を利用して発声中音声を瞬時にカット＆リフレッシュ、TTS引数クラス `Bundle` に対して `TextToSpeech.Engine.KEY_PARAM_VOLUME` と `volumeMultiplier` を渡すことで音圧コントロールを直接実行。

---

## 5. アプリ設定と永続化 (記憶スキーマ)

「出題学習設定」や「アプリ設定（音量・ダークモード等）」を開閉時・アプリビルド後にも完全に引き継ぐために、ローカルファイルベースの key-value 実装 `SharedPreferences` (ファイル名: `"TangoProPrefs"`) を利用し、ViewModelプロパティの getter/setter を通じて設定完了と同時に高速にデバイスにコミットを行っています。

### 永続化保存されるキー一覧
| SharedPreferences キー名 | デフォルト値 | 変数型 | 説明 |
| :--- | :--- | :--- | :--- |
| `lastSelectedGroupId` | 空 / なし | Long | 最後に選択して作業していた単語グループID |
| `selectedQuizCount` | `10` | Int | セッション問題数 (5問 / 10問 / 20問 / 50問 / 全問) |
| `darkThemeSelected` | `false` | Boolean | Pure BlackをベースとしたダークテーマのON/OFF |
| `isTtsEnabled` | `true` | Boolean | 出題時に英語音声を自動発音(TTS)するか |
| `ttsVolume` | `1.0f` | Float | 英語音声の読み上げ音量 (0% 〜 100%) |
| `soundStyle` | `"PIKO"` | String | 効果音のスタイル (PIKO / MELODY / BUZZER / MUTE) |
| `soundVolume` | `1.0f` | Float | 合成音による効果音(SE)の音量 (0% 〜 100%) |
| `studyDirectionForward` | `true` | Boolean | 出題方向 (true: 英語 ➡️ 日本語, false: 日本語 ➡️ 英語) |
| `studyMultipleChoice` | `true` | Boolean | 出題形式 (true: 4択クイズ, false: タイピングクイズ) |
| `filterMode` | `"all"` | String | 出題対象条件 (全問 / 未学習 / ミス / うろ覚え / タグ等) |
| `selectedTagConstraint` | `"すべて"` | String | 範囲指定選択時に対象となる品詞・タグのテキスト制約 |
| `rangeStart` | `1` | Int | 範囲指定開始インデックス番号 |
| `rangeEnd` | `-1` | Int | 範囲指定終了インデックス番号（-1は自動的に全単語数にバインド） |

---

## 6. CSV インポートフォーマット仕様

インポート機能では、ユーザーが様々なCSVツールから書き出して取り込めるようにUTF-8文字エンコード基準で厳格に解析・パーシングを行っています。

- **フォーマット形式**:
  `"英語単語,日本語訳,カテゴリ・タグ"` （品詞・タグはカンマを介して任意で追加可能。空欄でも動作します）。
- **データ不適合に対するフォールバック**:
  - 行が空欄、あるいはカンマがないフォーマット破壊行は警告とともに無視。
  - 改行コード `CRLF` または `LF` の混在に対応。
  - Excelなどで書き出したUTF-8のBOM（Byte Order Mark）を取り除くクリーニング処理。
  - すでに存在する同名称グループへの上書き結合ではなく、インポート毎に新しいユニークグループを作成して既存の統計履歴が汚染されるのを防ぎます。

---

## 7. 将来に向けたPC版（Desktop/Web）移植への技術選定・設計設計

本アプリは、ピュアな Kotlin 記述と、UI定義を抽象的に扱う Jetpack Compose 宣言型コンポーネントで構築されているため、効率よく移植が可能です。PC向けに開発をスケールするアプローチとして以下の2つの有力なオプションを推奨します。

---

### オプションA: Kotlin Multiplatform Context (KMP / Compose Multiplatform)
**Androidの実装コード、並びにクラス・ロジック設計をそのままほぼ100％流用してPC版を最速ビルドしたい場合に最も推奨されます。**

*   **開発可能プラットフォーム**: Windows, macOS, Linux (全て同一コードでカバー)
*   **技術構成**: Kotlin + Jetpack Compose (Desktop向け) + Room KMP + JetBrains KMP
*   **移植への変更設計**:
    - **UIレイヤー**: `MainActivity.kt` の Composable群（`MainAppContent` などのレイアウト構造）は、そのままインポートしてデスクトップウインドウ内に配置可能です。
    - **データレイヤー (Room)**: RoomライブラリはKMPに正式対応しており、`AppDatabase.kt` および `WordDao` はインターフェースを含め完全にそのまま再利用可能です。
    - **効果音シンセサイザー**: JVM / デスクトップで動かすため、Javaの標準ライブラリである `javax.sound.sampled.SourceDataLine` に対してPCMバイトストリームを作成して書き込む処理に変更します。数式や周波数の計算、量子のロジックはAndroid版をそのまま引き継ぐことができます。
    - **TTS読み上げ**: 
      - Windows: SAPI (Speech API) もしくは Java Speech API / OS内蔵の読み上げライブラリをjni/インターフェース呼び出し。
      - macOS: `Say` プロセスもしくは NSSpeechSynthesizer へテキストをフォーク。

---

### オプションB: Webテクノロジースタック (TypeScript + React / Tailwind CSS + Electron)
**将来的にクラウド同期や、ウェブブラウザ版 (Web App) を含むWeb領域へ進出したい場合。また、Webテクノロジに慣れた開発者が作成する場合に推奨されます。**

*   **開発可能プラットフォーム**: デスクトップアプリ (Windows/macOS) および 各種ブラウザ、PWA
*   **技術構成**: TypeScript + React / Webpack + Leaf / Tailwind + Capacitor または Electron (デスクトップラッパー)
*   **移植への変更設計**:
    - **UIレイヤー**: Compose のレイアウト（Row, Column, LazyColumn）を、CSSの FlexBox `flex flex-col` や、仮想スクロールリスト（`React Window`等）に置き換えてスタイリング。
    - **データレイヤー**: ローカルストレージに SQLite（`sql.js` や `SQLite Wasm`）を利用して、Androidと同じリレーショナルスキーマ（`study_groups` と `words`）を保持。
    - **自作シンセサイザー (SoundPlayer等価物)**:
      HTML5の機能である **Web Audio API** (`AudioContext`) を使用して、サイン波・矩形波のオシレーターをJavaScript上で駆動させます。
      ```typescript
      // Web Audio APIによる等価実装例
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const playPiko = (freq: number, duration: number, volume: number) => {
          const osc = ctx.createOscillator();
          const gain = ctx.createGain();
          osc.type = 'sine'; // ピコッ音:サイン波
          osc.frequency.setValueAtTime(freq, ctx.currentTime);
          gain.gain.setValueAtTime(volume * globalVolumeSetting, ctx.currentTime);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start();
          osc.stop(ctx.currentTime + duration);
      };
      ```
    - **TTS読み上げ**: Webブラウザがネイティブ対応している **Web Speech API** (`SpeechSynthesisUtterance`) を使って一瞬で移植可能です。言語・音量（volume）もプロパティを通じてAndroid同等の精密制御が保証されます。
      ```typescript
      const utterance = new SpeechSynthesisUtterance(wordEnglish);
      utterance.lang = 'en-US';
      utterance.volume = ttsVolumeSetting; // 0.0 to 1.0 調整可能
      window.speechSynthesis.speak(utterance);
      ```
    - **アプリ設定**: Webのブラウザ領域にある `localStorage` または IndexedDB をキーバリューとして活用し、設定スキーマ（SharedPreferencesとパラメータ完全一致）を格納。
    - **CSVインポート**: ブラウザに内蔵された `<input type="file">` と JavaScript のパーサー `FileReader` を用いて、テキスト分割 `split(',')` 処理を実行。

---
本ドキュメントを活用することで、Androidを中核とした「Tango pro」の高い機能性とレスポンスを維持したまま、高い相互運用性を持ったPC/Web版を設計・開発することが可能です。
**これをもって「Tango pro - 単語学習アプリ (Android)」の仕様をバージョン1.0として凍結し、正式に開発完了とします。**
