package com.qushe.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.qushe.app.core.BucketInfo
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qushe.app.core.MediaKind
import com.qushe.app.core.Stats
import com.qushe.app.core.formatSize
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentFolder
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.AccentPurple
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.qushe.app.ui.common.rememberThumbnailRequest
import android.net.Uri
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

/**
 * 首页。
 *
 * 刻意不放任何"还剩 N 张待处理"。整个应用的叙事是 done list：
 * 你已经回顾了多少、删掉了多少 —— 而不是一座永远清不完的山。
 */
@Composable
fun HomeScreen(
    stats: Stats,
    photoCount: Int?,
    screenshotCount: Int?,
    videoCount: Int?,
    favoriteCount: Int,
    photoAllowed: Boolean,
    videoAllowed: Boolean,
    /** 双击的含义由设置决定，所以这里必须跟着走 —— 写死"双击收藏"会跟设置对不上。 */
    doubleTapLikes: Boolean,
    /** 上滑的含义同样由设置决定：删除 or 收藏。文案必须跟着走。 */
    swipeUpDeletes: Boolean,
    /** 「整理文件夹」每行几列（2 / 3），来自设置页。 */
    folderColumns: Int,
    onStart: (MediaKind) -> Unit,
    onRequestPermission: (MediaKind) -> Unit,
    /** 列出所有文件夹（懒加载 —— 打开选择面板时才调，别拖慢首页）。 */
    loadBuckets: suspend () -> List<BucketInfo>,
    /** 从某个文件夹开一组。 */
    onStartBucket: (BucketInfo) -> Unit,
    /** 统计清零。由长按「已删除」触发（原来在设置页，1.4.1 搬过来）。 */
    onResetStats: () -> Unit = {},
) {
    var folderSheet by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var buckets by remember { mutableStateOf<List<BucketInfo>?>(null) }

    // 只在第一次打开时读一次 —— 这一步要扫全库的 bucket 列，不该在首页启动时做。
    LaunchedEffect(folderSheet) {
        if (folderSheet && buckets == null) buckets = loadBuckets()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(28.dp))

        Text(
            text = "取舍",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "像刷短视频一样刷自己的相册。不用下决心整理，想起来就划两下",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary,
        )

        Spacer(Modifier.height(26.dp))

        EntryCard(
            title = "整理照片",
            // 下滑**早已不做任何事**（收藏移到了左下角的星形按钮），
            // 所以这里不再提它 —— 提示一个不存在的动作比没有提示更糟。
            subtitle = "上滑${if (swipeUpDeletes) "删除" else "收藏"} · 左右翻页",
            count = photoCount,
            enabled = photoAllowed,
            accent = AccentKeep,
            onClick = { if (photoAllowed) onStart(MediaKind.PHOTO) else onRequestPermission(MediaKind.PHOTO) },
        )

        Spacer(Modifier.height(10.dp))

        EntryCard(
            title = "整理截屏",
            subtitle = "单独成组 · 一眼废片 · 删起来最快",
            count = screenshotCount,
            enabled = photoAllowed,
            accent = AccentPurple,
            onClick = {
                if (photoAllowed) onStart(MediaKind.SCREENSHOT) else onRequestPermission(MediaKind.SCREENSHOT)
            },
        )

        Spacer(Modifier.height(10.dp))

        EntryCard(
            title = "整理视频",
            // ${} 不能省：中文是 Kotlin 的合法标识符字符，
            // $doubleTapLikes喜欢 会被解析成一个叫"doubleTapLikes喜欢"的变量。
            subtitle = "上下滑切换 · 长按 2× · 双击${if (doubleTapLikes) "喜欢" else "收藏"}",
            count = videoCount,
            enabled = videoAllowed,
            accent = AccentInfo,
            onClick = { if (videoAllowed) onStart(MediaKind.VIDEO) else onRequestPermission(MediaKind.VIDEO) },
        )

        Spacer(Modifier.height(10.dp))

        // ---- 整理某个文件夹 ----
        //
        // 和上面三个**同一套视觉与文案结构**：它们是并列的四种"开一组"的方式，
        // 不是"三个主入口 + 一个附属入口"。早先做成了低调的一行，
        // 结果看起来像另一类东西（用户反馈）。
        // 没有数量概念（它背后是"挑哪个文件夹"，不是"共有多少条"），所以关掉右侧计数。
        EntryCard(
            title = "整理文件夹",
            subtitle = "单独清一个文件夹，如 Download、Weixin",
            count = null,
            enabled = photoAllowed || videoAllowed,
            accent = AccentFolder,
            showCount = false,
            onClick = { folderSheet = true },
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "到目前为止",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surface1)
                .padding(vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 「已回顾」拆成照片 / 视频两格：这两个是完全不同的习惯
            // （照片随手刷、视频专门看），混成一个总数看不出任何东西。
            // 标签写"看过照片"而不是"照片"：这一格是**累计已回顾**的数量，
            // 删掉照片之后它不会减少（那是"看过多少"，不是"相册里还有多少"）。
            // 只写"照片"会被读成相册总数 —— 用户报过"删了截屏总数没变"。
            StatCell(stats.reviewedPhotos.toString(), "看过照片", TextPrimary)
            StatCell(stats.reviewedVideos.toString(), "看过视频", TextPrimary)
            // 长按「已删除」= 统计清零（二次确认）。这格平时不可点，
            // 所以用 pointerInput 的长按手势，不会和别的交互打架。
            StatCell(
                value = stats.deleted.toString(),
                label = "已删除",
                accent = AccentDelete,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { confirmReset = true })
                },
            )
            StatCell(favoriteCount.toString(), "已收藏", AccentKeep)
            StatCell(formatSize(stats.freedBytes), "瘦身", AccentInfo)
        }

        if (stats.rounds > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "你已经完整地刷完相册 ${stats.rounds} 轮了",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // 「统计清零」的二次确认。入口是长按，更要保证"按到"不等于"执行"。
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Surface2,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("确认清零") },
            text = {
                Text(
                    "会把本应用的累计数字全部归零：已回顾、已删除、已收藏、瘦身，" +
                        "照片和视频不会有任何变化，收藏记录也不会丢 —— " +
                        "归零的只是那几个计数。此操作无法撤销",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetStats()
                    },
                ) {
                    Text("确认清零", color = AccentDelete, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
        )
    }

    if (folderSheet) {
        FolderSheet(
            buckets = buckets,
            columns = folderColumns,
            onPick = { picked ->
                folderSheet = false
                onStartBucket(picked)
            },
            onDismiss = { folderSheet = false },
        )
    }
}

@Composable
private fun EntryCard(
    title: String,
    subtitle: String,
    count: Int?,
    enabled: Boolean,
    accent: Color,
    /** false = 这类入口背后没有"多少条"的概念（比如「整理文件夹」）。 */
    showCount: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Surface1 else Surface1.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (enabled) accent else TextTertiary),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) TextPrimary else TextSecondary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
            )
            if (!enabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "尚未授予权限 · 点击授权",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentDelete,
                )
            }
        }
        // 数量。**null = 还在数**，显示 `…` 而不是 `0` ——
        // 否则首页一进来先闪一个"0"再跳成真实数字（用户报的观感问题）。
        // 真的数出来是 0（设备里确实没有照片）才显示 "0"。
        if (showCount) {
            Text(
                text = when {
                    !enabled -> "—"
                    count == null -> "…"
                    else -> count.toString()
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) accent else TextTertiary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}

/**
 * 文件夹选择页（**全屏**）。
 *
 * 之前是 `ModalBottomSheet` 里的纯文字列表：文件夹一多就要在半个屏幕里滚，
 * 而且全是文字，看不出哪个是"相册"哪个是"下载目录"。
 *
 * 现在改成全屏网格：每个文件夹一张封面（该文件夹里**最新**的那张），
 * 下面一行名字、一行数量 —— 和系统相册的"相簿"页是同一个读法。
 *
 * ---- 为什么是网格而不是列表 ----
 * 封面要占地方才有意义。列表里缩略图只能给 44~56dp，等于没有；
 * 网格每格 100~160dp，封面能看清内容，扫一眼就知道是哪个文件夹。
 *
 * 列数由设置决定（`Prefs.folderColumns`，2 或 3）：两列封面大、认得出；
 * 三列一屏多看一半，适合文件夹几十个的设备。
 * 代价是长名字会被截断 —— 但数量那一行能补足辨识度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSheet(
    buckets: List<BucketInfo>?,
    /** 每行几列（2 或 3），由设置页决定。 */
    columns: Int,
    onPick: (BucketInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    // ---- 同名文件夹合并成一条（1.4.0）----
    //
    // 一个文件夹里既有照片又有视频很常见（Camera / DCIM / WeiXin 都这样）。
    // 底层 `buckets()` 是分别扫 images 和 video 两张表再拼起来的，
    // 于是这种文件夹会给**两条同名记录**：直接铺进网格就是两格同名、
    // 按各自数量插在不同位置 —— 看起来像两个不相干的文件夹（用户反馈）。
    //
    // 现在按名字合成一条，副标题写清「1,204 张 · 38 个视频」；
    // 两类都有时才在点击后让用户选整理哪一类。
    val groups = remember(buckets) {
        buckets?.groupBy { it.name }?.map { (name, list) ->
            FolderGroup(
                name = name,
                photos = list.firstOrNull { it.kind == MediaKind.PHOTO },
                videos = list.firstOrNull { it.kind == MediaKind.VIDEO },
            )
        }?.sortedByDescending { it.total }
    }

    var choosing by remember { mutableStateOf<FolderGroup?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 自己处理 insets：Dialog 默认的窗口不铺满状态栏/导航栏
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ---- 顶栏：返回 + 标题 + 文件夹数 ----
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(start = 4.dp, end = 18.dp, top = 4.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = TextPrimary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "按文件夹挑一批",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                // 说清和默认模式的区别，否则用户会以为「只抽没看过的」失效了
                                text = if (groups == null) {
                                    "正在统计文件夹…"
                                } else {
                                    "共 ${groups.size} 个 · 只从这个文件夹抽，不套用「只看没看过的」"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }

                    if (groups == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = TextSecondary,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else if (groups.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "没有找到文件夹",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                            ),
                            // 三列时每格窄了约 1/3，间距跟着收一点，
                            // 否则一屏里全是缝、封面只剩一点点
                            horizontalArrangement = Arrangement.spacedBy(
                                if (columns >= 3) 10.dp else 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(groups, key = { it.name }) { group ->
                                FolderCell(
                                    group = group,
                                    compact = columns >= 3,
                                    onClick = {
                                        val only = group.only
                                        // 只有一类 → 直接开；两类都有 → 先问整理哪一类
                                        if (only != null) onPick(only) else choosing = group
                                    },
                                )
                            }
                        }
                    }
                }

                // ---- 「整理照片 / 整理视频」二选一 ----
                //
                // 不用 AlertDialog：这里已经在一个 Dialog 窗口里了，
                // 再开一个窗口容易出现"点外面关掉的是哪一个"的层级问题。
                // 直接在同一个 Surface 里铺一层遮罩，z 序完全可控。
                choosing?.let { group ->
                    FolderPickOverlay(
                        group = group,
                        onPick = {
                            onPick(it)
                            choosing = null
                        },
                        onDismiss = { choosing = null },
                    )
                }
            }
        }
    }
}

/**
 * 同名文件夹合成后的一条。
 *
 * 一个文件夹可能只有照片、只有视频，或者两类都有 ——
 * 后者需要用户在点开时再选一次整理哪一类。
 */
private data class FolderGroup(
    val name: String,
    val photos: BucketInfo?,
    val videos: BucketInfo?,
) {
    val total: Int get() = (photos?.count ?: 0) + (videos?.count ?: 0)

    /** 只有一类时直接开，不需要再问。 */
    val only: BucketInfo? get() = when {
        photos != null && videos == null -> photos
        videos != null && photos == null -> videos
        else -> null
    }

    /** 封面取数量多的那一类（数量相同取照片）—— 它更能代表这个文件夹。 */
    val cover: Uri? get() {
        val p = photos
        val v = videos
        return when {
            p == null -> v?.coverUri
            v == null -> p.coverUri
            p.count >= v.count -> p.coverUri ?: v.coverUri
            else -> v.coverUri ?: p.coverUri
        }
    }

    /** 「1,204 张 · 38 个视频」。只有一类时只写那一类。 */
    val subtitle: String get() = buildList {
        photos?.let { add("${thousand(it.count)} 张") }
        videos?.let { add("${thousand(it.count)} 个视频") }
    }.joinToString(" · ")

    /** 纯视频文件夹才在封面上挂「视频」角标；混合的靠副标题说明。 */
    val videoOnly: Boolean get() = photos == null && videos != null
}

/** 千分位：1204 → 1,204。文件夹里的内容动辄四位数，不分组很难一眼读数。 */
private fun thousand(n: Int): String = "%,d".format(n)

/** 「整理照片 / 整理视频」二选一。 */
@Composable
private fun FolderPickOverlay(
    group: FolderGroup,
    onPick: (BucketInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                // 吃掉卡片内部的点击，否则点选项以外的空白也会关掉
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
            )
            group.photos?.let { b ->
                PickRow("整理照片", "${thousand(b.count)} 张", AccentInfo) { onPick(b) }
            }
            group.videos?.let { b ->
                PickRow("整理视频", "${thousand(b.count)} 个", AccentKeep) { onPick(b) }
            }
            PickRow("取消", null, TextSecondary, onClick = onDismiss)
        }
    }
}

@Composable
private fun PickRow(
    title: String,
    subtitle: String?,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

/** 文件夹网格里的一格：正方形封面 + 名字 + 数量。 */
@Composable
private fun FolderCell(
    group: FolderGroup,
    /** 三列时每格只有约 100dp，标题字号要降一档，否则一行放不下四个字。 */
    compact: Boolean,
    onClick: () -> Unit,
) {
    // 只给封面圆角，**不要把整列一起 clip** —— 那样底部的副标题会被圆角削掉一截。
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface2),
        ) {
            val cover = group.cover
            if (cover != null) {
                AsyncImage(
                    // 系统相册已经生成好的缩略图，1~2ms 出图
                    model = rememberThumbnailRequest(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (group.videoOnly) {
                Text(
                    text = "视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = group.name,
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = group.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            maxLines = 1,
        )
    }
}
