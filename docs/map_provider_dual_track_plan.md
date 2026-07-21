# GoogleMap / ZENRIN 2系統プラン設計（将来実装用）

> ステータス: 設計確定・未実装（2026-07-21時点）
> 実装着手時はこのドキュメントを起点にすること。

## 背景

- 現状、地図表示は `com.google.maps.android.compose.GoogleMap` を直接呼んでおり、
  `map/MapProvider.kt` に抽象化された `DeliveryMapProvider`（`GoogleMapsDeliveryMapProvider` /
  `ZenrinDeliveryMapProvider`）はデータ取得系のみで、UI描画とは未接続。
- 料金体系を変更し、GoogleMapユーザーは安価、ZENRINユーザーは割高のプランとする方針。

## データモデル

`plan`（Free/Premium）と `mapProvider`（GoogleMaps/Zenrin）は独立した2軸として持つ
（掛け合わせ列挙にしない — 将来プラン種別が増えたときの組み合わせ爆発を避けるため）。

```kotlin
// DeliveryModels.kt
enum class MembershipPlan { Free, Premium }
enum class MapProviderType { GoogleMaps, Zenrin } // 既存 map/MapProvider.kt と共通化

data class UserProfile(
    ...
    val plan: MembershipPlan = MembershipPlan.Free,
    val mapProvider: MapProviderType = MapProviderType.GoogleMaps,
)
```

`LocalDeliveryStore.kt`: `loadUserProfile()` に `mapProvider` のJSONキーを追加。
キー欠損時（既存ユーザーのマイグレーション）は `GoogleMaps` にフォールバック。

## Play Billing 商品構成

現行の単一 `SUBSCRIPTION_PRODUCT_ID` を2商品に分割。

```kotlin
const val SUBSCRIPTION_PRODUCT_ID_GOOGLE = "hakobun_premium_google"  // 安価
const val SUBSCRIPTION_PRODUCT_ID_ZENRIN = "hakobun_premium_zenrin"  // 割高
```

Google Play Console側で別商品として登録（価格体系が大きく異なるため base plan 化はしない）。

## プラン切り替えフロー

- **Free → Premium(Google/Zenrin)**: 既存 `launchSubscription()` を商品ID引数化し、
  選択されたプランで `launchBillingFlow` を呼ぶだけ。
- **Premium(Google) ⇄ Premium(Zenrin) の乗り換え**:
  `BillingFlowParams.SubscriptionUpdateParams` を使用。

  ```kotlin
  val flowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(listOf(newProductDetailsParams))
      .setSubscriptionUpdateParams(
          BillingFlowParams.SubscriptionUpdateParams.newBuilder()
              .setOldPurchaseToken(currentPurchaseToken)
              .setSubscriptionReplacementMode(
                  BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
              )
              .build()
      )
      .build()
  ```

### 決定済み方針

- **精算タイミング: `DEFERRED`（次回更新時に切替）**
  現在の請求サイクル終了まで旧プランを維持し、次回更新日に新プランへ切替。
  即時日割り精算（`CHARGE_PRORATED_PRICE`）は会計処理が複雑になるため不採用。
  - UI上は「次回更新日(YYYY-MM-DD)からZenrinプランに切り替わります」という
    予約状態の表示が必要。DEFERREDは`purchases`へ即時反映されないため、
    `reconcileSubscriptionStatus()` は実際の更新日までは現行プランを維持し続ける。
- **Free降格時: `mapProvider` を強制的に `GoogleMaps` へリセット**
  Zenrinは常にPremium限定機能として扱い、無料版での誤動作リスクを避ける。
  再度Premium加入時に前回選択（Zenrin）を復元する仕組みは持たない。

## `reconcileSubscriptionStatus()` の変更

2商品どちらが有効かを見て `plan` と `mapProvider` を同時に確定させる。

```kotlin
val activeGoogle = purchases.firstOrNull {
    it.products.contains(SUBSCRIPTION_PRODUCT_ID_GOOGLE) && it.purchaseState == PURCHASED
}
val activeZenrin = purchases.firstOrNull {
    it.products.contains(SUBSCRIPTION_PRODUCT_ID_ZENRIN) && it.purchaseState == PURCHASED
}
val updated = when {
    activeZenrin != null -> userProfile.copy(plan = Premium, mapProvider = Zenrin)
    activeGoogle != null -> userProfile.copy(plan = Premium, mapProvider = GoogleMaps)
    else -> userProfile.copy(plan = Free, mapProvider = GoogleMaps) // Free降格時は強制リセット
}
```

`QueryPurchasesParams(ProductType.SUBS)` は両商品含めて1回のクエリで取得可能。

## UI

- `DeliveryMapProvider` の選択は `remember(userProfile.mapProvider, userProfile.plan)` で
  `GoogleMapsDeliveryMapProvider()` / `ZenrinDeliveryMapProvider()` を切り替え、地図描画部にDI。
- 設定画面に現行プラン表示＋「プラン変更」ボタンを追加。
  乗り換え時は `SubscriptionUpdateParams` 経由で切替予約を行う。

## 未着手の前提作業（この設計とは別に必要）

1. `HakobunApp.kt` 内の `GoogleMap(...)` 直呼び出し（3箇所）を、
   `DeliveryMapProvider` 経由で描画コンポーネントを差し替え可能にする層への置き換え。
2. ZENRIN地図SDKの法人契約・導入、および `ZenrinAddressSearchClient` /
   `ZenrinPostalCodeAddressClient` / `ZenrinRouteNavigator` の実装（現状すべて空実装）。
3. Google Play Consoleでの2商品登録・価格設定。

## 実装着手時の推奨順序

1. `DeliveryModels.kt`: `mapProvider` フィールド追加
2. `LocalDeliveryStore.kt`: 永続化・マイグレーション対応
3. Billing商品ID分割・`reconcileSubscriptionStatus()` 改修
4. 設定UIでのプラン変更導線
5. 地図描画層の `DeliveryMapProvider` 経由化（ZENRIN SDK導入と合わせて）
