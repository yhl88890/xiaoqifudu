package com.xuanji.repeater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 设置页（**独立全屏页面**）
 *
 * 【0916 从 ModalBottomSheet 改造】原来叫 `SettingsSheet`，是 `MainActivity` 里的一个
 * 283 行底部弹窗。改独立页面的三个理由：
 * 1. **信息量**：8 个设置项 + 主题色圆点行 + 两条长说明，半屏弹窗里要滚，
 *    以后还要加「数据导出」「测试连接」，弹窗塞不下。
 * 2. **返回手势**：BottomSheet 内置的 back 是两段式的（第一次 back 先滚回顶部，
 *    第二次才关），原来专门写了一段 `BackHandler` + `sheetState.hide()` 去绕，
 *    还踩过"卡在半收起状态"的坑 —— 独立页面的返回天然正确，那段 hack 可以删掉。
 * 3. **拆巨石**：283 行从 3226 行的 `MainActivity` 里搬出来。
 *
 * 【0916 层级修正】原来只有 3 档字号，且中间那档（`labelLarge`）**塞了 8 个不同性质
 * 的东西**：4 个分区标题（主题颜色 / 在线识别 / B站账号 / 字幕翻译）和 4 个设置项名
 * （深色模式 / 悬浮窗 / 启动时继续播放 / 播放页编辑模式）全挤在同一档、同一字号，
 * 而且 4 个分区标题里只有「主题颜色」做了灰化，另外 3 个没有 —— 处理还不一致。
 * 现在改成：**分区标题在卡片外（主色小字加粗）、设置项在卡片内（常规正文）**，
 * 靠「容器」而不是靠「字号差」区分，一眼就能分开。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(
    themeIndex: Int,
    darkMode: Boolean,
    floatOn: Boolean,
    resumeOn: Boolean,
    layoutEdit: Boolean,
    onThemeIndexChange: (Int) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onFloatChange: (Boolean) -> Unit,
    onResumeChange: (Boolean) -> Unit,
    onLayoutEditChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    // 独立页面：系统返回键直接回上一页（不再需要 BottomSheet 那套 hide() 动画 hack）
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val context = LocalContext.current

            // ---------------- 外观 ----------------
            SettingsGroup("外观") {
                Text(
                    text = "主题颜色",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MACARON_COLORS.forEachIndexed { i, c ->
                        val selected = i == themeIndex
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (darkMode) c.dark else c.light)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { onThemeIndexChange(i) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                SettingsSwitch("深色模式", darkMode, onDarkModeChange)
                SettingsSwitch(
                    text = "悬浮窗",
                    checked = floatOn,
                    onCheckedChange = onFloatChange,
                    // 【0916】阿亮："悬浮窗应该做一下解释"。短说明就放这一项下面，
                    // 完整权限清单在「软件介绍 → 权限说明」。
                    note = "在其他应用上方显示歌词与播放控制。首次开启会跳系统授权页，需要允许「显示在其他应用上层」。",
                )
            }

            // ---------------- 播放 ----------------
            SettingsGroup("播放") {
                SettingsSwitch("启动时继续播放", resumeOn, onResumeChange)
                // ⚠️【0916 待办】这一项其实不该在设置里：它是临时操作状态（开→拖模块→关），
                //    不是偏好项。理想位置是播放页上直接给个入口。这轮先保留，避免改动面过大。
                SettingsSwitch("播放页编辑模式", layoutEdit, onLayoutEditChange)
            }

            // ---------------- 发音 ----------------
            SettingsGroup("发音") {
                var speakSource by remember { mutableStateOf(SpeakPrefs.loadSource(context)) }
                Text(
                    text = "朗读用哪个声音",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = speakSource == SpeakPrefs.SOURCE_AUTO,
                        onClick = {
                            speakSource = SpeakPrefs.SOURCE_AUTO
                            SpeakPrefs.saveSource(context, SpeakPrefs.SOURCE_AUTO)
                        },
                        label = { Text("自动") },
                    )
                    FilterChip(
                        selected = speakSource == SpeakPrefs.SOURCE_TTS,
                        onClick = {
                            speakSource = SpeakPrefs.SOURCE_TTS
                            SpeakPrefs.saveSource(context, SpeakPrefs.SOURCE_TTS)
                        },
                        label = { Text("只用本地发音") },
                    )
                }
                Text(
                    text = "自动：单词和短句用有道真人音（更自然），整句太长的话有道读不了，会自动改用本地发音；" +
                        "只用本地发音：完全不联网，没网也能听。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                // 本地发音靠系统的 TTS 引擎，国内不少 ROM 没预装英文语音包 ⇒ 主动提示，别让他以为坏了
                var ttsStatus by remember { mutableStateOf(Pronounce.ttsStatus()) }
                LaunchedEffect(Unit) {
                    // 引擎初始化是异步的，刚进设置页时可能还没就绪，等一会儿再看一眼
                    repeat(12) {
                        delay(250)
                        val s = Pronounce.ttsStatus()
                        if (s != Pronounce.TtsStatus.NotReady) {
                            ttsStatus = s
                            return@LaunchedEffect
                        }
                    }
                    ttsStatus = Pronounce.ttsStatus()
                }
                // 🔴 两种"本地发音用不了"都要说清楚，不能让它静默：
                //    - NoEnglish：引擎在，但没装英文语音包 ⇒ 只能去下载
                //    - NotReady ：**压根没有可用的语音引擎**（部分国产 ROM 就这样）
                //      ⇒ 这时长句子会退回有道、而有道对长文本必 500 ⇒ 又变成"没声音"。
                //      阿亮报的"句子不响"就是这种静默失败，所以这里必须主动提示。
                if (ttsStatus == Pronounce.TtsStatus.NoEnglish) {
                    Text(
                        text = "系统里没有英文语音包，「本地发音」用不了，朗读还是走有道人声。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { openTtsSettings(context) }) {
                        Text("去系统设置下载")
                    }
                } else if (ttsStatus == Pronounce.TtsStatus.NotReady) {
                    Text(
                        text = "系统里没有可用的语音引擎，「本地发音」暂时用不了；" +
                            "遇到长句子可能会没声音。可以先去系统设置里装一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { openTtsSettings(context) }) {
                        Text("去系统设置看看")
                    }
                }
            }

            // ---------------- 识别 ----------------
            SettingsGroup("识别 · 自动生成字幕") {
                var asrProvider by remember { mutableStateOf(AsrPrefs.loadProvider(context)) }
                Text(
                    text = "识别服务商",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 3,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsrProvider.values().forEach { p ->
                        FilterChip(
                            selected = asrProvider == p.name,
                            onClick = {
                                asrProvider = p.name
                                AsrPrefs.saveProvider(context, p.name)
                            },
                            label = { Text(p.label) },
                        )
                    }
                }
                var asrKey by remember { mutableStateOf(AsrPrefs.loadKey(context)) }
                OutlinedTextField(
                    value = asrKey,
                    onValueChange = {
                        asrKey = it
                        AsrPrefs.saveKey(context, it)
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("粘贴服务商提供的 Key，仅存本机") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            // ---------------- B站 ----------------
            SettingsGroup("B站 账号（可选）") {
                var biliSess by remember { mutableStateOf(AsrPrefs.loadBiliSessdata(context)) }
                OutlinedTextField(
                    value = biliSess,
                    onValueChange = {
                        biliSess = it
                        AsrPrefs.saveBiliSessdata(context, it)
                    },
                    label = { Text("B站 SESSDATA（登录态）") },
                    placeholder = { Text("粘贴浏览器 cookie 里的 SESSDATA") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "登录一次即记住：用于抓需登录的 B站 字幕。留空走游客，大部分视频不受影响。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ---------------- 翻译 ----------------
            SettingsGroup("字幕翻译") {
                var translateModel by remember { mutableStateOf(AsrPrefs.loadTranslateModel(context)) }
                OutlinedTextField(
                    value = translateModel,
                    onValueChange = {
                        translateModel = it
                        AsrPrefs.saveTranslateModel(context, it)
                    },
                    label = { Text("翻译模型") },
                    placeholder = { Text("Qwen/Qwen2.5-7B-Instruct") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "需 SiliconFlow Key。主模型排队/限流时自动切换 HY3 限时免费版兜底。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ---------------- 数据 ----------------
            SettingsGroup("数据") {
                // 导出目的地交给用户选（SAF），可以直接存网盘 / 电脑 / 发给自己 ——
                // 只有落到 App 私有目录之外，才算真的"备份住了"。
                val exportLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val ok = DataBackup.writeTo(context, uri)
                    Toast.makeText(
                        context,
                        if (ok) "备份已导出" else "导出失败，换个位置再试",
                        Toast.LENGTH_LONG,
                    ).show()
                }

                // 导入：只合并不覆盖，导错了也不会丢东西
                val importLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val r = DataBackup.importFrom(context, uri)
                    Toast.makeText(context, r.message, Toast.LENGTH_LONG).show()
                }

                // 单词表（TXT）：只给国内单词本软件吃，一行一个词
                val wordListLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/plain")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val n = DataBackup.wordCount(context)
                    val msg = when {
                        n == 0 -> "生词本里还没有单词"
                        DataBackup.writeWordListTo(context, uri) -> "已导出 $n 个单词"
                        else -> "导出失败，换个位置再试"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }

                // Anki 卡片（CSV）：拿出去用 Anki 的间隔重复复习
                val ankiLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/csv")
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val n = DataBackup.entryCount(context)
                    val msg = when {
                        n == 0 -> "生词本里还没有内容"
                        DataBackup.writeAnkiCsvTo(context, uri) -> "已导出 $n 张卡片"
                        else -> "导出失败，换个位置再试"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }

                DataRow(
                    title = "导出备份",
                    note = "生词本 + 字幕库打包成一个 JSON，存到你自己选的地方（网盘 / 电脑都行）。换手机、清数据前先来这一下。",
                ) { exportLauncher.launch(DataBackup.defaultFileName()) }

                DataRow(
                    title = "导入备份",
                    note = "从以前导出的 JSON 恢复。已有的不会重复加，也不会删你现在的任何东西。",
                ) { importLauncher.launch(arrayOf("application/json", "text/plain")) }

                DataRow(
                    title = "导出单词表（TXT）",
                    note = "只导出单词（不含收藏的整句），一行一个，可以直接导入欧路 / 有道 / 不背单词的生词本。",
                ) { wordListLauncher.launch(DataBackup.wordListFileName()) }

                DataRow(
                    title = "导出 Anki 卡片（CSV）",
                    note = "单词和收藏的整句都导，正面英文、背面中文（含音标、词性、来源）。Anki 里「导入文件」直接选它即可，分隔符保持默认的逗号。",
                ) { ankiLauncher.launch(DataBackup.ankiCsvFileName()) }
            }

            // ---------------- 关于 ----------------
            SettingsGroup("关于") {
                var showIntro by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIntro = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "软件介绍",
                        // 【0916】原来是 titleMedium（跟页面标题「设置」同级），显得它像个大区块，
                        // 其实只是个入口 ⇒ 降一档到 bodyMedium，跟其他设置项平级。
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showIntro) {
                    AlertDialog(
                        onDismissRequest = { showIntro = false },
                        confirmButton = {
                            TextButton(onClick = { showIntro = false }) { Text("关闭") }
                        },
                        title = { Text("软件介绍") },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                IntroBlock(
                                    "阅读模式",
                                    "进入后，点任意句子 = 从该句开始复读（播到句尾自动停）；长按句子 = 展开本句翻译，并可整句或逐词加入生词本。无歌词/字幕的音频可先在顶栏点「生成字幕」在线识别。阅读模式会跟随你进来的模式：从音频模式进读音频字幕，从视频模式进读该视频字幕，点视频字幕某句会跳回视频并定位到那一句。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "在线识别（生成字幕）",
                                    "无歌词文件的音频，可自动在线识别生成逐句英文文本，用于跟读 / 翻译。默认硅基流动（国内直连、免信用卡）；Groq / OpenAI 服务器在海外，需自行联网。Key 仅保存在本机，仅用于向所选服务商鉴权。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "播放页编辑模式",
                                    "开启后播放页模块可拖动换位、拖底部 ≡ 调高，返回键退出。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "生词本",
                                    "收藏的单词 / 整句，可在顶栏或阅读页「生词本」入口查看与复习。" +
                                        "词一多翻列表找不到，所以上面有一排筛选档（全部 / 未掌握 / 已掌握 / 单词 / 句子，都带条数）" +
                                        "和一个搜索框（能搜单词、翻译、词性释义、来源 —— 不记得拼写时输中文也行）。" +
                                        "顶部一行「共 N 条 · 已掌握 M 条」是总进度。筛选不会记住，每次进来都是「全部」，" +
                                        "免得你以为词丢了。每条词后面那排星星就是熟练度：每连对 1 次亮 1 颗，" +
                                        "亮满 3 颗算「已掌握」（会退出复习队列），继续复习还能涨到 4 颗、5 颗。没复习过的词不显示星星。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "复习（把生词过一遍）",
                                    "入口在生词本右上角「复习 N」，N 是还没掌握的词数。玩法就一句话：" +
                                        "看到英文先在心里想意思 → 点一下卡片看答案 → 老实点「认识」或「不认识」。" +
                                        "答「认识」连对次数 +1，答「不认识」清零回到队列重来。" +
                                        "一轮 20 张，优先挑最久没看过的；答错的当场插回队尾再问一遍（最多来回 3 轮），" +
                                        "所以一轮结束时你确实把这一批都认了一遍。走完报个数：这轮做了几张、认识几个、答对率多少。" +
                                        "「再来一轮」会从剩下没掌握的里面再凑 20 张。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "为什么是「连对 3 次」而不是艾宾浩斯那套",
                                    "Anki 那种间隔重复要维护「间隔天数表 + 今天到期队列」，" +
                                        "第一次用会看到「今天没有要复习的」然后一脸懵 —— 那套适合天天刷的重度用户。" +
                                        "这里用的是最简单、随时能玩、一定有终点的规则：连对 3 次就掌握，答错就回炉。" +
                                        "卡片正面只有词，翻面前不给「认识 / 不认识」按钮 —— 没看答案就能点「认识」的话，那叫自欺欺人。" +
                                        "卡片上两个喇叭是「美音」和「英音」，点一下跟读，对照着听能听出区别。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "复习时自动发音",
                                    "复习页右上角有个「自动读」开关，打开后每翻到新的一张卡就自动把词念一遍，" +
                                        "不用再抬手去点喇叭 —— 眼睛看词、耳朵听音，两边一起过印象更深。" +
                                        "开关没打开的时候旁边不会冒出口音选项，因为不读的话「读哪个口音」是个没意义的问题。" +
                                        "打开后旁边会出现一颗小胶囊（写着当前是美音还是英音），点它展开就能切；" +
                                        "切完立刻用新口音念一遍当前这张，你马上能听出区别。" +
                                        "开关和口音都会记住，下次进来还是你上次的选择。" +
                                        "默认是关的 —— 发音要联网下音频，不打招呼就出声会吓人一跳，所以留给你自己开一次。" +
                                        "卡正面那两个「美音」「英音」按钮一直都在，自动读只负责省掉你每次动手。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "视频模式",
                                    "顶栏「视频选集」列出当前视频所在文件夹里的其他视频，点一下即切换；「生成字幕」可自动在线识别生成逐句字幕。播放时点字幕某句 = 从那句开始；上一句 / 下一句按钮精准跳句；支持全屏、调速与单句循环跟读。按「上一句」时会按音频模式的「重播次数」把该句复读几遍（两个模式共用这一项设置，视频里无需单独调），次数设为 ×1 则只跳句不复读。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "抓 B站 视频",
                                    "在视频模式里，左滑下方功能键即可露出 B站 链接输入框，粘贴链接就会在后台自动抓取：有官方字幕直接用并配中文翻译；没有则下载音频在线识别生成逐句字幕。抓取在后台进行，不影响你正在看的视频；加载完成后自动跳到该视频并播放（音频版会自动进入复读机播放）。成品（视频 + 双语字幕）落在 Download/XuanjiBili。需联网；无字幕视频还需在设置里填 ASR Key；翻译模型排队时自动切 HY3 限时免费版兜底。字幕翻译沿用长按字幕的原有逻辑，无需额外开关。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "其他设置",
                                    "主题颜色 / 深色模式调整外观；悬浮窗可在其他应用上方显示播放控制；「启动时继续播放」控制重新打开时是否续播。",
                                )
                                Spacer(Modifier.height(14.dp))
                                // 【0916】阿亮："什么功能需要什么权限也说一下"。
                                // 清单较长，按规矩放软件介绍里；悬浮窗那一项下面另有一句短说明。
                                Text(
                                    "权限说明",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                PermissionBlock(
                                    "悬浮窗（显示在其他应用上层）",
                                    "在其他 App 上方显示歌词与播放控制。不开启完全不影响使用，只有你主动打开时才需要授权。",
                                )
                                PermissionBlock(
                                    "读取存储",
                                    "扫描你手机里的音频 / 视频文件，生成选集和文件夹列表。",
                                )
                                PermissionBlock(
                                    "写入存储",
                                    "保存抓取的 B站 视频（落在 Download/XuanjiBili），以及你手动导出的备份文件。",
                                )
                                PermissionBlock(
                                    "通知",
                                    "后台播放时的常驻控制条，锁屏状态下也能切歌、暂停。",
                                )
                                PermissionBlock(
                                    "联网",
                                    "只用于三件事：在线识别字幕、字幕翻译、抓取 B站 视频。API Key 和 Cookie 只存在你本机，不上传。",
                                )
                                PermissionBlock(
                                    "后台播放",
                                    "让音频在切到后台或锁屏后继续播放，不被系统回收。不申请的话退到后台就断。",
                                )
                            }
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 设置分组：分区标题（主色小字加粗）在**卡片外**，设置项在**卡片内**。
 * 【0916】靠「容器」分区，而不是靠字号差 —— 比原来靠 3 档字号硬分清楚得多。
 */
/**
 * 跳到系统的「文字转语音」设置页，让用户下载英文语音包。
 * ⚠️ `com.android.settings.TTS_SETTINGS` **不是官方常量**，部分 ROM 认不出 ⇒ 兜底跳应用详情页。
 */
private fun openTtsSettings(ctx: Context) {
    val candidates = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}"),
        ),
    )
    for (i in candidates) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // 换下一个候选
        }
    }
    Toast.makeText(
        ctx,
        "打不开系统设置，请手动去「设置 → 语言 / 无障碍 → 文字转语音」里下载英文语音包",
        Toast.LENGTH_LONG,
    ).show()
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            // 跟播放页模块卡片同一套观感（primaryContainer 半透明）
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                content = content,
            )
        }
    }
}

/**
 * 一行「文字 + 开关」。
 * @param note 可选项下面的补充说明（12sp 灰字）。说明短的就直接写在这儿，
 *             长篇的（比如权限清单）放软件介绍里 —— 阿亮 0916 定的规矩。
 */
@Composable
private fun SettingsSwitch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    note: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, end = 52.dp),
            )
        }
    }
}

/** 「数据」分组里的一条：标题（可点）+ 下方灰字说明 */
@Composable
private fun DataRow(title: String, note: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

/** 权限说明里的一条：权限名（主色）+ 用途（灰字小字） */
@Composable
private fun PermissionBlock(name: String, usage: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = usage,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 软件介绍里的一段：小标题 + 正文。【0916】从 MainActivity 一起搬过来。 */
@Composable
private fun IntroBlock(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
