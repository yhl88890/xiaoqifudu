# 小七复读机 iOS 移植 · 朋友操作指南

## 这是什么
- 小七复读机（安卓版，当前 v65）的**源码包**，用来给 iOS 版当**移植参考原本 / 蓝本**。
- 安卓代码是 Kotlin + Jetpack Compose，**不能直接变成 iOS App**。它是"抄作业的原本"：你读它的逻辑，用 Swift/SwiftUI（或 Flutter）在 iOS 上重写一遍。

## ⚠️ 硬件门槛（第一步先看这个，别在老 Mac 上白折腾）

- **iOS 编译机的硬要求**：要往 iPhone 上装 app 做真机调试，必须能跑「配得上该 iPhone 系统版本」的 Xcode；而 Xcode 版本又绑定 macOS 版本。一句话：**Mac 系统越新，能支持的 iPhone 越新**。
- **2016 年的 Mac 直接出局**：这类机器官方最高只能升到 macOS 12 (Monterey)，对应最高 Xcode 14，最多支持到 iOS 16/17。**如果用来验收的 iPhone 是 iOS 17 以上（例如 iPhone 17 系列出厂就是最新 iOS），这台老 Mac 编译出来的包装不进去、真机调试都连不上。**
- **先查两个版本再动手**：朋友点「左上角苹果图标 → 关于本机」看 Mac 的 macOS 版本；再看 iPhone「设置 → 通用 → 关于本机 → 软件版本」。
  - Mac = macOS 13 (Ventura) 及以上，且 iPhone 系统不太老 → 可用，继续往下看。
  - Mac 停在 macOS 12 或更老，且 iPhone 是 iOS 17+ → **这台 Mac 当不了编译机，别在它身上装任何东西了，直接换机器**。
- **出路**：弄一台能跑最新 macOS 的 Mac（二手 M1/M2 Mac mini 最便宜，一千多块），或走云端 Mac（Codemagic / MacStadium 按月租）远程构建。iPhone 真机保留当验收载体。
- 本指南其余步骤都假设「已有一台系统够新的 Mac」。这关过不了，后面全白搭。

## Mac 上要装什么（按必须程度）
1. **Xcode**（必须，编 iOS 用）
   - 从 Mac App Store 搜 Xcode 下载（免费，但体积大，约 10GB+）。
   - ⚠️ 这一步**必须你自己点**：要登录 Apple ID、同意许可协议。AI/WorkBuddy 替不了这几个点击。
   - 装完在终端执行：`sudo xcodebuild -license accept` 和 `xcode-select --install`（装命令行工具）。
2. **Homebrew**（包管理器，装别的用）
   - 终端执行官网一行命令：`/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`
3. **Android Studio**（可选，只看参考代码用）
   - `brew install --cask android-studio`，或从官网下。用来打开本包的安卓工程读逻辑。
4. **Flutter**（仅当决定走 Flutter 跨平台路线才需要）
   - 按 flutter.dev 官方说明安装。

## 让那边的 WorkBuddy 代劳（可行，但有例外）
- 你朋友 Mac 上如果装了 WorkBuddy（就是现在帮你弄的这个 AI），可以**让它跑安装命令、配环境、读这份源码、甚至直接写 iOS 代码**——大部分敲命令的活它都能干。
- **唯一它干不了的**：Xcode 的 App Store 登录 + 许可协议点击，必须人来做。其余（Homebrew、Android Studio、Flutter、环境配置、读代码写代码）都能让 WorkBuddy 代劳。
- 建议玩法：朋友打开 WorkBuddy，把这份包解压后的文件夹指给它，说"帮我装好 iOS 开发环境，并照着安卓代码规划/写 iOS 移植"，它就能一步步带着弄。

## 移植路线（建议）
1. 先列"功能对照表"：安卓每个功能 iOS 怎么对应（复读控制、ASR 生词、字幕解析、CSV 导出…）。
2. 逐块重写：复读状态机、TeleAI/TeleSpeechASR 调用、B站字幕解析、生词本/复习逻辑。
3. 真机验收：需要一台 iPhone + $99/年 苹果开发者账号（**账号用阿亮自己的 Apple ID 注册**，版权和发布握自己手里）。

## 关于"自动抓 B站 视频"（上架提醒）
- 如果想在 iOS 版里做"App 自动下载 B站 视频"，上架 App Store 有风险。
- 行业里活下来的做法：套"文件管理器 / 粘贴链接"的外壳，不要做显式的"B站下载"按钮，也不要在名称描述里写死 B站。
- 最稳的底线：让用户自己把视频下好 AirDrop 进 iPhone，App 只做复读+ASR+字幕加工。

## 注意
- 本包已剔除编译缓存（app/build、.gradle、.idea 等），解压后直接用，干净。
- 安卓工程里的 local.properties 等本地配置已去掉，Android Studio 打开会自动重新生成。
- 源码 + 完整 git 历史都在，朋友可以 `git log` 看功能演进。

## 给朋友那边 WorkBuddy 的"开场指令"（复制粘贴即可）

> 说明：朋友那边的 WorkBuddy 没有我们的聊天上下文，所以这条指令自带背景，直接复制给那边的 WorkBuddy 就行。

```
你是我的 iOS 开发助手。我手上有个安卓 App 的源码，想移植成 iOS 版。请按下面步骤做，每完成一步跟我汇报，别一口气全干完。

0. 先读同一个文件夹里的 PORTING-GUIDE.md，搞清楚项目背景和约束（重点：安卓代码只是"参考蓝本"，不能直接变成 iOS；Xcode 必须由本人从 Mac App Store 下载并登录 Apple ID、同意许可协议，你代不了；B站自动下载属于上架灰区，先别做显式下载按钮）。

1. 环境准备（能用终端命令跑的都帮我跑）：
   - 装 Homebrew（若未装）。
   - 装 Android Studio（cask）：brew install --cask android-studio（用来打开安卓工程看参考逻辑，推荐装）。
   - 若走 Flutter 跨平台，装 Flutter SDK。
   - 提醒我：Xcode 要我自己从 App Store 下、登录、同意许可。等我装完后，你再帮我跑 sudo xcodebuild -license accept 和 xcode-select --install。
   - 跑 xcodebuild -version 和 swift --version 验证环境。

2. 读代码 + 出方案：通读 app/src 下的 Kotlin/Compose 源码，重点搞懂：复读状态机（次数/速度/句末检测）、TeleAI/TeleSpeechASR 调用、B站字幕解析、生词本/复习/CSV 导出。产出一份"功能对照表 + iOS 技术选型建议（SwiftUI 原生 vs Flutter）"，先别写代码，给我看方案。

3. 我确认方案后，再搭 iOS 工程骨架，先实现"能跑起来的最小复读机"（音频播放 + 复读控制），并在连着的 iPhone 上用免费 Apple ID 签名装上去让我试。

每步做完先汇报，我再决定下一步。
```
