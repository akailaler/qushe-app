plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qushe.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qushe.app"
        // minSdk 30 是刻意选择：MediaStore.createTrashRequest / createDeleteRequest
        // 与系统回收站（保留 30 天）都是 API 30 起才有，低于 30 需要 WRITE_EXTERNAL_STORAGE
        // 直接操作文件，完全另一套代码路径。回留同样只支持 Android 11+。
        minSdk = 30
        targetSdk = 35
        versionCode = 106
        versionName = "1.3.58"
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Lint 会报 "mipmap-anydpi-v26 的 v26 限定多余，minSdk 已经是 30"，
        // 但实测把它挪进无版本限定的 mipmap-anydpi 后，aapt2 会直接报
        // "resource mipmap/ic_launcher not found" —— adaptive-icon 必须带 v26 限定。
        // 这条检查对本工程（minSdk 30）本来也没什么价值，直接关掉。
        disable += "ObsoleteSdkInt"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // 直接读原始文件的 EXIF。MediaStore 的经纬度列在部分 ROM 上直接不返回，
    // 必须有第二条独立的路才谈得上"尽力"。
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    // coil-video 用来把视频的第一帧抽成缩略图，垫在播放器底下。
    // 有了它，切换视频时看到的是首帧而不是黑屏，观感差异非常大。
    implementation("io.coil-kt:coil-video:2.7.0")
    // coil-gif 让 GIF / 动态 WebP 真正动起来。不加这个依赖，动图只会停在第一帧。
    implementation("io.coil-kt:coil-gif:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
