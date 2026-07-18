package com.delivery.navigator.data

import android.content.Context
import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.TimeWindow
import org.json.JSONArray
import org.json.JSONObject

class LocalDeliveryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadPackages(): List<DeliveryPackage> {
        val source = preferences.getString(KEY_PACKAGES, null).orEmpty()
        if (source.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(source)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(item.toDeliveryPackage())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun savePackages(packages: List<DeliveryPackage>) {
        val array = JSONArray()
        packages.forEach { item -> array.put(item.toJson()) }
        preferences.edit().putString(KEY_PACKAGES, array.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_PACKAGES).apply()
    }

    private fun JSONObject.toDeliveryPackage(): DeliveryPackage {
        return DeliveryPackage(
            trackingCode = optString("trackingCode"),
            recipient = optString("recipient"),
            address = optString("address"),
            timeWindow = enumValueOrDefault(optString("timeWindow"), TimeWindow.Unspecified),
            size = optString("size"),
            colorLabel = optString("colorLabel"),
            packageType = optString("packageType"),
            cod = optBoolean("cod"),
            hasLocker = optBoolean("hasLocker"),
            memo = optString("memo"),
            latitude = optDouble("latitude"),
            longitude = optDouble("longitude"),
            status = enumValueOrDefault(optString("status"), DeliveryStatus.Pending)
        )
    }

    private fun DeliveryPackage.toJson(): JSONObject {
        return JSONObject()
            .put("trackingCode", trackingCode)
            .put("recipient", recipient)
            .put("address", address)
            .put("timeWindow", timeWindow.name)
            .put("size", size)
            .put("colorLabel", colorLabel)
            .put("packageType", packageType)
            .put("cod", cod)
            .put("hasLocker", hasLocker)
            .put("memo", memo)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("status", status.name)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, defaultValue: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: defaultValue
    }

    private companion object {
        const val PREFERENCES_NAME = "hakobun_delivery_store"
        const val KEY_PACKAGES = "packages"
    }
}
