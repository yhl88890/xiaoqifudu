plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xuanji.repeater"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xuanji.repeater"
        minSdk = 26
        targetSdk = 35
        versionCode = 86
        // 🔴 版本名**跟着 versionCode 走**（0919 阿亮要求）：
        //    他手机上「应用信息 → 版本」显示的就是这一行。以前这里写死 "1.2"
        //    ⇒ 装哪一版都显示 1.2，他在对话里说的 v49 / v50 根本对不上号。
        //    现在第三段 = versionCode ⇒ 显示 "1.2.51"。
        //    ⇒ 以后 bump versionCode 即可，**不用再单独改这一行**。
        versionName = "1.2.$versionCode"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "xuanji123"
            keyAlias = "xuanji"
            keyPassword = "xuanji123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 正式 release 签名（release.keystore，有效期 30 年）
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 【v75】B站 扫码登录：本地生成二维码（不上传第三方服务）
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}