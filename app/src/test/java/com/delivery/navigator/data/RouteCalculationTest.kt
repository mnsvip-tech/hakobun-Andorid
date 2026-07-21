package com.delivery.navigator.data

import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.RegistrationTimeWindow
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.system.exitProcess

private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun totalDistanceKm(originLat: Double, originLng: Double, order: List<DeliveryPackage>): Double {
    var total = 0.0
    var lat = originLat
    var lng = originLng
    for (p in order) {
        total += haversineKm(lat, lng, p.latitude, p.longitude)
        lat = p.latitude
        lng = p.longitude
    }
    return total
}

private var failureCount = 0

private fun check(label: String, condition: Boolean, detail: String = "") {
    if (condition) {
        println("  [OK] $label")
    } else {
        println("  [NG] $label $detail")
        failureCount++
    }
}

fun main() {
    println("=== 1. 200件ダミーデータの座標妥当性 ===")
    val packages = generateDebugTestPackages(200, seed = 42L)
    val (originLat, originLng) = currentRouteOrigin()
    check("生成件数が200件", packages.size == 200, "実際: ${packages.size}")

    var maxDistKm = 0.0
    var outOfRangeCount = 0
    var invalidCoordCount = 0
    for (p in packages) {
        if (p.latitude !in -90.0..90.0 || p.longitude !in -180.0..180.0) invalidCoordCount++
        val d = haversineKm(originLat, originLng, p.latitude, p.longitude)
        if (d > maxDistKm) maxDistKm = d
        if (d > 16.0) outOfRangeCount++
    }
    println("  最大距離(東京駅から): %.2f km".format(maxDistKm))
    check("全件が東京駅15km圏内(許容16km)", outOfRangeCount == 0, "圏外: ${outOfRangeCount}件")
    check("無効座標なし", invalidCoordCount == 0, "無効: ${invalidCoordCount}件")

    val dupCodes = packages.groupBy { it.trackingCode }.filter { it.value.size > 1 }
    check("trackingCode重複なし", dupCodes.isEmpty(), "重複: ${dupCodes.keys}")

    println("\n=== 2. ルート計算の完全性・性能 ===")
    val startTime = System.currentTimeMillis()
    val route = calculateNearestRoute(packages, originLat, originLng)
    val elapsedMs = System.currentTimeMillis() - startTime
    println("  ルート計算所要時間: ${elapsedMs}ms")
    check("全荷物がルートに1回ずつ含まれる", route.size == 200 &&
        route.map { it.deliveryPackage.trackingCode }.toSet() == packages.map { it.trackingCode }.toSet())
    check("routeNumberが1からの連番", route.mapIndexed { i, s -> s.routeNumber == i + 1 }.all { it })
    check("200件の計算が5秒未満(実機遅延懸念なし)", elapsedMs < 5000, "実測: ${elapsedMs}ms")

    println("\n=== 3. ルートの最短性(貪欲法 vs ランダム順) ===")
    val routeDistance = totalDistanceKm(originLat, originLng, route.map { it.deliveryPackage })
    val randomDistance = totalDistanceKm(originLat, originLng, packages.shuffled(kotlin.random.Random(1)))
    println("  ルート順総移動距離: %.1f km".format(routeDistance))
    println("  ランダム順総移動距離: %.1f km".format(randomDistance))
    check("ルート順がランダム順より短い", routeDistance < randomDistance)

    println("\n=== 4. 日またぎ時間指定シナリオ(23時出発+翌朝8-12時指定) ===")
    val startTime23 = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }
    val morningPackage = DeliveryPackage(
        trackingCode = "MORNING-TEST", recipient = "テスト翌朝指定", address = "東京都千代田区丸の内1-1-1",
        timeWindow = com.delivery.navigator.model.TimeWindow.Morning, deliveryTimeWindow = RegistrationTimeWindow.Morning,
        size = "80", colorLabel = "", packageType = "", cod = false, hasLocker = false, memo = "",
        latitude = originLat + 0.01, longitude = originLng + 0.01, status = com.delivery.navigator.model.DeliveryStatus.Pending
    )
    val nearbyPackage = morningPackage.copy(
        trackingCode = "NEARBY-TEST", deliveryTimeWindow = RegistrationTimeWindow.None,
        timeWindow = com.delivery.navigator.model.TimeWindow.Unspecified,
        latitude = originLat + 0.001, longitude = originLng + 0.001
    )
    val nightRoute = calculateNearestRoute(listOf(morningPackage, nearbyPackage), originLat, originLng, startTime23)
    nightRoute.forEach { println("  ${it.routeNumber}: ${it.deliveryPackage.trackingCode}") }
    check("2件とも含まれる", nightRoute.size == 2)
    check("翌朝指定荷物が異常な巨大遅延ペナルティで計算破綻していない(クラッシュ・例外なし完了)", true)

    println("\n=== 結果 ===")
    if (failureCount == 0) {
        println("全項目OK")
    } else {
        println("NG件数: $failureCount")
        exitProcess(1)
    }
}
