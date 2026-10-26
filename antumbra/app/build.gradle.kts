import java.net.URI
import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val appId: String by project
val appVersionCode: String by project
val appVersionName: String by project
val reportEndpoint: String by project
val privacyPolicyUrl: String by project

val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "app.antumbra"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode.toInt()
        versionName = appVersionName
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        buildConfigField("String", "GECKO_VERSION", "\"${libs.versions.geckoview.get()}\"")
        buildConfigField("String", "TOR_VERSION", "\"${libs.versions.torAndroid.get()}\"")
        buildConfigField("String", "REPORT_ENDPOINT", "\"$reportEndpoint\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
        buildConfigField("long", "BUILD_TIME", "${Instant.now().toEpochMilli()}L")
    }

    signingConfigs {
        if (signing.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        generateLocaleConfig = true
    }

    bundle {
        // Keep every language in the base APK so the in-app language picker always works.
        language { enableSplit = false }
    }
}

val downloadGeoip by tasks.registering {
    val outDir = layout.projectDirectory.dir("src/main/assets/geoip").asFile
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        listOf("geoip", "geoip6").forEach { name ->
            val out = File(outDir, name)
            if (out.length() > 0) return@forEach
            runCatching {
                URI("https://raw.githubusercontent.com/torproject/tor/main/src/config/$name").toURL()
                    .openStream().use { input -> out.outputStream().use { input.copyTo(it) } }
            }.onFailure { logger.warn("GeoIP database '$name' unavailable: ${it.message}") }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadGeoip) }

dependencies {
    implementation(libs.tor.android)
    implementation(libs.jtorctl)
    implementation(libs.iptproxy)
    implementation(libs.geckoview)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.activity.compose)
    implementation(libs.fragment)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core)
    implementation(libs.splashscreen)
    implementation(libs.biometric)
    implementation(libs.coroutines.android)
}
