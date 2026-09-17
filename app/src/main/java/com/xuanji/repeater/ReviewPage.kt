package com.xuanji.repeater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 复习页（0916 新增）——补上学习闭环里唯一断掉的那环。
 *
 * 【为什么要有它】阿亮攒生词攒了一个月，但 App 里从来没有"回头再看一遍"的地方，
 * 收藏的动作做了、复习的动作没做。这一页就是把这个动作补上。
 *
 * 【为什么不做间隔重复（SRS）】Anki 那套算法需要引入"间隔天数表 + 到期队列"，
 * 用户第一次用会看到"今天没有要复习的"然后一脸懵 —— 那玩意儿适合长期重度使用。
 * 这里用的是最简单的"连对 3 次就算掌握"：攒够就退出队列，答错就回炉。
 * 够用、直观、不会出现空页面。真想要精细的间隔重复，CSV 导出到 Anki 更合适。
 *
 * 一轮的组成：[VocabEntry.MASTERED_AT] 个未掌握的条目，外加本轮答错的（错一次补一轮，最多 3 次），
 * 所以"今天做完一轮"是有一个明确终点的。
 */
@Composable
fun ReviewPage(onBack: () -> Unit) {
    val context = LocalContext.current
    VocabStore.ensureLoaded(context)
    val allEntries by VocabStore.entries.collectAsStateWithLifecycle()

    // 每轮多少个：设置里可调（默认 20，见 ReviewPrefs.BATCH_CHOICES）。
    // 🔴 remember 只读一次就够 —— 改这个设置必须回设置页，再进来时这个页面是重建的，
    //    不存在"读到旧值"的问题。别改成每次重组都读一遍 SP。
    val batchSize = remember { ReviewPrefs.loadBatchSize(context) }
    // 本轮最多补几次错题，防止一直答错陷在死循环里出不来
    val maxRounds = 3

    // 本轮要复习的队列。用不可变 List 而不是 mutableStateListOf：
    // 状态只有一个写入点（下面几个函数），读出来就是当前值，
    // 不会出现"队列变化把某个 LaunchedEffect 又触发一次"的连锁反应。
    var queue by remember { mutableStateOf<List<VocabEntry>>(emptyList()) }
    // 本轮的总张数，进度条分母（队列被消费后 queue.size 会变小，所以要单独记）
    var roundTotal by remember { mutableStateOf(0) }
    // 本轮答错的，答完补成下一轮
    var wrongThisRound by remember { mutableStateOf<List<VocabEntry>>(emptyList()) }
    // 累计答对 / 答错（整场统计）
    var totalKnown by remember { mutableStateOf(0) }
    var totalUnknown by remember { mutableStateOf(0) }
    // 当前这张卡翻面了没
    var flipped by remember { mutableStateOf(false) }
    // 本轮补题次数
    var roundNo by remember { mutableStateOf(0) }
    // 整场结束
    var finished by remember { mutableStateOf(false) }
    // 开场引导（第一次进来说明怎么用）
    var started by remember { mutableStateOf(false) }

    // ---- 自动发音（0916 新增）----
    // 阿亮要的：复习时每翻到新的一张卡，自动把词念一遍，省得每次抬手点小喇叭。
    // 开关和口音都持久化，进来就是他上次的选择。
    var autoSpeak by remember { mutableStateOf(ReviewPrefs.loadAutoSpeak(context)) }
    var accent by remember { mutableStateOf(ReviewPrefs.loadAccent(context)) }
    // 口音选择条展开没（点开关旁边那个小胶囊才展开，平时不占地方）
    var accentOpen by remember { mutableStateOf(false) }

    /**
     * 开新一轮：挑 [batchSize] 个，优先挑最久没看的。
     *
     * `includeMastered`：
     *  - `false`（默认）—— **只挑没掌握的**。给「开始复习」用：进复习就该先练不会的。
     *  - `true` —— 已掌握的也一起进池子。给「再来一轮」用 ⇒ **整本重练一遍**。
     *
     * 🔴 「再来一轮」永远传 true。见下面 onAgain 里的长注释：传 false 时如果只剩 1 个没掌握，
     *    池子就只有那 1 个，会出现"连点几次再来一轮都是同一张卡"的蠢样子（0917 阿亮报）。
     *
     * 注意这里只在用户点「开始复习」/「再来一轮」时被调用一次，
     * 不存在"队列空了自动补"的隐式逻辑 —— 之前就是那么写的，
     * 结果第一轮刚装填完就被判成"本轮走完"，直接跳到了结算页。
     */
    fun startNewRound(includeMastered: Boolean = false) {
        val excludeIds = queue.map { it.id }.toSet()
        // 🔴【0917 阿亮报】这里原来写死 `!it.mastered` ⇒ 全部连对 3 次之后队列永远是空的，
        // 他就"没法再继续复习了"，页面只让他"攒新词"。学完了也该能接着练。
        // ⇒ 这一轮要不要把已掌握的词也放进来，交给调用方决定（ReadyState 的「再练一轮」传 true）。
        val picked = allEntries
            .filter { it.id !in excludeIds && (includeMastered || !it.mastered) }
            .sortedBy { it.lastReviewedAt }
            .take(batchSize)
        queue = picked
        roundTotal = picked.size
        wrongThisRound = emptyList()
        flipped = false
        roundNo++
    }

    /** 答完一张：从队首摘掉，然后看这一轮是不是走完了 —— 走完就在这儿当场判，不靠重组触发 */
    fun consumeAndAdvance(entry: VocabEntry, known: Boolean) {
        // 🔴 顺序很重要：必须先翻回正面，再换下一张。
        // 反过来的话，queue 一换、卡片立刻就渲染成"下一张词 + 还翻着面"，
        // 下一张的释义会闪一帧 —— 等于把答案提前泄露了。
        flipped = false

        val rest = queue.drop(1)
        queue = rest
        if (rest.isNotEmpty()) return

        // 本轮走完。还有错题、且没超过补题上限，就补一轮；否则收工。
        if (wrongThisRound.isNotEmpty() && roundNo < maxRounds) {
            val retry = wrongThisRound
            queue = retry
            roundTotal = retry.size
            wrongThisRound = emptyList()
        } else {
            finished = true
        }
    }

    val pendingCount = remember(allEntries) { allEntries.count { !it.mastered } }
    val totalCount = allEntries.size

    // 换口音：存下来 + 立刻用新口音念一遍当前这张（他点了就想马上听到区别）
    fun pickAccent(a: String) {
        accent = a
        ReviewPrefs.saveAccent(context, a)
        accentOpen = false
        queue.firstOrNull()?.let { Pronounce.speak(it.text, a) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
        // 复习进行中时顶栏可以有两层：上面一行是 返回 / 标题 / 自动发音块 / 剩 N，
        // 下面一层是"读哪个口音"（默认收起，点了小胶囊才展开）。
        Column(modifier = Modifier.fillMaxWidth()) {
            val inSession = started && !finished && queue.isNotEmpty()
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(XuanjiIcons.Back, contentDescription = "返回") }
                Text(
                    text = "复习",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                // 自动发音：只在真正做题时出现（开场页/结算页没卡片可念，摆在那儿是噪音）
                if (inSession) {
                    AutoSpeakControl(
                        enabled = autoSpeak,
                        accent = accent,
                        accentOpen = accentOpen,
                        onToggle = { on ->
                            autoSpeak = on
                            if (!on) accentOpen = false
                            ReviewPrefs.saveAutoSpeak(context, on)
                            // 打开就顺手念一遍当前这张：他马上能听到效果，
                            // 不用等翻到下一张才知道自己开对了没有
                            if (on) queue.firstOrNull()?.let { Pronounce.speak(it.text, accent) }
                        },
                        onPickAccent = { a -> pickAccent(a) },
                        onToggleAccent = { accentOpen = !accentOpen },
                    )
                    Text(
                        text = "剩 ${queue.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, end = 16.dp),
                    )
                }
            }

            // 口音选择：开关打开后点小胶囊才展开
            if (inSession && autoSpeak && accentOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "读哪个口音",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AccentChip("美音", accent == ReviewPrefs.ACCENT_US, XuanjiIcons.SpeakerUs) {
                        pickAccent(ReviewPrefs.ACCENT_US)
                    }
                    AccentChip("英音", accent == ReviewPrefs.ACCENT_UK, XuanjiIcons.SpeakerUk) {
                        pickAccent(ReviewPrefs.ACCENT_UK)
                    }
                }
            }
        }

        when {
            totalCount == 0 -> EmptyState("生词本还是空的\n先去听几段，把不认识的词收进来")
            !started -> ReadyState(
                pending = pendingCount,
                total = totalCount,
                onStart = {
                    started = true
                    startNewRound()
                },
                // 全掌握时的入口：不传 includeMastered 的话队列还是空的，点下去等于没反应
                onStartMastered = {
                    started = true
                    startNewRound(includeMastered = true)
                },
            )
            finished -> FinishState(
                known = totalKnown,
                unknown = totalUnknown,
                pendingLeft = pendingCount,
                onAgain = {
                    totalKnown = 0
                    totalUnknown = 0
                    roundNo = 0
                    queue = emptyList()
                    wrongThisRound = emptyList()
                    finished = false
                    // 🔴🔴 「再来一轮」= **重新开一整轮（含已掌握）**，永远传 true。
                    //
                    // 这里原来写的是 `includeMastered = pendingCount == 0`，想表达"没掌握的就练没掌握的"，
                    // 结果造出一个很蠢的死循环（0917 阿亮报）：
                    //   他生词本里只剩 1 个长句没掌握 ⇒ pendingCount=1 ⇒ includeMastered=false
                    //   ⇒ 池子 = 未掌握 = 只有那 1 个长句 ⇒ 点一次"再来一轮"还是它，再点还是它……
                    //   直到他对了 3 次、那句子凑够 3 星变成"已掌握"、pendingCount 归零，
                    //   才轮到整本列表出来 —— 他说的"第三轮后才出来"就是这个。
                    // ⇒ 「再来一轮」在用户脑子里就是"整本再练一遍"，不是"补零头"。
                    startNewRound(includeMastered = true)
                },
                onBack = onBack,
            )
            else -> {
                val current = queue.firstOrNull()
                when {
                    current != null -> key(current.id) {
                        // 🔴 用条目 id 当 key：换下一张时整棵卡片是全新实例，
                        // 上一张的 fadeOut 残留不可能渲染到新词上面（防答案泄露的第二道保险）。
                        //
                        // 自动发音：key **只认 current.id** —— "换了一张卡" = id 变 = effect 重跑，
                        // 正好一次念一个新词。
                        //
                        // 🔴🔴 绝不能把 autoSpeak / accent 塞进 key（0917 修掉的老 bug）：
                        // 开关的 onToggle 和换口音的 pickAccent **各自已经显式念过一遍**了，
                        // 再让 key 变化触发这个 effect，同一句就会被读两遍 ——
                        // 阿亮听到的就是"开了自动发音，字念了两遍"。
                        // 所以这里只认 id：id 没变就绝不再念。
                        //
                        // 🔴 也绝不能拿 queue.size 当 key：翻面（flipped 变化）或队列重排
                        // 都会把它再触发一次，变成反复念。
                        LaunchedEffect(current.id) {
                            if (autoSpeak) Pronounce.speak(current.text, accent)
                        }
                        ReviewCard(
                            entry = current,
                            flipped = flipped,
                            progressDone = (roundTotal - queue.size).coerceAtLeast(0),
                            progressTotal = roundTotal,
                            onFlip = { flipped = true },
                            onKnown = {
                                VocabStore.markReviewed(context, current.id, true)
                                totalKnown++
                                consumeAndAdvance(current, known = true)
                            },
                            onUnknown = {
                                VocabStore.markReviewed(context, current.id, false)
                                totalUnknown++
                                wrongThisRound = wrongThisRound + current
                                consumeAndAdvance(current, known = false)
                            },
                        )
                    }
                    // 队列空了但还没结算：只可能是"凑不出这一轮了"（剩下的都掌握了），
                    // 或者刚好答完最后一张的那一帧。前者给个收尾，后者等重组刷出结算页。
                    pendingCount == 0 -> FinishState(
                        known = totalKnown,
                        unknown = totalUnknown,
                        pendingLeft = 0,
                        // 原来这里是空的 `{}`，配合同样被藏起来的按钮 = 点了没反应。
                        // 现在给真动作：再练一轮（含已掌握）。（0917 阿亮报）
                        onAgain = {
                            totalKnown = 0
                            totalUnknown = 0
                            roundNo = 0
                            queue = emptyList()
                            wrongThisRound = emptyList()
                            finished = false
                            startNewRound(includeMastered = true)
                        },
                        onBack = onBack,
                    )
                    else -> FinishState(
                        known = totalKnown,
                        unknown = totalUnknown,
                        pendingLeft = pendingCount,
                        onAgain = {
                            totalKnown = 0
                            totalUnknown = 0
                            roundNo = 0
                            queue = emptyList()
                            wrongThisRound = emptyList()
                            finished = false
                            // 同上：必须整本重来，不能只补未掌握的（否则只剩 1 个时会卡在同一张卡上）
                            startNewRound(includeMastered = true)
                        },
                        onBack = onBack,
                    )
                }
            }
        }
    }
}

/** 开场：告诉他还剩多少个、点一下开始 */
@Composable
private fun ReadyState(
    pending: Int,
    total: Int,
    onStart: () -> Unit,
    onStartMastered: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                text = if (pending == 0) "全部掌握啦" else "还有 $pending 个没掌握",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (pending == 0) {
                    // 🔴【0917】原文案是"再听点新的，攒点新词吧" —— 等于把他关在门外。
                    // 他现在只有十几个词句，练完就卡住了，明确说"学完了也要能继续学"。
                    "生词本一共 $total 个，都已经连对 ${VocabEntry.MASTERED_AT} 次了。\n" +
                        "想再练一遍也行 —— 答错会退回队列重新练。"
                } else {
                    "生词本一共 $total 个。\n看到词先在心里想意思，点一下屏幕看答案。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (pending > 0) {
                Spacer(modifier = Modifier.height(28.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onStart),
                ) {
                    Text(
                        text = "开始复习",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 14.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "连对 ${VocabEntry.MASTERED_AT} 次就算掌握（列表里 ★★★），继续练还能涨星",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (total > 0) {
                // 全掌握了也得有按钮 —— 不然这一页只有一段"攒新词吧"的说明，他只能原路返回（0917 他报的）
                Spacer(modifier = Modifier.height(28.dp))
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onStartMastered),
                ) {
                    Text(
                        text = "再练一轮",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 14.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "练的是已经掌握的词；答错就退回队列，重新攒 ★★★",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 一张卡：正面只有一个词（逼你自己回想），点一下翻面看释义。
 *
 * 翻面后才出「认识 / 不认识」——没看答案就能点"认识"的话，
 * 手会比脑子快，一路点过去什么都没记住。
 */
@Composable
private fun ReviewCard(
    entry: VocabEntry,
    flipped: Boolean,
    progressDone: Int,
    progressTotal: Int,
    onFlip: () -> Unit,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 进度条：这一轮走完多少了
        if (progressTotal > 0) {
            LinearProgressIndicator(
                progress = { progressDone.toFloat() / progressTotal },
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(horizontal = 16.dp),
            )
        }

        // 卡片主体：点哪儿都翻面
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clickable(enabled = !flipped, onClick = onFlip),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // ---- 正面：词本身 ----
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                // 发音按钮：单词、句子都给了（0917 阿亮要求"句子也给加上"）。
                // 原来是 `if (!entry.isSentence)`，理由写的是"句子整句念没意义" —— 已作废。
                //
                // 🔴🔴 但"英音/美音"这两颗按钮在句子上的实际效果，**跟他想的可能不一样**，别在 UI 上假装：
                //   · 单词 / 短句（≤ Pronounce.UK_MAX_LEN = 40 字符）⇒ 走有道人声，
                //     **美音英音是两个真的不同的音**；
                //   · 长句（> 40 字符）⇒ 有道两种口音都会 HTTP 500，实际落到硅基流动在线合成，
                //     而**那边没有英美音这个维度**（只有 8 个预置音色，见 SpeakPrefs.SF_VOICES）。
                //     ⇒ 长句上这两颗按钮**只是跟单词卡片保持一致的外观**，点了听到的是同一个音色。
                //   要真让长句也分英美音，得换服务商或自己做音频变调，别在这里偷偷糊弄。
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    PronounceChip(
                        label = "美音",
                        icon = XuanjiIcons.SpeakerUs,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { Pronounce.speak(entry.text, "us") },
                    )
                    PronounceChip(
                        label = "英音",
                        icon = XuanjiIcons.SpeakerUk,
                        tint = MaterialTheme.colorScheme.tertiary,
                        onClick = { Pronounce.speak(entry.text, "uk") },
                    )
                }

                // ---- 背面：释义 ----
                AnimatedVisibility(visible = flipped, enter = fadeIn(), exit = fadeOut()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        // 音标
                        if (!entry.isSentence) {
                            val us = entry.usphone.orEmpty()
                            val uk = entry.ukphone.orEmpty()
                            val phonetic = when {
                                us.isNotEmpty() && uk.isNotEmpty() && us != uk -> "/$us/  /$uk/"
                                us.isNotEmpty() -> "/$us/"
                                uk.isNotEmpty() -> "/$uk/"
                                else -> ""
                            }
                            if (phonetic.isNotEmpty()) {
                                Text(
                                    text = phonetic,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        // 释义（词性释义优先，没有就用翻译）
                        val definition = entry.definition?.takeIf { it.isNotBlank() }
                        val translation = entry.translation?.takeIf { it.isNotBlank() }
                        val answer = definition ?: translation
                        if (answer != null) {
                            Text(
                                text = answer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            // 释义和翻译**都没有**时的兜底文案。
                            // 🔴 原来说的是"（这个词当时没查到释义）"—— 单词上没毛病，
                            //    但句子条目上也这么写就串味了（0917 阿亮问"背面你说的什么意思"）。
                            //    所以他问过之后改成按类型分开说。
                            Text(
                                text = if (entry.isSentence) "（这句当时没查到翻译）"
                                else "（这个词当时没查到释义）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 整句：上面若已用翻译当答案就不再补，避免同一条中文显示两遍
                        if (entry.isSentence && definition != null) {
                            translation?.let { t ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = t,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // 来源，给点语境
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "来自：${entry.source}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 没翻面时的提示（浮在卡片底部，不占布局高度）
            if (!flipped) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = "点一下看答案",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        // ---------- 底部按钮 ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (flipped) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnswerButton(
                        text = "不认识",
                        bg = MaterialTheme.colorScheme.errorContainer,
                        fg = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onUnknown,
                    )
                    AnswerButton(
                        text = "认识",
                        bg = MaterialTheme.colorScheme.primary,
                        fg = MaterialTheme.colorScheme.onPrimary,
                        onClick = onKnown,
                    )
                }
            } else {
                Text(
                    text = "先回想一下意思，再点屏幕看答案",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 自动发音开关（0916 新增）。
 *
 * 结构：一颗「自动读」开关 + 开关打开后旁边冒出来的一颗小胶囊（显示当前口音、点一下展开选择）。
 *
 * 【为什么开关没打开时不显示胶囊】他没打开自动朗读的话，"读哪个口音"是个无意义的问题，
 * 摆在那儿只会让顶栏更挤 —— 顶栏本来就还有「剩 N」。
 *
 * 【为什么开关自己做而不是用 Material 的 Switch】Switch 连带上下的留白有 32dp 高，
 * 塞进 48dp 的顶栏会把「剩 N」挤掉。自己画一颗 40×22 的小胶囊，尺寸可控。
 * 系统字体放大也不怕 —— 这里全是 dp 画的几何图形，没有跟着 sp 涨的文字。
 */
@Composable
private fun AutoSpeakControl(
    enabled: Boolean,
    accent: String,
    accentOpen: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickAccent: (String) -> Unit,
    onToggleAccent: () -> Unit,
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        // ---- 开关本体：点整块都能切，别只让那 40dp 的小胶囊可点 ----
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { onToggle(!enabled) }
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Icon(
                XuanjiIcons.SpeakerUs,
                contentDescription = null,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = "自动读",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 手画的小开关：40×22 胶囊 + 18dp 圆点
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (enabled) tint else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.outline
                        ),
                )
            }
        }

        // ---- 口音胶囊：开关打开后才出现 ----
        if (enabled) {
            Spacer(modifier = Modifier.width(4.dp))
            val us = accent == ReviewPrefs.ACCENT_US
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onToggleAccent),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        if (us) XuanjiIcons.SpeakerUs else XuanjiIcons.SpeakerUk,
                        contentDescription = null,
                        tint = if (us) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (us) "美音" else "英音",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (accentOpen) "▴" else "▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 口音选项（展开后那个「美音 / 英音」二选一） */
@Composable
private fun AccentChip(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 发音按钮：喇叭 + 文字标注（美音 / 英音）。
 *
 * 为什么不用 `IconButton`：它自带 48dp 最小触摸目标、且只放得下一个图标，
 * 没法标注是英音还是美音 —— 阿亮明确要求"标注上英音跟美音、做大一点、拉开距离"。
 * 自己画胶囊块：图标 26dp + 文字，整块高 48dp、左右留白够，拇指好按。
 */
@Composable
private fun PronounceChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = tint.copy(alpha = 0.12f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
        }
    }
}

/** 底部「认识 / 不认识」按钮：自绘圆角块，尺寸稳定不受系统字号影响 */@Composable
private fun AnswerButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
        }
    }
}

/** 一轮走完的收尾：报个数，让他看到"我今天干了多少" */
@Composable
private fun FinishState(
    known: Int,
    unknown: Int,
    pendingLeft: Int,
    onAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val total = known + unknown
    val rate = if (total > 0) known * 100 / total else 0

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$rate%",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "答对率",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "这轮做了 $total 张，认识 $known 个",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (pendingLeft == 0) {
                    "生词本里的都掌握了，想再练一遍随时来。"
                } else {
                    "还有 $pendingLeft 个没掌握，想再练一轮随时来。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("回生词本") }
                // 🔴 按钮不再只在 pendingLeft > 0 时给 —— 全掌握时也得能接着练（0917 阿亮报）
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onAgain),
                ) {
                    Text(
                        text = "再来一轮",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** 生词本为空 */
@Composable
private fun EmptyState(msg: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
