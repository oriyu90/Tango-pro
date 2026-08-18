# Tango pro紹介サイト QA（v2.0.0）

実施日: 2026-08-18

## 対象

- `index.html`
- `tokens.css`
- `assets/site.css`
- `assets/og.png`（1200×630）
- `robots.txt`
- `sitemap.xml`

追加導線:

- ページ内目次（機能、CSV、ダウンロード、インストール、問い合わせ）
- Android／macOS別インストール手順

## 構文・意味構造

- `html-validate`: errors 0 / warnings 0
- `csstree-validator`: `assets/site.css` / `tokens.css` ともにエラーなし
- `xmllint`: `sitemap.xml`妥当
- JSON-LDをJSONとして解析し、`SoftwareApplication` / version `2.0.0`を確認
- ページ内リンクの参照先ID、画像の`src` / `alt`、ローカル静的アセットの存在を確認

## レスポンシブ実表示

ローカルHTTP配信をブラウザで開き、以下の幅で実表示を確認した。

| viewport | 横スクロール | 画面外要素 | 主要リンク改行 | 画像 | Webフォント |
| --- | --- | --- | --- | --- | --- |
| 320×640 | なし | なし | なし | 正常 | loaded |
| 375×667 | なし | なし | なし | 正常 | loaded |
| 414×896 | なし | なし | なし | 正常 | loaded |
| 768×1024 | なし | なし | なし | 正常 | loaded |
| 1280×800 | なし | なし | なし | 正常 | loaded |
| 1440×900 | なし | なし | なし | 正常 | loaded |

1280×800では、ヒーローの見出し、説明、主要CTA、アプリアイコンがスクロール前の範囲に収まることを確認した。ページ内ナビゲーション「できること」から`#features`へ移動でき、対象見出しが表示される。ブラウザコンソールのerror / warningは0件。

実表示テストで検出した画像の縦横比固定と日本語見出しの不自然な改行を修正し、全viewportで再確認した。

インストール案内追加後も320／375／414／768／1280pxで再検証し、横スクロール、画面外要素、画像エラー、コンソールerror / warningがないことを確認した。目次の「インストール」から`#install`へ移動し、対象見出しが表示される。狭幅で補足リンクが改行し得る状態を修正し、320pxで主要操作リンクが1行に収まることを再確認した。

## アクセシビリティ・デザイン

- `lang="ja"`、スキップリンク、セマンティック見出し、ランドマーク、フォーカスリングを実装
- `prefers-reduced-motion`と`prefers-contrast`へ対応
- ダーク背景用フォーカス色を分離
- 本文／背景、補助文／背景、ボタン、フォーカスリングのコントラストを確認
- Hallmark 58-gate slop test: 58 / 58 pass
- 生成画像はOG兼学習イメージ1点のみ。架空の評価、利用者数、推薦コメントは不使用

主なWCAGコントラスト比:

| 組み合わせ | 比率 |
| --- | ---: |
| ink / paper | 17.28:1 |
| ink-soft / paper | 11.69:1 |
| paper / ink | 18.31:1 |
| gold / ink | 7.53:1 |
| gold-deep / paper | 4.71:1 |
| focus / paper | 6.44:1 |

## 検索・共有

- 一意で簡潔な`title`、meta description、canonicalを設定
- Open Graph / Xカードと1200×630画像を設定
- `SoftwareApplication` JSON-LDへOS、バージョン、開発者、価格、ライセンスを設定
- `robots.txt`と`sitemap.xml`を追加
- APK、DMG、Discord、X、公式サイト、GitHub ReleaseのリンクがHTTP 200へ到達することを確認
- GoogleのAndroid安全案内、AppleのGatekeeper案内、SHA-256チェックサムへのリンクがHTTP 200へ到達することを確認

Google Search Consoleへのプロパティ登録とサイトマップ送信は、GitHub Pages公開後に所有者アカウントから行う。
