import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

android {
    namespace = "com.delivery.navigator"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val storeFilePath = localProperties.getProperty("RELEASE_STORE_FILE")
                ?: providers.environmentVariable("RELEASE_STORE_FILE").orNull
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                    ?: providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                    ?: providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
                    ?: providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }

    defaultConfig {
        applicationId = "com.delivery.navigator"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"
        val mapsApiKey = localProperties.getProperty("MAPS_API_KEY")
            ?: providers.gradleProperty("MAPS_API_KEY").orNull
            ?: providers.environmentVariable("MAPS_API_KEY").orNull
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "FORCE_PLAN", "\"NONE\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    flavorDimensions += "testMode"
    productFlavors {
        // 通常ビルド。Google Play課金を実際に照会する(本番と同じ挙動)。
        create("standard") {
            dimension = "testMode"
        }
        // 実機テスト用: 課金照会をスキップしプレミアム状態を強制する。applicationIdは変更しないため、
        // standard/premiumOffと同時にはインストールできない(上書きされる)が、ビルド成果物(APK)は別名で残る。
        create("premiumOn") {
            dimension = "testMode"
            versionNameSuffix = "-premium-on"
            buildConfigField("String", "FORCE_PLAN", "\"PREMIUM\"")
        }
        // 実機テスト用: 課金照会をスキップし無料(トライアル切れ)状態を強制する。
        create("premiumOff") {
            dimension = "testMode"
            versionNameSuffix = "-premium-off"
            buildConfigField("String", "FORCE_PLAN", "\"FREE\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // premiumOn / premiumOff は課金照会を強制的にバイパスする実機テスト専用ビルド。
    // 誤ってPlay Consoleへ提出されないよう、これらのフレーバーのreleaseバリアントは生成しない。
    androidComponents {
        beforeVariants(selector().withBuildType("release")) { variantBuilder ->
            if (variantBuilder.productFlavors.any { (_, flavorName) -> flavorName != "standard" }) {
                variantBuilder.enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.register<JavaExec>("runRouteDiagnostics") {
    dependsOn("compileStandardDebugUnitTestKotlin")
    group = "verification"
    mainClass.set("com.delivery.navigator.data.RouteCalculationTestKt")
    classpath = files(
        "build/tmp/kotlin-classes/standardDebugUnitTest",
        "build/tmp/kotlin-classes/standardDebug"
    ) + configurations.getByName("standardDebugUnitTestRuntimeClasspath")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.maps.android:maps-compose:6.12.0")
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
