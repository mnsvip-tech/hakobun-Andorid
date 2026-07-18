package com.delivery.navigator.data

import android.content.Context
import com.delivery.navigator.model.CourseAddress
import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.RegularCourse
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

    fun loadRegularCourses(): List<RegularCourse> {
        val source = preferences.getString(KEY_REGULAR_COURSES, null).orEmpty()
        if (source.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(source)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(item.toRegularCourse())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveRegularCourses(courses: List<RegularCourse>) {
        val array = JSONArray()
        courses.forEach { course -> array.put(course.toJson()) }
        preferences.edit().putString(KEY_REGULAR_COURSES, array.toString()).apply()
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
            latitude = optDouble("latitude", 0.0),
            longitude = optDouble("longitude", 0.0),
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

    private fun JSONObject.toRegularCourse(): RegularCourse {
        val addressesArray = optJSONArray("addresses") ?: JSONArray()
        return RegularCourse(
            code = optString("code"),
            displayName = optString("displayName"),
            addresses = buildList {
                for (index in 0 until addressesArray.length()) {
                    add(addressesArray.getJSONObject(index).toCourseAddress())
                }
            }
        )
    }

    private fun RegularCourse.toJson(): JSONObject {
        val addressesArray = JSONArray()
        addresses.forEach { address -> addressesArray.put(address.toJson()) }
        return JSONObject()
            .put("code", code)
            .put("displayName", displayName)
            .put("addresses", addressesArray)
    }

    private fun JSONObject.toCourseAddress(): CourseAddress {
        return CourseAddress(
            recipient = optString("recipient"),
            address = optString("address"),
            timeWindow = enumValueOrDefault(optString("timeWindow"), TimeWindow.Unspecified),
            memo = optString("memo"),
            latitude = optDouble("latitude", 0.0),
            longitude = optDouble("longitude", 0.0)
        )
    }

    private fun CourseAddress.toJson(): JSONObject {
        return JSONObject()
            .put("recipient", recipient)
            .put("address", address)
            .put("timeWindow", timeWindow.name)
            .put("memo", memo)
            .put("latitude", latitude)
            .put("longitude", longitude)
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, defaultValue: T): T {
        return enumValues<T>().firstOrNull { it.name == value } ?: defaultValue
    }

    private companion object {
        const val PREFERENCES_NAME = "hakobun_delivery_store"
        const val KEY_PACKAGES = "packages"
        const val KEY_REGULAR_COURSES = "regular_courses"
    }
}
