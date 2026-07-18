package com.delivery.navigator.model

import androidx.compose.runtime.Immutable

enum class DeliveryStatus(val label: String) {
    Pending("未配達"),
    InProgress("配達中"),
    Completed("完了"),
    Absent("不在"),
    Redelivery("再配達"),
    ReturnToCompany("会社へ持ち戻り")
}

enum class TimeWindow(val label: String) {
    All("すべて"),
    Unspecified("なし"),
    Morning("午前"),
    Afternoon("午後"),
    Evening("夜間")
}

enum class RegistrationTimeWindow(val label: String) {
    None("なし"),
    Morning("午前中"),
    Noon("12-14時"),
    Afternoon("14-16時"),
    LateAfternoon("16-18時"),
    Evening("18-20時"),
    Night("19-21時"),
    LateNight("20-21時")
}

enum class PackageShape(val label: String, val symbol: String) {
    SmallBox("小箱", "□"),
    Envelope("封筒", "▭"),
    SoftBag("袋物", "◒"),
    LargeBox("大型", "▣"),
    Refrigerated("冷蔵", "❄"),
    Fragile("割物", "!")
}

enum class PackageColorType(val label: String) {
    Kraft("クラフト"),
    White("白"),
    Black("黒"),
    Colorful("色柄"),
    Cool("クール"),
    Unknown("不明")
}

enum class PackageSizeOption(val label: String) {
    Small("60"),
    Medium("80"),
    Large("100"),
    ExtraLarge("120+")
}

enum class AccountMenuItem(val title: String, val description: String) {
    Feedback("ユーザーからのフィードバック", "改善要望や不具合報告を運営へ送信"),
    Announcements("運営側からのお知らせ", "重要なお知らせ・メンテナンス情報"),
    Account("ユーザーアカウント情報", "ドライバー名・拠点・連絡先を確認"),
    Settings("設定項目", "地図・通知・表示の設定"),
    Help("ヘルプ問い合わせ", "使い方確認と問い合わせ導線"),
    Terms("ご利用規約", "利用条件とプライバシー方針")
}

@Immutable
data class DeliveryPackage(
    val trackingCode: String,
    val recipient: String,
    val address: String,
    val timeWindow: TimeWindow,
    val size: String,
    val colorLabel: String,
    val packageType: String,
    val cod: Boolean,
    val hasLocker: Boolean,
    val memo: String,
    val latitude: Double,
    val longitude: Double,
    val status: DeliveryStatus
)

@Immutable
data class AddressCandidate(
    val sourceLabel: String,
    val postalCode: String,
    val address: String,
    val confidenceLabel: String
)

@Immutable
data class PackageRegistrationResult(
    val address: String,
    val hasLocker: Boolean,
    val nameplate: String,
    val deliveryMemo: String,
    val packageMemo: String,
    val trackingNumber: String,
    val recipientName: String,
    val phoneNumber: String,
    val status: DeliveryStatus,
    val timeWindow: RegistrationTimeWindow,
    val shape: PackageShape,
    val colorType: PackageColorType,
    val size: PackageSizeOption
)

@Immutable
data class RouteStop(
    val deliveryPackage: DeliveryPackage,
    val routeNumber: Int
)

@Immutable
data class EndOfDaySummary(
    val deliveredStops: Int,
    val deliveredPackages: Int,
    val returnedPackages: Int
)

@Immutable
data class RegularCourse(
    val code: String,
    val displayName: String,
    val addresses: List<CourseAddress>
)

@Immutable
data class CourseAddress(
    val recipient: String,
    val address: String,
    val timeWindow: TimeWindow,
    val memo: String,
    val latitude: Double,
    val longitude: Double
)
