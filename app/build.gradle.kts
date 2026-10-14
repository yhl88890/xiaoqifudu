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
        // bump 版本只改这一个数即可（versionName 由它算出来）
        val code = 168
        versionCode = code
        // 🔴 版本名**跟着 versionCode 走**（0919 阿亮要求）：
        //    他手机上「应用信息 → 版本」显示的就是这一行。以前这里写死 "1.2"
        //    ⇒ 装哪一版都显示 1.2，他在对话里说的 v49 / v50 根本对不上号。
        // 🔴🔴【1011 阿亮】**过百进位**：第三段永远只留两位，百位进到中间那段，看着简洁。
        //    v99 → "1.2.99"；v139 → "1.3.39"；v205 → "1.4.5"；v300 → "1.5.0"。
        //    公式 = "1.${2 + code/100}.${code%100}"（<100 时与旧规则完全一致）。
        //    ⇒ 以后照样只 bump versionCode，**不用再单独改这一行**。
        versionName = "1.${2 + code / 100}.${code % 100}"
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
    //   ⚠️ 界面是自己画的 ⇒ 原本**不引 media3-ui**（省 ~190KB）。
    //   🔴🔴【v146 破例引入】「原生字幕」开关（阿亮定）要的是**播放器原生字幕渲染 + 默认长相**
    //     （原话：「里头播什么样就什么样，原声渲染」「就是要它默认的」）。
    //     我们的画面是**裸 SurfaceView**（`setVideoSurfaceView` 自定义 UI 路线），
    //     ExoPlayer 的 `TextRenderer` 虽然照样在跑、也照样选中了内封轨（v146 实测日志
    //     `READY TEXT tracks=2 [zh*选中 | zh]`），但**没有任何承载视图接住它** ⇒ 画面上不出字。
    //     media3 官方的承载物就是 `SubtitleView`（在 media3-ui 里）。
    //   ⚠️ **只引这一个库**，不用它的 PlayerView / PlayerControlView（我们界面仍是自己画的）
    //     ⇒ 只吃到 SubtitleView 那几个类，体积增量远小于整套 UI。
    implementation("androidx.media3:media3-ui:1.11.1")
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

// 🔴🔴【v146】把 `kotlin-stdlib` **钉死在工程的编译器版本 2.0.21**。
//   为什么必须钉：引入 `media3-ui:1.11.1`（为拿 `SubtitleView` 承载原生字幕）后，
//   它自己依赖 `kotlin-stdlib:2.2.10` ⇒ Gradle 冲突解析取最高版 ⇒ stdlib 升到 2.2.10，
//   而**我们的 Kotlin 编译器还是 2.0.21**，读不了 2.2 的 metadata：
//     "Module was compiled with an incompatible version of Kotlin.
//      The binary version of its metadata is 2.2.0, expected version is 2.0.0."
//   ⇒ 编译直接失败（compileReleaseKotlin）。**不是逻辑问题，是版本对齐问题。**
//   两条路：① 钉住 stdlib（选它，改动最小、不碰编译器）；
//          ② 把工程 Kotlin 升到 2.2.x（会牵动 Compose 编译器插件，风险大得多）。
//   ⚠️ 钉的是 **stdlib**（纯 Kotlin 运行时，向后兼容），不影响 media3-ui 的 Java 代码逻辑。
//   ⚠️ 以后要是真升级了工程 Kotlin（build.gradle.kts 里的 2.0.21），**这条要一并改或删掉**。
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.21")
    }
}