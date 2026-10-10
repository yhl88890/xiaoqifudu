package com.xuanji.repeater

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 【P2-b 第一批 · 1010】字幕域的全局状态 —— 「当前装的是哪份字幕、它从哪来、有什么话要说」。
 *
 * ## 为什么从 `MainActivity.kt` 搬出来
 *
 * 这几个成员原来住在 `MainActivity.kt` 的**文件顶层**。问题不在于"不好看"，在于**依赖方向反了**：
 * `sentences` 被 **15 个非 UI 文件**引用 —— 其中包含
 *   · **纯算法**：`SentenceSplitter` / `SentenceCodec`
 *   · **后台 Service**：`PlayerService`
 *   · **备份模块**：`DataBackup`
 * ⇒ 这些模块为了拿"当前字幕"，都得反过来依赖 `MainActivity`（成了上帝对象）。
 *
 * 搬进这个 object 后，大家都依赖 `SubtitleState`，**依赖方向正过来了**。
 *
 * ## 怎么用（迁移纪律）
 *
 * · 别的文件要引用这里的成员，**文件头加几行 import 即可，调用点/赋值点一行都不用改**：
 *   ```kotlin
 *   import com.xuanji.repeater.SubtitleState.sentences
 *   import com.xuanji.repeater.SubtitleState.srtName
 *   ```
 *   🔴 **只能逐个成员导**：`import com.xuanji.repeater.SubtitleState.*` 会**编译报错**
 *   （`Cannot import on demand from object`）—— Kotlin 不支持 object 的星号导入。
 *   ⚠️ 也别指望"同包就不用导"：同包免除的是**顶层声明**，object 的**成员**不算，照样得导。
 * · Compose 的重组订阅基于 SnapshotStateObserver，**跟"属性住哪"无关** ⇒ 订阅/重组照常。
 * · 🔴 **别绕过下面那 5 个具名入口直接给 `sentences`/`srtName`/`subtitlesFromAsr` 赋值**
 *   （理由见那 5 个函数的注释块）。
 *
 * ⚠️ 这个 object 里**只放状态和"操作这几个状态"的函数**，别顺手把别的东西塞进来 ——
 *   一旦这里开始依赖 UI 类型，后台 Service 就又编不过了（P2-b 摸底时确认过这条约束）。
 */
object SubtitleState {

    /** 当前字幕的句子列表 —— 全 App 的字幕数据中心（后台 Service 与纯算法模块都读它）。 */
    var sentences by mutableStateOf<List<Sentence>>(emptyList())

    // 当前字幕的来源名（同名字幕文件名 / 手动选的那份的原名 / "生成字幕"）。
    // 【1008】原为 private：视频页「选字幕」选完要立刻把它换成新来源名，故放开成 internal（只读用它，别在别处乱写）。
    var srtName by mutableStateOf("")

    /**
     * 🔴🔴【1010 阿亮定】这份字幕是不是**我们自己 ASR 生成**的。
     *
     * 判据：阿亮原话「**ASR 都不管，只要是我们生成的就都不管**」——
     *   我们 ASR 出来的字幕是**中英混杂**的（语气词/助词是中文，正文是英文），
     *   它**不是双语字幕**，只是碰巧两样都有 ⇒ **任何"按语言分行"的处理都不许碰它**：
     *     · 字幕档位（只看英文/只看中文）不裁它 —— 裁了会把英文正文连中文一起丢掉；
     *     · 行序归一（英文提上）不调它 —— 那行"含汉字"会被误判成中文行、整行挪到下面。
     *   ⇒ 只对**抓来的现成字幕**（手动选的 / 同名 .srt / 内封轨 / B站 CC）生效那两道处理。
     *
     * ⚠️ 为什么用一个显式布尔、不去认 `srtName == "生成字幕"` 这个字符串：
     *   字符串判据改个文案就静默失效，而且 `srtName` 别处也会被改写（选字幕/切轨）。
     *   这个标志只在【三处 ASR 出字幕的地方】置 true、【其余所有换字幕的地方】置 false。
     *   （唯一的例外：冷启从 SP 恢复时按 [ASR_SUBTITLE_NAME] 还原一次，见 `loadVideoPrefs`。）
     */
    var subtitlesFromAsr by mutableStateOf(false)

    /** ASR 自动生成的字幕在 [srtName] 里的固定名字（「写」和「冷启还原」两处共用，别各写一份字面量）。 */
    const val ASR_SUBTITLE_NAME = "生成字幕"

    /**
     * 给用户看的一句话（ASR 阶段提示 / 识别结果 / 报错文案）。
     *
     * ⚠️ 它和上面几个同属"当前字幕"这一族：**换字幕时一律清空**（"上一句话作废"），
     *   但**各出口写的结果文案不同**（完成 / 部分失败 / 报错），所以它不属于"永远一起变"那组 ——
     *   `beginAsrRun` 与各装载函数都只负责把它清空，真正的结果文案由 ASR 任务出口自己写。
     */
    var asrMsg by mutableStateOf("")

    // ==================== 🔴🔴 字幕装载的唯一入口（1010 收口，别绕过去直接赋值）====================
    //
    // 【为什么非收不可 —— 这是 P2 立项的真实理由，不是"代码不好看"】
    //
    // `sentences` + `srtName` + `subtitlesFromAsr` 这三个字段**永远是一起变的** ——
    // 它们共同描述同一件事：「当前装的是哪一份字幕、它是从哪来的」。
    // 可它们原来散在 **12 处**各自赋值，而且分两种毛病：
    //   · **有几处压根不设 `subtitlesFromAsr`**（手动选/同名字幕、内封轨）—— 靠"前面刚清空过一次"兜着。
    //     这叫**隐式依赖**：哪天有人把清空挪走、或在清空与装载之间插一个新来源，标志就会带着旧值，
    //     界面立刻把 ASR 的混杂字幕按双语档去裁 —— **画面看着正常，但中英文各少一半，静默出错**。
    //   · 置 true 的只有 3 处 ASR 出字幕的地方，全靠人记着。
    //
    // 【收口方式：不给调用方传布尔值】
    // 按"来源"分成**具名入口**。传布尔就一定有人传错；不给布尔，这种错就不可能发生。
    // ⚠️ 新增任何"换字幕"入口，都必须走下面 5 个函数之一，**别再直接给那三个字段赋值**。

    /** 清空字幕（换视频时）：来源作废，4 个字段一起归零。 */
    fun clearSubtitles() {
        sentences = emptyList()
        srtName = ""
        subtitlesFromAsr = false
        asrMsg = ""
    }

    /**
     * 装载**抓来的现成字幕**：手动选的 / 同名 .srt / 内封轨 / B站 CC。
     * ⇒ 要跟字幕档位、要按双语行序把英文提上（即 `subtitlesFromAsr = false`）。
     */
    fun loadReadySubtitles(list: List<Sentence>, name: String) {
        sentences = list
        srtName = name
        subtitlesFromAsr = false
        asrMsg = ""
    }

    /**
     * 装载**我们自己 ASR 生成的字幕** ⇒ 档位与行序**两道都不碰**
     * （阿亮：我们生成的一律不管，理由见 [subtitlesFromAsr]）。
     * ⚠️ 名字固定写 [ASR_SUBTITLE_NAME]、**不让调用方传** —— 传了就会出现"名字说生成、标志说抓来"的自相矛盾。
     */
    fun loadAsrSubtitles(list: List<Sentence>) {
        sentences = list
        srtName = ASR_SUBTITLE_NAME
        subtitlesFromAsr = true
        asrMsg = ""
    }

    /**
     * 装载**字幕库里归档的字幕**（降级回退路径）。
     *
     * ⚠️ `srtName` 固定写 [ASR_SUBTITLE_NAME]（这份确实出自那次生成），
     *   但**是不是 ASR 的以库里的标记为准**：老数据没有这个字段 ⇒ false ⇒ 当"抓来的"处理（更安全）。
     *   ⇒ 这里**故意允许出现"名字是生成字幕、标志是 false"**的组合，那是历史数据的真实情况、不是 bug。
     */
    fun loadArchivedSubtitles(list: List<Sentence>, fromAsr: Boolean, msg: String = "") {
        sentences = list
        srtName = ASR_SUBTITLE_NAME
        subtitlesFromAsr = fromAsr
        asrMsg = msg
    }

    /**
     * 冷启恢复字幕（[loadVideoPrefs] 用）：来源按 `srtName` 回推。
     *
     * ⚠️ 这**不违反**"别拿 srtName 当判据"（见 [subtitlesFromAsr] 的说明）——
     *   因为这里是**读回自己写下的固定值**（`saveVideoPrefs` 存的就是 srtName），不是"看文案猜来源"。
     * 🔴 顺序要紧：**先定来源、再解句子** —— [decodeSentences] 的 `reorderBilingual` 要靠这个标志。
     */
    fun restoreSubtitles(sentencesRaw: String, name: String) {
        val fromAsr = name == ASR_SUBTITLE_NAME
        srtName = name
        subtitlesFromAsr = fromAsr
        asrMsg = ""
        sentences = decodeSentences(sentencesRaw, reorderBilingual = !fromAsr)
    }
}
