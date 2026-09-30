package com.xuanji.repeater

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * 媒体文件操作：删除 / 重命名。
 * 分区存储各 SDK 分支：
 * - API>=30：MediaStore.createDeleteRequest（系统确认框）删除
 * - API 29：createWriteRequest 授权后文件系统级操作
 * - API<29：WRITE_EXTERNAL_STORAGE 权限后文件系统级操作
 */
object FileOps {

    fun uriFor(item: AudioItem): Uri? = try {
        ContentUris.withAppendedId(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            item.id
        )
    } catch (_: Exception) {
        null
    }

    // ================= 【0930 v79】视频版重载 =================
    // 背景：视频行菜单原本**只有「忽略」**，当初的顾虑是"别动到 MediaStore 里的视频记录"
    // （见 FolderPage 顶部注释）。但视频体积大、清理动机比音频强得多；而且下面这套走的是
    // MediaStore 正规通道（系统确认框 + ContentResolver.delete），**不是裸 rm**，风险跟音频一样可控。
    // ⇒ 直接复用音频那套，只把 MediaStore 表从 Audio 换成 Video。
    // ⚠️ VideoItem 与 AudioItem 结构相同（id/path/title），但**是不同类型**，只能靠重载区分。

    fun uriFor(item: VideoItem): Uri? = try {
        ContentUris.withAppendedId(
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            item.id
        )
    } catch (_: Exception) {
        null
    }

    // ⚠️ **不能叫 createDeleteIntent/createWriteIntent**：Kotlin 泛型擦除后
    // `List<AudioItem>` 与 `List<VideoItem>` 的 JVM 签名**完全一样**
    // ⇒ 报 "Platform declaration clash"。所以视频版一律加 `Video` 后缀。
    // （`uriFor` 和 `deleteWithPermission` 收的是单个对象、类型不同，重载没问题。）

    /** API30+：视频的系统删除请求 */
    fun createVideoDeleteIntent(context: Context, items: List<VideoItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < 30) return null
        val uris = items.mapNotNull { uriFor(it) }
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /** API29：视频的写授权请求 */
    fun createVideoWriteIntent(context: Context, items: List<VideoItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val uris = items.mapNotNull { uriFor(it) }
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createWriteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /** 已获授权后删视频：文件系统删 + 媒体库记录删（跟音频版同逻辑） */
    fun deleteWithPermission(context: Context, item: VideoItem): Boolean {
        var ok = false
        try {
            ok = File(item.path).delete()
        } catch (_: Exception) {
        }
        try {
            uriFor(item)?.let { context.contentResolver.delete(it, null, null) }
        } catch (_: Exception) {
        }
        return ok
    }

    /** API30+：系统删除请求（返回 PendingIntent，由 Activity Result 启动，弹系统确认框） */
    fun createDeleteIntent(context: Context, items: List<AudioItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < 30) return null
        val uris = items.mapNotNull { uriFor(it) }
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /** API29+：写权限请求（返回 PendingIntent，删除/重命名前授权，弹系统确认框） */
    fun createWriteIntent(context: Context, items: List<AudioItem>): PendingIntent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val uris = items.mapNotNull { uriFor(it) }
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createWriteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /** 已获得写权限后直接删除文件并清理媒体库记录 */
    fun deleteWithPermission(context: Context, item: AudioItem): Boolean {
        var ok = false
        try {
            ok = File(item.path).delete()
        } catch (_: Exception) {
        }
        try {
            uriFor(item)?.let { context.contentResolver.delete(it, null, null) }
        } catch (_: Exception) {
        }
        return ok
    }

    /** 已获得写权限后重命名：File.renameTo + 删除媒体库旧记录 + 扫描新路径入库。
     * 新名不含扩展名时自动补原扩展名。
     */
    fun renameWithPermission(context: Context, item: AudioItem, newName: String): Boolean {
        val oldFile = File(item.path)
        if (!oldFile.exists()) return false
        var name = newName.trim()
        if (name.isEmpty()) return false
        val ext = item.path.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !name.endsWith(".$ext", ignoreCase = true)) name = "$name.$ext"
        val parent = oldFile.parent ?: return false
        val newFile = File(parent, name)
        if (newFile.exists()) return false
        if (!oldFile.renameTo(newFile)) return false
        try {
            uriFor(item)?.let { context.contentResolver.delete(it, null, null) }
        } catch (_: Exception) {
        }
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(newFile.absolutePath),
                arrayOf("audio/*"),
                null
            )
        } catch (_: Exception) {
        }
        return true
    }

    // ---------------- 文件夹级操作 ----------------

    /** 文件夹内全部音频的媒体库 Uri（含被忽略文件；删除/重命名文件夹用） */
    private fun folderUris(context: Context, dir: String): List<Uri> =
        AudioScanner.scanFolderAudios(context, dir).mapNotNull { uriFor(it) }

    /** API30+：系统删除文件夹内全部音频的请求（弹系统确认框） */
    fun createFolderDeleteIntent(context: Context, dir: String): PendingIntent? {
        if (Build.VERSION.SDK_INT < 30) return null
        val uris = folderUris(context, dir)
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /** API29+：文件夹内全部音频的写授权请求（删除/重命名文件夹前授权） */
    fun createFolderWriteIntent(context: Context, dir: String): PendingIntent? {
        if (Build.VERSION.SDK_INT < 29) return null
        val uris = folderUris(context, dir)
        if (uris.isEmpty()) return null
        return try {
            MediaStore.createWriteRequest(context.contentResolver, uris)
        } catch (_: Exception) {
            null
        }
    }

    /** 已授权：删除文件夹内全部音频并清理媒体库记录，尝试清空目录 */
    fun deleteFolderWithPermission(context: Context, dir: String): Boolean {
        val audios = AudioScanner.scanFolderAudios(context, dir)
        if (audios.isEmpty()) return false

        // API30+ 拥有"所有文件访问"：文件系统级递归删除（含 LRC 与空目录）
        if (Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager()) {
            var okAll = true
            for (a in audios) {
                try {
                    if (!File(a.path).delete()) okAll = false
                } catch (_: Exception) {
                    okAll = false
                }
                try {
                    uriFor(a)?.let { context.contentResolver.delete(it, null, null) }
                } catch (_: Exception) {
                }
                try {
                    File(LrcParser.lrcPathFor(a.path)).delete()
                } catch (_: Exception) {
                }
            }
            try {
                File(dir).deleteRecursively()
            } catch (_: Exception) {
            }
            return okAll
        }

        var ok = true
        for (a in audios) {
            try {
                if (!File(a.path).delete()) ok = false
            } catch (_: Exception) {
                ok = false
            }
            try {
                uriFor(a)?.let { context.contentResolver.delete(it, null, null) }
            } catch (_: Exception) {
            }
        }
        // 目录内只剩非音频文件时删除失败，无副作用
        try {
            File(dir).delete()
        } catch (_: Exception) {
        }
        return ok
    }

    /** 已授权：文件夹重命名。
     *  API<29：WRITE 权限下整体目录 renameTo + 媒体库迁移；
     *  API29+：目录 renameTo 需要目录权限（createWriteRequest 不授予），改为——
     *  新建目录 → MediaStore.update(RELATIVE_PATH) 逐个移动音频（系统级移动，授权范围内）
     *  → LRC 复制到新目录并尽力删除旧文件 → 清理旧目录。
     */
    fun renameFolderWithPermission(context: Context, dir: String, newName: String): Boolean {
        fun log(m: String) {
            android.util.Log.d("XJRen", m)
            try {
                val f = java.io.File(context.filesDir, "xj_rename.log")
                f.appendText(java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()) + " " + m + "\n")
            } catch (_: Exception) {
            }
        }
        log("enter dir=$dir new=$newName")
        val oldDir = File(dir)
        if (!oldDir.exists() || !oldDir.isDirectory) { log("oldDir missing"); return false }
        var name = newName.trim()
        if (name.isEmpty()) { log("empty name"); return false }
        val parent = oldDir.parent ?: return false
        val newDir = File(parent, name)
        if (newDir.exists()) { log("newDir exists"); return false }
        val audios = AudioScanner.scanFolderAudios(context, dir)
        log("audios=${audios.size}")
        if (audios.isEmpty()) return false
        var ok = true

        // API30+ 拥有"所有文件访问"：复制方案（File API 全通；复制产生新 inode，新文件可正常入库）
        if (Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager()) {
            log("MANAGE branch")
            // 1. 全部复制（全部成功后才删除旧文件，避免数据丢失）
            val copies = mutableListOf<Pair<AudioItem, java.io.File>>()
            for (a in audios) {
                val src = java.io.File(a.path)
                val dst = java.io.File(newDir, src.name)
                try {
                    src.copyTo(dst, overwrite = true)
                    copies.add(a to dst)
                    log("copied ${src.name}")
                } catch (e: Exception) {
                    log("copy EX: ${e.javaClass.simpleName}: ${e.message}")
                    ok = false
                }
            }
            // 2. LRC 复制
            for (a in audios) {
                val lrc = java.io.File(LrcParser.lrcPathFor(a.path))
                if (!lrc.exists()) continue
                try {
                    lrc.copyTo(java.io.File(newDir, lrc.name), overwrite = true)
                    log("lrc copied ${lrc.name}")
                } catch (e: Exception) {
                    log("lrc copy EX: ${e.message}")
                }
            }
            // 3. 复制全部成功 → 删旧文件 + 删旧媒体库记录（文件已不在旧路径，只删记录不误删）
            if (copies.isNotEmpty() && ok) {
                for ((a, _) in copies) {
                    try {
                        java.io.File(a.path).delete()
                    } catch (_: Exception) {
                    }
                    try {
                        val u = uriFor(a)
                        if (u != null) context.contentResolver.delete(u, null, null)
                    } catch (_: Exception) {
                    }
                }
            } else {
                log("copy failed, keep originals")
                return false
            }
            // 4. 扫描新文件入库（新 inode）
            try {
                MediaScannerConnection.scanFile(
                    context,
                    copies.map { it.second.absolutePath }.toTypedArray(),
                    arrayOf("audio/*"),
                    null
                )
            } catch (_: Exception) {
            }
            // 5. 清理旧目录
            try {
                File(dir).deleteRecursively()
            } catch (_: Exception) {
            }
            return ok
        }

        // API<29：全权限下目录整体重命名
        if (Build.VERSION.SDK_INT < 29) {
            if (!oldDir.renameTo(newDir)) { log("renameTo failed"); return false }
            for (a in audios) {
                try {
                    uriFor(a)?.let { context.contentResolver.delete(it, null, null) }
                } catch (_: Exception) {
                }
            }
            val scanPaths = mutableListOf<String>()
            for (a in audios) {
                val newPath = a.path.replaceFirst(dir, newDir.absolutePath)
                scanPaths.add(newPath)
                scanPaths.add(LrcParser.lrcPathFor(newPath))
            }
            try {
                MediaScannerConnection.scanFile(context, scanPaths.toTypedArray(), null, null)
            } catch (_: Exception) {
            }
            return true
        }

        // API29+：新建目录 → 音频逐个移动 → LRC 复制迁移 → 清理旧目录
        if (!newDir.mkdirs()) { log("mkdirs failed"); return false }
        log("mkdirs ok")
        // RELATIVE_PATH 必须以 "/" 开头和结尾，如 "/AudioRepeater/XJTestRenamed/"
        val relativeBase = newDir.absolutePath.removePrefix("/storage/emulated/0/").trim('/')
        if (Build.VERSION.SDK_INT >= 35) {
            // Android15+：MediaStore.update(RELATIVE_PATH) 只允许标准媒体目录（Alarms/Music/...），
            // 自定义目录被判 (invalid) 拒绝。改为授权范围内 copy+delete：
            // openInputStream 读源文件（createWriteRequest 授权内可读）→ 写入新目录 → 删旧记录 → 扫描入库。
            for (a in audios) {
                val srcUri = uriFor(a)
                if (srcUri == null) { log("uri null for ${a.path}"); ok = false; continue }
                try {
                    val input = context.contentResolver.openInputStream(srcUri)
                    if (input == null) { log("openInputStream null for $srcUri"); ok = false; continue }
                    val fileName = a.path.substringAfterLast('/').ifEmpty { "audio.mp3" }
                    val newFile = java.io.File(newDir, fileName)
                    input.use { ins ->
                        java.io.FileOutputStream(newFile).use { outs -> ins.copyTo(outs) }
                    }
                    log("copied ${a.path} -> ${newFile.absolutePath}")
                    // 删除旧文件+旧媒体库记录（授权内）
                    val delRows = context.contentResolver.delete(srcUri, null, null)
                    log("delete old uri=$srcUri rows=$delRows")
                    // 新文件扫描入库
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(newFile.absolutePath),
                        arrayOf("audio/*"),
                        null
                    )
                } catch (e: Exception) {
                    log("copy EX: ${e.javaClass.simpleName}: ${e.message}")
                    ok = false
                }
            }
        } else {
            // API29~34：MediaStore.update(RELATIVE_PATH) 移动（自定义目录仍允许）
            for (a in audios) {
                val uri = uriFor(a)
                if (uri == null) {
                    log("uri null for ${a.path}")
                    ok = false
                    continue
                }
                try {
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "/$relativeBase/")
                    }
                    val ret = context.contentResolver.update(uri, values, null, null)
                    log("update uri=$uri ret=$ret")
                    if (ret == 0) ok = false
                } catch (e: Exception) {
                    log("update EX: ${e.javaClass.simpleName}: ${e.message}")
                    ok = false
                }
            }
        }
        // LRC 迁移：复制到新目录（新建允许）+ 尽力删除旧文件
        for (a in audios) {
            val oldLrc = File(LrcParser.lrcPathFor(a.path))
            if (!oldLrc.exists()) continue
            try {
                val newLrc = File(newDir, oldLrc.name)
                oldLrc.copyTo(newLrc, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(newLrc.absolutePath), null, null)
            } catch (_: Exception) {
            }
            try {
                oldLrc.delete()
            } catch (_: Exception) {
            }
        }
        // 旧目录仅剩无法删除的文件时，删除失败无副作用
        try {
            File(dir).delete()
        } catch (_: Exception) {
        }
        return ok
    }
}