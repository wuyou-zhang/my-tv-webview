import java.io.BufferedReader

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.lizongying.mytv0"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lizongying.mytv.local"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    applicationVariants.all {
        outputs.all {
            (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName = "我的电视 本地版.apk"
        }
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // Flag to enable support for the new language APIs
        // For AGP 4.1+
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // For AGP 7.4+
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.media3.ui)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.datasource.rtmp)

    implementation(libs.nanohttpd)
    implementation(libs.gua64)
    implementation(libs.zxing)
    implementation(libs.glide)

    implementation(libs.gson)
    implementation(libs.okhttp)

    implementation(libs.core.ktx)
    implementation(libs.coroutines)

    implementation(libs.constraintlayout)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)

    implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
}