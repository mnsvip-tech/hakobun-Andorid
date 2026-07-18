# CLAUDE.md — HAKOBUN Android プロジェクト開発ガイドライン

> このファイルはClaudeが本プロジェクトで作業する際に自動的に読み込まれます。
> 元定義: `Agents.md`

---

## プロジェクト基本情報

- **アプリ名**: HAKOBUN（配送ドライバー向け配達管理アプリ）
- **言語 / フレームワーク**: Kotlin / Jetpack Compose
- **namespace**: `com.delivery.navigator`
- **作業ルート**: `C:\Users\mnsvi\OneDrive\デスクトップ\Mobileアプリ\Android`

---

## UI・レイアウト設計

- 新規画面は**すべて Jetpack Compose**で構築する。
- XML レイアウトは作成しない。既存 XML がある場合は `AndroidView` / `ComposeView` を経由して段階的に Compose へ移行する。
- `@Composable` 関数はステートレスに保ち、状態は `HakobunApp` などの上位コンポーザブルで一元管理する。

---

## パフォーマンス最適化

- **不要な Recomposition の防止**
  - 引数に渡すデータクラスには `@Immutable` または `@Stable` アノテーションを付与する。
  - lambda はできる限り `remember` でキャプチャして再生成を避ける。
  - `LaunchedEffect` のキーはリストより `size` や `id` など軽量な値を使う。
- **メモリ管理**
  - `CoroutineScope` は `rememberCoroutineScope()` または `viewModelScope` に紐付ける。
  - `SnapshotStateList` の変更は必要最小限に留め、差分更新を優先する。

---

## Google Play ポリシー準拠 (必須チェック)

| 項目 | ルール |
|:--|:--|
| **プライバシー / データセキュリティ** | 個人情報・デバイスデータは HTTPS/TLS + AES-256 で保護。SharedPreferences に個人情報を平文保存しない。 |
| **パーミッション最小化** | 実行時パーミッションはコア機能に必要な最小限のみ要求。バックグラウンド位置情報・全ストレージアクセスは原則要求しない。 |
| **SDK 安全性** | 外部ライブラリ追加時は Google Play ポリシー違反（不審なデータ収集・難読化コード実行）がないか確認する。 |
| **targetSdk** | `build.gradle.kts` の `targetSdk` は Google Play 要件の最新 API レベルに追従する（現在: 36）。 |

---

## エージェント作業ワークフロー

1. コード修正前に潜在的バグ・非推奨 API を確認する。
2. Agents.md のガイドラインに従い、セキュアでクリーンなコードを生成する。
3. 変更後は「影響範囲」「変更した依存関係」をコミットメッセージまたは報告に含める。
4. パーミッションやネットワーク接続を変更した場合は必ず Play ポリシーとの整合性をセルフチェックする。

---

## ディレクトリ構成

```
app/src/main/java/com/delivery/navigator/
├── MainActivity.kt
├── data/
│   ├── DeliveryRepository.kt   # ビジネスロジック・サンプルデータ
│   └── LocalDeliveryStore.kt   # SharedPreferences 永続化
├── model/
│   └── DeliveryModels.kt       # データモデル・Enum 定義
├── ui/
│   └── HakobunApp.kt           # メイン Composable ルート
├── address/
│   └── AddressInputClient.kt
└── map/
    ├── MapProvider.kt
    ├── GoogleMapsProvider.kt
    └── ZenrinMapProvider.kt
```
