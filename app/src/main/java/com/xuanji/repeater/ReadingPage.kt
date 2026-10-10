package com.xuanji.repeater

import com.xuanji.repeater.SubtitleState.subtitleMode
import com.xuanji.repeater.SubtitleState.subtitlesFromAsr
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 【结构·阶段1】阅读模式的「阅读源」。
 * - null：跟播放器的音频阅读（正文来源**自动判定**：有视频源就读视频字幕，否则读音频字幕。
 *         0914 起「音频 / 视频 / 全部」来源 tab 已整体去掉，不再让用户手选）
 * - 非 null：阅读的是正在内联播放的视频（只列该视频字幕、点句跳视频）
 *
 * 之前这是散着的 5 个参数，收成一个对象后，「阅读的是视频」和「视频字幕/点句回调」
 * 不可能再对不上；MainActivity 侧也只剩一个判断（有源 = 视频）。
 */
data class ReadingSource(
    val title: String,
    val sentences: List<Sentence>,
    val activeIndex: Int,
    val onPlaySentence: (index: Int, startMs: Long) -> Unit,
)

/**
 * 阅读模式页：逐句展示歌词文本（句子区间与 LRC 歌词行按时间对齐）。
 * - 点击句子：按该句复读（播到句尾自动停）
 * - 长按句子：句子下方展开「翻译 + 生词本」—— 自动翻译本句、整句加入生词本、
 *   句内英文单词逐个点选加入。无歌词文件时退化为纯时间轴占位。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReadingPage(
    svc: PlayerService?,
    state: PlayerState,
    onOpenVocab: () -> Unit,
    // 【结构·阶段1】阅读源收成一个对象（null = 跟播放器的音频阅读）。
    // 之前是 5 个平行参数（readingFromVideo / videoSentences / videoTitle /
    // onPlayVideoSentence / activeVideoIndex）分开传，谁漏传一个就静默出错
    // （典型：「标志说在放视频、但字幕/点句回调没给」）。现在只有两种状态：
    // 有 ReadingSource = 读视频字幕；没有 = 读音频。不存在半吊子组合。
    videoSource: ReadingSource? = null,
    // 【0903 用户反馈】「全部」字幕库条目 → 打开对应媒体播放（带句子、可点句）。
    // 由 MainActivity 按 source 定位：视频走 openVideoFileByPath 进阅读(视频字幕)，
    // 音频走 PlayerService.setPlaylist 播放；path 为空的旧归档无文件可播，点击给出提示由外部处理。
    onPlayLibraryEntry: ((SubtitleEntry) -> Unit)? = null,
    // 【0914】阅读模式顶栏「阅读选集」点击 → 打开「阅读选集」视图。
    // 该视图就是原来的「全部」（合并播放历史：音频+视频都列、可点播），
    // 只是入口从「一排 tab 里的第三个」搬到了顶栏按钮 —— 音频进阅读、视频进阅读都能用。
    libraryRequest: androidx.compose.runtime.MutableState<Boolean>? = null,
    // 【v89 阿亮："滑到哪里，返回来还在哪里"】正文句子列表滚动状态由外部（MainActivity）持有：
    // 音频源阅读切页回来不再跳顶。
    // 🔴 视频源阅读的「进页定位到当前句」LaunchedEffect 保持原样（跟随优先，阿亮 1005 定），
    // 传默认值时行为与旧版完全一致。
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    VocabStore.ensureLoaded(context)
    val vocabCount by VocabStore.entries.collectAsStateWithLifecycle()
    var expandedIndex by remember { mutableStateOf(-1) }

    // 阅读(视频源)时本页作为「视频模式隐去播放窗口、只显字幕」的覆盖层，
    // 复用 VideoView 的同一个播放器（不另建）。
    val readingFromVideo = videoSource != null

    // 【0914 统一界面】原来中间有一排「音频 / 视频 / 全部」来源按钮，而且只有音频进阅读才有、
    // 视频进阅读没有 —— 同一个页面两副面孔。现在整排去掉：
    //  · 正文显示什么由「阅读源」直接决定（有视频源 → 视频字幕；没有 → 音频字幕），
    //    不再让用户手选，也就不会出现「切到视频 tab、其实根本没有视频源」这种死页；
    //  · 原来的「全部」搬到顶栏「阅读选集」按钮下。
    // 本页只剩两种视图：字幕列表 ⇄ 阅读选集。
    // key 用「源标识字符串」而不是对象本身：对象里的回调每次重组都是新实例，
    // 拿对象当 key 会让状态每次重组都被重置。
    val readingSourceKey = videoSource?.let { "video:${it.title}" } ?: "audio"
    var showLibrary by remember(readingSourceKey) {
        mutableStateOf(false)
    }
    // 顶栏「阅读选集」触发：开/关这个视图（再点一下回到字幕）
    libraryRequest?.let { req ->
        LaunchedEffect(req.value) {
            if (req.value) { showLibrary = !showLibrary; req.value = false }
        }
    }
    // 正文来源不再靠 tab 选：有视频源就读视频字幕，没有就读音频字幕。
    val displaySentences = videoSource?.sentences ?: state.sentences
    // 来源名（长按展开里「整句加入生词本」记录用）
    val source = videoSource?.title ?: (state.currentItem?.title ?: "歌词")
    val activeIndex = videoSource?.activeIndex ?: state.activeSentenceIndex

    val hasLyrics = state.lyrics.isNotEmpty()
    // 【v89】listState 改由外部传入（默认 rememberLazyListState() 保持兼容），本行删除：
    // val listState = rememberLazyListState()
    // 「播放中高亮句自动滚动」由 SentenceListPane 内部统一负责（activeIndex 已归到正确来源），
    // 这里不再重复一遍 —— 之前两处各写一份，正是「改一处忘一处」的来源。
    LaunchedEffect(Unit) {
        // 进阅读(视频源)首屏直接定位到当前句，避免停在列表顶部（即时跳转，不从第 1 句滚过去）
        if (readingFromVideo && activeIndex >= 0) listState.scrollToItem(activeIndex)
    }

    // 不透明背景：作为视频模式的覆盖层时，必须盖住背后常驻的 VideoView（只留字幕 + 功能键）；
    // 音频阅读时也是正常页面底色，避免透出后面内容。
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 头部：标题 + 生词本入口（含数量）
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // 阅读选集视图下换个标题，让用户知道自己不在字幕列表里了
                    text = if (showLibrary) "阅读选集" else "阅读模式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = onOpenVocab,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        XuanjiIcons.Vocab,
                        contentDescription = "生词本",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "生词本（${vocabCount.size}）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        // 【0914】来源切换那一排（音频/视频/全部）已整体去掉，见上方说明。
        if (showLibrary) {
            // 阅读选集 = 原来的「全部」：合并播放历史，音频+视频都列、可点播。
            // 每次打开都重新读一次（刚生成完字幕的也能立刻看到）。
            val allEntries = remember { SubtitleLibrary.loadAll(context) }
            AllLibraryView(
                entries = allEntries,
                onPlay = { entry ->
                    // 选中即离开选集视图：视频会换成新的视频源（仍停在阅读页），音频会跳到播放器
                    showLibrary = false
                    onPlayLibraryEntry?.invoke(entry)
                },
            )
        } else {
            val list = displaySentences
            if (list.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when {
                            readingFromVideo -> "该视频还没有字幕\n（可先在视频模式点「生成字幕」或导入 .srt）"
                            state.splitting -> "正在断句分析…\n（${(state.splitProgress.coerceIn(0f, 1f) * 100).toInt()}%）"
                            else -> "阅读模式\n（需先播放并完成断句）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // 【结构·阶段1】列表骨架走共用实现（SentenceListView.kt）。
                SentenceListPane(
                    sentences = list,
                    activeIndex = activeIndex,
                    modifier = Modifier.fillMaxSize(),
                    listState = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) { index, sentence ->
                    ReadingSentenceRow(
                        index = index,
                        sentence = sentence,
                        active = activeIndex == index,
                        hasLyrics = hasLyrics,
                        expanded = expandedIndex == index,
                        source = source,
                        // 【1010】只有"读视频字幕"这一路才谈得上 ASR 豁免（音频那路本来就是单语、
                        //   档位天然不裁）。`videoSource != null` 时全局 `subtitlesFromAsr` 是准的。
                        fromAsr = videoSource != null && subtitlesFromAsr,
                        onPlay = if (readingFromVideo) {
                            { videoSource?.onPlaySentence?.invoke(index, sentence.startMs) }
                        } else {
                            { svc?.playSentence(index) }
                        },
                        onToggleExpand = {
                            expandedIndex = if (expandedIndex == index) -1 else index
                        },
                    )
                }
            }
        }
    }
}

/** 字幕库汇总（「阅读选集」）：列出归档，可展开看句子；有源文件路径的条目可点「播放」打开原媒体 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllLibraryView(
    entries: List<SubtitleEntry>,
    onPlay: ((SubtitleEntry) -> Unit)? = null,
) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "字幕库还是空的\n（在音频 / 视频模式播放并断句后，会自动归档到这里）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(entries) { entry ->
            var expanded by remember(entry.title to entry.source) { mutableStateOf(false) }
            val isVideo = entry.source == SubtitleLibrary.SOURCE_VIDEO
            Surface(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isVideo) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = if (isVideo) "视频" else "音频",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isVideo) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = entry.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${entry.sentences.size} 句",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // 【0903 用户反馈】「全部」不能播放 → 有源文件路径的条目给播放键：打开原媒体到
                        // 对应模式（视频→阅读视频字幕 / 音频→播放器），点句复读跟平时一致。
                        // 视频条目一律给播放键（老归档没存 path 时，点击时按标题在媒体库找文件兜底）；
                        // 音频条目仍需有 path 才可播。
                        if (onPlay != null && (entry.source == SubtitleLibrary.SOURCE_VIDEO || !entry.path.isNullOrBlank())) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { onPlay(entry) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "播放",
                                    tint = if (isVideo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    if (expanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        entry.sentences.forEachIndexed { i, s ->
                            Text(
                                text = "${i + 1}. ${s.text}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 阅读模式的句子卡片：点按复读（视频源则跳视频），长按展开翻译 + 生词操作 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReadingSentenceRow(
    index: Int,
    sentence: Sentence,
    active: Boolean,
    hasLyrics: Boolean,
    expanded: Boolean,
    source: String,
    fromAsr: Boolean,
    onPlay: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val context = LocalContext.current
    val text = sentence.text
    // 🔴🔴【1010 阿亮定】阅读模式**跟随全局字幕档位**（`subtitleMode`，见 SubtitleState.kt）。
    //   这一段字幕是从视频带过来的**同一份双语数据**，视频那边选了"只看英文"，
    //   这里也必须是只看英文 —— 否则就是"视频全显英文、阅读模式中英都摆着"。
    //   规则与视频模式**完全一致**（都走 SentenceCodec 里同一套）：
    //     收起态 = 按档位裁（`filterByMode`）；展开态 = 给**全量**（长按把藏起来那行亮出来）。
    //   ⚠️ 判据用全局的 `subtitleMode`，**不要在这里另存一份**（副本 = 两边各说各话）。
    //   🔴🔴【1010】`fromAsr` = 这份字幕是**我们自己 ASR 生成的** ⇒ **一律不裁、不重排**，
    //     长按照旧翻译（见 VideoPage.displayTextOf 里同一段说明）。与视频模式同一条纪律。
    val bilingual = !fromAsr && isBilingual(text)
    val shownText = if (fromAsr) text else filterByMode(text, subtitleMode)
    val expandedText = text
    val wordSource = if (fromAsr) text else englishOnly(text)

    // 翻译状态：先取内存缓存，未命中在展开时联网拉取
    var translation by remember(text) { mutableStateOf(Translator.cached(text)) }
    // 注意：初始必须为 false，否则 LaunchedEffect 的 !translating 条件永远不满足，翻译永不启动（会一直转圈）
    var translating by remember(text) { mutableStateOf(false) }
    var failed by remember(text) { mutableStateOf(false) }
    // 点句中单词弹出的单词卡当前选中词（null = 不显示）
    var selectedWord by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(expanded, text, bilingual) {
        // 🔴【1010】双语字幕**不做整句翻译**（它本来中英两行都有了）——
        //   与视频模式同一条规则：双语 ⇒ 长按直接进选词，不调翻译接口。
        if (expanded && !bilingual && translation == null && !translating) {
            translating = true
            failed = false
            val t = Translator.translate(text)
            translation = t
            failed = t == null
            translating = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .combinedClickable(onClick = onPlay, onLongClick = onToggleExpand),
        shape = RoundedCornerShape(14.dp),
        color = when {
            active -> MaterialTheme.colorScheme.primaryContainer
            expanded -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (active || expanded) 0.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // 长按展开后整句变为可点单词（点单词弹卡片）；未展开显示普通文本。
            // 正文渲染（有文本/无文本有歌词/纯波形断句三种规则）与播放器列表共用同一份实现。
            if (expanded && expandedText.isNotEmpty()) {
                // 【1010】展开 = **全量**（只看英文/只看中文档下把藏起来那行补回来）；
                //   点词/生词本只认英文（`ClickableWordsRow` 内部只对英文分词）。
                ClickableWordsRow(text = expandedText, onWordClick = { selectedWord = it })
            } else {
                SentenceTextBody(
                    index = index,
                    // 【1010】收起态按**全局档位**裁（跟视频模式同一套）。
                    text = shownText,
                    active = active,
                    hasLyrics = hasLyrics,
                    emptyLyricText = "（前奏/无文本）",
                )
            }
            if (expanded && expandedText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                // 🔴【1010】双语字幕**不显示翻译块**（本来就有中文）—— 与视频模式同规则。
                //   只有**纯英文**字幕才走整句翻译。
                if (!bilingual) {
                    when {
                        translating -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "正在翻译…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        failed -> Text(
                            "翻译失败，请检查网络后长按重试",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> Text(
                            translation ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                // 整句加入生词本：双语只加**英文**部分（阿亮：只加英文不加中文）
                AddSentenceButton(context = context, text = wordSource, source = source)
                // 【1010 阿亮】原来这里还有一行「再长按可收起」——**删掉**：
                //   纯提醒、零信息量（打开的东西再按一下就收，是通用直觉，不用教），
                //   却**单独占一整行**、灰字还发闷，白撑高卡片。行为一个字没动，只是少一行字。
            }
        }
    }
    // 单词卡（点句中单词弹出）
    if (selectedWord != null) {
        WordCardDialog(
            word = selectedWord!!,
            source = source,
            onDismiss = { selectedWord = null },
        )
    }
}

/** 整句加入生词本按钮（展开区底部） */
@Composable
fun AddSentenceButton(context: Context, text: String, source: String) {
    val sentenceAdded = VocabStore.contains(text)
    Surface(
        onClick = {
            if (sentenceAdded) {
                Toast.makeText(context, "整句已在生词本中", Toast.LENGTH_SHORT).show()
            } else {
                VocabStore.add(context, text, source, true)
                Toast.makeText(context, "整句已加入生词本", Toast.LENGTH_SHORT).show()
            }
        },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (sentenceAdded) XuanjiIcons.Check else XuanjiIcons.Vocab,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (sentenceAdded) "整句已在生词本" else "整句加入生词本",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 把句子拆成 token：英文单词（可点）+ 其余（空格/标点，不可点），保留原句排版 */
fun splitIntoTokens(text: String): List<Pair<Boolean, String>> {
    val regex = Regex("([A-Za-z]+(?:['’\\-][A-Za-z]+)*)|([^A-Za-z]+)")
    return regex.findAll(text).map { m ->
        if (m.groups[1]?.value != null) true to m.value else false to m.value
    }.toList()
}

/** 整句渲染为可点单词行：点单词弹单词卡 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickableWordsRow(text: String, onWordClick: (String) -> Unit) {
    // 🔴🔴【1010 阿亮报】双语字幕展开后**中文贴到右半边**，不从行首开始。
    //   根因：整个文本只丢给**一个** FlowRow ⇒ 分词正则把「\n中文…」整块当成**一个不可拆 token**
    //   （`[^A-Za-z]+` 一吃到底），FlowRow 见当前行右侧还有空位就**直接塞进去**；
    //   而 token 内部的 `\n` 对 FlowRow 无效（FlowRow 只在「排不下」时换行，不认 `\n`）。
    //   ⇒ 改法：**自己按 `\n` 拆段，一段一个 FlowRow**，段与段之间纵向排列 ⇒ 中文自然从行首起。
    //   ✅ 单行文本（阅读模式、纯英文/纯中文单语字幕）拆出来只有 1 段 ⇒ 与改动前**行为完全一致**。
    val lines = remember(text) { text.split('\n').filter { it.isNotEmpty() } }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            val tokens = remember(line) { splitIntoTokens(line) }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                tokens.forEach { (isWord, t) ->
                    if (isWord) {
                        Text(
                            text = t,
                            modifier = Modifier
                                .clickable { onWordClick(t) }
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = t,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** 单词卡：点句中单词弹出，显示美/英音标 + 发音 + 释义 + 加入生词本 */
@Composable
fun WordCardDialog(word: String, source: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var info by remember(word) { mutableStateOf(Translator.lookupWordCached(word)) }
    var loading by remember(word) { mutableStateOf(info == null) }
    var added by remember(word) { mutableStateOf(VocabStore.contains(word)) }

    LaunchedEffect(word) {
        if (info == null) {
            loading = true
            info = Translator.lookupWord(word)
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = word,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column {
                // 美音
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { Pronounce.speak(word, "us") },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            XuanjiIcons.SpeakerUs,
                            contentDescription = "美音发音",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "美",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(16.dp),
                    )
                    Text(
                        text = info?.usphone?.let { "/$it/" } ?: "/—/",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 英音
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { Pronounce.speak(word, "uk") },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            XuanjiIcons.SpeakerUk,
                            contentDescription = "英音发音",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        "英",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.width(16.dp),
                    )
                    Text(
                        text = info?.ukphone?.let { "/$it/" } ?: "/—/",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 释义
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "查询中…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    info?.definition != null -> Text(
                        text = info!!.definition!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    else -> Text(
                        "未查到该词释义",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (added) {
                        Toast.makeText(context, "「$word」已在生词本中", Toast.LENGTH_SHORT).show()
                    } else {
                        VocabStore.add(context, word, source, false)
                        added = true
                        Toast.makeText(context, "「$word」已加入生词本", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Icon(
                    if (added) XuanjiIcons.Check else XuanjiIcons.Vocab,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (added) "已在生词本" else "加入生词本")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
