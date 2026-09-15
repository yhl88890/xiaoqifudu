package com.xuanji.repeater

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
                                    "收藏的单词 / 整句，可在顶栏或阅读页「生词本」入口查看与复习。",
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
