package com.xuanji.repeater

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文件夹页：
 * - 总览：所有含音频的文件夹（名称/数量/总时长），点入详情，可忽略整个文件夹、刷新
 * - 详情：点击音频 → 整个文件夹音频+歌词加载进播放列表并播放；行内菜单：忽略文件/重命名/删除
 * - 已忽略：被忽略的文件夹/文件列表，可取消忽略
 */
@Composable
fun FolderPage(
    context: Context,
    svc: PlayerService?,
    state: PlayerState,
    onBackToPlayer: () -> Unit,
    /** 从哪个入口进来的：音频模式进来看音频，视频模式进来看视频 */
    initialType: MediaType = MediaType.AUDIO,
    /** 当前正在看的视频路径（视频列表里高亮用） */
    currentVideoPath: String = "",
    /** 视频选集入口：true 时进页即直接钻到「当前视频所在文件夹」，跳过文件夹总览 */
    jumpToCurrentVideoFolder: Boolean = false,
    /** 视频选集入口使用简化头部：隐藏返回/文件夹名/音频视频 tab，把数量并入排序行 */
    simplifiedHeader: Boolean = false,
    /** 点中一个视频：由外层决定怎么打开（视频模式会自动切过去播放） */
    onOpenVideo: (VideoItem, List<VideoItem>) -> Unit,
    // 【1007 统一】点中一个音频：由外层决定怎么打开（切到音频模式播放）——
    // 与 onOpenVideo 对称。原来音频走「setPlaylist + 原路返回(onPlay)」：从视频模式进时
    // onPlay 把页面切回视频页，音频在后台播了画面却没变 = 阿亮说的"选了没反应/跳回原视频"。
    onOpenAudio: (AudioItem, List<AudioItem>) -> Unit,
    // 【v89 阿亮："滑到哪里，返回来还在哪里"】以下 4 个状态由 MainActivity 持有：
    // when(page) 切页销毁本页组合后仍保留，回到选集时停在原来那层的原位置。
    // 🔴 带自动跟随的列表不在本页，无跟随冲突。
    /** 总览文件夹列表的滚动状态 */
    folderOverviewListState: LazyListState = remember { LazyListState() },
    /** 详情文件列表的滚动状态 */
    folderFileListState: LazyListState = remember { LazyListState() },
    /** 当前钻入的文件夹（null = 总览）。提层后从播放页回来不再掉回第一层 */
    folderCurrent: MutableState<FolderItem?> = remember { mutableStateOf(null) },
    /** 总览搜索词。提层保住：不然回来列表内容变了，滚动位置对不上条目 */
    folderQueryState: MutableState<String> = remember { mutableStateOf("") },
) {
    var currentFolder by folderCurrent
    var showIgnored by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    // 详情页里当前看哪一类。入口类型变了（比如从视频模式进来）就跟着变
    var mediaType by remember { mutableStateOf(initialType) }
    LaunchedEffect(initialType) { mediaType = initialType }

    // 视频选集：进页即钻到当前视频所在文件夹（与音频选集同感，跳过文件夹总览）。
    // 找不到（如视频不在媒体库）则回退到总览。
    LaunchedEffect(jumpToCurrentVideoFolder) {
        if (!jumpToCurrentVideoFolder || currentVideoPath.isEmpty()) return@LaunchedEffect
        val folders = withContext(Dispatchers.IO) {
            MediaScanner.scanFolders(context, IgnorePrefs.load(context))
        }
        val target = folders.firstOrNull { f -> f.videos.any { it.path == currentVideoPath } }
        if (target != null) {
            currentFolder = target
            if (target.audios.isEmpty() && target.videos.isNotEmpty()) mediaType = MediaType.VIDEO
        }
    }

    // 文件夹页内部返回：已忽略/详情 → 总览；总览时交给全局返回（回播放页）
    // 视频选集入口（jumpToCurrentVideoFolder）直接进入当前视频所在文件夹详情，
    // 此时返回应直接回到视频播放模式，而不是先回到文件夹总览。
    BackHandler(enabled = currentFolder != null || showIgnored) {
        when {
            showIgnored -> showIgnored = false
            currentFolder != null && jumpToCurrentVideoFolder -> onBackToPlayer()
            currentFolder != null -> currentFolder = null
        }
    }

    when {
        showIgnored -> IgnoredPage(
            context = context,
            onBack = { showIgnored = false },
            onChanged = { refreshKey++ },
        )
        currentFolder != null -> FolderDetailPage(
            context = context,
            svc = svc,
            state = state,
            folder = currentFolder!!,
            mediaType = mediaType,
            onTypeChange = { mediaType = it },
            currentVideoPath = currentVideoPath,
            simplifiedHeader = simplifiedHeader,
            // 【v89】详情文件列表滚动状态由 Activity 层持有
            fileListState = folderFileListState,
            onBack = { currentFolder = null },
            onChanged = { refreshKey++ },
            onPlay = onBackToPlayer,
            onOpenVideo = onOpenVideo,
            onOpenAudio = onOpenAudio,
        )
        else -> FolderOverviewPage(
            context = context,
            refreshKey = refreshKey,
            // 【v89】总览滚动状态 + 搜索词由 Activity 层持有：回来还停在原位置
            folderListState = folderOverviewListState,
            queryState = folderQueryState,
            onOpenFolder = { f ->
                currentFolder = f
                // 这个文件夹里只有视频没有音频时，直接落到视频那一栏，别让用户看着空列表发懵
                if (f.audios.isEmpty() && f.videos.isNotEmpty()) mediaType = MediaType.VIDEO
            },
            onShowIgnored = { showIgnored = true },
        )
    }
}

/** 总览：文件夹列表 */
@Composable
private fun FolderOverviewPage(
    context: Context,
    refreshKey: Int,
    // 【v89】滚动状态 / 搜索词由外部（MainActivity）持有：页面销毁重建后仍保留
    folderListState: LazyListState = remember { LazyListState() },
    queryState: MutableState<String> = remember { mutableStateOf("") },
    onOpenFolder: (FolderItem) -> Unit,
    onShowIgnored: () -> Unit,
) {
    var folders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }
    var folderQuery by queryState
    var ignoreTarget by remember { mutableStateOf<FolderItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FolderItem?>(null) }
    var renameTarget by remember { mutableStateOf<FolderItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<FolderItem?>(null) }
    var showStorageGuide by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 展示列表：按文件夹名搜索过滤
    val displayFolders = remember(folders, folderQuery) {
        val q = folderQuery.trim()
        if (q.isEmpty()) folders
        else folders.filter { it.name.contains(q, ignoreCase = true) }
    }

    // 等待系统授权后执行的文件夹级操作（删除 / 重命名）
    var pendingOp by remember { mutableStateOf<PendingOp?>(null) }
    fun runPendingOp() {
        val op = pendingOp ?: return
        pendingOp = null
        val ok = when (op.type) {
            PendingOpType.DELETE_FOLDER -> FileOps.deleteFolderWithPermission(context, op.folder?.path ?: return)
            PendingOpType.RENAME_FOLDER -> FileOps.renameFolderWithPermission(context, op.folder?.path ?: return, op.name ?: "")
            else -> false
        }
        Toast.makeText(
            context,
            when {
                !ok -> "操作失败：文件不可用或无权限"
                op.type == PendingOpType.DELETE_FOLDER -> "已删除文件夹"
                else -> "已重命名"
            },
            Toast.LENGTH_SHORT,
        ).show()
        refreshTick++
    }
    // API30+：系统删除确认框（文件夹级）
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "已删除文件夹", Toast.LENGTH_SHORT).show()
            refreshTick++
        }
    }
    // API29+：写授权确认框（文件夹删除/重命名共用）
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) runPendingOp()
    }
    // API<29：WRITE_EXTERNAL_STORAGE 权限
    val legacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runPendingOp()
    }

    /** 发起删除文件夹：按 SDK 分支走系统确认 / 写授权 / 旧权限 */
    fun requestDeleteFolder(folder: FolderItem) {
        // Android11+ 文件夹级操作需要"所有文件访问"（MediaStore 只允许标准媒体目录）
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            showStorageGuide = true
            return
        }
        // 已授予"所有文件访问"：直接文件系统级删除（无需系统确认框）
        if (Build.VERSION.SDK_INT >= 30) {
            scope.launch(Dispatchers.IO) {
                val ok = FileOps.deleteFolderWithPermission(context, folder.path)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (ok) "已删除文件夹" else "删除失败：部分文件无法删除",
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshTick++
                }
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = FileOps.createFolderDeleteIntent(context, folder.path) ?: run {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                return
            }
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else if (Build.VERSION.SDK_INT >= 29) {
            val pi = FileOps.createFolderWriteIntent(context, folder.path) ?: run {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                return
            }
            pendingOp = PendingOp(type = PendingOpType.DELETE_FOLDER, folder = folder)
            writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            pendingOp = PendingOp(type = PendingOpType.DELETE_FOLDER, folder = folder)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runPendingOp()
            } else {
                legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    /** 发起重命名文件夹：需要写权限（API29+ 系统授权 / API<29 WRITE 权限） */
    fun requestRenameFolder(folder: FolderItem, newName: String) {
        // Android11+ 文件夹级操作需要"所有文件访问"（MediaStore 只允许标准媒体目录）
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            showStorageGuide = true
            return
        }
        // 已授予"所有文件访问"：直接文件系统级复制迁移（无需系统确认框）
        if (Build.VERSION.SDK_INT >= 30) {
            scope.launch(Dispatchers.IO) {
                val ok = FileOps.renameFolderWithPermission(context, folder.path, newName)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (ok) "已重命名" else "重命名失败：文件不可用或无权限",
                        Toast.LENGTH_SHORT,
                    ).show()
                    refreshTick++
                }
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val pi = FileOps.createFolderWriteIntent(context, folder.path) ?: run {
                Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                return
            }
            pendingOp = PendingOp(type = PendingOpType.RENAME_FOLDER, folder = folder, name = newName)
            writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            pendingOp = PendingOp(type = PendingOpType.RENAME_FOLDER, folder = folder, name = newName)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runPendingOp()
            } else {
                legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    LaunchedEffect(refreshKey, refreshTick) {
        loading = true
        folders = withContext(Dispatchers.IO) {
            // 音频 + 视频一起扫：同一个文件夹里两类都能看见
            MediaScanner.scanFolders(context, IgnorePrefs.load(context))
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶行：标题 + 已忽略 + 刷新
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "文件夹",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowIgnored) { Text("已忽略") }
IconButton(onClick = { refreshTick++ }) { Icon(Icons.Filled.Refresh, contentDescription = "刷新") }
}
OutlinedTextField(
    value = folderQuery,
    onValueChange = { folderQuery = it },
    placeholder = { Text("搜索文件夹") },
    singleLine = true,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
)
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            folders.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text("未找到含音频或视频的文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
displayFolders.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text("未找到匹配的文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
else -> LazyColumn(
                    state = folderListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                items(displayFolders, key = { it.path }) { folder ->
                    Surface(
                        onClick = { onOpenFolder(folder) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                XuanjiIcons.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        ) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = folderSummary(folder),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 行尾菜单：忽略整个文件夹
                        Box {
                            IconButton(onClick = { menuFor = folder }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            AppDropdownMenu(
                                expanded = menuFor == folder,
                                onDismissRequest = { menuFor = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("忽略此文件夹") },
                                    onClick = {
                                        menuFor = null
                                        ignoreTarget = folder
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    onClick = {
                                        menuFor = null
                                        renameText = folder.name
                                        renameTarget = folder
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = {
                                        menuFor = null
                                        deleteTarget = folder
                                    },
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    if (showStorageGuide) {
        AlertDialog(
            onDismissRequest = { showStorageGuide = false },
            title = { Text("需要“所有文件访问”权限") },
            text = { Text("Android 11 及以上系统对文件夹整体重命名/删除有额外限制（仅允许操作系统标准媒体目录）。\n\n开启“所有文件访问”后，即可重命名/删除任意文件夹（含其中的歌词文件）。") },
            confirmButton = {
                TextButton(onClick = {
                    showStorageGuide = false
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    } catch (_: Exception) {
                        Toast.makeText(context, "请到 设置→应用→小七复读机→所有文件访问 中开启", Toast.LENGTH_LONG).show()
                    }
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { showStorageGuide = false }) { Text("取消") }
            },
        )
    }

    ignoreTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { ignoreTarget = null },
            title = { Text("忽略文件夹") },
            text = { Text("忽略后“${folder.name}”内的音频将不再加载（可在“已忽略”中恢复）。") },
            confirmButton = {
                TextButton(onClick = {
                    IgnorePrefs.add(context, folder.path)
                    ignoreTarget = null
                    refreshTick++
                }) { Text("忽略") }
            },
            dismissButton = {
                TextButton(onClick = { ignoreTarget = null }) { Text("取消") }
            },
        )
    }

    // 删除文件夹确认
    deleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件夹") },
            // 【1003 P0】文案要说清**只删音频+歌词，不会碰视频/图片等其它文件**
            // （代码已去掉递归删除，这里同步如实告知；顺带告诉用户目录空时才会被清掉）
            text = {
                Text(
                    "将删除“${folder.name}”里的 ${folder.audios.size} 个音频文件" +
                        "（含同名字幕），不可恢复。\n\n" +
                        "视频、图片等其它文件不会被删除，文件夹也会保留。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    requestDeleteFolder(folder)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 重命名文件夹对话框
    renameTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名文件夹") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("新文件夹名") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        renameTarget = null
                        requestRenameFolder(folder, renameText)
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 详情：文件夹内音频列表 + 管理（忽略/重命名/删除） */
@Composable
private fun FolderDetailPage(
    context: Context,
    svc: PlayerService?,
    state: PlayerState,
    folder: FolderItem,
    mediaType: MediaType,
    onTypeChange: (MediaType) -> Unit,
    currentVideoPath: String = "",
    simplifiedHeader: Boolean = false,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onPlay: () -> Unit,
    onOpenVideo: (VideoItem, List<VideoItem>) -> Unit,
    // 【1007 统一】点音频也交给外层统一切模式（与 onOpenVideo 对称，见 FolderPage 参数注释）
    onOpenAudio: (AudioItem, List<AudioItem>) -> Unit,
    // 【v89】文件列表滚动状态由外部（MainActivity）持有：页面销毁重建后仍保留
    fileListState: LazyListState = remember { LazyListState() },
) {
    var audios by remember { mutableStateOf(folder.audios) }
    var videos by remember { mutableStateOf(folder.videos) }
    var refreshTick by remember { mutableStateOf(0) }
    var menuFor by remember { mutableStateOf<AudioItem?>(null) }
    var videoMenuFor by remember { mutableStateOf<VideoItem?>(null) }
    // 视频的「忽略文件」确认
    var ignoreVideoTarget by remember { mutableStateOf<VideoItem?>(null) }
    // 【0930 v79】视频删除：原来视频只有「忽略」，现在补上跟音频一致的「删除」。
    // 复用同一个 pendingOp 通道（PendingOpType 加两个视频分支），授权流程完全一致。
    var videoDeleteTarget by remember { mutableStateOf<VideoItem?>(null) }
    // 等待系统授权后要删的视频（只有删除一种操作 ⇒ 直接存 VideoItem，不需要 PendingOp 包装）
    var pendingVideoOp by remember { mutableStateOf<VideoItem?>(null) }
    var ignoreTarget by remember { mutableStateOf<AudioItem?>(null) }
    var deleteTarget by remember { mutableStateOf<AudioItem?>(null) }
    var renameTarget by remember { mutableStateOf<AudioItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FolderSortPrefs.load(context)) }
    var query by remember { mutableStateOf("") }

    // 当前展示列表：搜索过滤 + 排序
    val displayAudios = remember(audios, sortMode, query) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) audios
        else audios.filter { fileNameWithoutExt(it.path).contains(q, ignoreCase = true) }
        sortedAudios(filtered, sortMode)
    }
    // 视频用同一套过滤/排序规则，切换类型时顺序不会跳
    val displayVideos = remember(videos, sortMode, query) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) videos
        else videos.filter { fileNameWithoutExt(it.path).contains(q, ignoreCase = true) }
        sortedVideos(filtered, sortMode)
    }

    // 等待系统授权后执行的操作（删除 / 重命名）
    var pendingOp by remember { mutableStateOf<PendingOp?>(null) }
    fun runPendingOp() {
        val op = pendingOp ?: return
        pendingOp = null
        val ok = when (op.type) {
            PendingOpType.DELETE -> FileOps.deleteWithPermission(context, op.item ?: return)
            PendingOpType.RENAME -> FileOps.renameWithPermission(context, op.item ?: return, op.name ?: "")
            PendingOpType.DELETE_FOLDER -> FileOps.deleteFolderWithPermission(context, op.folder?.path ?: return)
            PendingOpType.RENAME_FOLDER -> FileOps.renameFolderWithPermission(context, op.folder?.path ?: return, op.name ?: "")
        }
        Toast.makeText(
            context,
            when {
                !ok -> "操作失败：文件不可用或无权限"
                op.type == PendingOpType.DELETE -> "已删除"
                else -> "已重命名"
            },
            Toast.LENGTH_SHORT,
        ).show()
        refreshTick++
        onChanged()
    }
    // API30+：系统删除确认框
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            refreshTick++
            onChanged()
        }
    }
    // API29+：写授权确认框（删除/重命名共用）
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) runPendingOp()
    }
    // API<29：WRITE_EXTERNAL_STORAGE 权限
    val legacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runPendingOp()
    }

    // ================= 【0930 v79】视频删除（与音频完全对称的一套） =================
    // 为什么另起一套而不复用音频的 launcher：音频那三个 launcher 回调里跑的是 runPendingOp()，
    // 它只认 pendingOp（音频项）。视频项类型不同 ⇒ 混用会取到 null 直接 return。
    // 视频只有「删除」一种操作 ⇒ 不需要 PendingOpType，一个 VideoItem 就够了。
    fun runPendingVideoOp() {
        val item = pendingVideoOp ?: return
        pendingVideoOp = null
        val ok = FileOps.deleteWithPermission(context, item)
        Toast.makeText(
            context,
            if (ok) "已删除" else "操作失败：文件不可用或无权限",
            Toast.LENGTH_SHORT,
        ).show()
        refreshTick++
        onChanged()
    }
    val videoDeleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            refreshTick++
            onChanged()
        }
    }
    val videoWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) runPendingVideoOp()
    }
    val videoLegacyWriteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runPendingVideoOp()
    }

    /** 【0930 v79】发起删视频：与 requestDelete 同构，只是换成视频的表 */
    fun requestVideoDelete(item: VideoItem) {
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = FileOps.createVideoDeleteIntent(context, listOf(item)) ?: run {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                return
            }
            videoDeleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else if (Build.VERSION.SDK_INT >= 29) {
            val pi = FileOps.createVideoWriteIntent(context, listOf(item)) ?: run {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                return
            }
            pendingVideoOp = item
            videoWriteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            pendingVideoOp = item
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runPendingVideoOp()
            } else {
                videoLegacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    /** 发起删除：按 SDK 分支走系统确认 / 写授权 / 旧权限 */
    fun requestDelete(item: AudioItem) {
        if (Build.VERSION.SDK_INT >= 30) {
            val pi = FileOps.createDeleteIntent(context, listOf(item)) ?: run {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                return
            }
            deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else if (Build.VERSION.SDK_INT >= 29) {
            val pi = FileOps.createWriteIntent(context, listOf(item)) ?: run {
                Toast.makeText(context, "删除失败", Toast.LENGTH_SHORT).show()
                return
            }
            pendingOp = PendingOp(type = PendingOpType.DELETE, item = item)
            writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            pendingOp = PendingOp(type = PendingOpType.DELETE, item = item)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runPendingOp()
            } else {
                legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    /** 发起重命名：需要写权限（API29+ 系统授权 / API<29 WRITE 权限） */
    fun requestRename(item: AudioItem, newName: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val pi = FileOps.createWriteIntent(context, listOf(item)) ?: run {
                Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                return
            }
            pendingOp = PendingOp(type = PendingOpType.RENAME, item = item, name = newName)
            writeLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            pendingOp = PendingOp(type = PendingOpType.RENAME, item = item, name = newName)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runPendingOp()
            } else {
                legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // 重扫本文件夹（忽略/删除/重命名后刷新；文件夹空了则退回总览）
    LaunchedEffect(folder.path, refreshTick) {
        val fresh = withContext(Dispatchers.IO) { MediaScanner.scanFolder(context, folder.path) }
        if (fresh == null || (fresh.audios.isEmpty() && fresh.videos.isEmpty())) {
            if (refreshTick > 0) onBack()
            return@LaunchedEffect
        }
        audios = fresh.audios
        videos = fresh.videos
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (simplifiedHeader) {
            // 视频选集简化头部：隐藏返回/文件夹名/音频视频 tab，排序与视频数量放在同一行
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = sortMode == FolderSortMode.NUMERIC,
                    onClick = {
                        sortMode = FolderSortMode.NUMERIC
                        FolderSortPrefs.save(context, sortMode)
                    },
                    label = { Text("数字") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = sortMode == FolderSortMode.ALPHA,
                    onClick = {
                        sortMode = FolderSortMode.ALPHA
                        FolderSortPrefs.save(context, sortMode)
                    },
                    label = { Text("字母") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = sortMode == FolderSortMode.MTIME,
                    onClick = {
                        sortMode = FolderSortMode.MTIME
                        FolderSortPrefs.save(context, sortMode)
                    },
                    label = { Text("时间") },
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${videos.size} 个视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 顶行：返回 + 文件夹名 + 数量
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (mediaType == MediaType.AUDIO) "${audios.size} 首" else "${videos.size} 个视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 类型切换：音频 / 视频。
            // 同一个文件夹里两类混放时靠它分清 —— 切到哪一类，列表里就只会有哪一类，不会串。
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = mediaType == MediaType.AUDIO,
                    onClick = { onTypeChange(MediaType.AUDIO) },
                    label = { Text("音频 ${audios.size}") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = mediaType == MediaType.VIDEO,
                    onClick = { onTypeChange(MediaType.VIDEO) },
                    label = { Text("视频 ${videos.size}") },
                )
            }
            // 排序切换 + 搜索框
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = sortMode == FolderSortMode.NUMERIC,
                    onClick = {
                        sortMode = FolderSortMode.NUMERIC
                        FolderSortPrefs.save(context, sortMode)
                    },
                    label = { Text("数字") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = sortMode == FolderSortMode.ALPHA,
                    onClick = {
                        sortMode = FolderSortMode.ALPHA
                        FolderSortPrefs.save(context, sortMode)
                    },
                    label = { Text("字母") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = sortMode == FolderSortMode.MTIME,
                    onClick = {
                        sortMode = FolderSortMode.MTIME
                        FolderSortPrefs.save(context, sortMode)
                    },
                    label = { Text("时间") },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索文件名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        val isAudioTab = mediaType == MediaType.AUDIO
        val curListEmpty = if (isAudioTab) audios.isEmpty() else videos.isEmpty()
        val curDisplayEmpty = if (isAudioTab) displayAudios.isEmpty() else displayVideos.isEmpty()
        if (curListEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isAudioTab) "此文件夹暂无音频" else "此文件夹暂无视频",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (curDisplayEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到匹配的文件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = fileListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                if (isAudioTab) {
                    items(displayAudios, key = { it.path }) { item ->
                        val selected = state.currentItem?.path == item.path
                        Surface(
                            onClick = {
                                // 【1007 统一】点音频 → 外层切到音频模式播放（与点视频对称）。
                                // 原来在这里 svc?.setPlaylist(...) + onPlay()：「原路返回」在
                                // 视频模式进来时会把页面切回视频页 = 选了没反应。
                                onOpenAudio(item, displayAudios)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (selected) 0.dp else 1.dp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileNameWithoutExt(item.path),
                                        style = if (selected) MaterialTheme.typography.bodyLarge
                                        else MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatDuration(item.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (item.hasLrc) {
                                            Text(
                                                text = " · 歌词",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                // 行尾菜单：忽略文件 / 重命名 / 删除
                                Box {
                                    IconButton(onClick = { menuFor = item }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                                    }
                                    AppDropdownMenu(
                                        expanded = menuFor == item,
                                        onDismissRequest = { menuFor = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("忽略文件") },
                                            onClick = {
                                                menuFor = null
                                                ignoreTarget = item
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("重命名") },
                                            onClick = {
                                                menuFor = null
                                                renameText = fileNameWithoutExt(item.path)
                                                renameTarget = item
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除") },
                                            onClick = {
                                                menuFor = null
                                                deleteTarget = item
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(displayVideos, key = { it.path }) { item ->
                        val selected = currentVideoPath.isNotEmpty() && currentVideoPath == item.path
                        Surface(
                            // 点视频 → 外层切成视频模式并打开播放
                            onClick = { onOpenVideo(item, displayVideos) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (selected) 0.dp else 1.dp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.VideoFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        text = fileNameWithoutExt(item.path),
                                        style = if (selected) MaterialTheme.typography.bodyLarge
                                        else MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatDuration(item.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (item.hasSrt) {
                                            Text(
                                                text = " · 字幕",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                // 【0930 v79】视频行菜单：忽略文件 + 删除（原来只有「忽略」）
                                Box {
                                    IconButton(onClick = { videoMenuFor = item }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                                    }
                                    AppDropdownMenu(
                                        expanded = videoMenuFor == item,
                                        onDismissRequest = { videoMenuFor = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("忽略文件") },
                                            onClick = {
                                                videoMenuFor = null
                                                ignoreVideoTarget = item
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除") },
                                            onClick = {
                                                videoMenuFor = null
                                                videoDeleteTarget = item
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 【0930 v79】删除视频确认（与音频删除弹窗同文案风格）
    videoDeleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { videoDeleteTarget = null },
            title = { Text("删除视频") },
            text = { Text("将删除“${fileNameWithoutExt(item.path)}”这个视频文件（会弹系统确认框）。删了就没了，不能恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val t = item
                    videoDeleteTarget = null
                    requestVideoDelete(t)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { videoDeleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 忽略视频确认
    ignoreVideoTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { ignoreVideoTarget = null },
            title = { Text("忽略文件") },
            text = { Text("忽略后“${fileNameWithoutExt(item.path)}”将不再显示（可在“已忽略”中恢复）。") },
            confirmButton = {
                TextButton(onClick = {
                    IgnorePrefs.add(context, item.path)
                    ignoreVideoTarget = null
                    refreshTick++
                    onChanged()
                }) { Text("忽略") }
            },
            dismissButton = {
                TextButton(onClick = { ignoreVideoTarget = null }) { Text("取消") }
            },
        )
    }

    // 忽略文件确认
    ignoreTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { ignoreTarget = null },
            title = { Text("忽略文件") },
            text = { Text("忽略后“${item.title}”将不再加载（可在“已忽略”中恢复）。") },
            confirmButton = {
                TextButton(onClick = {
                    IgnorePrefs.add(context, item.path)
                    ignoreTarget = null
                    refreshTick++
                    onChanged()
                }) { Text("忽略") }
            },
            dismissButton = {
                TextButton(onClick = { ignoreTarget = null }) { Text("取消") }
            },
        )
    }

    // 删除确认
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件") },
            text = { Text("确定删除“${item.title}”吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    requestDelete(item)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    // 重命名对话框
    renameTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("新文件名") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        renameTarget = null
                        requestRename(item, renameText)
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }
}

/** 已忽略列表：可取消忽略 */
@Composable
private fun IgnoredPage(
    context: Context,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    var paths by remember { mutableStateOf(IgnorePrefs.load(context).toList().sorted()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("已忽略", style = MaterialTheme.typography.titleMedium)
        }
        if (paths.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无已忽略的项目", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(paths) { path ->
                    val isDir = path.endsWith('/') || java.io.File(path).isDirectory
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = path.substringAfterLast('/').ifEmpty { path },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = (if (isDir) "文件夹 · " else "文件 · ") + path,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = {
                            IgnorePrefs.remove(context, path)
                            paths = IgnorePrefs.load(context).toList().sorted()
                            onChanged()
                        }) { Text("取消忽略") }
                    }
                }
            }
        }
    }
}

private enum class PendingOpType { DELETE, RENAME, DELETE_FOLDER, RENAME_FOLDER }

private data class PendingOp(
    val type: PendingOpType,
    val item: AudioItem? = null,
    val folder: FolderItem? = null,
    val name: String? = null,
)

/**
 * 文件夹总览的数量摘要：音频和视频分开标出来。
 * 用户问「我点开这个文件夹，怎么知道里面是音频还是视频」—— 这一行就是答案。
 */
private fun folderSummary(f: FolderItem): String {
    val parts = mutableListOf<String>()
    if (f.audios.isNotEmpty()) parts.add("${f.audios.size} 首音频")
    if (f.videos.isNotEmpty()) parts.add("${f.videos.size} 个视频")
    if (f.audios.isNotEmpty()) parts.add(formatDuration(f.totalMs))
    return parts.joinToString(" · ")
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}