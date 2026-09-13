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
 * 字幕翻译（对接硅基流动 SiliconFlow 的 chat/completions）。
 *
 * - 批量把英文字幕翻译成中文，减少调用次数。
 * - 主模型遇到排队/限流（HTTP 429/503、或返回排队/过载/繁忙等提示）时，
 *   自动切换到兜底模型 [fallbackModel]（腾讯混元 Hy3：tencent/Hy3，当前 SiliconFlow 限时免费版）。
 * - 没有配置 Key 时返回与输入等长的空列表（调用方再退回免费翻译通道）。
 * - 全程失败也不抛异常，翻译缺失不影响「英文识别 + 播放学习」主流程。
 */
object SubtitleTranslator {
    private const val TAG = "SubtitleTranslator"
    const val FALLBACK_MODEL = "tencent/Hy3"
    const val DEFAULT_PRIMARY_MODEL = "Qwen/Qwen2.5-7B-Instruct"
    private const val BASE_URL = "https://api.siliconflow.cn/v1"

    /** 排队/限流特征（命中即触发切换兜底模型） */
    private val QUEUE_HINTS = listOf(
        "queue", "rate", "ratelimit", "rate_limit", "overload", "overloaded",
        "繁忙", "排队", "too many requests", "model is currently", "currently overloaded",
        "try again", "请稍后", "busy", "capacity", "429", "503", "请求过于频繁", "限流"
    )

    /**
     * 批量翻译。返回与 english 等长的中文列表（翻译失败项为 ""）。
     * @param primaryModel 主模型（默认 Qwen/Qwen2.5-7B-Instruct，可在设置里改）
     * @param fallbackModel 兜底模型（默认腾讯混元 Hy3 限时免费版）
     */
    suspend fun translateBatch(
        english: List<String>,
        apiKey: String,
        primaryModel: String = DEFAULT_PRIMARY_MODEL,
        fallbackModel: String = FALLBACK_MODEL,
        baseUrl: String = BASE_URL,
        batchSize: Int = 25
    ): List<String> = withContext(Dispatchers.IO) {
        val result = MutableList(english.size) { "" }
        if (apiKey.isBlank() || english.isEmpty()) return@withContext result
        try {
            var from = 0
            while (from < english.size) {
                val to = min(from + batchSize, english.size)
                val batch = english.subList(from, to)
                // 先主模型；主模型排队/限流则切换 HY3 兜底
                val (primaryRes, primaryErr) = callModel(batch, apiKey, baseUrl, primaryModel)
                val translated = if (primaryRes != null) {
                    primaryRes
                } else if (primaryErr != null && isQueueError(primaryErr) && fallbackModel != primaryModel) {
                    Log.w(TAG, "主模型($primaryModel)排队/限流，切换 HY3 兜底: $primaryErr")
                    val (fbRes, fbErr) = callModel(batch, apiKey, baseUrl, fallbackModel)
                    fbRes ?: run { Log.w(TAG, "HY3 兜底也失败: $fbErr"); emptyList() }
                } else {
                    Log.w(TAG, "翻译失败($primaryModel): $primaryErr")
                    emptyList()
                }
                for (i in batch.indices) {
                    if (i < translated.size) result[from + i] = translated[i]
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

    /** 判断错误文案是否属于排队/限流（应切换兜底模型）。 */
    private fun isQueueError(err: String): Boolean {
        val lower = err.lowercase()
        return QUEUE_HINTS.any { lower.contains(it) }
    }
}
