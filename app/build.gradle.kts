plugins {
    id("com.android.application")
}

// Stable GeckoView 157, maven metadata release 157.0.20260924084938 (2026-09-24).
// The AAR minSdk is 26 and minCompileSdk is 37.1. Fire OS 7 is API 28, so the
// app minSdk is 28. The published geckoview AAR contains arm64-v8a, armeabi-v7a,
// and x86_64; ABI splits keep each APK to one of those.
val geckoviewVersion = "157.0.20260924084938"

// Optional. buildspec.yml decodes ANDROID_KEYSTORE_BASE64 into this path.
val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAliasName = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPasswordValue = System.getenv("ANDROID_KEY_PASSWORD")

android {
    namespace = "watch.cosmo"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "watch.cosmo"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GECKOVIEW_VERSION", "\"$geckoviewVersion\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    if (!releaseKeystorePath.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAliasName
                keyPassword = releaseKeyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (!releaseKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        jniLibs {
            // Fire OS 7 can load uncompressed native libs (API 28). Keep the
            // default packaging so libxul is not extracted a second time.
            useLegacyPackaging = false
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
                ?.identifier
            val abiOffset = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            val base = output.versionCode.orNull ?: variant.outputs.first().versionCode.orNull ?: 1
            output.versionCode.set(base * 10 + abiOffset)
        }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.appcompat:appcompat:1.8.0")
    testImplementation("junit:junit:4.13.2")
}
