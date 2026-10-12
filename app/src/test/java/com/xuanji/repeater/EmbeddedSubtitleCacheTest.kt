package com.xuanji.repeater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 内封轨缓存「总量封顶」的单测。【1012 新建】
 *
 * 为什么必须测：这个函数**会删用户的文件**（虽然是缓存），而且逻辑里有个
 * `drop(MAX_CACHE_FILES - 1)` 这种**很容易写成 `drop(MAX)` 的 off-by-one**。
 * 两个方向都难受：少删一个 ⇒ 上限形同虚设；多删一个 ⇒ 白白多扒一次轨。
 *
 * 🔴 最要紧的一条断言是「**刚写完那份必须留下**」—— 如果 `keep` 被误删，
 *   用户刚扒好的缓存当场失效，下次打开又要等几秒，而且**看起来像随机变慢**、极难查。
 *
 * ⚠️ 这里只测纯文件操作（`trimCache` 不碰 Context），不测扒轨本身。
 */
class EmbeddedSubtitleCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 造 [n] 份缓存文件，mtime 依次递增（下标越大越新）。 */
    private fun makeFiles(dir: File, n: Int): List<File> =
        (0 until n).map { i ->
            File(dir, "hash%02d__label.srt".format(i)).apply {
                writeText("x")
                // ⚠️ 必须让 mtime **互不相同** —— 相同的话排序不稳定，用例会随机红/绿
                setLastModified(1_000_000L + i * 1000L)
            }
        }

    @Test
    fun `没超上限_一个都不删`() {
        val dir = tmp.newFolder("c")
        val files = makeFiles(dir, EmbeddedSubtitle.MAX_CACHE_FILES)

        EmbeddedSubtitle.trimCache(dir, keep = files.last())

        assertEquals(EmbeddedSubtitle.MAX_CACHE_FILES, dir.listFiles()!!.size)
    }

    @Test
    fun `超上限_修剪到正好上限`() {
        val dir = tmp.newFolder("c")
        val files = makeFiles(dir, EmbeddedSubtitle.MAX_CACHE_FILES + 5)

        EmbeddedSubtitle.trimCache(dir, keep = files.last())

        assertEquals(
            "修剪后应该正好是上限（多删一个 = 白扒一次，少删一个 = 上限失效）",
            EmbeddedSubtitle.MAX_CACHE_FILES,
            dir.listFiles()!!.size,
        )
    }

    @Test
    fun `刚写完那份必须留下_哪怕它是最旧的`() {
        // 🔴 这条最要紧：`keep` 被误删 ⇒ 用户刚扒好的缓存当场失效。
        val dir = tmp.newFolder("c")
        val files = makeFiles(dir, EmbeddedSubtitle.MAX_CACHE_FILES + 3)
        val keep = files.first()          // 故意挑最旧的一份当"刚写完的"
        keep.setLastModified(1L)

        EmbeddedSubtitle.trimCache(dir, keep = keep)

        assertTrue("刚写完的那份不能被删", keep.exists())
        assertEquals(EmbeddedSubtitle.MAX_CACHE_FILES, dir.listFiles()!!.size)
    }

    @Test
    fun `删的是最旧的_最新的都留着`() {
        val dir = tmp.newFolder("c")
        val files = makeFiles(dir, EmbeddedSubtitle.MAX_CACHE_FILES + 4)
        val oldest = files.first()

        EmbeddedSubtitle.trimCache(dir, keep = files.last())

        assertFalse("最旧的应该被删", oldest.exists())
        assertTrue("次新的应该留着", files[files.size - 2].exists())
    }

    @Test
    fun `目录里混着子目录_只数文件不数目录`() {
        val dir = tmp.newFolder("c")
        val files = makeFiles(dir, EmbeddedSubtitle.MAX_CACHE_FILES + 2)
        File(dir, "subdir").mkdirs()

        EmbeddedSubtitle.trimCache(dir, keep = files.last())

        assertTrue("子目录不该被删", File(dir, "subdir").isDirectory)
        assertEquals(
            "文件数（不含子目录）应修剪到上限",
            EmbeddedSubtitle.MAX_CACHE_FILES,
            dir.listFiles { f: File -> f.isFile }!!.size,
        )
    }

    @Test
    fun `空目录_不崩`() {
        val dir = tmp.newFolder("c")
        EmbeddedSubtitle.trimCache(dir, keep = File(dir, "nothere.srt"))
        assertEquals(0, dir.listFiles()!!.size)
    }
}
