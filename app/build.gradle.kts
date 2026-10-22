plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
    id("com.diffplug.spotless")
}

android {
    namespace = "de.szalkowski.activitylauncher"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId =
            providers.environmentVariable("APPID").orElse(providers.gradleProperty("APPID")).getOrElse("de.szalkowski.activitylauncher")
        minSdk = 16
        targetSdk = 37
        versionCode = 20500
        versionName = "2.5.0-dev"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "de.szalkowski.activitylauncher.HiltTestRunner"
    }
    testBuildType = "debug"
    flavorDimensions += listOf("distribution", "ads")
    productFlavors {
        create("oss") {
            dimension = "distribution"
            minSdk = 16
            multiDexEnabled = true
            applicationId =
                providers.environmentVariable(
                    "APPID",
                ).orElse(providers.gradleProperty("APPID")).getOrElse("de.szalkowski.activitylauncher.oss")
        }
        create("playStore") {
            dimension = "distribution"
            minSdk = 24
            applicationId =
                providers.environmentVariable("APPID").orElse(providers.gradleProperty("APPID")).getOrElse("de.szalkowski.activitylauncher")
        }
        create("noads") {
            dimension = "ads"
            resValue("string", "admob_banner_id", "unused")
            resValue("string", "publisher_id", "")
            resValue("string", "app_id", "")
        }
        create("ads") {
            dimension = "ads"
            val admobAppId = providers.environmentVariable("ADMOB_APP_ID").getOrElse("")
            val publisherId = providers.environmentVariable("PUBLISHER_ID").getOrElse("")
            val appId = providers.environmentVariable("APP_ID").getOrElse("")
            manifestPlaceholders["ADMOB_APP_ID"] = admobAppId
            resValue("string", "publisher_id", publisherId)
            resValue("string", "app_id", appId)
        }
    }
    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("KEYSTORE").orNull
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            signingConfig =
                if (providers.environmentVariable("KEYSTORE").isPresent) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    bundle { language { enableSplit = false } }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

ksp { arg("room.generateKotlin", "true") }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}

androidComponents {
    beforeVariants(selector().withFlavor("distribution", "oss").withFlavor("ads", "ads")) {
        it.enable = false
    }
}

// Conditionally apply Google services, Firebase, and AppLovin plugins
// and validate required environment variables
val taskNames = gradle.startParameter.taskNames
val isAdsBuild =
    taskNames.any { it.contains("Ads", ignoreCase = true) } &&
        !taskNames.any { it.contains("Noads", ignoreCase = true) }

if (isAdsBuild) {
    check(providers.environmentVariable("ADMOB_APP_ID").isPresent) {
        "ADMOB_APP_ID environment variable must be set for ads builds"
    }
    check(providers.environmentVariable("PUBLISHER_ID").isPresent) {
        "PUBLISHER_ID environment variable must be set for ads builds"
    }
    check(providers.environmentVariable("APP_ID").isPresent) {
        "APP_ID environment variable must be set for ads builds"
    }

    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    apply(plugin = "com.google.firebase.firebase-perf")
    apply(plugin = "applovin-quality-service")

    extensions.findByName("applovin")?.let { extension ->
        val adReviewKey = providers.environmentVariable("AD_REVIEW_KEY").get()
        val method = extension.javaClass.methods.find { it.name == "setApiKey" }
        method?.invoke(extension, adReviewKey)
    }
}

// Configure Spotless for code formatting
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint("1.2.1")
            .editorConfigOverride(
                mapOf(
                    "android" to "true",
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "ktlint_standard_chain-rule-first" to "disabled",
                    "ktlint_standard_backing-property-naming" to "disabled",
                ),
            )
    }

    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts")
        ktlint("1.2.1")
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml")
        leadingTabsToSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Configure build-time formatting for development
tasks.named("preBuild") {
    // Auto-format on development builds, but check on CI
    if (System.getenv("CI") == null) {
        dependsOn("spotlessApply")
    } else {
        dependsOn("spotlessCheck")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    "ossImplementation"("androidx.multidex:multidex:2.0.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.dagger:hilt-android:2.59.2")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    "playStoreImplementation"("com.google.android.play:review-ktx:2.0.2")

    "adsImplementation"(platform("com.google.firebase:firebase-bom:34.14.1"))
    "adsImplementation"("com.google.firebase:firebase-analytics")
    "adsImplementation"("com.google.firebase:firebase-crashlytics")
    "adsImplementation"("com.google.firebase:firebase-perf")
    "adsImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    "adsImplementation"("com.intergi.playwire:playwiresdk_total:13.1.0")
    "adsImplementation"("com.google.android.gms:play-services-ads-identifier:18.3.0")
    "adsImplementation"("com.applovin:applovin-sdk:13.6.3")

    "ksp"("com.google.dagger:hilt-compiler:2.59.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("org.mockito:mockito-android:5.11.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.59.2")
}
