import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // No `org.jetbrains.kotlin.android`: AGP 9 compiles Kotlin itself.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// -------------------------------------------------------------------------------------------------
// Version — `version.properties` is the single source, read without fallback (see its header).
// -------------------------------------------------------------------------------------------------
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    require(f.exists()) { "version.properties not found at the root of the project." }
    f.inputStream().use { load(it) }
}
val appVersionCode: Int = requireNotNull(versionProps.getProperty("versionCode")?.trim()?.toIntOrNull()) {
    "versionCode missing or not an integer in version.properties."
}
val appVersionName: String = requireNotNull(versionProps.getProperty("versionName")?.trim()?.ifBlank { null }) {
    "versionName missing or blank in version.properties."
}

// -------------------------------------------------------------------------------------------------
// Signing — keystore.properties at the root, never committed (.gitignore). It names four keys:
// storeFile (the key lives OUTSIDE the repository, J:\applications\_keystores\), storePassword, keyAlias,
// keyPassword. Absent, a release build is signed with the debug key: that is how the CI exercises R8
// without the real key, and such an APK is never published.
// -------------------------------------------------------------------------------------------------
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreFile.exists()
if (hasReleaseKey) {
    // Every key named: a partial file would otherwise fail late, on a null path or password.
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach { key ->
        require(!keystoreProps.getProperty(key).isNullOrBlank()) { "keystore.properties has no $key." }
    }
}

// -------------------------------------------------------------------------------------------------
// Languages — the translated folders ARE the list (as Read Files Tech). English is the source, in values/;
// every values-XX/ that carries a strings.xml is a shipped language. `localeFilters` (a language missing
// there is STRIPPED from the APK without an error) and the Android 13+ per-app language picker
// (`generateLocaleConfig`) both read this one list. tools/check-translations.py checks the translations.
// -------------------------------------------------------------------------------------------------
val shippedLanguages: List<String> = listOf("en") + file("src/main/res").listFiles().orEmpty()
    .filter { File(it, "strings.xml").isFile }
    // `car` is the car UI mode, three letters as a language would be.
    .mapNotNull { Regex("values-((?!car$)[a-z]{2,3})").matchEntire(it.name)?.groupValues?.get(1) }
    .sorted()

android {
    // AGP slips an ENCRYPTED list of our dependencies into the APK signing block, for the Play console.
    // This app is not on Play: an unreadable blob has no place in a binary whose argument is to be
    // verifiable, and the F-Droid scanner refuses it (Agenda Tech, 2026-09).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // The name and the applicationId, decided on 2026-11-01 (docs/ARCHITECTURE.md, D-002). The applicationId
    // is final the day a first APK is published: an update can never change it.
    namespace = "com.filestech.callhalt"
    // compileSdk 37 as in the whole portfolio: the AndroidX releases built for AGP 9 require it.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.filestech.callhalt"
        // Android 10: the S9 test phone, and the oldest version this method can run on (PROJET.md, § 5).
        minSdk = 29
        // ⚠️ NOT 37 (docs/ARCHITECTURE.md, D-008). From Android 17, an app TARGETING API 37 or higher gets
        // SMS that carry a one-time code THREE HOURS late (SMS_RECEIVED withheld, provider filtered), unless
        // it is the default SMS app — the very signal of PROJET.md § 4.3. Raising it ends that detection.
        targetSdk = 36

        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // `.debug`: a development build installs beside a release one and never reads its journal.
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // No ABI splits: the app ships no native code, so one APK serves every phone (Pass Tech's choice).

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += shippedLanguages
        generateLocaleConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room schema export — the dedicated plugin owns room.schemaLocation; do NOT also pass it through
// `ksp { arg(...) }`. Schemas land in schemas/<db-class-fqcn>/<version>.json and are COMMITTED: they are
// what a migration test replays (App Manager Tech). Version 1 stays editable until a first APK ships.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)

    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.coroutines.test)
}
