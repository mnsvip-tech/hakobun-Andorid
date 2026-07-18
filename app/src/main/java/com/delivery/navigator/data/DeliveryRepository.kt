package com.delivery.navigator.data

import android.content.Intent
import android.provider.CalendarContract
import com.delivery.navigator.model.AddressCandidate
import com.delivery.navigator.model.CourseAddress
import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.EndOfDaySummary
import com.delivery.navigator.model.PackageRegistrationResult
import com.delivery.navigator.model.RegularCourse
import com.delivery.navigator.model.RegistrationTimeWindow
import com.delivery.navigator.model.RouteStop
import com.delivery.navigator.model.TimeWindow
import java.util.Calendar
import java.util.Locale

private const val CURRENT_LATITUDE = 35.681236
private const val CURRENT_LONGITUDE = 139.767125

fun currentRouteOrigin(): Pair<Double, Double> = CURRENT_LATITUDE to CURRENT_LONGITUDE

fun samplePackages(): List<DeliveryPackage> = listOf(
    DeliveryPackage(
        trackingCode = "DA-1028",
        recipient = "山田 太郎",
        address = "東京都千代田区丸の内1-1-1",
        timeWindow = TimeWindow.Morning,
        size = "80",
        colorLabel = "クラフト",
        packageType = "小箱",
        cod = false,
        hasLocker = true,
        memo = "宅配BOXあり。入口は建物右側。",
        latitude = 35.6842,
        longitude = 139.7629,
        status = DeliveryStatus.Pending
    ),
    DeliveryPackage(
        trackingCode = "DA-1041",
        recipient = "佐藤 花子",
        address = "東京都中央区銀座4-5-6",
        timeWindow = TimeWindow.Afternoon,
        size = "60",
        colorLabel = "白",
        packageType = "封筒",
        cod = true,
        hasLocker = false,
        memo = "代引き。インターホン後、管理室側へ。",
        latitude = 35.6717,
        longitude = 139.7650,
        status = DeliveryStatus.InProgress
    ),
    DeliveryPackage(
        trackingCode = "DA-1077",
        recipient = "鈴木 一郎",
        address = "東京都港区芝公園4-2-8",
        timeWindow = TimeWindow.Evening,
        size = "100",
        colorLabel = "冷蔵",
        packageType = "冷蔵",
        cod = false,
        hasLocker = false,
        memo = "不在時は会社へ持ち戻り。置き配不可。",
        latitude = 35.6586,
        longitude = 139.7454,
        status = DeliveryStatus.Absent
    ),
    DeliveryPackage(
        trackingCode = "DA-1099",
        recipient = "田中 二郎",
        address = "東京都台東区浅草2-3-1",
        timeWindow = TimeWindow.Unspecified,
        size = "80",
        colorLabel = "不明",
        packageType = "袋物",
        cod = false,
        hasLocker = false,
        memo = "時間指定なし。地図の時間指定表示では非表示。",
        latitude = 35.7148,
        longitude = 139.7967,
        status = DeliveryStatus.Pending
    )
)

fun regularCourses(): List<RegularCourse> = listOf(
    RegularCourse(
        code = "A",
        displayName = "Aコース",
        addresses = listOf(
            CourseAddress("A-01 佐々木", "東京都千代田区大手町1-1-1", TimeWindow.Morning, "午前指定が多いエリア", 35.6862, 139.7633),
            CourseAddress("A-02 高橋", "東京都中央区日本橋2-2-2", TimeWindow.Afternoon, "宅配BOX確認", 35.6811, 139.7737),
            CourseAddress("A-03 中村", "東京都港区新橋3-3-3", TimeWindow.Unspecified, "定期配送", 35.6663, 139.7580)
        )
    ),
    RegularCourse(
        code = "B",
        displayName = "Bコース",
        addresses = listOf(
            CourseAddress("B-01 小林", "東京都目黒区目黒1-4-1", TimeWindow.Morning, "入口左", 35.6339, 139.7156),
            CourseAddress("B-02 加藤", "東京都渋谷区恵比寿2-5-2", TimeWindow.Evening, "夜間指定", 35.6467, 139.7101)
        )
    ),
    RegularCourse(
        code = "C",
        displayName = "Cコース",
        addresses = listOf(
            CourseAddress("C-01 吉田", "東京都新宿区西新宿3-6-3", TimeWindow.Afternoon, "高層階", 35.6896, 139.6921),
            CourseAddress("C-02 山本", "東京都中野区中野4-7-4", TimeWindow.Unspecified, "管理室あり", 35.7074, 139.6658)
        )
    ),
    RegularCourse(
        code = "D",
        displayName = "Dコース",
        addresses = listOf(
            CourseAddress("D-01 松本", "東京都杉並区高円寺5-8-5", TimeWindow.Morning, "明日想定コース", 35.7056, 139.6497),
            CourseAddress("D-02 井上", "東京都練馬区豊玉6-9-6", TimeWindow.Evening, "再配達注意", 35.7356, 139.6517),
            CourseAddress("D-03 木村", "東京都板橋区板橋7-10-7", TimeWindow.Afternoon, "置き配可", 35.7512, 139.7093)
        )
    )
)

fun createPackagesFromCourse(course: RegularCourse, existingCount: Int): List<DeliveryPackage> {
    return course.addresses.mapIndexed { index, address ->
        val generatedNumber = existingCount + index + 1
        DeliveryPackage(
            trackingCode = "COURSE-${course.code}-$generatedNumber",
            recipient = address.recipient,
            address = address.address,
            timeWindow = address.timeWindow,
            size = "80",
            colorLabel = course.displayName,
            packageType = "定期",
            cod = false,
            hasLocker = false,
            memo = address.memo,
            latitude = address.latitude,
            longitude = address.longitude,
            status = DeliveryStatus.Pending
        )
    }
}

suspend fun createCourseAddressFromInput(
    recipient: String,
    address: String,
    timeWindow: TimeWindow,
    memo: String
): CourseAddress {
    val (lat, lng) = geocodeAddress(address)
    return CourseAddress(
        recipient = recipient.ifBlank { "届け先未入力" },
        address = address,
        timeWindow = timeWindow,
        memo = memo.ifBlank { "定期コース登録" },
        latitude = lat,
        longitude = lng
    )
}

suspend fun createDeliveryPackageFromRegistration(
    result: PackageRegistrationResult,
    existingCount: Int
): DeliveryPackage {
    val generatedNumber = existingCount + 1
    val memo = listOf(result.nameplate, result.deliveryMemo, result.packageMemo)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { "登録画面から追加" }
    val (lat, lng) = geocodeAddress(result.address)

    return DeliveryPackage(
        trackingCode = result.trackingNumber.ifBlank { "HB-${1000 + generatedNumber}" },
        recipient = result.recipientName.ifBlank { "届け先未入力" },
        address = result.address,
        timeWindow = result.timeWindow.toRouteTimeWindow(),
        size = result.size.label,
        colorLabel = result.colorType.label,
        packageType = result.shape.label,
        cod = false,
        hasLocker = result.hasLocker,
        memo = memo,
        latitude = lat,
        longitude = lng,
        status = result.status
    )
}

private suspend fun geocodeAddress(address: String): Pair<Double, Double> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(address, "UTF-8")
            val apiKey = com.delivery.navigator.BuildConfig.MAPS_API_KEY
            val url = java.net.URL(
                "https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&language=ja&region=JP&key=$apiKey"
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val root = org.json.JSONObject(json)
            val results = root.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val location = results.getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location")
                Pair(location.getDouble("lat"), location.getDouble("lng"))
            } else {
                prefectureFallback(address)
            }
        }.getOrElse { prefectureFallback(address) }
    }
}

private fun prefectureFallback(address: String): Pair<Double, Double> {
    val prefectureCoords = mapOf(
        "北海道" to Pair(43.06, 141.35), "青森" to Pair(40.82, 140.74), "岩手" to Pair(39.70, 141.15),
        "宮城" to Pair(38.27, 140.87), "秋田" to Pair(39.72, 140.10), "山形" to Pair(38.24, 140.36),
        "福島" to Pair(37.75, 140.47), "茨城" to Pair(36.34, 140.45), "栃木" to Pair(36.57, 139.88),
        "群馬" to Pair(36.39, 139.06), "埼玉" to Pair(35.86, 139.65), "千葉" to Pair(35.61, 140.12),
        "東京" to Pair(35.69, 139.69), "神奈川" to Pair(35.45, 139.64), "新潟" to Pair(37.90, 139.02),
        "富山" to Pair(36.70, 137.21), "石川" to Pair(36.59, 136.63), "福井" to Pair(36.07, 136.22),
        "山梨" to Pair(35.66, 138.57), "長野" to Pair(36.65, 138.18), "岐阜" to Pair(35.39, 136.72),
        "静岡" to Pair(34.98, 138.38), "愛知" to Pair(35.18, 136.91), "三重" to Pair(34.73, 136.51),
        "滋賀" to Pair(35.00, 135.87), "京都" to Pair(35.02, 135.76), "大阪" to Pair(34.69, 135.50),
        "兵庫" to Pair(34.69, 135.18), "奈良" to Pair(34.69, 135.83), "和歌山" to Pair(34.23, 135.17),
        "鳥取" to Pair(35.50, 134.24), "島根" to Pair(35.47, 133.06), "岡山" to Pair(34.66, 133.93),
        "広島" to Pair(34.40, 132.46), "山口" to Pair(34.19, 131.47), "徳島" to Pair(34.07, 134.56),
        "香川" to Pair(34.34, 134.04), "愛媛" to Pair(33.84, 132.77), "高知" to Pair(33.56, 133.53),
        "福岡" to Pair(33.61, 130.42), "佐賀" to Pair(33.25, 130.30), "長崎" to Pair(32.74, 129.87),
        "熊本" to Pair(32.79, 130.74), "大分" to Pair(33.24, 131.61), "宮崎" to Pair(31.91, 131.42),
        "鹿児島" to Pair(31.56, 130.56), "沖縄" to Pair(26.21, 127.68)
    )
    return prefectureCoords.entries.firstOrNull { address.contains(it.key) }?.value
        ?: Pair(35.69, 139.69)
}

suspend fun searchPostalCode(postalCode: String): AddressCandidate {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val url = java.net.URL("https://zipcloud.ibsnet.co.jp/api/search?zipcode=$postalCode")
            val json = url.readText(Charsets.UTF_8)
            val root = org.json.JSONObject(json)
            val results = root.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val item = results.getJSONObject(0)
                val address = "${item.optString("address1")}${item.optString("address2")}${item.optString("address3")}"
                AddressCandidate(
                    sourceLabel = "郵便番号検索",
                    postalCode = postalCode,
                    address = address,
                    confidenceLabel = "一致"
                )
            } else {
                AddressCandidate(
                    sourceLabel = "郵便番号検索",
                    postalCode = postalCode,
                    address = "該当する住所が見つかりません",
                    confidenceLabel = "未確認"
                )
            }
        }.getOrElse {
            AddressCandidate(
                sourceLabel = "郵便番号検索",
                postalCode = postalCode,
                address = "通信エラーが発生しました",
                confidenceLabel = "エラー"
            )
        }
    }
}

fun exportPackages(packages: List<DeliveryPackage>): String {
    return packages.joinToString(separator = "\n") { item ->
        listOf(
            item.trackingCode,
            item.recipient,
            item.address,
            item.timeWindow.label,
            item.memo
        ).joinToString(",") { value -> value.replace(",", " ") }
    }
}

fun importPackages(source: String, existingCount: Int): List<DeliveryPackage> {
    return source
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexedNotNull { index, line ->
            val parts = line.split(",").map { it.trim() }
            if (parts.size < 3) {
                null
            } else {
                val generatedNumber = existingCount + index + 1
                DeliveryPackage(
                    trackingCode = parts.getOrNull(0).orEmpty().ifBlank { "IMPORT-${1000 + generatedNumber}" },
                    recipient = parts.getOrNull(1).orEmpty().ifBlank { "インポート先" },
                    address = parts.getOrNull(2).orEmpty(),
                    timeWindow = parseTimeWindow(parts.getOrNull(3).orEmpty()),
                    size = "80",
                    colorLabel = "インポート",
                    packageType = "住所データ",
                    cod = false,
                    hasLocker = false,
                    memo = parts.getOrNull(4).orEmpty(),
                    latitude = 35.65 + ((generatedNumber * 19) % 60) / 1000.0,
                    longitude = 139.70 + ((generatedNumber * 29) % 90) / 1000.0,
                    status = DeliveryStatus.Pending
                )
            }
        }
        .toList()
}

fun calculateNearestRoute(packages: List<DeliveryPackage>): List<RouteStop> {
    val remaining = packages.toMutableList()
    val ordered = mutableListOf<RouteStop>()
    var currentLatitude = CURRENT_LATITUDE
    var currentLongitude = CURRENT_LONGITUDE

    while (remaining.isNotEmpty()) {
        val next = remaining.minBy { item ->
            val latitudeDiff = item.latitude - currentLatitude
            val longitudeDiff = item.longitude - currentLongitude
            latitudeDiff * latitudeDiff + longitudeDiff * longitudeDiff
        }
        remaining.remove(next)
        ordered.add(RouteStop(next, ordered.size + 1))
        currentLatitude = next.latitude
        currentLongitude = next.longitude
    }

    return ordered
}

suspend fun fetchDrivingRoutePoints(
    origin: Pair<Double, Double>,
    destination: Pair<Double, Double>
): List<Pair<Double, Double>> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val apiKey = com.delivery.navigator.BuildConfig.MAPS_API_KEY
            if (apiKey.isBlank()) return@runCatching emptyList()
            val url = java.net.URL(
                "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origin.first},${origin.second}" +
                    "&destination=${destination.first},${destination.second}" +
                    "&mode=driving&language=ja&region=JP&key=$apiKey"
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val root = org.json.JSONObject(json)
            val routes = root.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val encoded = routes
                    .getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .optString("points")
                decodePolyline(encoded)
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }
    }
}

private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()
    var index = 0
    var latitude = 0
    var longitude = 0

    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var byte: Int
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20 && index < encoded.length)
        latitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

        result = 0
        shift = 0
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20 && index < encoded.length)
        longitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

        points.add(Pair(latitude / 100000.0, longitude / 100000.0))
    }

    return points
}

fun calculateEndOfDaySummary(packages: List<DeliveryPackage>): EndOfDaySummary {
    val delivered = packages.count { it.status == DeliveryStatus.Completed }
    val returned = packages.count { it.status == DeliveryStatus.ReturnToCompany }
    return EndOfDaySummary(
        deliveredStops = delivered,
        deliveredPackages = delivered,
        returnedPackages = returned
    )
}

fun createEndOfDayCalendarIntent(summary: EndOfDaySummary): Intent {
    val todayStart = Calendar.getInstance(Locale.JAPAN).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val tomorrowStart = todayStart + 24 * 60 * 60 * 1000
    val description = """
        配達件数: ${summary.deliveredStops}
        配達個数: ${summary.deliveredPackages}
        持ち戻り個数: ${summary.returnedPackages}
    """.trimIndent()

    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        type = "vnd.android.cursor.item/event"
        putExtra(CalendarContract.Events.TITLE, "HAKOBUN 本日の配送実績")
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(Intent.EXTRA_TEXT, description)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, todayStart)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, tomorrowStart)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    }
}

fun DeliveryStatus.hidesMapPin(): Boolean {
    return this == DeliveryStatus.Completed || this == DeliveryStatus.ReturnToCompany
}

private fun RegistrationTimeWindow.toRouteTimeWindow(): TimeWindow {
    return when (this) {
        RegistrationTimeWindow.None -> TimeWindow.Unspecified
        RegistrationTimeWindow.Morning -> TimeWindow.Morning
        RegistrationTimeWindow.Noon,
        RegistrationTimeWindow.Afternoon,
        RegistrationTimeWindow.LateAfternoon -> TimeWindow.Afternoon
        RegistrationTimeWindow.Evening,
        RegistrationTimeWindow.Night,
        RegistrationTimeWindow.LateNight -> TimeWindow.Evening
    }
}

private fun parseTimeWindow(value: String): TimeWindow {
    return when (value) {
        TimeWindow.Morning.label, "午前中" -> TimeWindow.Morning
        TimeWindow.Afternoon.label, "12-14時", "14-16時", "16-18時" -> TimeWindow.Afternoon
        TimeWindow.Evening.label, "18-20時", "19-21時", "20-21時" -> TimeWindow.Evening
        else -> TimeWindow.Unspecified
    }
}
