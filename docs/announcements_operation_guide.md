# 運営お知らせ 運用マニュアル

HAKOBUNアプリ内「運営側からのお知らせ」画面は、アプリの再ビルド・再リリースなしで
文言を更新できます。`docs/announcements.json` を書き換えてGitHubへpushするだけです。

## 全体の流れ

```
運営が編集ページで入力
        ↓
announcements.json をダウンロード
        ↓
docs/announcements.json を上書き
        ↓
git commit & push
        ↓
アプリが起動時に自動取得 → ユーザーに表示
```

## 手順1: お知らせを編集する

1. `announcements_editor.html` をエクスプローラーからダブルクリックしてブラウザで開く
   - デスクトップ直下のコピー: `C:\Users\mnsvi\OneDrive\デスクトップ\Mobileアプリ\announcements_editor.html`
   - リポジトリ内の正本: `docs/announcements_editor.html`
2. 「**announcements.jsonを読み込む**」ボタンを押し、`docs/announcements.json` を選択
   - 現在配信中のお知らせ一覧がカード形式で表示される
3. 内容を編集:
   - **既存のお知らせを直す** → 該当カードのタイトル/本文を書き換える
   - **新しいお知らせを追加** → 「＋ お知らせを追加」→ ID・タイトル・本文・公開日を入力
   - **古いお知らせを消す** → カード内の「このお知らせを削除」

   | 項目 | 入力例 |
   |---|---|
   | ID | `2026-08-01-maintenance`（英数字・ハイフンのみ、重複しないように） |
   | タイトル | `メンテナンスのお知らせ` |
   | 本文 | `8/1 2:00〜4:00にシステムメンテナンスを行います。` |
   | 公開日 | `2026-08-01` |

4. 画面下部の「生成されるJSON」プレビューで内容を確認

## 手順2: ファイルを反映する

1. 「**announcements.json をダウンロード**」を押す
2. ダウンロードされたファイルを、リポジトリの `docs/announcements.json` に**上書きコピー**する
   （エクスプローラーでダウンロードフォルダから該当パスへドラッグ＆ドロップでOK）

## 手順3: GitHubへ反映する

PowerShellで以下を実行する。

```powershell
Set-Location "C:\Users\mnsvi\OneDrive\デスクトップ\Mobileアプリ\Android"
git add docs/announcements.json
git commit -m "お知らせ更新: <内容の要約>"
git push
```

pushが完了した時点で配信完了。**アプリの再ビルド・再リリースは不要。**

## ユーザー側の反映タイミング

- ユーザーがアプリ内メニューの「運営側からのお知らせ」画面を**開いたとき**に、
  `docs/announcements.json` を都度fetchする
- 取得に失敗した場合（オフライン等）は前回キャッシュを表示、キャッシュも無ければ
  既定の案内文言を表示するため、配信ミスでアプリが壊れることはない

## 注意点

- **JSONの形式を崩さない**: 編集ページを使わず直接JSONを手書きする場合は、
  配列 `[ {...}, {...} ]` の構文エラーに注意（カンマの付け忘れなど）。
  壊れたJSONの場合はアプリ側が自動的に前回表示にフォールバックする。
- **IDは重複させない**: 将来「既読管理」などを追加する場合の一意キーとして使う想定。
- **公開リポジトリ**: `hakobun-Andorid` はPublicなので、announcements.jsonの内容は
  誰でも閲覧可能。機密情報は書かないこと。

## 関連ファイル

- `docs/announcements.json` — 配信データ本体
- `docs/announcements_editor.html` — 編集ツール（リポジトリ内の正本）
- `app/src/main/java/com/delivery/navigator/data/DeliveryRepository.kt` の
  `fetchAnnouncements()` — アプリ側の取得処理
- `app/src/main/java/com/delivery/navigator/data/LocalDeliveryStore.kt` の
  `loadAnnouncements()` / `saveAnnouncements()` — オフラインキャッシュ処理
