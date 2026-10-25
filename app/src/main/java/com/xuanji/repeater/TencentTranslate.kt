package com.xuanji.repeater

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯云「批量文本翻译」—— **纯备用翻译通道**。
 *
 * ## 阿亮为什么加这个（1017 原话）
 * > 「我只是多一个接口，让它复制原来的所有功能就行了，不用添加功能好不好，就是多一个翻译接口。
 * >   如果硅基流动不能用了，我现在所有的功能都能正常正常使用，就这么个意图」
 *
 * ⇒ 所以这个文件的**唯一职责**：把"英文字幕 → 中文字幕"这一步**换个来源**。
 *   **不加任何功能、不改任何界面流程**，契约跟 [SubtitleTranslator.translateBatch] 完全一致
 *   （入参英文列表、出参等长中文列表、失败项为 ""）⇒ 调用方只需在入口做个 if 分流。
 *
 * ## 为什么单独一个文件、不并进 [AsrProvider]
 * 腾讯不是 OpenAI 兼容接口：要 `AppID` + `SecretId` + `SecretKey` **三个值**、要自己算 TC3 签名、
 * 而且它**不提供语音识别**。硬塞进 `AsrProvider`（"一把 Key 管四处"的枚举）会把识别和 TTS 一起污染。
 *
 * ## 官方限制（记着，踩了就是 400）
 * - 🔴 **单次请求的文本长度总和必须 < 2000 字符**（是**字符**，不是条数）⇒ 必须按字符数分批。
 * - 语种：`Source="en"` `Target="zh"`。
 * - `ProjectId` 默认项目填 `0`。
 */
object TencentTranslate {

    private const val TAG = "TencentTranslate"
    private const val HOST = "tmt.tencentcloudapi.com"
    private const val SERVICE = "tmt"
    private const val ACTION = "TextTranslateBatch"
    private const val VERSION = "2018-03-21"
    private const val REGION = "ap-guangzhou"

    /** 官方硬限制 2000 字符，留点余量给 JSON 转义 */
    private const val MAX_CHARS_PER_REQUEST = 1800

    /** 一批最多几条（纯防御，真正的闸是字符数） */
    private const val MAX_ITEMS_PER_REQUEST = 25

    /** 腾讯云的三个凭据。`AppID` 这批翻译接口其实用不上，但 ASR 那边要用，一并存着省得再问。 */
    data class Cred(val appId: String, val secretId: String, val secretKey: String) {
        val usable: Boolean get() = secretId.isNotBlank() && secretKey.isNotBlank()
    }

    /**
     * 批量翻译（英 → 中）。返回与入参**等长**的列表，翻不了的项给 `""`。
     * ⚠️ 阻塞调用 ⇒ 调用方必须在 IO 线程/`withContext(Dispatchers.IO)` 里跑。
     */
    fun translateBatch(english: List<String>, cred: Cred): List<String> {
        val out = MutableList(english.size) { "" }
        if (!cred.usable || english.isEmpty()) return out

        var cursor = 0
        while (cursor < english.size) {
            val idxs = ArrayList<Int>()
            var chars = 0
            while (cursor + idxs.size < english.size && idxs.size < MAX_ITEMS_PER_REQUEST) {
                val i = cursor + idxs.size
                val len = english[i].length
                if (idxs.isNotEmpty() && chars + len > MAX_CHARS_PER_REQUEST) break
                idxs.add(i)
                chars += len
            }
            if (idxs.isEmpty()) break

            val texts = idxs.map { english[it] }
            val translated = runCatching { callOnce(texts, cred) }
                .onFailure { Log.w(TAG, "这一批没翻成：${it.message}") }
                .getOrNull()
            if (translated != null && translated.size == texts.size) {
                idxs.forEachIndexed { k, srcIdx -> out[srcIdx] = translated[k] }
            }
            cursor += idxs.size
        }
        return out
    }

    private fun callOnce(texts: List<String>, cred: Cred): List<String> {
        val payload = JSONObject().apply {
            put("Source", "en")
            put("Target", "zh")
            put("ProjectId", 0)
            put("SourceTextList", JSONArray(texts))
        }.toString()

        val ts = System.currentTimeMillis() / 1000
        val authorization = genAuthorization(payload, ts, cred)

        val conn = (URL("https://$HOST").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-TC-Action", ACTION)
            setRequestProperty("X-TC-Version", VERSION)
            setRequestProperty("X-TC-Timestamp", ts.toString())
            setRequestProperty("X-TC-Region", REGION)
            setRequestProperty("Authorization", authorization)
        }
        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val httpCode = conn.responseCode
            val body = (if (httpCode in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (httpCode !in 200..299) throw RuntimeException("HTTP $httpCode：$body")

            val resp = JSONObject(body).getJSONObject("Response")
            resp.optJSONObject("Error")?.let {
                throw RuntimeException("${it.optString("Code")} ${it.optString("Message")}")
            }
            val arr = resp.getJSONArray("TargetTextList")
            return (0 until arr.length()).map { arr.optString(it, "") }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    // ---------------- TC3-HMAC-SHA256 签名 ----------------
    // 官方 Python 示例的逐行翻译。⚠️ 三个坑：
    //   ① `canonicalHeaders` **自带结尾换行**，拼 `canonicalRequest` 时还要再加一个 `\n`（会看到空行，是对的）
    //   ② `x-tc-action` 的值参与签名时**必须小写**（`texttranslatebatch`），但真正发的 header 用大写驼峰
    //   ③ `date` 用 **UTC**，不是本地时区 —— 差一天就签名过期
    private fun genAuthorization(payload: String, timestampSec: Long, cred: Cred): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestampSec * 1000))

        val contentType = "application/json; charset=utf-8"
        val canonicalHeaders =
            "content-type:$contentType\n" +
                "host:$HOST\n" +
                "x-tc-action:${ACTION.lowercase(Locale.US)}\n"
        val signedHeaders = "content-type;host;x-tc-action"
        val hashedPayload = sha256Hex(payload)

        val canonicalRequest =
            "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + hashedPayload

        val credentialScope = "$date/$SERVICE/tc3_request"
        val stringToSign =
            "TC3-HMAC-SHA256\n$timestampSec\n$credentialScope\n${sha256Hex(canonicalRequest)}"

        val secretDate = hmacSha256(("TC3" + cred.secretKey).toByteArray(Charsets.UTF_8), date)
        val secretService = hmacSha256(secretDate, SERVICE)
        val secretSigning = hmacSha256(secretService, "tc3_request")
        val signature = hmacSha256(secretSigning, stringToSign).toHex()

        return "TC3-HMAC-SHA256 Credential=${cred.secretId}/$credentialScope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .toHex()

    private fun hmacSha256(key: ByteArray, msg: String): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(msg.toByteArray(Charsets.UTF_8))
        }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
