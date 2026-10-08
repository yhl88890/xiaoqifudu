# 小七复读机（xuanji）

一个**给自己一个人学英语用**的 Android 播放器：把「听 → 断句 → 查词 → 存词 → 复习 → 看进步」打通成一条链。
不做社交、不做课程、不做账号体系，就解决一件事：**手上有什么材料（本地音频 / 美剧字幕 / B 站视频），就能拿来精听。**

- 包名：`com.xuanji.repeater`
- 版本：`1.2`（versionCode `42`）
- SDK：minSdk `26` / targetSdk `35` / compileSdk `35`
- 技术栈：Kotlin + Jetpack Compose（Material 3），**无第三方 UI / 网络 / 播放库**
- 签名：正式签名 `release`（`app/release.keystore`，指纹 `3fe6e64f…aefccb`，支持同签名覆盖升级）

> ⚠️ 目录说明：`xuanji-app-fixed/` 是**当前在用的工程**（源码 + git 都在这）；同级的 `xuanji-app/` 是历史副本，别改错。

---

## 一、它有哪些功能

App 一共 **7 个页面**（`Page` 枚举定义在 `AppPages.kt`）：

| 页面 | 干什么 |
|---|---|
| **PLAYER** 播放页 | 核心页。音频播放 + 复读（次数 / 倍速 / 三种播放模式）+ 断句列表 + 波形进度条 + 内嵌查词。模块化布局，可拖动排序、调高矮 |
| **TRACK** 选集页 | 当前文件所在文件夹的音频列表，当前项高亮，点即切 |
| **READING** 阅读模式 | 全屏读句子，点击跳句、整句翻译；**正文来源自动判定**（有视频源就读视频字幕，没有就读音频字幕），**不设来源 tab** |
| **FOLDER** 文件夹页 | 总览所有含音频的文件夹（数量 / 总时长）→ 进详情 → 整夹加载播放；行内可忽略 / 重命名 / 删除 |
| **VOCAB** 生词本 | 收藏的单词与句子（含翻译、来源、时间）；排序可切换（加入时间 / 首字母），筛选不持久化 |
| **REVIEW** 复习页 | 把攒下来的词和句子拿出来重过一遍，带熟练度星级、自动发音 |
| **VIDEO** 视频模式 | 本地视频播放 + 字幕；可从视频音轨生成字幕（ASR）；支持粘贴 B 站链接直接导入 |

另外还有几条**横跨页面**的能力：

- **断句 / 加歌词**：本地音频走波形断句（`SentenceSplitter`），有歌词文件则按歌词对齐；长句自动在停顿处切分
- **ASR 生成字幕**：硅基流动 `TeleAI/TeleSpeechASR` 拿带时间戳的分段 → 对齐 + 合并 + 修边界
- **B 站导入**：粘贴链接 → 后台前台服务跑「下载 → 抽音轨 → 云端识别 → 合成双语字幕」，切后台也不中断
- **在线发音**：短句走有道真人发音，长句走硅基流动 TTS → 系统 TTS → 有道切块的兜底链
- **悬浮歌词**：跨应用悬浮卡片显示当前句（跟读用）
- **生词提醒**：定时在通知栏换一个还没掌握的生词（口径是「每日任务」，详见 `HANDBOOK.md`）
- **学习统计**：累计生词 / 已掌握 / 本周新增，直接用生词本现有数据现算
- **数据导出**（入口在**设置页**，不是生词本页）：生词本 + 字幕库的 JSON 备份、单词 TXT、**Anki 卡片 CSV**
  （两列 = 正面英文 / 背面中文，可直接导入 Anki 走间隔重复）

---

## 二、代码地图（拆巨石后）

源码全在 `app/src/main/java/com/xuanji/repeater/`，**70 个文件 / 约 2.48 万行**。按职责分组：

### 入口与骨架
| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 唯一的 Activity。承载应用根 `AppRoot`（页面切换、底栏、各页装配）+ **文件级视频状态** |
| `AppPages.kt` | `Page` 枚举（7 个页面） |
| `XuanjiApp.kt` | Application。只干一件事：把 `applicationContext` 交给 `Pronounce` |

### 音频播放内核
| 文件 | 职责 |
|---|---|
| `PlayerService.kt` | **播放内核**：MediaPlayer 播放 + MediaSession / 前台通知（锁屏控制）+ 播放模式 + 复读 + 音频焦点 |
| `PlayerState.kt` | 对外暴露的播放状态 |
| `PlayMode.kt` | 播放模式枚举（顺序 / 列表循环 / 单曲循环） |
| `PlaybackSession.kt` | **媒体类型的唯一真值**（当前在用音频还是视频通道） |
| `MediaController.kt` | 对外的媒体控制接口（视频模式时把控制权转发给视频） |
| `PlaybackPrefs.kt` | 一次已保存的播放现场（续播） |
| `RepeatPrefs.kt` | 复读次数 / 倍速（**音频与视频共享**） |
| `SleepTimer.kt` | 睡眠定时器：倒计时到点暂停播放（音频 / 视频各停各的），不杀 App |

### 播放页 / 选集页 UI
| 文件 | 职责 |
|---|---|
| `PlayerPage.kt` | 播放页（模块化布局，编辑模式可拖动 / 调高） |
| `SentenceListView.kt` | 句子列表骨架：播放器内嵌列表 与 阅读模式列表**共用** |
| `PlayerLayoutPrefs.kt` | 播放页模块的排序 / 高度持久化 |
| `TrackPage.kt` | 选集页：当前文件所在文件夹的音频列表，当前项高亮 |
| `AudioItem.kt` | 一条音频（选集列表的数据模型） |

### 视频模式
| 文件 | 职责 |
|---|---|
| `VideoPage.kt` | 视频页 + 视频播放控制（`attachVideoPlayback` / `videoSeek` / `VideoPage` 等） |
| `VideoUi.kt` | 视频模式的独立小组件（进度条 / 长按快进手势） |
| `VideoLayoutPrefs.kt` | 视频页模块的排序 / 高度持久化 |
| `VideoScanner.kt` | 扫描设备媒体库里的视频 |

### 断句 / ASR / 字幕
| 文件 | 职责 |
|---|---|
| `SentenceSplitter.kt` | 波形断句核心（MediaExtractor + MediaCodec，纯自研） |
| `AsrTextAligner.kt` | ASR 文本对齐：段内断句、加标点、短句合并、边界修正 |
| `LongSentenceSplit.kt` | 长句切分共享工具（音频与视频**共用同一套算法**） |
| `TimelineRecut.kt` | 借 B站 字幕轨的真实时间边界重切 ASR 长句（v62，治「断句又臭又长」） |
| `SpeechRecognizer.kt` | 单句 / 分段语音识别（硅基流动） |
| `AsrPrefs.kt` | ASR 服务商 + API Key 配置 |
| `SentenceCache.kt` | 断句结果缓存（句子 + 量化后的波形） |
| `SentenceCodec.kt` | 句子表的文本序列化 + 短句合并工具 |
| `SentenceLabels.kt` | 无歌词时给句子编号（第 1 句 / 第 2 句…） |
| `LrcParser.kt` / `SrtParser.kt` | 歌词 / 字幕解析 |
| `SubtitleParser.kt` | **字幕唯一真值**：SRT / ASS / SSA 解析、多级自动匹配（`pickBest`）、双语同刻合并、垃圾样式剔除 |
| `ManualSubtitle.kt` | 手动指定的字幕（SAF 选文件 → 复制进私有目录 + 按视频路径记，**优先级最高**） |
| `SubtitleExport.kt` | 把当前句表导出成 SRT（同名不覆盖） |
| `EmbeddedSubtitle.kt` | **视频内封字幕轨**提取（v120）：Media3 `MatroskaExtractor` 读 MKV 字幕轨 + 系统 MediaExtractor 兜其它容器；自动挑轨、缓存、静默退回 |

### B 站导入链路
| 文件 | 职责 |
|---|---|
| `BiliClient.kt` | B 站网络客户端（纯 `HttpURLConnection` + `org.json`，零额外依赖） |
| `BiliLogin.kt` | B 站扫码登录（二维码生成 + 轮询），登录后字幕轨列表才拿得到 |
| `BiliImport.kt` | 导入编排：把链接变成「可播放的本地文件 + 双语字幕」 |
| `BiliImportService.kt` | **前台服务**：长流程放这儿跑，切后台不被系统掐 |
| `BiliImportBus.kt` | 进度 / 完成事件总线 |
| `MediaMuxer.kt` | DASH 的音视频流合成单个 MP4（不用 ffmpeg） |
| `VideoAudioExtractor.kt` | 从视频抽音轨 → 16kHz 单声道 PCM WAV |
| `WavConverter.kt` | 音频转码（m4a/AAC → 识别接口能收的格式） |
| `AudioSlice.kt` | 音频区间裁剪（不重编码） |

### 文件与媒体库
`FolderPage.kt`（文件夹页）、`FolderSort.kt`、`AudioScanner.kt`、`FileOps.kt`（删除 / 重命名）、`IgnorePrefs.kt`（忽略列表）

### 学习闭环
`ReadingPage.kt`（阅读模式）、`VocabPage.kt` / `VocabStore.kt` / `VocabPrefs.kt`（生词本）、`VocabReminder.kt`（生词提醒 + 开机重排广播）、`ReviewPage.kt` / `ReviewPrefs.kt`（复习）、`StatsPage.kt`（学习统计）、`SubtitleLibrary.kt`（字幕归档）、`Translator.kt` / `SubtitleTranslator.kt`（查词 / 翻译）、
`DataBackup.kt`（JSON 备份 + 单词 TXT + **Anki 卡片 CSV** 的生成与写出）

### 发音
`Pronounce.kt`（在线真人发音 + TTS 三道兜底链）、`SpeakPrefs.kt`（发音通道设置）

### 外观与通用
`SettingsPage.kt`（设置页：各项开关 + **数据导出**入口 + App 内「软件介绍」）、`ThemeState.kt` / `ThemeMenu.kt`（主题色 + 全 App 统一下拉菜单）、`XuanjiIcons.kt`（手绘矢量图标，避免引入大图标库）、`SplashOverlay.kt`（启动动画）、`FloatLyricsService.kt`（悬浮歌词）、`CrashGuard.kt`（崩溃现场落盘）

> 🛠 **这张地图会过期。** 一旦新增 / 删除 / 搬迁了源文件，记得回来同步它，再跑一下校验：
>
> ```bash
> python ../tools/check_doc_map.py
> ```
>
> 它会告诉你：地图里有没有写过期的文件名、哪些新文件漏写了、上面那句「N 个文件 / 约 M 万行」还对不对。
> 全绿就说明地图没过期。

---

## 三、数据存在哪（四处互不相干，别记混）

| 数据 | 位置 |
|---|---|
| 音频播放现场 | `SharedPreferences` → `playback_prefs` 的 `saved_json` |
| 视频播放现场 | `SharedPreferences` → **`xuanji_video.xml`**（不是 `video_prefs`） |
| 字幕 / 断句归档 | `SubtitleLibrary` |
| 生词本 | `filesDir/vocab.json`（**不是 SharedPreferences**） |

---

## 四、构建与打包

环境：**JDK 17** + **Android SDK 35**（`ANDROID_HOME` 指向 SDK 目录，或项目里建 `local.properties`）。

```bash
./gradlew.bat --stop                          # 先停掉可能占着文件的旧进程
# 删掉 lint 缓存（不删有时会假报错）
rm -rf app/build/intermediates/lint-cache
./gradlew.bat assembleRelease                 # 约 1.5 分钟
```

产物：`app/build/outputs/apk/release/app-release.apk`（正式签名，R8 混淆）。

⚠️ 几条踩过的坑（完整版见 `.workbuddy/memory/MEMORY.md`「构建 / 交付」）：
- **别 `clean`**（1.5 分钟能好的事会变成好几分钟）
- 别只信 `BUILD SUCCESSFUL`：可能只跑了 lint 没重编 ⇒ 查 APK 的 **mtime** + 在 dex 里搜新字符串
- Gradle 按**内容哈希**判断改动，`touch` 不触发重编
- **回退打包必须先把 `versionCode` 抬到比已装的高**（Android 拒绝降级覆盖）

更细的电脑端环境说明见 `../电脑端开发指南.md`（那份是 8 月写的，环境部分仍有效，结构部分已过时）。

---

## 五、文档分工（别写重了）

| 文档 | 放什么 |
|---|---|
| **本 README** | 稳定的事实：项目定位、功能清单、代码地图、怎么构建、数据在哪 |
| `../.workbuddy/memory/HANDBOOK.md` | **踩坑与「为什么这么设计」**：ASR / 断句、发音、布局白边、adb 验收、各类「故意这么设计」清单、拆巨石手法 |
| `../.workbuddy/memory/MEMORY.md` | 项目硬约束速查（≤9KB） |
| `../.workbuddy/memory/YYYY-MM-DD.md` | 逐日工作记录 |
| **App 内「设置 → 关于 → 软件介绍」** | 给**用户**看的大白话说明（源码 `SettingsPage.kt` 的 `showIntro`）——**不是**本文件 |

> 🔴 改「软件介绍」请改 App 内那份，不要写到这里来。两者受众不同。
