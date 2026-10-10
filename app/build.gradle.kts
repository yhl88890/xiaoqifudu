plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xuanji.repeater"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xuanji.repeater"
        minSdk = 26
        targetSdk = 35
        versionCode = 138
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
    // 【1010】JVM 单测里 `android.*` 的桩方法默认会抛 "Method ... not mocked"。
    //   我们只测纯逻辑，但 `SubtitleParser.rx()` 的**异常分支**里有一句 `android.util.Log.e`
    //   （只有正则编译失败才走到）—— 让桩返回默认值，免得"平时全绿、一跑到异常路径就炸"。
    //   ⚠️ 这是 JVM 单测的标准配置，不影响 APK 的任何行为。
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
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
    // 【v119 · 视频内核换 ExoPlayer】框架 MediaPlayer 的 MKV 解析器不认 AC3/EAC3（整条音轨静默跳过、
    //   不报错）⇒ MKV+杜比必哑。ExoPlayer 自带 MatroskaExtractor，原生支持 A_AC3/A_EAC3，
    //   且能读内封字幕轨（阶段2）。详见 HANDBOOK「视频没声音」。
    //   ⚠️ 界面是自己画的 ⇒ **不引 media3-ui**（省 ~190KB）。
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-extractor:1.11.1")
    // 【v75】B站 扫码登录：本地生成二维码（不上传第三方服务）
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 【1010】纯逻辑单元测试 —— **跑在电脑上、不需要手机**（这是本工程第一个测试依赖）。
    //   为什么只加 junit、不加 androidx.test：要测的 `SentenceCodec` / `SubtitleParser` /
    //   `SentenceSplitter` 全是**纯 Kotlin 函数**（一个 Android API 都不碰）⇒ JVM 单测足够，
    //   不需要仿真器、不需要真机。加了 androidx.test 反而会把测试绑到设备上、跑得慢。
    //   ⚠️ 这是**唯一**新增的依赖；现有依赖与构建配置一行没动。已实测 mavenCentral 可达。
    testImplementation("junit:junit:4.13.2")
}