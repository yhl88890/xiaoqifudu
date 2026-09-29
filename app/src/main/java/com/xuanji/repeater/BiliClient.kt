package com.xuanji.repeater

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/**
 * B站网络客户端（纯 HttpURLConnection + org.json，零额外依赖，与 SpeechRecognizer/Translator 同路子）。
 * 移植自 bilibili-english-app 的 BiliClient / WBI 签名逻辑。
 *
 * 能力：
 *  - 游客 cookie（无需登录即可取大部分公开视频的播放地址与字幕）
 *  - WBI 签名（B站 player 接口必须）
 *  - fetchTimedSubtitle：取 B站 CC 英文字幕，并就近对齐中文字幕轨得到双语
 *  - fetchPlayUrl：取 DASH 分离的视频流 + 音频流地址（用于本机 mux 成 MP4）
 *  - downloadAudio / downloadVideoStream：把流下载到本地文件
 */
object BiliClient {

    private const val TAG = "BiliClient"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"

    // WBI 混排表（与 Python 版 / bilibili-english-app 一致）
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40, 61,
        26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    private var wbiKeys: Pair<String, String>? = null
    private var wbiTs = 0L
    private var guestCookie: String? = null
    private var guestCookieTs = 0L

    // ---------- 基础工具 ----------
    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (i in MIXIN_KEY_ENC_TAB) {
            if (i < orig.length) sb.append(orig[i])
        }
        return sb.substring(0, 32.coerceAtMost(sb.length))
    }

    private fun buildHeaders(cookie: String?): Map<String, String> {
        val h = mutableMapOf(
            "User-Agent" to UA,
            "Referer" to REFERER,
            "Accept" to "application/json, text/plain, */*",
        )
        if (!cookie.isNullOrEmpty()) h["Cookie"] = cookie
        return h
    }

    private fun httpGetJson(url: String, cookie: String? = null, timeoutMs: Int = 20000): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            buildHeaders(cookie).forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val raw = try {
            if (code in 200..299) conn.inputStream.bufferedReader().readText()
            else conn.errorStream?.bufferedReader()?.readText() ?: ""
        } finally { conn.disconnect() }
        if (code !in 200..299) throw RuntimeException("HTTP $code: $url")
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw RuntimeException("返回非 JSON（可能限流或需登录 cookie）: $url")
        }
    }

    // ---------- 游客 cookie ----------
    @Synchronized
    private fun getGuestCookie(): String? {
        val now = System.currentTimeMillis()
        if (guestCookie != null && now - guestCookieTs < 3600_000) return guestCookie
        try {
            val d = httpGetJson("https://api.bilibili.com/x/frontend/finger/spi")
            val data = d.optJSONObject("data") ?: return null
            val b3 = data.optString("b_3")
            val b4 = data.optString("b_4")
            if (b3.isNotEmpty()) {
                guestCookie = if (b4.isNotEmpty()) "buvid3=$b3; buvid4=$b4" else "buvid3=$b3"
                guestCookieTs = now
                return guestCookie
            }
        } catch (e: Exception) {
            Log.w(TAG, "guest cookie fail: ${e.message}")
        }
        return null
    }

    // ---------- WBI 签名 ----------
    @Synchronized
    private fun fetchWbiKeys(cookie: String?) {
        val ck = cookie ?: getGuestCookie()
        val d = httpGetJson("https://api.bilibili.com/x/web-interface/nav", cookie = ck)
        val wbi = d.optJSONObject("data")?.optJSONObject("wbi_img")
            ?: throw RuntimeException("获取 WBI 密钥失败: ${d.optString("message")}")
        val imgKey = wbi.optString("img_url").substringAfterLast("/").substringBefore(".")
        val subKey = wbi.optString("sub_url").substringAfterLast("/").substringBefore(".")
        wbiKeys = imgKey to subKey
        wbiTs = System.currentTimeMillis()
    }

    private fun sign(params: Map<String, String>, cookie: String?): String {
        val now = System.currentTimeMillis()
        if (wbiKeys == null || now - wbiTs > 3600_000) fetchWbiKeys(cookie ?: getGuestCookie())
        val (imgKey, subKey) = wbiKeys!!
        val mixinKey = getMixinKey(imgKey + subKey)
        val p = params.toMutableMap()
        p["wts"] = (now / 1000).toString()
        val sorted = p.toSortedMap()
        val filtered = sorted.mapValues { (_, v) -> v.filter { c -> c !in "!'()*" } }
        val query = filtered.map { (k, v) ->
            "$k=" + URLEncoder.encode(v, "UTF-8")
        }.joinToString("&")
        val wRid = md5(query + mixinKey)
        return "$query&w_rid=$wRid"
    }

    // ---------- URL 解析 ----------
    /** 从可能夹带中文/分享文案/标点的输入里抠出干净链接或 b23.tv 短链 */
    private fun extractUrl(input: String): String? {
        val tokens = input.split(' ', '\n', '\t', '　', '，', '。', '、', '）', ')', '】', ']', '>', '”', '"', '\'', '（', '(')
        for (t in tokens) {
            val u = t.trim('.', ')', '】', '}', '>', '”', '"', '\'', ']', '，', '。', '（', '(')
            if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("b23.tv/"))
                return u
        }
        return null
    }

    private fun resolveShort(url: String): String {
        val u = url.trim().replace("﻿", "").replace(" ", "")
        if (!u.contains("b23.tv")) return u
        return try {
            val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", UA)
            }
            conn.inputStream.close()
            conn.url.toString()
        } catch (e: Exception) {
            Log.w(TAG, "resolveShort fail: ${e.message}")
            u
        }
    }

    /** 公开：从输入解析 BV 号（供历史记录去重）。解析失败抛异常。 */
    fun parseBvid(input: String): String = extractBvid(input)

    private fun extractBvid(input: String): String {
        val direct = Regex("BV[0-9A-Za-z]+").find(input)
        if (direct != null) return direct.value
        val url = extractUrl(input)
            ?: throw RuntimeException("没识别到 B站链接或 BV 号。请粘贴视频页链接，或形如 BV1NE411v7W8 的 BV 号。")
        val resolved = resolveShort(url)
        val m = Regex("BV[0-9A-Za-z]+").find(resolved)
            ?: throw RuntimeException("从链接里没找到 BV 号，请确认这是 B站视频页链接。")
        return m.value
    }

    // ---------- 字幕 ----------
    private fun getCidAndTitle(bvid: String, cookie: String?): Pair<Long, String> {
        val d = httpGetJson("https://api.bilibili.com/x/web-interface/view?bvid=$bvid", cookie = cookie)
        if (d.optInt("code") != 0) throw RuntimeException("获取视频信息失败: ${d.optString("message")}")
        val data = d.getJSONObject("data")
        return data.optLong("cid") to data.optString("title")
    }

    /** 字幕轨的"像不像英文"打分：高 = 更像英文轨。 */
    private fun enScore(s: JSONObject): Int {
        val lan = s.optString("lan").lowercase()
        val doc = s.optString("lan_doc").lowercase()
        return when {
            lan.startsWith("en") -> 3          // en / en-US
            lan.contains("en") -> 2            // ai-en 之类
            doc.contains("英文") || doc.contains("english") -> 2
            lan.startsWith("zh") || lan.contains("zh") || lan.contains("ai-zh") -> 0
            doc.contains("中文") -> 0
            else -> 1                          // 认不出来，当中间态
        }
    }

    /**
     * 从字幕轨列表里挑出「主轨」和「辅助轨」。
     *
     * 🔴【0929 阿亮纠正】原实现是 `pickLan(subList, "en")` —— **只认 lan 以 en 开头的轨**，
     *   找不到就抛"该视频没有英文字幕"⇒ 整个 CC 路径作废、转去跑 ASR。
     *   实测踩到两个坑（BV1bDbG6ZE5U，31 分钟英语播客）：
     *     1. `need_login_subtitle=true` 时游客**看不到任何轨** ⇒ 被当成"没字幕"；
     *        登录后该视频只有 1 条轨 `lan='ai-zh'`（B站 AI 听译的中文，平均 2.5 秒/条）。
     *     2. 就算登录了，`pickLan("en")` 也**永远匹配不到 `ai-zh`** ⇒ 照样没用上。
     *
     * 阿亮的要求（原话）：**"有中文就是中文字幕，有英文就是英文字幕，不用你来区分"** ——
     *   不要把中文轨当"译文"去处理，**轨道写的是什么语言，就是什么语言**。
     *   所以我们改成：**有哪些轨就用哪些轨**，只是排个优先级：
     *     · 主轨（当正文文本）：优先英文轨（学英语要原文）；没有英文就用中文轨。
     *     · 辅助轨（当对照）：优先中文轨（给英文配中文对照）。
     *   返回 null 才表示"真的一条轨都没有"⇒ 调用方转 ASR 是对的。
     */
    private fun pickTracks(subList: JSONArray?): Pair<JSONObject?, JSONObject?> {
        if (subList == null || subList.length() == 0) return null to null
        val all = mutableListOf<JSONObject>()
        for (i in 0 until subList.length()) all.add(subList.getJSONObject(i))
        if (all.isEmpty()) return null to null

        // 同分时优先 UP 主传的（ai_type==0），其次 AI 生成的
        fun bestOf(candidates: List<JSONObject>): JSONObject? =
            candidates.minByOrNull { it.optInt("ai_type", 1) }

        val byScore = all.groupBy { enScore(it) }
        val enTracks = (byScore[3] ?: emptyList()) + (byScore[2] ?: emptyList())
        val zhTracks = byScore[0] ?: emptyList()

        val main = bestOf(enTracks) ?: bestOf(zhTracks) ?: bestOf(all)
        // 辅助轨：跟主轨不是同一条就行（主轨是中文时就没有辅助轨）
        val aux = if (main != null && enScore(main) >= 2) bestOf(zhTracks) else null
        return main to aux
    }

    /** 解析某条字幕轨道的 body，返回 (start, end, text) 列表。 */
    private fun parseSubBody(sub: JSONObject, cookie: String?): List<SubLine> {
        var subUrl = sub.optString("subtitle_url")
        if (subUrl.startsWith("//")) subUrl = "https:$subUrl"
        val subData = httpGetJson(subUrl, cookie)
        val body = subData.optJSONArray("body") ?: JSONArray()
        val out = mutableListOf<SubLine>()
        for (i in 0 until body.length()) {
            val it = body.getJSONObject(i)
            val content = it.optString("content").trim()
            if (content.isEmpty()) continue
            val start = it.optDouble("from", 0.0)
            val end = it.optDouble("to", 0.0)
            out.add(SubLine(content, start, end))
        }
        return out
    }

    /**
     * 取带时间戳字幕：返回 (标题, 台词列表)。
     * - 优先用 B站 CC 英文字幕；若同时有中文字幕轨，按时间就近对齐得到双语。
     * - 该视频没有英文字幕时抛异常（调用方据此转 ASR 路径）。
     * - need_login_subtitle 且未登录 cookie → 抛明确异常提示去登录。
     */
    suspend fun fetchTimedSubtitle(inputUrl: String, cookie: String? = null): Pair<String, List<SubLine>> =
        withContext(Dispatchers.IO) {
            val ck = cookie ?: getGuestCookie()
            val bvid = extractBvid(inputUrl)
            val (cid, title) = getCidAndTitle(bvid, ck)

            val q = sign(mapOf("bvid" to bvid, "cid" to cid.toString()), ck)
            val player = httpGetJson("https://api.bilibili.com/x/player/wbi/v2?$q", cookie = ck)

            var needLogin = false
            var subList: JSONArray? = null
            if (player.optInt("code") == 0) {
                val pdata = player.optJSONObject("data") ?: JSONObject()
                needLogin = pdata.optBoolean("need_login_subtitle")
                subList = pdata.optJSONObject("subtitle")?.optJSONArray("list")
                    ?: pdata.optJSONObject("subtitle")?.optJSONArray("subtitles")
            } else {
                try {
                    val old = httpGetJson("https://api.bilibili.com/x/player/v2?bvid=$bvid&cid=$cid", cookie = ck)
                    if (old.optInt("code") == 0)
                        subList = old.optJSONObject("data")?.optJSONObject("subtitle")?.optJSONArray("list")
                } catch (e: Exception) {
                    Log.w(TAG, "fallback player/v2 fail: ${e.message}")
                }
            }

            if (needLogin && cookie.isNullOrEmpty())
                throw RuntimeException("该视频的字幕要 B站 登录才给看（游客看不到轨道）。去设置页填 SESSDATA 登录，或改用其他视频。")

            // 🔴【0929】改成"有几条用几条"：主轨当正文、辅助轨当对照。
            //   原实现只认 en 开头 ⇒ 只有 ai-zh 中文轨的视频（很常见）被误判成"没字幕"。
            val (mainSub, auxSub) = pickTracks(subList)
            if (mainSub == null)
                throw RuntimeException("该视频没有字幕轨（可能是硬字幕——字幕烧进画面了，任何工具都取不出来）。将改用语音识别生成。")
            val mainLines = parseSubBody(mainSub, ck)
            if (mainLines.isEmpty()) throw RuntimeException("字幕内容为空。")

            // 辅助轨按时间就近对齐成对照（中文配英文、或反之）
            val auxLines = if (auxSub != null) parseSubBody(auxSub, ck) else emptyList()
            val lines = if (auxLines.isNotEmpty()) {
                mainLines.map { m ->
                    val best = auxLines.minByOrNull { abs(it.start - m.start) }
                        ?.takeIf { abs(it.start - m.start) <= 8.0 }
                    SubLine(m.text, m.start, m.end, best?.text ?: "")
                }
            } else mainLines
            title to lines
        }

    /** 播放地址（DASH 分离流）：返回视频流 + 音频流 URL。 */
    suspend fun fetchPlayUrl(inputUrl: String, cookie: String? = null): PlayInfo =
        withContext(Dispatchers.IO) {
            val ck = cookie ?: getGuestCookie()
            val bvid = extractBvid(inputUrl)
            val (cid, _) = getCidAndTitle(bvid, ck)
            val q = sign(mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to "64",
                "fnval" to "16",
                "fourk" to "1"
            ), ck)
            val player = httpGetJson("https://api.bilibili.com/x/player/wbi/playurl?$q", cookie = ck)
            if (player.optInt("code") != 0)
                throw RuntimeException("获取播放地址失败: ${player.optString("message")}")
            val data = player.optJSONObject("data")
                ?: throw RuntimeException("无播放数据（部分版权视频不支持）")
            val dash = data.optJSONObject("dash")
                ?: throw RuntimeException("该视频不支持 DASH 流式播放（可能是版权限制）")
            val vids = dash.optJSONArray("video") ?: JSONArray()
            var videoUrl = ""
            var bestH = -1
            for (i in 0 until vids.length()) {
                val v = vids.getJSONObject(i)
                val h = v.optInt("height")
                if (h > bestH) { bestH = h; videoUrl = v.optString("baseUrl") }
            }
            val auds = dash.optJSONArray("audio") ?: JSONArray()
            var audioUrl = ""
            var bestB = -1
            for (i in 0 until auds.length()) {
                val a = auds.getJSONObject(i)
                val b = a.optInt("bandwidth")
                if (b > bestB) { bestB = b; audioUrl = a.optString("baseUrl") }
            }
            if (audioUrl.isEmpty() && videoUrl.isEmpty())
                throw RuntimeException("无可播放地址。")
            PlayInfo(videoUrl = videoUrl, audioUrl = audioUrl, hasVideo = videoUrl.isNotEmpty())
        }

    /** 下载指定 URL 到 destFile（带 Referer，流式写入，适配大文件）。 */
    suspend fun downloadUrlTo(url: String, destFile: File, timeoutMs: Int = 300_000) =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20000
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", REFERER)
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw RuntimeException("下载失败 HTTP $code")
            }
            conn.inputStream.use { input ->
                destFile.outputStream().use { out -> input.copyTo(out) }
            }
            if (destFile.length() == 0L) throw RuntimeException("下载内容为空")
        }

    /** 取视频标题（语音转文字流程用） */
    suspend fun fetchTitle(inputUrl: String, cookie: String? = null): String =
        withContext(Dispatchers.IO) {
            val ck = cookie ?: getGuestCookie()
            val bvid = extractBvid(inputUrl)
            val (_, title) = getCidAndTitle(bvid, ck)
            title
        }

    /** 下载 B站音频流到 destFile（选最低带宽音频，体积小、识别够用）。 */
    suspend fun downloadAudio(inputUrl: String, cookie: String? = null, destFile: File) =
        withContext(Dispatchers.IO) {
            val ck = cookie ?: getGuestCookie()
            val bvid = extractBvid(inputUrl)
            val (cid, _) = getCidAndTitle(bvid, ck)
            val q = sign(mapOf(
                "bvid" to bvid, "cid" to cid.toString(), "qn" to "64", "fnval" to "16", "fourk" to "1"
            ), ck)
            val player = httpGetJson("https://api.bilibili.com/x/player/wbi/playurl?$q", cookie = ck)
            if (player.optInt("code") != 0)
                throw RuntimeException("获取播放地址失败: ${player.optString("message")}")
            val data = player.optJSONObject("data")
                ?: throw RuntimeException("无播放数据（部分版权视频不支持）")
            val dash = data.optJSONObject("dash")
                ?: throw RuntimeException("该视频不支持 DASH 流式播放")
            val auds = dash.optJSONArray("audio") ?: JSONArray()
            if (auds.length() == 0) throw RuntimeException("无音频流")
            var audioUrl = ""
            var minB = Long.MAX_VALUE
            for (i in 0 until auds.length()) {
                val a = auds.getJSONObject(i)
                val b = a.optLong("bandwidth")
                if (b < minB) { minB = b; audioUrl = a.optString("baseUrl") }
            }
            if (audioUrl.isEmpty()) throw RuntimeException("无可用音频流")
            downloadUrlTo(audioUrl, destFile)
        }

    /** 下载 B站最佳清晰度视频流到 destFile（与音频流分开下载，后续 mux）。 */
    suspend fun downloadVideoStream(inputUrl: String, cookie: String? = null, destFile: File) =
        withContext(Dispatchers.IO) {
            val ck = cookie ?: getGuestCookie()
            val bvid = extractBvid(inputUrl)
            val (cid, _) = getCidAndTitle(bvid, ck)
            val q = sign(mapOf(
                "bvid" to bvid, "cid" to cid.toString(), "qn" to "64", "fnval" to "16", "fourk" to "1"
            ), ck)
            val player = httpGetJson("https://api.bilibili.com/x/player/wbi/playurl?$q", cookie = ck)
            if (player.optInt("code") != 0)
                throw RuntimeException("获取播放地址失败: ${player.optString("message")}")
            val data = player.optJSONObject("data")
                ?: throw RuntimeException("无播放数据（部分版权视频不支持）")
            val dash = data.optJSONObject("dash")
                ?: throw RuntimeException("该视频不支持 DASH 流式播放")
            val vids = dash.optJSONArray("video") ?: JSONArray()
            if (vids.length() == 0) throw RuntimeException("无视频流")
            var videoUrl = ""
            var bestH = -1
            for (i in 0 until vids.length()) {
                val v = vids.getJSONObject(i)
                val h = v.optInt("height")
                if (h > bestH) { bestH = h; videoUrl = v.optString("baseUrl") }
            }
            if (videoUrl.isEmpty()) throw RuntimeException("无可用视频流")
            downloadUrlTo(videoUrl, destFile)
        }
}

/** 一条字幕：EN 文本 + 起止时间(秒) + 可选中文（B站 ZH 轨对齐 / ASR 后翻译填入）。 */
data class SubLine(val text: String, val start: Double, val end: Double, val cn: String = "") : java.io.Serializable

/** 播放地址信息：DASH 分离的视频流 / 音频流。 */
data class PlayInfo(val videoUrl: String, val audioUrl: String, val hasVideo: Boolean)
