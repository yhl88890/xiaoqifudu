package com.xuanji.repeater

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
/**
 * 「试听这个音色」用的范文。
 * ⚠️ 必须**长过有道的长度上限**（英音 40 字符），否则这段会走去有道、试不出在线合成那条路。
 * 这句 94 个字符，稳过。
 */
private const val SPEAK_TEST_SENTENCE =
    "The best way to improve your English is to listen to real conversations every single day."

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

            // 🔴🔴【v44 阿亮："你选哪个模型就填哪个模型的 API 吗？总不能是代码里地址写死的呢"】
            //   他是对的。服务商从这一版起是**「识别 + 字幕翻译 + 长句发音」三处共用的总开关**，
            //   所以这个状态必须提到**页面级**：
            //   下面「发音」组在「识别 · 翻译」组**前面**，原来它是那组里的局部变量，
            //   发音组根本读不到 ⇒ 换了服务商音色列表不会跟着变（= 拿别家的音色 id 发请求 = 4xx = 长句哑）。
            var asrProvider by remember { mutableStateOf(AsrProvider.current(context)) }

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
                // 【0919】说明从「软件介绍 → 悬浮窗」搬回来了（他嫌那波删得太干净）。
                //    这句主要防一个误会：别以为它是"显示视频画面"的悬浮窗。
                // 【0919 第二轮 · 阿亮改口】他嫌「别的 App 看歌词台词」**不但没防住误会、
                //    本身还是个病句**（原话："什么叫别的 APP 看歌词台词的？这叫什么表述啊"）
                //    ⇒ 他直接定成「仅显示字幕」——正面说清"只出字幕、不出画面"，一次解决两件事。
                //    🔴 病根：v49 为了缩短，把原句「别的 App 看**上**歌词、台词」的「上」删了，
                //    「别的 App」于是从**地点状语**变成**主语** ⇒ 读成"别的 App 在看歌词"。
                //    ⚠️ 以后写这类"地点 + 看某物"的短说明，**别省掉「上 / 里」**。
                SettingsSwitch(
                    "悬浮窗", floatOn, onFloatChange,
                    note = "仅显示字幕",
                )
            }

            // ---------------- 播放 ----------------
            SettingsGroup("播放") {
                // 🔴【0919】这个开关**只管音频**，而且语义是"粗粒度"的：
                //    开着 = 每次打开 App 都自动接着上次位置续播（**不看上次是播还是暂停**）；
                //    关了 = 一律不自动播，等你手动点。
                //    视频**不听它的**：视频永远是"上次在播就接着播、上次暂停就还是暂停"。
                //   ⇒ 他拍板「不删开关，改成把这层关系写清」（删的话要给音频补一个
                //      `wasPlaying` 字段，改动反而大）。括号里只提"不管视频"就够，别再啰嗦 ——
                //      他原话："1 不用，因为这字儿太多了。应该要换行了吧。"
                SettingsSwitch(
                    "启动时继续播放", resumeOn, onResumeChange,
                    note = "只管音频",
                )
                // ⚠️【0916 待办，仍未做】这一项其实不该在设置里：它是临时操作状态（开→拖模块→关），
                //    不是偏好项。理想位置是播放页上直接给个入口。0919 先用括号说明缓解，位置照旧。
                SettingsSwitch(
                    "播放页编辑模式", layoutEdit, onLayoutEditChange,
                    note = "拖动换位拉伸",
                )
            }

            // ---------------- 发音 ----------------
            SettingsGroup("发音") {
                // 🔴🔴【0919 阿亮拍板：把原来这一项「朗读用哪个声音」整行删掉】
                //   他原话："这选那肯定是选自动啊……他们又是互相托底的状态嘛，我感觉好像没什么必要。"
                //   我核了代码，**他对**，而且比"没必要"更严重 —— 那个「只用本地发音」**名不副实**：
                //     系统 TTS 读不出来时它会照常联网（在线合成 / 有道切块），承诺的离线保证不了；
                //     而对"本地 TTS 用不了"的机器（他真机就是）它 100% 等价于「自动」。
                //   ⇒ 选项、`speakSource`、置灰用的 `autoMode` 一起删。发音怎么走由
                //     `Pronounce.speakInternal` 定死；解释搬进「软件介绍 → 朗读是怎么发音的」。
                //   ⚠️ **别再把它加回来**，也别新增同类的"通道开关"——加之前先想清它承诺的是什么。
                // 🔴【v44】音色跟着「识别服务商」走。`remember(asrProvider)` 是必须的：
                //   换家时要**重新读这一家的**音色存档，否则会拿着别家的音色 id 去发请求
                //   （接口 4xx ⇒ 长句静默变哑，界面上什么都看不出来）。
                var voiceId by remember(asrProvider) { mutableStateOf(SpeakPrefs.loadVoice(context, asrProvider)) }
                var voiceMenu by remember { mutableStateOf(false) }
                val voiceLabel = asrProvider.voiceLabel(voiceId)
                val dim = MaterialTheme.colorScheme.onSurfaceVariant

                Text(
                    // 【0919 改文案】原来写"自动模式下，超过 40 字的音色" ——
                    //   「自动」这个"模式"已经不存在了（选项删了），留着那个词只会让人再懵一次。
                    //   现在直说它管什么。字数跟原来一样（15 字），他 font_scale 1.1~1.3 也不会折行。
                    // 🔴【0917 统一字号】`labelMedium`(12sp) → `bodyMedium`(14sp)，
                    //   跟所有开关行同号（他报过"左右字体大小不一样"）。
                    text = "长句（超过 40 字）的朗读音色",
                    style = MaterialTheme.typography.bodyMedium,
                    color = dim,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                // 【0917 阿亮："把那个『试听这个音色』放到那几个音色的右边呀，那不就又少占一行吗"】
                //    ⇒ 音色按钮和试听按钮并成同一行。
                // 🔴 试听 = 自检。长句没声音的毛病以前是**静默失败**，只能远程看日志猜，
                //    给他一个按钮：念一遍 + Toast 说明"这次走的是哪条通道"，出问题他自己就看见了。
                // 【0919】原来这里写"选了「只用本地发音」时跟音色一起置灰" —— 那个选项删了，
                //    所以这个按钮现在**只在"这家没有语音合成接口"时才置灰**（目前三家都有，属于保险）。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        OutlinedButton(
                            onClick = { voiceMenu = true },
                            // 这家没有语音合成接口时按钮也置灰（目前三家都有，属于保险）
                            enabled = asrProvider.hasTts,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        ) {
                            // 不指定 style ⇒ 默认 `labelLarge`(14sp)，跟左边标题同号
                            Text(voiceLabel)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("▾")
                        }
                        AppDropdownMenu(
                            expanded = voiceMenu,
                            onDismissRequest = { voiceMenu = false },
                        ) {
                            // 🔴 v44：音色表来自当前服务商，不是写死的那 8 个
                            asrProvider.ttsVoices.forEach { (id, label) ->
                                DropdownMenuItem(
                                    // 勾选标记跟播放页「重播次数」那个下拉保持同一套写法
                                    text = { Text(if (id == voiceId) "$label ✓" else label) },
                                    onClick = {
                                        voiceId = id
                                        SpeakPrefs.saveVoice(context, asrProvider, id)
                                        voiceMenu = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            Toast.makeText(context, "试听中…", Toast.LENGTH_SHORT).show()
                            Pronounce.testLongSentence(SPEAK_TEST_SENTENCE) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                    ) { Text("试听这个音色") }
                }
                // 本地发音靠系统的 TTS 引擎，国内不少 ROM 没预装英文语音包、甚至没有引擎 ⇒ 主动提示，别让他以为坏了
                //
                // 🔴【0917 阿亮报："红字说系统里没有可用语音引擎，我去系统设置里看我是有引擎的，多邻国都能用"】
                //    老写法是"进本页后轮询 3 秒，拿不到就不再刷新"，而引擎初始化是**异步**的
                //    （首次要绑引擎服务，国产 ROM 上更慢）⇒ 只要比那 3 秒慢，就永远显示"没有引擎"，
                //    他之后在系统里补装了也不会自己变 —— 看着就是误报。
                //    现在改成事件驱动：Pronounce 每次状态变化都把 ttsVersion +1，这里跟着重算。
                val ttsVer by Pronounce.ttsVersion.collectAsStateWithLifecycle()
                val ttsStatus = remember(ttsVer) { Pronounce.ttsStatus() }
                when (ttsStatus) {
                    // 还在初始化：🔴 **不许用报错的口气**，也不断言"没有引擎"。
                    // 就一行淡色小字，等回调回来自己会换掉。
                    Pronounce.TtsStatus.Checking -> Text(
                        text = "正在检查系统语音引擎…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    // 有英文语音，一切正常：什么都不说（没问题就别占他注意力）
                    Pronounce.TtsStatus.Ok -> Unit
                    Pronounce.TtsStatus.NoEnglish -> {
                        Text(
                            // 【0919 改文案】原来是"……朗读还是走有道人声"—— 半对：短句走有道，
                            //   长句走**在线合成**。而且「朗读用哪个声音」那个选项已删，
                            //   本机发音现在只是长句链路的第 ② 道兜底 ⇒ 口气从"你有问题"改成"不影响听"。
                            text = "系统没装英文语音包，本机发音用不上；不影响听 —— " +
                                "短句走有道人声、长句走在线合成。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TtsDiagLine(ttsVer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { openTtsSettings(context) }) {
                                Text("去系统设置下载")
                            }
                            // 「重新检查」：他在系统里装完语音包不用重启 App
                            TextButton(onClick = { Pronounce.retryTts() }) { Text("重新检查") }
                        }
                    }
                    Pronounce.TtsStatus.NoEngine -> {
                        Text(
                            // 【0919 改文案】同上：本机发音只是长句链路的第 ② 道兜底，缺了不影响正常听。
                            text = "没检测到可用的语音引擎，本机发音用不上；不影响听 —— " +
                                "长句会走在线合成。想在系统里装也行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        TtsDiagLine(ttsVer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { openTtsSettings(context) }) {
                                Text("去系统设置看看")
                            }
                            TextButton(onClick = { Pronounce.retryTts() }) { Text("重新检查") }
                        }
                    }
                }
            }

            // ---------------- 生词提醒 ----------------
            SettingsGroup("生词提醒") {
                // 0917 阿亮要做「通知栏轮播生词」：隔一阵子换一个词，瞄一眼就当复习。
                // 实现细节全在 VocabReminder.kt（AlarmManager，不是前台服务 —— 理由见那个文件头）。
                // 🔴 0919 改了两处口径（他报的）：池子从"只挑没掌握的"改成**全本**、
                //    "今天做完了没"的判据从"复习过一张卡"改成"**练完一整轮**"。
                var remindOn by remember { mutableStateOf(VocabReminder.isOn(context)) }
                var interval by remember { mutableStateOf(VocabReminder.loadInterval(context)) }
                // 【0923】「怎么让它一直响」的指引弹窗。两个入口都开：
                //   ① **刚打开这个开关时自动弹**（他点名的："就是要打开通知的时候就弹这个"）
                //   ② 开关下方的常驻一行 ⇒ 🔴 这条是**必须的**，不是可选项：
                //      他手机上这个功能**早就是开着的**，只在"开→关"那一刻弹，他这辈子都见不到。
                var showKeepAlive by remember { mutableStateOf(false) }

                // Android 13+ 通知要**运行时**授权，没有的话 notify() 会被系统直接丢掉。
                // 所以开关不能只写 SP：先要权限，拿到结果再决定开不开。
                val notifPerm = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        // 🔴🔴【0922 补的】这里以前**忘了把 remindOn 置 true**（只有"拒绝"那一支写了）。
                        // 后果：第一次开这个开关时，系统弹出权限框 → 他点允许 → 权限给了、SP 写上了、
                        // 闹钟排了、通知也发出来了，**但界面开关还是显示"关"**，下面「多久换一个」也不出现
                        // ⇒ 他一看"没开成"，又去点一下，体验上就是"这开关要按两下才认"。
                        // 真机复现：Redmi `23054RA19C`，给完权限后 dump 出来 checked 仍是 false。
                        remindOn = true
                        // 【0923】成功不再弹 Toast —— 改成弹「保活指引」弹窗（阿亮要的："就是要打开
                        //   通知的时候就弹这个"）。只有**非正常结果**才额外补一句 Toast，
                        //   免得"已开启 XX"的提示和弹窗叠在一起。
                        val r = VocabReminder.setOn(context, true)
                        if (r != VocabReminder.Result.Posted) {
                            Toast.makeText(context, remindToastText(r), Toast.LENGTH_LONG).show()
                        }
                        showKeepAlive = true
                    } else {
                        remindOn = false
                        VocabReminder.setOn(context, false)
                        Toast.makeText(context, "系统没给通知权限，提醒发不出来", Toast.LENGTH_LONG).show()
                    }
                }

                SettingsSwitch(
                    text = "在通知栏轮播生词",
                    checked = remindOn,
                    onCheckedChange = { want ->
                        if (!want) {
                            remindOn = false
                            VocabReminder.setOn(context, false)
                        } else if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            remindOn = true
                            val r = VocabReminder.setOn(context, true)
                            if (r != VocabReminder.Result.Posted) {
                                Toast.makeText(context, remindToastText(r), Toast.LENGTH_LONG).show()
                            }
                            showKeepAlive = true
                        }
                    },
                    // 🔴 「像每日任务」那句是 0917 阿亮定的口径，别删、也别写成"没词就不提醒"
                    // 【0917 精简】那句话本身没错，但按他的要求整句搬进了「软件介绍 → 在通知栏轮播生词」。
                    //    这里不再挂 note —— 设置页只留标题和控件。
                )

                if (remindOn) {
                    // 【0917 阿亮："生词提醒那一栏，多久换一个？那几个选项 15 / 30 / 1 小时 /
                    //    2 小时，也给它弄成小弹窗放到『多久换一个』的右边，这不又少了两行吗"】
                    PickerRow(
                        label = "多久换一个",
                        options = VocabReminder.INTERVAL_CHOICES,
                        current = interval,
                        labelOf = { if (it < 60) "$it 分钟" else "${it / 60} 小时" },
                        onPick = {
                            interval = it
                            VocabReminder.saveInterval(context, it)
                        },
                    )
                    // 【0917 精简】原来这里有一段「有词的时候轮播：只挑还没掌握的…」，
                    // 已搬进「软件介绍 → 在通知栏轮播生词」。

                    // 🔴🔴【0923 新增】常驻的「保活」入口。
                    //   起因：他亲测「把后台划掉提醒就没了」（实测是 `am force-stop` ⇒ 闹钟被
                    //   **删除**，不是压住，所以改硬提醒也救不回来）。App 这边能做的只有
                    //   「把系统设置怎么改讲清楚 + 一键跳过去」。
                    //   ⚠️ 放在 `if (remindOn)` 里是他点头过的口径：没开提醒就不需要保活。
                    //   ⚠️ 只跳 app 详情页 —— 真机实测（Redmi 23054RA19C / MIUI V816）：
                    //      `INSTALLED_APP_DETAILS` ✓ 直达，而且**省电策略 + 自启动 就在同一页**，
                    //      一次跳转够用；`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 在这台是
                    //      "No activity found"，`com.miui.powerkeeper/*` 也没有设置界面 ⇒ 都别用。
                    DataRow(title = "怎么让它一直提醒") { showKeepAlive = true }
                }

                if (showKeepAlive) {
                    AlertDialog(
                        onDismissRequest = { showKeepAlive = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showKeepAlive = false
                                openAppBatterySettings(context)
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showKeepAlive = false }) { Text("知道了") }
                        },
                        title = { Text("怎么让它一直提醒") },
                        text = {
                            // 🔴🔴【0923 阿亮当场否掉第一版】原话："那个弹窗的内容太多了，太复杂了，
                            //   这种复杂的内容都放在软件介绍里。弹窗只告诉他你要打开什么什么什么
                            //   这个功能才能实现。就行了。"
                            //   ⇒ 弹窗**只列"要开哪几样"**，原因/原理/兜底全搬去「软件介绍」。
                            //   第一版把"为什么会被停掉 + 四步 + 兜底"全塞进去，真机量下来正文
                            //   占了 1870px，④ 和末尾那句直接被挤出屏幕 ⇒ 又长又看不全。
                            Text(
                                text = "需要打开这三样：\n" +
                                    "① 通知 → 允许\n" +
                                    "② 省电策略 → 无限制\n" +
                                    "③ 自启动 → 允许\n" +
                                    "点「去设置」直达，这三样都在那一页上。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                    )
                }
            }

            // ---------------- 识别 · 翻译（共用一把 Key） ----------------
            // 【0918 阿亮："把它跟那个填 API Key 的放到一个类目里头，放到一块儿"】
            //  原来「字幕翻译」是独立一组，里面放一个**手填框** —— 他的原话："看着像要我填东西，
            //  这个误导很严重"，而且识别那边是下拉、这边是手填，同一页两套做法。
            //  现在两处一起改：
            //   ① 两组并成一组（本来就是同一件事：同一把 Key、同一家硅基流动，
            //      软件介绍里那句「一把 Key 管四处」说的就是这个）
            //   ② 翻译引擎改成跟「识别服务商」**同款的下拉**（`PickerRow`），**不给手填**
            //      ⇒ 从根上消灭"型号打错一个字母就静默失败"
            SettingsGroup("识别 · 翻译") {
                // 【0917 阿亮："识别服务商也一样，那 3 个服务商也给它弄成点击小弹窗，
                //    放到『识别服务商』这几个字的右边，这不又少了两行"】
                // 🔴 这个控制的是**整页三处**（识别 / 翻译 / 发音），状态在页面顶部，别在这儿再声明一个。
                PickerRow(
                    label = "识别服务商",
                    options = AsrProvider.values().toList(),
                    current = asrProvider,
                    labelOf = { it.label },
                    onPick = {
                        asrProvider = it
                        AsrPrefs.saveProvider(context, it.name)
                    },
                )
                var asrKey by remember { mutableStateOf(AsrPrefs.loadKey(context)) }
                OutlinedTextField(
                    value = asrKey,
                    onValueChange = {
                        asrKey = it
                        AsrPrefs.saveKey(context, it)
                    },
                    // 【0917 阿亮："那几个框儿之前你把它改短了，现在看着改短了很别扭，
                    //    你还不如把它整个铺开"】⇒ 撤销 v31 的 66% 收窄，恢复整行铺开。
                    //    原来那行独立的"免费、免信用卡…"说明行也撤了 —— 信息并进 label，
                    //    省一行；完整申请步骤在「关于 → 软件介绍」。
                    label = { Text("API Key（免费注册）") },
                    placeholder = { Text("粘贴服务商提供的 Key，仅存本机") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
                // 【0918 手填框 → 下拉】【v44 列表跟着服务商走】
                //   🔴 模型名跨家不通用：把 `Qwen/...` 发给 Groq 直接 400，而 400 不算"排队"
                //      ⇒ 不进兜底 ⇒ 翻译静默变空。所以必须 `forProvider(asrProvider)`；
                //      而且 `remember(asrProvider)` —— 换家时要重新读**这一家**存的模型。
                var translateModel by remember(asrProvider) {
                    mutableStateOf(
                        SubtitleTranslator.Model.ofId(
                            asrProvider,
                            AsrPrefs.loadTranslateModel(context, asrProvider),
                        )
                    )
                }
                PickerRow(
                    // 【0918 阿亮："「翻译模型」改成「翻译引擎」"】
                    //   用户不懂"模型"，但懂"引擎" —— 而且这一项实际选的就是"用谁去翻"。
                    //   ⚠️ 改名要连「软件介绍」里所有引用一起改，不然他按名字去搜会搜不到。
                    label = "翻译引擎",
                    options = SubtitleTranslator.Model.forProvider(asrProvider),
                    current = translateModel,
                    labelOf = { it.label },
                    onPick = {
                        translateModel = it
                        AsrPrefs.saveTranslateModel(context, asrProvider, it.id)
                    },
                )
            }

            // ---------------- B站 ----------------
            // 【0918 阿亮："B站账号可选和字幕翻译上下位置换一下，就是可选的都放到下边儿"】
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
                    // 【0917 铺开】撤销 v31 的 66% 收窄 —— 他说改短了看着别扭。
                    modifier = Modifier.fillMaxWidth(),
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

                // 【0917 精简】四条各自的长 note 全部搬进「软件介绍 → 数据（导出 / 导入）」，
                //    设置页只留四个可点的标题。
                DataRow(title = "导出备份") { exportLauncher.launch(DataBackup.defaultFileName()) }

                DataRow(title = "导入备份") {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                }

                DataRow(title = "导出单词表（TXT）") {
                    wordListLauncher.launch(DataBackup.wordListFileName())
                }

                DataRow(title = "导出 Anki 卡片（CSV）") {
                    ankiLauncher.launch(DataBackup.ankiCsvFileName())
                }
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
                                // 🔴🔴【0917 阿亮定的头版】他原话："要重点强调一下…我们这个软件最初
                                //    最突出的功能就是这个「上一句」会重复 3 遍或者几遍，这个是可调的…
                                //    跟其他家的复读类产品或者复读机都不一样，因为其他家的都是 AB 复读的最多。"
                                //    ⇒ 「上一句复读」必须排第一，而且要把"跟别家怎么不一样"讲出来。
                                //    事实核对（别写错）：默认 ×3，可选 1/3/5/7，入口在**播放页**的
                                //    「重播次数」chip（**不在设置页**）；音频走 PlayerService.prevSentence()，
                                //    视频共用同一份 RepeatPrefs，所以视频里不用单独调。
                                IntroBlock(
                                    "这个软件最不一样的地方：「上一句」会替你反复播",
                                    "别的复读机想复读一句，你得自己掐头去尾圈一个 A 点、B 点，圈完还得清掉，换一句又得重圈 —— 这就是 AB 复读。\n" +
                                        "这里不用圈。点一下「上一句」，它就把那一句反复播给你听，默认 3 遍，播完自己往下走。\n" +
                                        "嫌少嫌多就调：播放页那颗「重播次数」，点开能选 1 / 3 / 5 / 7 遍，选完就记住。\n" +
                                        "为什么能这么省事：句子的头尾是程序自己切好的（有歌词、字幕就按歌词切，没有就先在线识别断句），" +
                                        "所以你点的是「一句」，不是一个时间点。\n" +
                                        "音频和视频两个模式共用这一个设置 —— 在视频里听也照这个遍数来，不用两边各调一次。\n" +
                                        "设成 1 遍就是只跳句、不重复。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "阅读模式",
                                    "进入后，点任意句子 = 从该句开始复读（播到句尾自动停）；长按句子 = 展开本句翻译，并可整句或逐词加入生词本。无歌词/字幕的音频可先在顶栏点「生成字幕」在线识别。阅读模式会跟随你进来的模式：从音频模式进读音频字幕，从视频模式进读该视频字幕，点视频字幕某句会跳回视频并定位到那一句。",
                                )
                                Spacer(Modifier.height(12.dp))
                                // 🔴🔴【0917 阿亮新增要求】他的原话："所有要填 API 的地方要跟人说清楚：
                                //   用的是哪个模型？为什么用这个模型？去哪儿申请、官网是哪个、
                                //   或者搜哪个关键词？它是免费的，这个在「为什么」里头就跟他说清楚。"
                                //   ⇒ 这一节就是照这四条写的。顺序别乱：模型 → 为什么 → 怎么申请 → 花多少钱。
                                //   ⚠️ 事实（0917 查过硅基流动官网价格页，别凭印象改）：
                                //   TeleAI/TeleSpeechASR 与 FunAudioLLM/SenseVoiceSmall 都在官方
                                //   **免费模型**名单里；新用户另送 16 元赠金（免费模型不吃赠金）。
                                //   注册需手机号 + 实名认证，免信用卡；免费模型速率固定、不可提额。
                                //   ⚠️ 正文里不许出现 markdown 星号 —— 这是纯 Text，会原样显示出来。
                                IntroBlock(
                                    "在线识别（生成字幕）",
                                    "没有歌词文件的音频，可以先在线识别，自动生成逐句英文，用来跟读和翻译。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "① 用的哪个模型",
                                    "硅基流动的 TeleAI/TeleSpeechASR。\n" +
                                        "另外两个可选：Groq 的 whisper-large-v3、OpenAI 的 whisper-1。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "② 为什么默认用它",
                                    "三个原因：\n" +
                                        "1. 它是免费的。TeleAI/TeleSpeechASR 就在硅基流动的官方免费模型名单里，" +
                                        "注册不用信用卡，用起来也不花钱。\n" +
                                        "2. 服务器在国内，直连就能用，不用折腾网络。Groq 和 OpenAI 的服务器在海外，" +
                                        "得自己解决联网，还可能要绑海外信用卡。\n" +
                                        "3. 它返回的结果带每句话的时间点，这点最关键 —— 没有时间点就切不出句子，" +
                                        "复读功能也就无从谈起。（硅基流动的 SenseVoiceSmall 识别也准，但它只回一整段" +
                                        "纯文本、不给时间点，所以没选它。）",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "③ 上哪申请（三步）",
                                    // 【0929 阿亮要求】把官网换成他的推广链接：别人从这个链接注册，
                                    //   他能拿邀请奖励。⚠️ 这个链接是**他个人的**，改之前先问他；
                                    //   哪天失效了他会来说，别自己改成官网。
                                    "1. 手机浏览器打开 cloud.siliconflow.cn/i/BsGqKWbf（搜关键词「硅基流动」也能找到）。\n" +
                                        "2. 手机号注册 → 按提示做实名认证（免费模型要实名之后才能用）。全程不用信用卡。\n" +
                                        "3. 登录后在左边找到「API 密钥」→ 新建一个 → 把那串 sk- 开头的复制下来，" +
                                        "粘到「设置 → 识别 · 自动生成字幕 → API Key」那个框里。\n" +
                                        "粘完就能用了。这个 Key 只存在你手机上，只会发给硅基流动做验证，不会传到别处。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "④ 要花多少钱",
                                    "识别用的是一个免费模型，所以正常用下来不花钱。新用户注册还会另送 16 元赠金" +
                                        "（用不到也没关系，不会自动扣）。\n" +
                                        "要注意的是：免费模型有速度上限、也不能提额 —— 偶尔会慢一点、忙的时候会排队，" +
                                        "但不会偷偷扣钱。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "一把 Key 管四处",
                                    "只需要填这一个 Key，下面四个地方都用它，不用分别去申请：\n" +
                                        "· 在线识别字幕\n" +
                                        "· 字幕翻译（给整批字幕配中文）\n" +
                                        "· 长句的在线发音合成\n" +
                                        "· 抓 B站 视频时的识别\n" +
                                        "这四处全都跟着上面「识别服务商」那一栏走：你换成 Groq，四处一起换成 Groq，" +
                                        "所以要填的是那一家自己的 Key，别混着来。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "国内还有别家吗？为什么没做进来",
                                    "有。阿里云百炼、火山引擎（豆包）、讯飞都能做语音识别，但它们不是同一套接口：" +
                                        "讯飞要三个密钥加自己算签名，火山要塞专用的请求头，百炼走的是另一条对话通道 —— " +
                                        "每一家都得单独写一段适配代码，不是填个网址就能通用的。\n" +
                                        "所以这个 App 目前只支持上面那三家（它们都是同一个标准接口）。" +
                                        "你要是在别处看到教人填百炼、火山，在这个 App 里是连不上的，别白折腾。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "字幕翻译用哪个引擎",
                                    "设置 →「识别 · 翻译」里头那个「翻译引擎」，是个下拉，点一下就能换。" +
                                        "里面有哪几个，是跟着上面「识别服务商」变的：\n" +
                                        "· 选硅基流动 → 通义千问（默认）、混元翻译（专用）、混元 Hy3（免费），这三个都免费；\n" +
                                        "· 选 Groq → GPT-OSS 120B（默认）、GPT-OSS 20B、Llama 3.3 70B；\n" +
                                        "· 选 OpenAI → GPT-4o mini（默认）、GPT-4o。\n" +
                                        "为什么不能混着挑？因为这三家的模型名字互不通用 —— 把硅基流动的模型名发给 Groq，" +
                                        "对方直接拒收。所以列表跟着服务商走，让你选不出错的东西。\n" +
                                        "想让字幕翻得更准，硅基流动那档可以换成「混元翻译（专用）」——" +
                                        "腾讯专门做翻译的模型，免费，翻字幕比通用模型地道。\n" +
                                        "它会自己兜底：选的那个排队、被限流、或者哪天被官方下线了（名字改了、这个模型没了），" +
                                        "都会自动换成同一家的另一个模型接着干 —— 换成功一次，这段视频剩下的字幕就直接用新的，" +
                                        "不会一批一批反复撞墙。只有联网本身出问题（断网、连不上服务器）它才不换，那时候换谁都没用。\n" +
                                        "还有，长按某一句查中文走的是另一条免费通道（有道 + MyMemory 竞速），" +
                                        "根本不需要 Key，也不受这里影响。",
                                )
                                Spacer(Modifier.height(12.dp))
                                // 【v44】新增一整节。这条是用户自己指出来的 bug（原话见 HANDBOOK
                                //    「服务商 / Key / 地址 三者错配」），而且**设置页的位置一点没动** ——
                                //    不写清楚他会以为"没改"或者"少了什么"。
                                IntroBlock(
                                    "换了「识别服务商」，翻译和发音会跟着一起换",
                                    "以前只有「识别字幕」认那一栏，翻译和长句发音的地址是写死在程序里的 —— " +
                                        "你换成 Groq、填了 Groq 的 Key，结果是识别能用、字幕翻译和长句朗读偷偷坏掉，" +
                                        "界面上还看不出来。现在这三处统一了：选哪家，识别、翻译、长句发音就全走哪家，" +
                                        "共用同一把 Key。\n" +
                                        "顺带说两件跟「换家」有关的事：\n" +
                                        "· 音色列表也跟着换。硅基流动是「沉稳女声」那 8 个中文名，" +
                                        "Groq 是 Autumn / Troy 那几个英文名，OpenAI 是 Alloy / Nova 那几个。" +
                                        "每家记各家的，切回来还是你原先挑的那个。\n" +
                                        "· Groq 的长句合成有两条硬限制：一次最多 200 个字符，而且只出 wav 格式。" +
                                        "超过 200 字符的句子它会自动跳过在线合成、改用系统本地发音或者有道切块 —— " +
                                        "所以偶尔会听到音色不一样，那不是坏了。点「试听这个音色」会告诉你这次实际走的哪条路。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "B站 SESSDATA（可选，不填也能用）",
                                    "抓一部分 B站 视频的字幕需要登录状态才给。留空就走游客，大部分视频不受影响。" +
                                        "这个东西得从浏览器 cookie 里翻出来，有点麻烦 —— 真需要的时候跟我说，我教你弄。",
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
                                        "一轮做多少张，就在复习页顶栏那颗「每轮 N 个」上点一下改（10~100，默认 20），" +
                                        "不用再回设置页找。挑词是两段：一轮里固定留四分之一的名额给从来没复习过的词，" +
                                        "剩下的按「最久没碰过的先上」排 —— 这样刚存的词不会被一堆老账压在底下，" +
                                        "老账也不会永远轮不到。答错的当场插回队尾再问一遍（最多来回 3 轮），" +
                                        "所以一轮结束时你确实把这一批都认了一遍。走完报个数：这轮做了几张、认识几个、答对率多少。" +
                                        "「再来一轮」是把整个生词本重新排一轮（已经掌握的也会进来），" +
                                        "不是只补没掌握的那几个 —— 不然只剩一个没掌握时，你会连着好几轮都在看同一张卡。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "学习统计",
                                    "想看看自己攒了多少、掌握了多少，点生词本页第二行最左边的「学习统计」。" +
                                        "进去是一页：累计生词多少、已掌握多少（带百分比）、这周新加了多少；" +
                                        "下面还有一张「近 8 周新增」的柱状图，和一条「掌握度分布」" +
                                        "（没掌握 / 学了一半 / 已掌握各占多少）。" +
                                        "这些数字都是按你生词本里现成的记录实时算的，不用你额外记什么、也不用开任何开关。",
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
                                        "卡正面那两个「美音」「英音」按钮一直都在（单词和句子都有），自动读只负责省掉你每次动手。",
                                )
                                Spacer(Modifier.height(12.dp))
                                // 【0919 重写】阿亮："那有时候音色不对是什么情况？你全删掉，就在软件介绍里讲清楚。"
                                //    ⇒ 「朗读用哪个声音」那个开关删了（理由见「发音」组的注释），
                                //      这一节从"讲层级"改成"讲发音到底怎么走 + 为什么音色会不一样"。
                                //    顺便把「40 字」这个门槛和"英文 40 字符≈7 个单词、所以长句才是常态"
                                //      继续留着 —— 这还是他最容易误解的地方（以为"长句"是特例）。
                                //    ⚠️ 门槛常量在 Pronounce.UK_MAX_LEN=40；美音上限 US_MAX_LEN=16。
                                IntroBlock(
                                    "朗读是怎么发音的（为什么有时候音色不一样）",
                                    "这里没有「发音模式」要你选，按句子长短自动分两条路，说清：\n" +
                                        "· 单词和短句（40 个字符以内，英文大概 7 个词）→ 走有道的真人音，" +
                                        "免费、不用注册。卡片上那两颗「美音」「英音」就是给它用的" +
                                        "（美音超过 16 个字符会不够用，程序会自动换成英音）。\n" +
                                        "· 长句（超过 40 个字符）→ 三道防线依次试，谁先出声用谁：\n" +
                                        "　① 在线合成 —— 用你挑的那个音色。这是**唯一**「你选的嗓子」能生效的地方；\n" +
                                        "　② 手机自带的语音引擎 —— 离线，但嗓子是手机定的，跟你挑的音色无关；\n" +
                                        "　③ 有道切块 —— 把长句切碎了喂给有道，最差也是真人音。\n" +
                                        "所以「有时候音色不对」= ① 没走成、掉到 ② 或 ③ 去了，**不是坏了**。" +
                                        "常见原因就三个：没填「识别」那把 Key、当时没网、或者句子超过了这一家" +
                                        "单次合成的上限（Groq 一次最多 200 字符）。\n" +
                                        "门槛提醒：「40 个字符」英文才 7 个单词左右，所以实际用起来长句才是常态，" +
                                        "大部分句子走的都是在线合成那条路。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "长句用哪个音色",
                                    "就是上面那条「在线合成」通道用哪个嗓子。硅基流动、Groq、OpenAI " +
                                        "各有一套音色、互不通用，所以列表跟着设置页上面的「识别服务商」走。\n" +
                                        "点开设置页那个按钮挑一个，挑完点「试听这个音色」当场听一句，" +
                                        "它还会告诉你这次实际走的是哪条路。\n" +
                                        "换个音色之后，之前听过的长句要重新合成一次（缓存是按音色分开存的）。\n" +
                                        "注意：这个音色**只对「在线合成」那一条路有效**。长句一旦掉到手机自带引擎" +
                                        "或者有道切块，听到的就是那两边的嗓子，跟你挑的没关系。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "卡片上那两颗「美音」「英音」跟上面是什么关系",
                                    "那两颗是给短句用的：单词和短句走有道，里面有真的英音、美音两套" +
                                        "（美音超过 16 个字符会不够用，程序会自动换成英音）。\n" +
                                        "但长句走的是在线合成，它没有英美音之分 —— 所以长句上点哪颗，" +
                                        "听到的都是你在上面选的那个音色。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "悬浮窗（在其他应用上方显示）",
                                    "打开后，你在别的 App 里也能看到歌词和播放控制，不用切回来。首次开启会跳系统授权页，" +
                                        "需要允许「显示在其他应用上层」。\n" +
                                        "不开完全不影响使用，只有你主动打开的时候才需要这个授权。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "在通知栏轮播生词",
                                    "像每日任务：到点就提醒你复习，有没有词都会说一声。" +
                                        "哪天「正经练完一整轮」才安静到第二天 —— 只是进去翻两张不算，" +
                                        "半路退出、没把这一轮做完，该提醒还是提醒。\n" +
                                        // 【0922 改】原来写的是"把通知栏拉下来看得全"——那是旧版的行为：
                                        // 音标只塞在展开全文（bigText）里，他不主动拉下来就永远看不见。
                                        // ⚠️ 注意是**正文**（text）那两行，**不是标题行** ——
                                        //    标题只有一行，词 + 音标拼一起长词必被省略号吃掉
                                        //    （v53 那版就这么写的，实测 `vocabulary  /vəˈk…`，已返工）。
                                        "通知里给的是一张完整的卡：英文、音标、中文都在卡片上，一眼看得全。" +
                                        "点通知直接进复习页，不响也不震，多久换一个词可以自己调。\n" +
                                        "挑词的顺序跟复习页一样：最久没碰过的先上，还没掌握的排在前面；" +
                                        "连对 3 次的词也会轮进来（跟复习页的「再练一轮」一个口径），" +
                                        "所以不会出现「没词可弹」。\n" +
                                        "这个功能要通知权限，开启的时候系统会问一次，不给的话提醒发不出来。\n" +
                                        // 【0923 加】阿亮亲测"把后台划掉提醒就没了"，他自己拍板：
                                        //   这半句必须写进软件介绍（原话"你要在那个软件说明里把这个功能
                                        //   给它说清楚"），而且他看过这一版文案说"就这么写都 OK"。
                                        //   ⚠️ 口径是「**别把它划掉/强行停止**」，不是"后台必须活着" ——
                                        //   普通的省内存回收不影响（闹钟还在，系统会自己把 App 拉起来）。
                                        "提醒靠的是手机自己的闹钟。但手机为了省电，会把你从「最近任务」" +
                                        "划掉的 App 彻底停掉，连闹钟一起清掉 —— 那段时间就不提醒了" +
                                        "（这不是这个软件的问题，别的本地提醒软件也一样）。\n" +
                                        "想让它稳，必做的就是三件：通知允许、省电策略改成「无限制」、" +
                                        "自启动允许；设置页「生词提醒」那栏里有一颗「怎么让它一直提醒」，" +
                                        "点进去直达，这三样都在那一页上。\n" +
                                        // 🔴【0923 阿亮质疑过"上锁是不是不必要"——他问得对，所以这里必须
                                        //   写成"可选"，别再列进"必做"，否则等于拿保险当刚需吓人。
                                        //   上锁治的只是"被划掉"这一个动作（force-stop ⇒ 闹钟被删）。
                                        "另外还有一个可选的保险：在「最近任务」里给这张卡片上锁。" +
                                        "锁了之后就清不掉、也划不掉了。你不去手动划它的话，不锁也没事。\n" +
                                        "万一还是漏了：下次打开 App 会自动补一条，不用重新设置。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "数据（导出 / 导入）",
                                    "导出备份：把生词本 + 字幕库打包成一个 JSON，存到你自己选的地方（网盘、电脑都行）。" +
                                        "换手机、清数据之前先来这一下。\n" +
                                        "导入备份：从以前导出的 JSON 恢复。已有的不会重复加，也不会删你现在的任何东西 —— " +
                                        "只合并不覆盖，导错了也不丢东西。\n" +
                                        "导出单词表（TXT）：只导出单词、不含收藏的整句，一行一个，" +
                                        "可以直接导入欧路 / 有道 / 不背单词的生词本。\n" +
                                        "导出 Anki 卡片（CSV）：单词和收藏的整句都导，正面英文、背面中文（含音标、词性、来源）。" +
                                        "在 Anki 里点「导入文件」直接选它就行，分隔符保持默认的逗号。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "视频模式",
                                    "顶栏「视频选集」列出当前视频所在文件夹里的其他视频，点一下即切换；「生成字幕」可自动在线识别生成逐句字幕。播放时点字幕某句 = 从那句开始；上一句 / 下一句按钮精准跳句；支持全屏、调速与单句循环跟读。按「上一句」时会按音频模式的「重播次数」把该句复读几遍（两个模式共用这一项设置，视频里无需单独调），次数设为 ×1 则只跳句不复读。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    "抓 B站 视频",
                                    "在视频模式里，左滑下方功能键即可露出 B站 链接输入框，粘贴链接就会在后台自动抓取：有官方字幕直接用并配中文翻译；没有则下载音频在线识别生成逐句字幕。抓取在后台进行，不影响你正在看的视频；加载完成后自动跳到该视频并播放（音频版会自动进入复读机播放）。成品（视频 + 双语字幕）落在 Download/XuanjiBili。需联网；无字幕视频还需在设置里填 ASR Key；翻译引擎排队或者被下线时会自动换同一家的另一个模型兜底。字幕翻译沿用长按字幕的原有逻辑，无需额外开关。",
                                )
                                Spacer(Modifier.height(12.dp))
                                // 【0918】阿亮："软件介绍里头你可以写吗？算是稳了吧" ⇒ 补上 v33/v34 那两轮
                                //    对设置页的收整。这些是用户看得见、会问"原来那排胶囊哪儿去了"的变化，
                                //    不写他就会以为功能被删了。（字号统一 / 弹窗跟主题同色是纯外观，不写。）
                                IntroBlock(
                                    "设置页怎么用（多选项都改成「点一下弹出来挑」了）",
                                    "设置页里凡是「有好几个选项让你挑」的（识别服务商、翻译引擎、多久换一个……），" +
                                        "现在都收成了一行：左边写这一项叫什么，右边写着当前选的是哪个，点右边那颗按钮就弹出一列选项。\n" +
                                        "以前它们是一排小胶囊全铺在页面上，选项一多就占掉好几行，还得横向找；" +
                                        "现在点开才展开，页面清爽，也省得翻半天。功能一个没少。\n" +
                                        "另外「可选」的那几项（比如 B站 账号）都挪到各自分区的最后面了 —— 不填不影响任何功能，" +
                                        "用不上的人直接跳过就行。",
                                )
                                Spacer(Modifier.height(12.dp))
                                IntroBlock(
                                    // 【0917】悬浮窗已经从这句里拆出去单独成条了（它自己有说明），
                                    //    这里再提一遍会重复。
                                    "其他设置",
                                    "主题颜色和深色模式调整外观（主题色在深色下会自动换成配套的一套）；" +
                                        "「启动时继续播放」只管音频 —— 视频不看这个开关：" +
                                        "开着，每次打开 App 都自动接着上次的位置续播；关了就等你手动点。" +
                                        "视频那边永远是上次退出时在播就接着播、是暂停就还是暂停。",
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
        "打不开系统设置，请手动去「设置 → 无障碍（或更多设置）→ 文字转语音输出」：" +
            "先看「首选引擎」是不是你想要的那个，再给它下英文语音包",
        Toast.LENGTH_LONG,
    ).show()
}

/**
 * 开生词提醒之后要说的那句话。
 *
 * 🔴 别再把 [VocabReminder.Result.DoneToday] 说成"生词本里没有要复习的词"
 * —— 0917 阿亮就是被那句话劝退的：他以为"没词=这功能没用"，
 * 而实际上"没词也照样提醒、**练完一整轮**才安静"才是口径
 * （0919 又从"复习过一张卡"收紧成"练完一整轮"，见 `VocabReminder.doneRoundToday`）。
 */
private fun remindToastText(r: VocabReminder.Result): String = when (r) {
    VocabReminder.Result.Posted -> "已开启，通知栏现在就有一条"
    VocabReminder.Result.DoneToday -> "已开启，今天已经练完一轮了，明天再提醒你"
    VocabReminder.Result.NoPermission -> "已开启，但系统没给通知权限，发不出来"
    VocabReminder.Result.Off -> "已关闭提醒"
}

/**
 * 🔴【0917 二次排查】**只在出错时露脸的一行诊断** —— 系统里到底有几个 TTS 引擎能被我们看见。
 *
 * 存在的唯一理由是分清两种"没引擎"，它们修法完全不同（见 `Pronounce.engineReport()`）：
 *   - **0 个** ⇒ 引擎在但**我们看不见** ⇒ manifest 少了 `<queries>` 的包可见性声明；
 *   - **有引擎**却还是初始化失败 ⇒ 是引擎服务本身／默认引擎指向了坏引擎。
 * 这轮确认完就可以删掉，别让它长期占着界面。
 */
@Composable
private fun TtsDiagLine(ttsVer: Int) {
    Text(
        text = remember(ttsVer) { Pronounce.engineReport() },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
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
 * @param note 可选的补充说明 —— **括号灰字嵌在标签与开关之间的空隙里，不另占一行**（0919 阿亮定的）。
 *             字号**跟标签一样大**（`bodyMedium`）：0917 他定过"一页一档字号"，
 *             而且他原话"不放小也行，你直接就是括号灰字"⇒ 靠**括号 + 灰色**区分层级，不靠字号差。
 *             说明短的就写这儿；长篇的（比如权限清单）仍放软件介绍里 —— 阿亮 0916 定的规矩。
 *             ⚠️ 但他 0919 又纠正过一次："之前那一波做简化的时候，删得有点太简洁了" ⇒
 *             说明别一味往软件介绍里搬，能塞进这行空隙的就塞回来。
 */
@Composable
private fun SettingsSwitch(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    note: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!note.isNullOrBlank()) {
            // 【0919 阿亮】说明不另占一行 ⇒ 塞进「标签」和「开关」中间的空隙，两边加全角括号。
            // 🔴 字号 `labelSmall`(11sp)，**比标签小**。这是实测逼出来的，别往上调：
            //    同字号(14sp)时三条全被截成「…」；降到 12sp 还是截。11sp 才够。
            // 🔴🔴 **`maxLines = 2` 是故意的"保险丝"，别改回 1**（0919 阿亮要求「1.5 倍以内一律显示全」）：
            //    正常字号下三条都只占一行，**看不出区别**；只有放不下时（超大字号 / 窄屏）
            //    才会折成两行，宁可把行撑高，也不让字被吃掉。
            // 🔴🔴 **判断"有没有被截断"只能看截图**：`uiautomator dump` 的 `text` 返回的是**原文**，
            //    渲染成「…」它照样给你完整字符串（v47 就被它骗过一轮，当成排版没问题）。
            // 🔴🔴 **而且必须在"真机宽度"的模拟器上验**：模拟器默认 ≈392dp 逻辑宽，真机常见 360dp，
            //    差这 33dp 就是"放得下 / 截断"的分界。验前先 `adb shell wm density 480`，
            //    验完 `wm density reset`。**v48 就是因为没调窄，误判成"1.3 没问题"，
            //    结果阿亮 145% 的真机上照样是「（只管音频，不管视…）」。**
            // 🔴 文案长度是**压着 360dp 屏在 1.5 倍下的可用宽度**写的，改长了一定要重跑上面两步验收。
            Text(
                text = "（$note）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        } else {
            // 没说明时也要有东西把开关顶到最右，否则 Switch 会贴着标签
            Spacer(modifier = Modifier.weight(1f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 「数据」分组里的一条：标题（可点）+ 下方灰字说明。
 *  【0917 精简】`note` 改成可选 —— 阿亮要求设置页只留标题和控件，
 *  四条导出/导入的长说明全搬进了「软件介绍 → 数据（导出 / 导入）」。 */
@Composable
private fun DataRow(title: String, note: String? = null, onClick: () -> Unit) {
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
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * 【0917 阿亮："一个功能尽量只占一行，有限的屏幕里显示更多信息"】
 *
 * 「标签 + 右侧小按钮」的单行选择器：标签撑满左侧，按钮印着当前值，点开是下拉菜单。
 *
 * 原来是 `FlowRow` 铺一串 `FilterChip` —— 3 个选项就要吃掉 2 行、4 个就是 3 行
 * （标题还占 1 行）。收成一行后，识别服务商 / 翻译引擎 / 多久换一个 各少占 1~2 行。
 * （「朗读用哪个声音」原本也在这名单里，0919 整项删掉了 —— 理由见「发音」组。）
 *
 * ⚠️ 调用点注意：**同一行放不下时会挤** —— 标签用 `weight(1f)` 顶住，按钮不会被压掉，
 *    但标签太长会自己换行。所以标签要短（≤ 8 个中文字）。
 */
@Composable
private fun <T> PickerRow(
    label: String,
    options: List<T>,
    current: T,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            // 🔴【0917 阿亮："这些按钮左边儿和右边儿的文字字体大小都不一样，要统一一下"】
            //    原来是 `labelMedium`(12sp)，而同一页的开关行（深色模式 / 悬浮窗…）用的是
            //    `bodyMedium`(14sp) ⇒ 一页里两档字号，看着就是乱的。
            //    统一到 **`bodyMedium`**，跟开关行、主题颜色、数据行全部对齐。
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) dim else dim.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f),
        )
        Box {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                // 不显式指定 style ⇒ 走 OutlinedButton 默认的 `labelLarge`(14sp)，
                // 跟左边标签的 `bodyMedium`(14sp) 同号（这也是本次要修的"左右不一样大"）。
                Text(labelOf(current))
                Spacer(modifier = Modifier.size(6.dp))
                Text("▾")
            }
            AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        // 勾选标记跟播放页「重播次数」那个下拉保持同一套写法
                        text = { Text(if (o == current) labelOf(o) + " ✓" else labelOf(o)) },
                        onClick = {
                            onPick(o)
                            open = false
                        },
                    )
                }
            }
        }
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

/**
 * 跳到本 App 的「系统设置 → 应用信息」页。
 *
 * 🔴🔴【0923 真机实测过才写的，别凭理论改】Redmi 23054RA19C / MIUI V816：
 *   - `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` ✓ **能开**，而且那一页上
 *     **「省电策略」和「自启动」两行都在** ⇒ 保活指引只要跳一次就够，不用两颗按钮。
 *   - `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` = "No activity found"（这台没有）。
 *   - `com.miui.powerkeeper` 底下的 Activity 里**没有**能直达省电策略的（全是后台服务）；
 *     `com.miui.securitycenter/...AutoStartManagementActivity` ✓ 能开，但只到自启动那一页，
 *     ⚠️ **只作兜底候选**、不作首选（能少跳一次就少跳一次）。
 *   - 「最近任务里上锁」系统里**没有**可跳的入口 ⇒ 那条只能写在文字里。
 */
private fun openAppBatterySettings(ctx: Context) {
    val candidates = listOf(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}"),
        ),
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        Intent(Settings.ACTION_SETTINGS),
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
        "打不开系统设置，请手动去「设置 → 应用 → 小七复读机」：" +
            "省电策略改成「无限制」、自启动改成「允许」",
        Toast.LENGTH_LONG,
    ).show()
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
