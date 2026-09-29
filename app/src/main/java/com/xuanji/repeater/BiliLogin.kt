package com.xuanji.repeater

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * B站 扫码登录（v75 新增）。
 *
 * ## 为什么要做
 * 以前只能让用户开浏览器 → F12 → 从 cookie 里翻出 222 个字符的 SESSDATA 粘进来。
 * 这个体验说不过去，而且**手动填只给了 SESSDATA 一个键**：
 *   - 缺 `buvid3`/`buvid4` 设备指纹 ⇒ 跟账号对不上 ⇒ B站 风控当"陌生设备" ⇒ 频繁 412
 *   - 缺 `bili_jct`/`DedeUserID` ⇒ 请求头不像真浏览器
 * 扫码登录则由 B站 **一次性下发全套凭据 + 与账号绑定的设备指纹**，这些毛病自然就好了。
 *
 * ## 流程（B站 官方 Web 端接口，两步）
 * ```
 * 1. GET passport.bilibili.com/x/passport-login/web/qrcode/generate
 *      → data.qrcode_key（轮询用的钥匙）
 *      → data.url       （编码进二维码的内容）
 * 2. GET passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=…
 *      → data.code == 86101 等待扫码
 *                   == 86090 已扫码，等手机上确认
 *                   == 86038 二维码过期
 *                   == 0     成功
 * ```
 *
 * ## 🔴 最大的坑（务必记住）
 * `/poll` 成功时不从 JSON body 给 cookie，而是放在 **HTTP 响应头 `Set-Cookie`** 里。
 * 网上大量实现栽在这 —— 提示"登录成功"但 cookie 拿不到，看着像网络问题。
 * 本实现用 [readSetCookies] 从响应头抠，**必须显式读 header**，别只看 body。
 *
 * ## 隐私
 * 二维码在本机用 ZXing 生成，**不上传任何第三方二维码服务**。
 */
object BiliLogin {

    private const val TAG = "BiliLogin"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"

    /** 扫码状态机。调用方据此更新 UI 文案。 */
    enum class State {
        /** 二维码已生成，等用户扫 */
        WAITING,
        /** 已扫码，等用户在手机上点确认 */
        SCANNED,
        /** 成功，凭据已拿到 */
        SUCCESS,
        /** 二维码过期，要重新生成 */
        EXPIRED,
        /** 出错 */
        FAILED,
    }

    /** 一次扫码会话：二维码图片 + 轮询钥匙。 */
    data class QrSession(val bitmap: Bitmap, val key: String)

    /** 轮询结果：状态 + （成功时）拿到的 cookie 串。 */
    data class PollResult(val state: State, val cookie: String? = null, val nickname: String? = null)

    // ---------- 第一步：生成二维码 ----------

    /**
     * 向 B站 申请一个登录二维码，并在本机渲染成 Bitmap。
     *
     * @param sizePx 二维码图片边长（像素）。UI 里一般给 480 就够清晰了。
     */
    suspend fun createQrSession(sizePx: Int = 480): QrSession = withContext(Dispatchers.IO) {
        val json = getJson("https://passport.bilibili.com/x/passport-login/web/qrcode/generate", null)
        if (json.optInt("code") != 0)
            throw RuntimeException("申请登录二维码失败：${json.optString("message")}")
        val data = json.optJSONObject("data")
            ?: throw RuntimeException("申请登录二维码失败：返回数据为空")
        val key = data.optString("qrcode_key")
        val url = data.optString("url")
        if (key.isBlank() || url.isBlank())
            throw RuntimeException("申请登录二维码失败：二维码内容为空")
        QrSession(renderQr(url, sizePx), key)
    }

    /** 用 ZXing 把登录链接画成二维码位图（纯本地，不外传）。 */
    private fun renderQr(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            // 🔴 留 1 格白边：没有静区的话有些扫码器认不出来
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    // ---------- 第二步：轮询扫码结果 ----------

    /**
     * 阻塞式轮询，直到扫码成功 / 过期 / 出错。**调用方负责放在协程里**。
     *
     * @param onState 每次状态变化时回调，用来更新 UI 文案。
     * @return 成功时 [PollResult.SUCCESS]，并带上完整 cookie 串与昵称。
     */
    suspend fun pollUntilDone(
        key: String,
        onState: (State) -> Unit = {},
    ): PollResult = withContext(Dispatchers.IO) {
        // 二维码有效期约 3 分钟，轮询间隔 2 秒（官方文档建议不低于 1 秒）
        val maxTries = 100
        repeat(maxTries) {
            delay(2000)
            val raw = getRaw("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key", null)
            val json = runCatching { JSONObject(raw.body) }.getOrNull()
                ?: return@withContext PollResult(State.FAILED)

            // ⚠️ 两层 code：外层 = 请求本身成没成；data.code = 扫码状态
            if (json.optInt("code") != 0) return@withContext PollResult(State.FAILED)
            val data = json.optJSONObject("data") ?: return@withContext PollResult(State.FAILED)

            when (data.optInt("code")) {
                0 -> {
                    // 🔴 关键：cookie 在 Set-Cookie 响应头里，不在 body
                    val cookie = readSetCookies(raw.headers)
                    if (cookie.isNullOrBlank()) {
                        Log.w(TAG, "poll 成功但响应头没拿到 cookie")
                        return@withContext PollResult(State.FAILED)
                    }
                    onState(State.SUCCESS)
                    val nick = runCatching { fetchNickname(cookie) }.getOrNull()
                    return@withContext PollResult(State.SUCCESS, cookie, nick)
                }
                86090 -> onState(State.SCANNED)
                86038 -> { onState(State.EXPIRED); return@withContext PollResult(State.EXPIRED) }
                86101 -> onState(State.WAITING)
                else -> onState(State.WAITING)
            }
        }
        onState(State.EXPIRED)
        PollResult(State.EXPIRED)
    }

    /**
     * 从 `Set-Cookie` 响应头里拼出 cookie 串。
     *
     * 🔴 这一步是整条链最容易错的地方：`HttpURLConnection` 的 `getHeaderFields()` 会把
     *    同名头聚成一个 List，key 大小写还不一定 —— 所以要**遍历找名字**，不能直接 get。
     * 只保留名值对，丢掉 `Path`/`Domain`/`Expires` 这些属性（B站 只要名值）。
     */
    private fun readSetCookies(headers: Map<String, List<String>>): String? {
        val out = mutableListOf<String>()
        for ((k, v) in headers) {
            if (!k.equals("Set-Cookie", ignoreCase = true)) continue
            for (line in v) {
                val first = line.substringBefore(';').trim()
                if (first.contains('=')) out.add(first)
            }
        }
        if (out.isEmpty()) return null
        // 去重（同名保留最后一个，后下发的优先）
        val map = LinkedHashMap<String, String>()
        for (kv in out) {
            val name = kv.substringBefore('=').trim()
            if (name.isNotEmpty()) map[name] = kv
        }
        return map.values.joinToString("; ")
    }

    /** 拿着 cookie 问一次 nav，顺便把昵称取回来（当"登录成功"的直观证据）。 */
    private fun fetchNickname(cookie: String): String? {
        val json = getJson("https://api.bilibili.com/x/web-interface/nav", cookie)
        if (json.optInt("code") != 0) return null
        return json.optJSONObject("data")?.optString("uname")?.ifBlank { null }
    }

    /**
     * 公开的登录态自检：给设置页显示"当前登的是谁"。
     * 返回 null 表示没登录 / cookie 失效。
     */
    suspend fun checkLogin(context: Context): String? = withContext(Dispatchers.IO) {
        val cookie = BiliClient.buildCookie(AsrPrefs.loadBiliSessdata(context)) ?: return@withContext null
        runCatching { fetchNickname(cookie) }.getOrNull()
    }

    // ---------- 极简 HTTP（只给登录这条链用，不动 BiliClient） ----------

    private class Raw(val body: String, val headers: Map<String, List<String>>)

    private fun getRaw(url: String, cookie: String?): Raw {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", REFERER)
            setRequestProperty("Origin", "https://www.bilibili.com")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            if (!cookie.isNullOrEmpty()) setRequestProperty("Cookie", cookie)
            // 🔴 不自动跟随跳转：登录成功后可能 302，我们要的是那一跳的 Set-Cookie
            instanceFollowRedirects = false
        }
        val code = conn.responseCode
        val body = try {
            if (code in 200..299) conn.inputStream.bufferedReader().readText()
            else conn.errorStream?.bufferedReader()?.readText() ?: ""
        } finally {
            // ⚠️ headers 要在 disconnect 之前读完
        }
        val headers = conn.headerFields ?: emptyMap()
        conn.disconnect()
        if (code !in 200..299 && headers.isEmpty())
            throw RuntimeException("HTTP $code")
        return Raw(body, headers)
    }

    private fun getJson(url: String, cookie: String?): JSONObject =
        JSONObject(getRaw(url, cookie).body)

    /** 登出：清掉本地凭据（B站 那边不需要通知）。 */
    fun logout(context: Context) {
        AsrPrefs.saveBiliSessdata(context, "")
    }
}
