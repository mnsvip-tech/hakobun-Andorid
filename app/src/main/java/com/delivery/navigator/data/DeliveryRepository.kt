package com.delivery.navigator.data

import android.content.Intent
import android.provider.CalendarContract
import com.delivery.navigator.model.AddressCandidate
import com.delivery.navigator.model.Announcement
import com.delivery.navigator.model.CourseAddress
import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.EndOfDaySummary
import com.delivery.navigator.model.PackageRegistrationResult
import com.delivery.navigator.model.RegularCourse
import com.delivery.navigator.model.RegistrationTimeWindow
import com.delivery.navigator.model.RouteStop
import com.delivery.navigator.model.TimeWindow
import com.delivery.navigator.model.hourRange
import com.delivery.navigator.model.toRegistrationTimeWindow
import com.delivery.navigator.model.toTimeWindow
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val CURRENT_LATITUDE = 35.681236
private const val CURRENT_LONGITUDE = 139.767125

fun currentRouteOrigin(): Pair<Double, Double> = CURRENT_LATITUDE to CURRENT_LONGITUDE

/**
 * 実機テスト用: 奈良県内の各市町村中心地の周辺約1.5km圏内にダミー荷物をcount件散らして生成する。
 * 住所の市町村名と生成座標を対応させることで、住所とピン位置の乖離を防ぐ。
 * 時間指定・配達ステータスはランダム化し、ピン表示・ルート計算・ステータス更新の実機検証に使う。
 */
fun generateDebugTestPackages(count: Int, seed: Long = System.currentTimeMillis()): List<DeliveryPackage> {
    val random = kotlin.random.Random(seed)
    // 地名と実際のおおよその中心座標(緯度, 経度)を対応させる
    val wards = listOf(
        Triple("奈良市登大路町", 34.6853, 135.8329),
        Triple("奈良市二条大路南", 34.6851, 135.8050),
        Triple("奈良市学園南", 34.6706, 135.7644),
        Triple("大和郡山市北郡山町", 34.6494, 135.7827),
        Triple("生駒市俵口町", 34.6912, 135.7011),
        Triple("生駒市谷田町", 34.6805, 135.6970),
        Triple("橿原市八木町", 34.5033, 135.7906),
        Triple("橿原市畝傍町", 34.4833, 135.7900),
        Triple("天理市川原城町", 34.5966, 135.8374),
        Triple("桜井市粟殿", 34.5150, 135.8467),
        Triple("香芝市関屋", 34.5350, 135.6983),
        Triple("大和高田市大中", 34.5150, 135.7365),
        Triple("御所市中央町", 34.4633, 135.7417),
        Triple("五條市新町", 34.3609, 135.6989),
        Triple("宇陀市榛原区", 34.5717, 135.9250),
        Triple("葛城市新庄", 34.5033, 135.7167),
        Triple("北葛城郡王寺町", 34.6014, 135.7017),
        Triple("北葛城郡広陵町", 34.5450, 135.7217),
        Triple("磯城郡田原本町", 34.5567, 135.7967),
        Triple("山辺郡山添村", 34.6417, 135.9750),
        Triple("生駒郡斑鳩町", 34.6136, 135.7317),
        Triple("生駒郡三郷町", 34.6167, 135.6853),
        Triple("高市郡明日香村", 34.4667, 135.8183),
        Triple("吉野郡吉野町", 34.4372, 135.8611)
    )
    val surnames = listOf("山田", "佐藤", "鈴木", "田中", "高橋", "伊藤", "渡辺", "中村", "小林", "加藤")
    val givenNames = listOf("太郎", "花子", "一郎", "美咲", "健太", "由美", "翔太", "さくら", "大輔", "愛子")

    return (1..count).map { index ->
        val (ward, baseLat, baseLng) = wards.random(random)
        val recipient = "${surnames.random(random)} ${givenNames.random(random)}"
        // 各地区の中心座標から半径約1km圏内に散らす(緯度1度≒111km, 経度1度≒91km@奈良緯度)
        val radiusKm = sqrt(random.nextDouble()) * 1.0
        val angle = random.nextDouble() * 2 * Math.PI
        val lat = baseLat + (radiusKm * kotlin.math.cos(angle)) / 111.0
        val lng = baseLng + (radiusKm * kotlin.math.sin(angle)) / 91.0
        val timeWindow = RegistrationTimeWindow.entries.random(random)
        val status = DeliveryStatus.entries.random(random)
        DeliveryPackage(
            trackingCode = "TEST-${1000 + index}",
            recipient = recipient,
            address = "奈良県$ward${random.nextInt(1, 9)}-${random.nextInt(1, 30)}-${random.nextInt(1, 20)}",
            timeWindow = timeWindow.toTimeWindow(),
            deliveryTimeWindow = timeWindow,
            size = "80",
            colorLabel = "テスト",
            packageType = "テストデータ",
            cod = random.nextBoolean(),
            hasLocker = random.nextBoolean(),
            memo = "実機テスト用ダミーデータ #$index",
            latitude = lat,
            longitude = lng,
            status = status,
            nameplate = recipient,
            packageMemo = "",
            phoneNumber = "090-0000-${(1000 + index).toString().padStart(4, '0')}",
            shape = com.delivery.navigator.model.PackageShape.entries.random(random)
        )
    }
}

fun samplePackages(): List<DeliveryPackage> = listOf(
    DeliveryPackage(
        trackingCode = "DA-1028",
        recipient = "山田 太郎",
        address = "東京都千代田区丸の内1-1-1",
        timeWindow = TimeWindow.Morning,
        deliveryTimeWindow = RegistrationTimeWindow.Morning,
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
        deliveryTimeWindow = RegistrationTimeWindow.Afternoon,
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
        deliveryTimeWindow = RegistrationTimeWindow.Evening,
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
        deliveryTimeWindow = RegistrationTimeWindow.None,
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

fun defaultRegularCourses(): List<RegularCourse> = listOf("A", "B", "C", "D").map { code ->
    RegularCourse(code = code, displayName = "${code}コース", addresses = emptyList())
}

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
            deliveryTimeWindow = address.timeWindow.toRegistrationTimeWindow(),
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
    context: android.content.Context,
    recipient: String,
    address: String,
    timeWindow: TimeWindow,
    memo: String
): CourseAddress {
    val (lat, lng) = geocodeAddress(context, address)
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
    context: android.content.Context,
    result: PackageRegistrationResult,
    existingCount: Int
): DeliveryPackage {
    val generatedNumber = existingCount + 1
    val memo = result.deliveryMemo.ifBlank { "登録画面から追加" }
    val (lat, lng) = if (result.latitude != 0.0 || result.longitude != 0.0) {
        result.latitude to result.longitude
    } else {
        geocodeAddress(context, result.address)
    }

    return DeliveryPackage(
        trackingCode = result.trackingNumber.ifBlank { "HB-${1000 + generatedNumber}" },
        recipient = result.recipientName.ifBlank { "届け先未入力" },
        address = result.address,
        timeWindow = result.timeWindow.toTimeWindow(),
        deliveryTimeWindow = result.timeWindow,
        size = result.size.label,
        colorLabel = result.colorType.label,
        packageType = result.shape.label,
        cod = false,
        hasLocker = result.hasLocker,
        memo = memo,
        latitude = lat,
        longitude = lng,
        status = result.status,
        nameplate = result.nameplate,
        packageMemo = result.packageMemo,
        phoneNumber = result.phoneNumber,
        shape = result.shape
    )
}

private const val ANNOUNCEMENTS_URL =
    "https://raw.githubusercontent.com/mnsvip-tech/hakobun-Andorid/main/docs/announcements.json"

suspend fun fetchAnnouncements(): List<Announcement> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val url = java.net.URL(ANNOUNCEMENTS_URL)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = try {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val array = org.json.JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Announcement(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    publishedAt = obj.optString("publishedAt")
                )
            }
        }.getOrElse { emptyList() }
    }
}

// Google MapsのREST API(Geocoding/Directions)はAPIキー制限を「Androidアプリ」で設定した場合、
// X-Android-Package / X-Android-Cert ヘッダーの付与が必須になる。ヘッダーを付けずに平文キーだけで
// 呼び出すと、キーが漏洩した際に第三者が別アプリ・別サーバーから流用できてしまうため必ず付与する。
private fun java.net.HttpURLConnection.applyAndroidRestrictionHeaders(context: android.content.Context) {
    setRequestProperty("X-Android-Package", context.packageName)
    val certSha1 = signingCertificateSha1(context)
    if (certSha1.isNotBlank()) {
        setRequestProperty("X-Android-Cert", certSha1)
    }
}

private fun signingCertificateSha1(context: android.content.Context): String {
    return runCatching {
        val signature = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()
        } ?: return ""
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
        // X-Android-Cert ヘッダーはコロンなしの連続した16進文字列である必要がある
        // （Google Cloud Console の登録画面はコロン区切り表示だが、HTTPヘッダーの値は区切り文字なし）。
        digest.joinToString("") { "%02X".format(it) }
    }.getOrDefault("")
}

suspend fun geocodeAddressPublic(context: android.content.Context, address: String): Pair<Double, Double> =
    geocodeAddress(context, address)

private suspend fun geocodeAddress(context: android.content.Context, address: String): Pair<Double, Double> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(address, "UTF-8")
            val apiKey = com.delivery.navigator.BuildConfig.MAPS_API_KEY
            val url = java.net.URL(
                "https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&language=ja&region=JP&key=$apiKey"
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.applyAndroidRestrictionHeaders(context)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = try {
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
            val root = org.json.JSONObject(json)
            val results = root.optJSONArray("results")
            val location = results
                ?.takeIf { it.length() > 0 }
                ?.optJSONObject(0)
                ?.optJSONObject("geometry")
                ?.optJSONObject("location")
            if (location != null) {
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

suspend fun searchPostalCode(postalCode: String, context: android.content.Context): AddressCandidate {
    val sourceLabel = context.getString(com.delivery.navigator.R.string.postal_search_source)
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val url = java.net.URL("https://zipcloud.ibsnet.co.jp/api/search?zipcode=$postalCode")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = org.json.JSONObject(json)
            val results = root.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val item = results.getJSONObject(0)
                val address = "${item.optString("address1")}${item.optString("address2")}${item.optString("address3")}"
                AddressCandidate(
                    sourceLabel = sourceLabel,
                    postalCode = postalCode,
                    address = address,
                    confidenceLabel = context.getString(com.delivery.navigator.R.string.postal_match)
                )
            } else {
                AddressCandidate(
                    sourceLabel = sourceLabel,
                    postalCode = postalCode,
                    address = context.getString(com.delivery.navigator.R.string.postal_not_found),
                    confidenceLabel = context.getString(com.delivery.navigator.R.string.postal_unconfirmed)
                )
            }
        }.getOrElse {
            AddressCandidate(
                sourceLabel = sourceLabel,
                postalCode = postalCode,
                address = context.getString(com.delivery.navigator.R.string.postal_communication_error),
                confidenceLabel = context.getString(com.delivery.navigator.R.string.postal_error)
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
            item.deliveryTimeWindow.label,
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
                val deliveryTimeWindow = parseDeliveryTimeWindow(parts.getOrNull(3).orEmpty())
                DeliveryPackage(
                    trackingCode = parts.getOrNull(0).orEmpty().ifBlank { "IMPORT-${1000 + generatedNumber}" },
                    recipient = parts.getOrNull(1).orEmpty().ifBlank { "インポート先" },
                    address = parts.getOrNull(2).orEmpty(),
                    timeWindow = deliveryTimeWindow.toTimeWindow(),
                    deliveryTimeWindow = deliveryTimeWindow,
                    size = "80",
                    colorLabel = "インポート",
                    packageType = "住所データ",
                    cod = false,
                    hasLocker = false,
                    memo = parts.getOrNull(4).orEmpty(),
                    latitude = 0.0,
                    longitude = 0.0,
                    status = DeliveryStatus.Pending
                )
            }
        }
        .toList()
}

/** 平均走行速度(km/h)。ラッシュ時間帯は渋滞を想定し低めの値を使う簡易補正。 */
private fun estimatedSpeedKmhForHour(hour: Int): Double = when (hour) {
    in 7..9, in 17..19 -> 15.0
    in 10..16 -> 22.0
    else -> 28.0
}

private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusM * c
}

/** 出発時刻からの経過分を加味し、区間の走行時間(分)を渋滞想定込みで見積もる。 */
private fun estimatedTravelMinutes(distanceMeters: Double, atHour: Int): Double {
    val speedKmh = estimatedSpeedKmhForHour(atHour)
    val distanceKm = distanceMeters / 1000.0
    return (distanceKm / speedKmh) * 60.0
}

// 地理的な近さを最優先とし、時間指定の遅れは同程度の距離の候補間でのみ選択を左右する
// 弱い参考情報として扱う(以前は3.0で、時間指定違反が数十分でも移動時間の差を圧倒し、
// 近くの荷物を後回しにしてしまっていた)。
private const val LATENESS_PENALTY_PER_MINUTE = 0.3

/**
 * 渋滞(時間帯別の平均速度補正)と各荷物の時間指定(timeWindow)を考慮して配送順を決定する。
 * 総移動時間が最短になることを優先し、時間指定は守れない場合でも後続の荷物として組み込む(ソフト制約)。
 */
fun calculateNearestRoute(
    packages: List<DeliveryPackage>,
    originLat: Double = CURRENT_LATITUDE,
    originLng: Double = CURRENT_LONGITUDE,
    startTime: Calendar = Calendar.getInstance()
): List<RouteStop> {
    val remaining = packages.toMutableList()
    val ordered = mutableListOf<RouteStop>()
    var currentLatitude = originLat
    var currentLongitude = originLng
    var elapsedMinutes = 0.0
    // 出発時刻からの絶対経過分で扱うため、時刻情報は「出発日0時からの分」に正規化する(日またぎでも%24による丸めのずれが起きない)。
    val startMinuteOfDay = startTime.get(Calendar.HOUR_OF_DAY) * 60.0 + startTime.get(Calendar.MINUTE)

    // 時間指定枠が出発時点で既に終了している場合は「翌日の枠」を指すものとして扱う(23時出発+翌朝指定などの日またぎに対応)。
    // ルート出発時刻(startMinuteOfDay)基準で一度だけ決まる値なので、経過時間や到着予定に応じて枠の日付がずれることはない。
    fun windowAbsoluteMinutes(startWindowHour: Int, endWindowHour: Int): Pair<Double, Double> {
        val windowEndToday = endWindowHour * 60.0
        val dayIndex = if (startMinuteOfDay > windowEndToday) 1.0 else 0.0
        return (dayIndex * 1440.0 + startWindowHour * 60.0) to (dayIndex * 1440.0 + endWindowHour * 60.0)
    }

    while (remaining.isNotEmpty()) {
        val currentHour = (((startMinuteOfDay + elapsedMinutes) / 60.0).toInt()) % 24
        val next = remaining.minBy { item ->
            val distanceMeters = haversineMeters(currentLatitude, currentLongitude, item.latitude, item.longitude)
            val travelMinutes = estimatedTravelMinutes(distanceMeters, currentHour)
            val arrivalAbsoluteMinute = startMinuteOfDay + elapsedMinutes + travelMinutes
            val latenessPenalty = item.deliveryTimeWindow.hourRange()?.let { (startWindowHour, endWindowHour) ->
                val (_, windowEndAbs) = windowAbsoluteMinutes(startWindowHour, endWindowHour)
                val overMinutes = arrivalAbsoluteMinute - windowEndAbs
                if (overMinutes > 0) overMinutes * LATENESS_PENALTY_PER_MINUTE else 0.0
            } ?: 0.0
            travelMinutes + latenessPenalty
        }
        remaining.remove(next)

        val distanceMeters = haversineMeters(currentLatitude, currentLongitude, next.latitude, next.longitude)
        val travelMinutes = estimatedTravelMinutes(distanceMeters, currentHour)
        elapsedMinutes += travelMinutes
        next.deliveryTimeWindow.hourRange()?.let { (startWindowHour, endWindowHour) ->
            val arrivalAbsoluteMinute = startMinuteOfDay + elapsedMinutes
            val (windowStartAbs, _) = windowAbsoluteMinutes(startWindowHour, endWindowHour)
            if (arrivalAbsoluteMinute < windowStartAbs) {
                elapsedMinutes = windowStartAbs - startMinuteOfDay
            }
        }

        ordered.add(RouteStop(next, ordered.size + 1))
        currentLatitude = next.latitude
        currentLongitude = next.longitude
    }

    return ordered
}

suspend fun fetchDrivingRoutePoints(
    context: android.content.Context,
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
            connection.applyAndroidRestrictionHeaders(context)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = try {
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
            val root = org.json.JSONObject(json)
            val encoded = root.optJSONArray("routes")
                ?.takeIf { it.length() > 0 }
                ?.optJSONObject(0)
                ?.optJSONObject("overview_polyline")
                ?.optString("points")
                .orEmpty()
            if (encoded.isNotBlank()) decodePolyline(encoded) else emptyList()
        }.getOrElse { emptyList() }
    }
}

private val drivingRouteCache = java.util.concurrent.ConcurrentHashMap<String, List<Pair<Double, Double>>>()

private fun drivingRouteCacheKey(origin: Pair<Double, Double>, destination: Pair<Double, Double>): String {
    fun round5(value: Double): Double = Math.round(value * 100_000.0) / 100_000.0
    return "${round5(origin.first)},${round5(origin.second)}->${round5(destination.first)},${round5(destination.second)}"
}

/**
 * fetchDrivingRoutePoints の結果をアプリ起動中のみメモリにキャッシュする。
 * 同じ出発地→目的地(座標を小数5桁で丸めて判定)の描画では Directions API を再呼び出しせず、
 * キャッシュ済みのポリラインを返すことで課金対象の呼び出し回数を抑える。
 * 空(取得失敗)の結果はキャッシュしないため、次回に再試行できる。
 */
suspend fun fetchDrivingRoutePointsCached(
    context: android.content.Context,
    origin: Pair<Double, Double>,
    destination: Pair<Double, Double>
): List<Pair<Double, Double>> {
    val key = drivingRouteCacheKey(origin, destination)
    drivingRouteCache[key]?.let { return it }
    val points = fetchDrivingRoutePoints(context, origin, destination)
    if (points.isNotEmpty()) {
        drivingRouteCache[key] = points
    }
    return points
}

data class NearbyPlace(
    val placeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

/**
 * Google Places API (Nearby Search) で周辺のコンビニ/ガソリンスタンド等を検索する。
 * type には "convenience_store" や "gas_station" を渡す。
 */
suspend fun fetchNearbyPlaces(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
    type: String,
    radiusMeters: Int = 1500
): List<NearbyPlace> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val apiKey = com.delivery.navigator.BuildConfig.MAPS_API_KEY
            val url = java.net.URL(
                "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=$latitude,$longitude&radius=$radiusMeters&type=$type&language=ja&key=$apiKey"
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.applyAndroidRestrictionHeaders(context)
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            val json = try {
                connection.inputStream.bufferedReader().readText()
            } finally {
                connection.disconnect()
            }
            val root = org.json.JSONObject(json)
            val results = root.optJSONArray("results") ?: return@runCatching emptyList()
            (0 until results.length()).mapNotNull { i ->
                val item = results.optJSONObject(i) ?: return@mapNotNull null
                val loc = item.optJSONObject("geometry")?.optJSONObject("location") ?: return@mapNotNull null
                NearbyPlace(
                    placeId = item.optString("place_id"),
                    name = item.optString("name"),
                    latitude = loc.getDouble("lat"),
                    longitude = loc.getDouble("lng")
                )
            }
        }.getOrDefault(emptyList())
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
        setDataAndType(CalendarContract.Events.CONTENT_URI, "vnd.android.cursor.item/event")
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

private fun parseDeliveryTimeWindow(value: String): RegistrationTimeWindow {
    return when (value) {
        RegistrationTimeWindow.Morning.label, "午前中" -> RegistrationTimeWindow.Morning
        RegistrationTimeWindow.Afternoon.label, TimeWindow.Afternoon.label, "12-14時" -> RegistrationTimeWindow.Afternoon
        RegistrationTimeWindow.LateAfternoon.label -> RegistrationTimeWindow.LateAfternoon
        RegistrationTimeWindow.Evening.label, TimeWindow.Evening.label, "20-21時" -> RegistrationTimeWindow.Evening
        RegistrationTimeWindow.Night.label -> RegistrationTimeWindow.Night
        else -> RegistrationTimeWindow.None
    }
}
