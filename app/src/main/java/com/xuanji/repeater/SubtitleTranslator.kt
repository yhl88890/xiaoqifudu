package com.xuanji.repeater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * 字幕翻译（走当前[服务商][AsrProvider]的 chat/completions，OpenAI 兼容）。
 *
 * - 批量把英文字幕翻译成中文，减少调用次数。
 * - **地址 / 模型 / 兜底全部跟着「识别服务商」走**（v44）。以前地址写死硅基流动，
 *   用户换成 Groq 填 Groq 的 Key 之后，翻译会拿着 Groq 的 Key 去敲硅基流动 ⇒ 401 ⇒
 *   而 401 不算"排队" ⇒ 兜底不触发 ⇒ **字幕翻译静默变空**。
 * - 主模型**不可用**时自动切换到**同一家**的另一个模型兜底（模型名跨家不通用，不能切到别家去）。
 *   "不可用"包含三种：
 *     ① 排队/限流（HTTP 429/503、或返回排队/过载/繁忙等提示）
 *     ② 模型被官方**下线 / 改名** ⇒ 回 400 / 404
 *     ③ 返回内容解析不出来（模型不听话、吐了段废话）
 *   🔴【0918 阿亮点出来的】以前只有①会兜底，②③ 被判成"这不是排队"⇒ 不换模型 ⇒
 *     **翻译静默变空**（界面上一点提示都没有）。现在凡是"服务器给了回应"的都换模型再试一次。
 *   唯一**不**兜底的是纯连接异常 / 超时：换模型救不了断网，只会把等待时间翻倍。
 * - 同一段视频里只要兜底成功过一次，**后面的批次直接用兜底模型**，
 *   不再"每批都先拿下线模型失败一次"（长视频十几批 ⇒ 十几倍无谓请求）。
 * - 没有配置 Key 时返回与输入等长的空列表（调用方再退回免费翻译通道）。
 * - 全程失败也不抛异常，翻译缺失不影响「英文识别 + 播放学习」主流程。
 */
object SubtitleTranslator {
    private const val TAG = "SubtitleTranslator"

    /**
     * 字幕翻译可选模型 —— **每个模型都归属于某一家服务商**。
     *
     * 🔴🔴【v44】模型名**跨家不通用**：把 `Qwen/Qwen2.5-7B-Instruct` 发给 Groq 直接 400，
     *   而 400 不算"排队" ⇒ 不进兜底分支 ⇒ **翻译静默变空，界面上看不出来**。
     *   所以列表必须跟着「识别服务商」走；地址也一样（原来这里写死 `api.siliconflow.cn`，已删）。
     *
     * 【0918 阿亮："那个框看着像要我填东西，这个误导很严重"】
     *   原来是个 `OutlinedTextField` 手填型号 —— 打错一个字母就静默失败，
     *   而且"清空"会存下空串（`getString` 的默认值只在键不存在时生效）⇒ 更坏。
     *   ⇒ 改成下拉，本枚举就是下拉的全部选项。**加模型前先核对官方模型表**，
     *     写进一个不存在的型号 = 400 = 静默变空。
     */
    enum class Model(val provider: AsrProvider, val id: String, val label: String) {
        // ---------------- 硅基流动（国内直连、免信用卡，三个都在官方免费名单里） ----------------
        QWEN(AsrProvider.SILICONFLOW, "Qwen/Qwen2.5-7B-Instruct", "通义千问（默认）"),
        HUNYUAN_MT(AsrProvider.SILICONFLOW, "tencent/Hunyuan-MT-7B", "混元翻译（专用）"),
        HY3(AsrProvider.SILICONFLOW, "tencent/Hy3", "混元 Hy3（免费）"),
        // ---------------- Groq（按官方生产模型表取的三个，`openai/gpt-oss-120b` 是它推荐的默认） ----------------
        GROQ_OSS120B(AsrProvider.GROQ, "openai/gpt-oss-120b", "GPT-OSS 120B（默认）"),
        GROQ_OSS20B(AsrProvider.GROQ, "openai/gpt-oss-20b", "GPT-OSS 20B（更快）"),
        GROQ_LLAMA70B(AsrProvider.GROQ, "llama-3.3-70b-versatile", "Llama 3.3 70B"),
        // ---------------- OpenAI（按量计费、要绑卡） ----------------
        OPENAI_4O_MINI(AsrProvider.OPENAI, "gpt-4o-mini", "GPT-4o mini（默认）"),
        OPENAI_4O(AsrProvider.OPENAI, "gpt-4o", "GPT-4o（更准更贵）"),
        ;

        companion object {
            /** 某一家有哪些模型 —— 设置页那个下拉就用它 */
            fun forProvider(p: AsrProvider): List<Model> = values().filter { it.provider == p }

            /** 某一家默认用哪个（列表第一个） */
            fun defaultFor(p: AsrProvider): Model = forProvider(p).first()

            /** id 是不是这一家的合法模型名 */
            fun isValidFor(p: AsrProvider, id: String?): Boolean =
                forProvider(p).any { it.id == id?.trim() }

            /**
             * 按 id 反查。空串 / 未知 id / **别家的 id** 一律回落这家默认 ——
             * 绝不把非法模型名发给接口（发出去就是 400 + 静默变空）。
             */
            fun ofId(p: AsrProvider, id: String?): Model =
                forProvider(p).firstOrNull { it.id == id?.trim() } ?: defaultFor(p)

            /** 排队/限流兜底：**在同一家里**换另一个模型（跨家换等于把别家的名字发过去） */
            fun fallbackFor(p: AsrProvider, primaryId: String): String =
                forProvider(p).firstOrNull { it.id != primaryId }?.id ?: primaryId
        }
    }

    /** 排队/限流特征（命中即触发切换兜底模型） */
    private val QUEUE_HINTS = listOf(
        "queue", "rate", "ratelimit", "rate_limit", "overload", "overloaded",
        "繁忙", "排队", "too many requests", "model is currently", "currently overloaded",
        "try again", "请稍后", "busy", "capacity", "429", "503", "请求过于频繁", "限流"
    )

    /**
     * 批量翻译（对外主入口）。返回与 english 等长的中文列表（翻译失败项为 ""）。
     *
     * 🔴 **地址 / 主模型 / 兜底模型全部由 [provider] 推出来**，调用方不要再自己拼字符串。
     * @param primaryModelId 主模型 id；传 null / 空 / 非法 / **别家的 id** 都自动回落这家的默认
     */
    suspend fun translateBatch(
        english: List<String>,
        apiKey: String,
        provider: AsrProvider,
        primaryModelId: String? = null,
        batchSize: Int = 25,
    ): List<String> {
        val primary = Model.ofId(provider, primaryModelId)
        return translateBatchAt(
            english = english,
            apiKey = apiKey,
            primaryModel = primary.id,
            fallbackModel = Model.fallbackFor(provider, primary.id),
            baseUrl = provider.chatBaseUrl,
            batchSize = batchSize,
        )
    }

    /**
     * 底层批量翻译。⚠️ 参数全部由 [translateBatch] 解析好，**别直接调它** ——
     * 少那一层 `ofId` 校验就多一条"把别家的模型名发出去"的路。
     */
    private suspend fun translateBatchAt(
        english: List<String>,
        apiKey: String,
        primaryModel: String,
        fallbackModel: String,
        baseUrl: String,
        batchSize: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        val result = MutableList(english.size) { "" }
        if (apiKey.isBlank() || english.isEmpty()) return@withContext result
        // 🔴【0918 保底】空模型名会 400，而那个错误不算"排队"，进不了兜底分支 ⇒ 翻译**静默变空**。
        //    （设置页已改成下拉，正常不会再有空串；这里是后端的最后一道。）
        val primary = primaryModel.ifBlank { Model.defaultFor(AsrProvider.SILICONFLOW).id }
        try {
            // 这一段实际在用的模型。兜底一旦成功就把它记住 —— 主模型整段下线时，
            // 后续批次直接用兜底，不用每批都先撞一次墙（长视频十几批 = 十几倍无谓请求 + 十几倍延迟）。
            var activeModel = primary
            var from = 0
            while (from < english.size) {
                val to = min(from + batchSize, english.size)
                val batch = english.subList(from, to)
                val (res1, err1) = callModel(batch, apiKey, baseUrl, activeModel)
                var translated = res1
                if (translated == null) {
                    // 🔴【0918】原来这里有个 `isQueueError(err1)` 的门槛，只有排队/限流才兜底。
                    //   模型下线回的是 400/404，过不了那道门 ⇒ 直接 emptyList ⇒ **翻译静默变空**。
                    //   现在换成 isWorthSwapping：只要服务器给了回应（含解析失败）就值得换模型再试。
                    if (err1 != null && fallbackModel != activeModel && isWorthSwapping(err1)) {
                        val why = if (isQueueError(err1)) "排队/限流" else "不可用（可能已下线/改名）"
                        Log.w(TAG, "$activeModel $why，切同家兜底 $fallbackModel: $err1")
                        val (res2, err2) = callModel(batch, apiKey, baseUrl, fallbackModel)
                        if (res2 != null) {
                            translated = res2
                            if (activeModel != fallbackModel) {
                                Log.i(TAG, "余下批次改用兜底模型 $fallbackModel（主模型 $primary 不可用）")
                                activeModel = fallbackModel
                            }
                        } else {
                            Log.w(TAG, "兜底 $fallbackModel 也失败: $err2")
                        }
                    } else {
                        Log.w(TAG, "翻译失败($activeModel): $err1")
                    }
                }
                val got = translated ?: emptyList()
                for (i in batch.indices) {
                    if (i < got.size) result[from + i] = got[i]
                }
                from = to
            }
        } catch (e: Exception) {
            Log.w(TAG, "translateBatch failed: ${e.message}")
        }
        result
    }

    /** 调用单个模型，返回 (结果列表?, 错误文案?)；成功则错误为 null，失败则结果为 null。 */
    private fun callModel(
        batch: List<String>,
        apiKey: String,
        baseUrl: String,
        model: String
    ): Pair<List<String>?, String?> {
        val numbered = batch.mapIndexed { i, s -> "${i + 1}. $s" }
        val prompt = buildString {
            append(
                "你是一个英文字幕翻译器。下面是一组按序号排列的英文字幕，请逐句翻译成中文，" +
                        "保持序号与顺序严格对应，只输出一个 JSON 数组（字符串数组），不要任何额外说明或 Markdown。" +
                        "例如输入 [\"1. Hello world\",\"2. How are you\"] 输出 [\"你好世界\",\"你好吗\"]。\n"
            )
            append(JSONArray(numbered).toString())
        }
        val bodyJson = JSONObject().apply {
            put("model", model)
            put("temperature", 0.3)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }
        return try {
            val conn = (URL(baseUrl.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = try {
                if (code in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText() ?: ""
            } finally { conn.disconnect() }
            if (code !in 200..299) {
                val msg = try { JSONObject(raw).optString("message", raw) } catch (_: Exception) { raw }
                return null to "HTTP $code: $msg"
            }
            val json = JSONObject(raw)
            val content = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "") ?: ""
            val cleaned = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val arrText = Regex("\\[.*\\]", RegexOption.DOT_MATCHES_ALL).find(cleaned)?.value ?: cleaned
            val arr = try { JSONArray(arrText) } catch (_: Exception) { null }
            if (arr == null) return null to "解析翻译结果失败: $cleaned"
            val list = List(arr.length()) { i -> arr.optString(i) }
            list to null
        } catch (e: Exception) {
            null to (e.message ?: "network error")
        }
    }

    /** 判断错误文案是否属于排队/限流（只用来区分日志措辞，**不再当兜底的门槛**）。 */
    private fun isQueueError(err: String): Boolean {
        val lower = err.lowercase()
        return QUEUE_HINTS.any { lower.contains(it) }
    }

    /**
     * 值不值得**换个模型再试一次**。
     *
     * 🔴【0918】以前门槛是 `isQueueError` —— 只认排队/限流。模型被下线/改名时接口回 400/404，
     *   文案里没有排队特征 ⇒ 不兜底 ⇒ 翻译静默变空。现在放宽成"服务器给了回应就值得换"：
     *   - `HTTP 4xx/5xx: ...` ⇒ 值：可能就是这个模型没了，换同家的另一个多半能活
     *   - `解析翻译结果失败: ...` ⇒ 值：这个模型不听话，换一个试试
     *   - 其它（`connect timed out` / `Unable to resolve host` / `Connection reset` 等）⇒ 不值：
     *     换模型救不了断网，只会让等待时间翻倍（超时 30s ⇒ 每批 60s）
     */
    private fun isWorthSwapping(err: String): Boolean =
        err.startsWith("HTTP ") || err.startsWith("解析")
}
