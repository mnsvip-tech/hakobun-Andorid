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
    Morning("午前"),
    Afternoon("14-16時"),
    LateAfternoon("16-18時"),
    Evening("18-20時"),
    Night("19-21時")
}

fun RegistrationTimeWindow.toTimeWindow(): TimeWindow = when (this) {
    RegistrationTimeWindow.None -> TimeWindow.Unspecified
    RegistrationTimeWindow.Morning -> TimeWindow.Morning
    RegistrationTimeWindow.Afternoon,
    RegistrationTimeWindow.LateAfternoon -> TimeWindow.Afternoon
    RegistrationTimeWindow.Evening,
    RegistrationTimeWindow.Night -> TimeWindow.Evening
}

fun TimeWindow.toRegistrationTimeWindow(): RegistrationTimeWindow = when (this) {
    TimeWindow.Morning -> RegistrationTimeWindow.Morning
    TimeWindow.Afternoon -> RegistrationTimeWindow.Afternoon
    TimeWindow.Evening -> RegistrationTimeWindow.Evening
    TimeWindow.All,
    TimeWindow.Unspecified -> RegistrationTimeWindow.None
}

/** 時間帯指定を「開始時, 終了時(24h表記)」に変換。指定なしは null。 */
fun RegistrationTimeWindow.hourRange(): Pair<Int, Int>? = when (this) {
    RegistrationTimeWindow.None -> null
    RegistrationTimeWindow.Morning -> 8 to 12
    RegistrationTimeWindow.Afternoon -> 14 to 16
    RegistrationTimeWindow.LateAfternoon -> 16 to 18
    RegistrationTimeWindow.Evening -> 18 to 20
    RegistrationTimeWindow.Night -> 19 to 21
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

enum class MembershipPlan { Free, Premium }

@Immutable
data class UserProfile(
    val driverName: String = "",
    val base: String = "",
    val contact: String = "",
    val email: String = "",
    val plan: MembershipPlan = MembershipPlan.Free,
    val isRegistered: Boolean = false,
    /** 初回登録日時(epoch millis)。無料トライアル(登録から14日間)の起算点。未登録時は0。 */
    val registeredAt: Long = 0L
)

const val FREE_TRIAL_DAYS = 14

fun UserProfile.isFreeTrialExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!isRegistered) return false
    if (registeredAt <= 0L) return true
    val trialEndMillis = registeredAt + FREE_TRIAL_DAYS * 24L * 60L * 60L * 1000L
    return nowMillis >= trialEndMillis
}

@Immutable
data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val publishedAt: String
)

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
    val deliveryTimeWindow: RegistrationTimeWindow,
    val size: String,
    val colorLabel: String,
    val packageType: String,
    val cod: Boolean,
    val hasLocker: Boolean,
    val memo: String,
    val latitude: Double,
    val longitude: Double,
    val status: DeliveryStatus,
    val nameplate: String = "",
    val packageMemo: String = "",
    val phoneNumber: String = "",
    val shape: PackageShape = PackageShape.SmallBox
)

@Immutable
data class AddressCandidate(
    val sourceLabel: String,
    val postalCode: String,
    val address: String,
    val confidenceLabel: String,
    val recipientName: String = ""
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
    val size: PackageSizeOption,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
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
