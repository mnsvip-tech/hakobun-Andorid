package com.delivery.navigator.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.delivery.navigator.model.CourseAddress
import com.delivery.navigator.model.DeliveryPackage
import com.delivery.navigator.model.DeliveryStatus
import com.delivery.navigator.model.MembershipPlan
import com.delivery.navigator.model.PackageShape
import com.delivery.navigator.model.RegistrationTimeWindow
import com.delivery.navigator.model.RegularCourse
import com.delivery.navigator.model.TimeWindow
import com.delivery.navigator.model.UserProfile
import com.delivery.navigator.model.toRegistrationTimeWindow
import org.json.JSONArray
import org.json.JSONObject

class LocalDeliveryStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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

    fun loadUserProfile(): UserProfile {
        val src = preferences.getString(KEY_USER_PROFILE, null).orEmpty()
        if (src.isBlank()) return UserProfile()
        return runCatching {
            val obj = JSONObject(src)
            UserProfile(
                driverName = obj.optString("driverName"),
                base = obj.optString("base"),
                contact = obj.optString("contact"),
                email = obj.optString("email"),
                plan = enumValueOrDefault(obj.optString("plan"), MembershipPlan.Free),
                isRegistered = obj.optBoolean("isRegistered", false)
            )
        }.getOrDefault(UserProfile())
    }

    fun saveUserProfile(profile: UserProfile) {
        val obj = JSONObject()
            .put("driverName", profile.driverName)
            .put("base", profile.base)
            .put("contact", profile.contact)
            .put("email", profile.email)
            .put("plan", profile.plan.name)
            .put("isRegistered", profile.isRegistered)
        preferences.edit().putString(KEY_USER_PROFILE, obj.toString()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_PACKAGES).apply()
    }

    private fun JSONObject.toDeliveryPackage(): DeliveryPackage {
        val timeWindow = enumValueOrDefault(optString("timeWindow"), TimeWindow.Unspecified)
        val deliveryTimeWindow = if (has("deliveryTimeWindow")) {
            enumValueOrDefault(optString("deliveryTimeWindow"), timeWindow.toRegistrationTimeWindow())
        } else {
            timeWindow.toRegistrationTimeWindow()
        }
        return DeliveryPackage(
            trackingCode = optString("trackingCode"),
            recipient = optString("recipient"),
            address = optString("address"),
            timeWindow = timeWindow,
            deliveryTimeWindow = deliveryTimeWindow,
            size = optString("size"),
            colorLabel = optString("colorLabel"),
            packageType = optString("packageType"),
            cod = optBoolean("cod"),
            hasLocker = optBoolean("hasLocker"),
            memo = optString("memo"),
            latitude = optDouble("latitude", 0.0),
            longitude = optDouble("longitude", 0.0),
            status = enumValueOrDefault(optString("status"), DeliveryStatus.Pending),
            nameplate = optString("nameplate"),
            packageMemo = optString("packageMemo"),
            phoneNumber = optString("phoneNumber"),
            shape = enumValueOrDefault(optString("shape"), PackageShape.SmallBox)
        )
    }

    private fun DeliveryPackage.toJson(): JSONObject {
        return JSONObject()
            .put("trackingCode", trackingCode)
            .put("recipient", recipient)
            .put("address", address)
            .put("timeWindow", timeWindow.name)
            .put("deliveryTimeWindow", deliveryTimeWindow.name)
            .put("size", size)
            .put("colorLabel", colorLabel)
            .put("packageType", packageType)
            .put("cod", cod)
            .put("hasLocker", hasLocker)
            .put("memo", memo)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("status", status.name)
            .put("nameplate", nameplate)
            .put("packageMemo", packageMemo)
            .put("phoneNumber", phoneNumber)
            .put("shape", shape.name)
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
        const val KEY_USER_PROFILE = "user_profile"
    }
}
