package com.xuanji.repeater

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 缓存内容：句子 + 波形（波形归一化 0..1，量化到 byte 后 Base64 存储，紧凑） */
data class CachedSplit(
    val sentences: List<Sentence>,
    val waveform: FloatArray,
    val totalMs: Long,
)

/** 句子缓存：按音频文件路径 hash 落盘（JSON），避免重复分析 */
object SentenceCache {

    private fun cacheFile(context: Context, item: AudioItem): File {
        val dir = File(context.filesDir, "sentences")
        dir.mkdirs()
        return File(dir, "${item.path.hashCode()}.json")
    }

    fun load(context: Context, item: AudioItem): CachedSplit? {
        return try {
            val f = cacheFile(context, item)
            if (!f.exists()) return null
            val root = JSONObject(f.readText())
            val arr = root.getJSONArray("sentences")
            val texts = root.optJSONArray("texts")
            val sentences = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val t = if (texts != null && i < texts.length()) texts.optString(i, "") else ""
                Sentence(o.getLong("s"), o.getLong("e"), t)
            }
            CachedSplit(sentences, decodeWaveform(root.optString("waveform", "")), root.optLong("totalMs", 0L))
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, item: AudioItem, split: CachedSplit) {
        try {
            val arr = JSONArray()
            val txt = JSONArray()
            for (s in split.sentences) {
                arr.put(JSONObject().put("s", s.startMs).put("e", s.endMs))
                txt.put(s.text)
            }
            val root = JSONObject()
                .put("sentences", arr)
                .put("texts", txt)
                .put("waveform", encodeWaveform(split.waveform))
                .put("totalMs", split.totalMs)
            cacheFile(context, item).writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    private fun encodeWaveform(w: FloatArray): String {
        if (w.isEmpty()) return ""
        val bytes = ByteArray(w.size) { (w[it].coerceIn(0f, 1f) * 255f).toInt().toByte() }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decodeWaveform(s: String): FloatArray {
        if (s.isEmpty()) return FloatArray(0)
        val bytes = Base64.decode(s, Base64.NO_WRAP)
        return FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }
    }
}