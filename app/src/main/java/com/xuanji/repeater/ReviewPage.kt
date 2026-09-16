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

    // 每轮 20 个：够一次碎片时间的量，太小要反复点开始、太大一轮走不完
    val batchSize = 20
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

    /**
     * 开新一轮：从"未掌握"里挑 [batchSize] 个，优先挑最久没看的。
     *
     * 注意这里只在用户点「开始复习」/「再来一轮」时被调用一次，
     * 不存在"队列空了自动补"的隐式逻辑 —— 之前就是那么写的，
     * 结果第一轮刚装填完就被判成"本轮走完"，直接跳到了结算页。
     */
    fun startNewRound() {
        val excludeIds = queue.map { it.id }.toSet()
        val picked = allEntries
            .filter { !it.mastered && it.id !in excludeIds }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
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
            if (started && !finished && queue.isNotEmpty()) {
                Text(
                    text = "剩 ${queue.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
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
                    startNewRound()
                },
                onBack = onBack,
            )
            else -> {
                val current = queue.firstOrNull()
                when {
                    current != null -> key(current.id) {
                        // 🔴 用条目 id 当 key：换下一张时整棵卡片是全新实例，
                        // 上一张的 fadeOut 残留不可能渲染到新词上面（防答案泄露的第二道保险）。
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
                        onAgain = {},
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
                            startNewRound()
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
private fun ReadyState(pending: Int, total: Int, onStart: () -> Unit) {
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
                    "生词本一共 $total 个，都已经连对 ${VocabEntry.MASTERED_AT} 次了。\n再听点新的，攒点新词吧。"
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

                // 单词带发音按钮（句子不给，句子整句念没意义）
                if (!entry.isSentence) {
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
                        val answer = entry.definition?.takeIf { it.isNotBlank() }
                            ?: entry.translation?.takeIf { it.isNotBlank() }
                        if (answer != null) {
                            Text(
                                text = answer,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Text(
                                text = "（这个词当时没查到释义）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // 整句释义单独再给一行（句子条目靠它）
                        if (entry.isSentence) {
                            entry.translation?.takeIf { it.isNotBlank() }?.let { t ->
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
                    "生词本里已经没有需要复习的了，漂亮。"
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
                if (pendingLeft > 0) {
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
