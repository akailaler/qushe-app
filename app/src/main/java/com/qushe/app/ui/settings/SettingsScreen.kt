package com.qushe.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.qushe.app.core.MediaRepository
import com.qushe.app.core.Prefs
import com.qushe.app.core.Stats
import com.qushe.app.core.formatSize
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.Surface3
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary

private val GROUP_SIZE_CHOICES = Prefs.GROUP_SIZE_CHOICES

@Composable
fun SettingsScreen(
    prefs: Prefs,
    stats: Stats,
    permissionTick: Int,
    onProbeLocation: suspend () -> MediaRepository.LocationProbe,
    onRequestLocationPermission: () -> Unit,
    onStatsReset: () -> Unit,
    onOpenAppSettings: () -> Unit,
    /** 反向同步：把系统相册里的收藏导入本应用。返回一句给用户看的话。 */
    onImportSystemFavorites: suspend () -> String,
    /** 正向同步：把本应用的收藏写入系统相册。返回一句给用户看的话。 */
    onSyncFavoritesNow: suspend () -> String,
    /** 还有多少项没写入系统相册。 */
    pendingFavoriteSync: Int,
    /** 显示相对时间（3 天前）。由外层持有 state，见 `AppRoot.relativeDates`。 */
    relativeDates: Boolean,
    onRelativeDatesChange: (Boolean) -> Unit,
    /** 显示具体日期（2020/6/6）。 */
    absoluteDates: Boolean,
    onAbsoluteDatesChange: (Boolean) -> Unit,
) {
    var groupSize by remember { mutableIntStateOf(prefs.groupSize) }
    var onlyUnseen by remember { mutableStateOf(prefs.onlyUnseen) }
    var deleteToTrash by remember { mutableStateOf(prefs.deleteToTrash) }
    var videoMuted by remember { mutableStateOf(prefs.videoMuted) }
    var autoPlay by remember { mutableStateOf(prefs.autoPlayVideo) }
    var autoSync by remember { mutableStateOf(prefs.autoSyncFavorites) }
    var railOnRight by remember { mutableStateOf(prefs.railOnRight) }
    var doubleTapLikes by remember { mutableStateOf(prefs.doubleTapLikes) }
    var showLocation by remember { mutableStateOf(prefs.showMediaLocation) }

    // 位置自检。三种失败（没授权 / 系统拒绝读该字段 / 内容本身没位置）
    // 在界面上的表现完全一样，所以这里主动查一次、把结论直接说出来。
    var locationProbe by remember { mutableStateOf<MediaRepository.LocationProbe?>(null) }
    LaunchedEffect(showLocation, permissionTick) {
        locationProbe = if (showLocation) onProbeLocation() else null
    }
    var confirmReset by remember { mutableStateOf(false) }

    // ---- 反向同步系统收藏 ----
    //
    // 做成一个**显式的动作**而不是开关：它会扫一遍全库，
    // 没人希望它每隔一会儿自己跑一次。跑完把结论原话说出来 ——
    // "读不到任何收藏"和"本来就一致"是完全不同的两件事，不能混成一句。
    var importingFavorites by remember { mutableStateOf(false) }
    var importNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // ---- 用 `LazyColumn` 而不是 `Column + verticalScroll` ----
    //
    // `verticalScroll` 的 Column 会**一次性组合全部内容**。这一页有二十几块设置项，
    // 从首页滑过来时要在**手势进行中**现组合 + 现测量 ⇒ 掉帧（用户反馈
    // "首页左右滑动到其他页面感觉有些卡顿"，这一页是最重的一页）。
    //
    // 主体按"整理方式 / 删除 / 视频播放 + 位置 / 收藏 + 统计"切成 4 块 `item` ——
    // 不是每块设置项一个 item（那样 item 数量太多、每个都太矮），
    // 也不要一整页一个 item（那就等于没改）。
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        // ---- 第 1 块（分块是为了让 LazyColumn 惰性组合；块内顺序与原来一致）----
        item {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(24.dp))
        }

        // ---- 第 2 块（分块是为了让 LazyColumn 惰性组合；块内顺序与原来一致）----
        item {
            // ---- 整理方式 ----
            SectionTitle("整理方式")

            SettingBlock(
                title = "每组数量",
                subtitle = "太小没有仪式感，太大一次做不完。默认 $DEFAULT_LABEL 张。",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUP_SIZE_CHOICES.forEach { choice ->
                        Chip(
                            text = choice.toString(),
                            selected = groupSize == choice,
                            onClick = {
                                groupSize = choice
                                prefs.groupSize = choice
                            },
                        )
                    }
                }
            }

            // ---- 「上滑画面做什么」这条已删（1.3.48）----
            //
            // 原来这里是"上滑 = 删除 / 上滑 = 收藏"二选一。但收藏早就搬到了
            // 整理页左下角的星形按钮上，**上滑收藏这条路是死的**：
            // `DraggablePhoto` 的 FAVORITE 分支从来不调用 `onDecision`，
            // 于是关掉这个开关时上滑只会弹一句提示、什么都不做 —— 一条会骗人的设置。
            // 与其留一个选错就失效的开关，不如直接删掉。
            // 偏好 `swipeUpDeletes` 本身保留（老配置读得出来），只是不再有 UI 改它。

            // ---- 时间格式 ----
            //
            // 两个**独立开关**而不是二选一：四种组合都要能表达
            // （只相对 / 只具体 / 都显示 / 都不显示）。
            // 二选一的胶囊做不到后两种 —— 关掉一个就等于选中另一个。
            SettingBlock(
                title = "相对时间",
                subtitle = "「3 天前」这种写法。两个都关时，胶囊只显示文件夹和大小。",
            ) {
                Switch(
                    checked = relativeDates,
                    onCheckedChange = {
                        onRelativeDatesChange(it)
                        prefs.relativeDates = it
                    },
                    colors = switchColors(),
                )
            }

            SettingBlock(
                title = "具体日期",
                subtitle = "「2020/6/6」这种写法。和相对时间同时开启时，它会显示在胶囊第二行。",
            ) {
                Switch(
                    checked = absoluteDates,
                    onCheckedChange = {
                        onAbsoluteDatesChange(it)
                        prefs.absoluteDates = it
                    },
                    colors = switchColors(),
                )
            }

            SettingBlock(
                title = "只抽没看过的",
                subtitle = "看过的在整轮内不再出现；刷完一轮会自动开启新一轮。",
            ) {
                Switch(
                    checked = onlyUnseen,
                    onCheckedChange = {
                        onlyUnseen = it
                        prefs.onlyUnseen = it
                    },
                    colors = switchColors(),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("删除")

            SettingBlock(
                title = "删除进回收站",
                subtitle = if (deleteToTrash) {
                    "系统保留 30 天，期间随时可以恢复。"
                } else {
                    "直接彻底删除，立刻释放空间，无法恢复。"
                },
            ) {
                Switch(
                    checked = deleteToTrash,
                    onCheckedChange = {
                        deleteToTrash = it
                        prefs.deleteToTrash = it
                    },
                    colors = switchColors(),
                )
            }

            if (!deleteToTrash) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentDelete.copy(alpha = 0.12f))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "提醒：本地文件彻底删除后不可恢复。建议先跑一段时间回收站模式。",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentDelete,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // ---- 第 3 块（分块是为了让 LazyColumn 惰性组合；块内顺序与原来一致）----
        item {
            Spacer(Modifier.height(20.dp))
            SectionTitle("视频播放")

            SettingBlock(
                title = "默认静音",
                subtitle = "看任何视频都不外放：整理视频页、收藏 / 喜欢 / 回收站的预览、" +
                    "长按播放的实况片段，都认这一条。预览页顶栏右上角可随时切回有声。",
            ) {
                Switch(
                    checked = videoMuted,
                    onCheckedChange = {
                        videoMuted = it
                        prefs.videoMuted = it
                    },
                    colors = switchColors(),
                )
            }

            SettingBlock(
                title = "自动播放",
                subtitle = "进入即开始播放，不用再点一下。",
            ) {
                Switch(
                    checked = autoPlay,
                    onCheckedChange = {
                        autoPlay = it
                        prefs.autoPlayVideo = it
                    },
                    colors = switchColors(),
                )
            }

            // 和下面「双击画面做什么」同一个理由：开关只能表达"是不是右边"，
            // 用户还得在脑子里把"关闭"翻译成"那就是左边"。
            // 两个选项各自写清位置，就没有这一步翻译了。
            SettingBlock(
                title = "操作栏放在哪",
                subtitle = "影响整理视频、收藏页、喜欢页的侧边操作（喜欢 / 收藏 / 删除）。",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = "左侧",
                        selected = !railOnRight,
                        onClick = {
                            railOnRight = false
                            prefs.railOnRight = false
                        },
                    )
                    Chip(
                        text = "右侧",
                        selected = railOnRight,
                        onClick = {
                            railOnRight = true
                            prefs.railOnRight = true
                        },
                    )
                }
            }

            // 用两个胶囊而不是一个开关：开关只能表达"是不是喜欢"，
            // 用户还得在脑子里把"关闭"翻译成"那就是收藏"。
            // 两个选项都摆在屏幕上，各自写清后果，就没有翻译这一步了。
            SettingBlock(
                title = "双击画面做什么",
                subtitle = if (doubleTapLikes) {
                    "双击 = 喜欢。只记在本应用里，不碰系统相册；整理时左下角的红心也是它。"
                } else {
                    // 同上：跟随「同步收藏到系统相册」开关，不无条件承诺。
                    "双击 = 收藏。${
                        if (autoSync) {
                            "会同步进系统相册的「收藏」夹；整理时右下角的星形也是它。"
                        } else {
                            "自动同步已关闭，只记在本应用内；可到「收藏」页手动同步。"
                        }
                    }"
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = "喜欢",
                        selected = doubleTapLikes,
                        onClick = {
                            doubleTapLikes = true
                            prefs.doubleTapLikes = true
                        },
                    )
                    Chip(
                        text = "收藏",
                        selected = !doubleTapLikes,
                        onClick = {
                            doubleTapLikes = false
                            prefs.doubleTapLikes = false
                        },
                    )
                }
            }

            // 把两者的区别再摆一次。这不是重复 —— 上面那行是"当前选了什么"，
            // 这里回答的是"所以到底有什么不一样"，那正是用户真正要判断的东西。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface1)
                    .padding(12.dp),
            ) {
                Column {
                    Text(
                        text = "「喜欢」和「收藏」的区别",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "喜欢：只存在本应用里，可以随手标记，不必担心同步。\n" +
                            "收藏：会写进系统相册的「收藏」夹，手机自带的相册应用里也看得到。",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }

            val probe = locationProbe
            val locationSubtitle = when {
                !showLocation -> "已关闭，左下角不再显示地点。"
                probe == null -> "已开启，正在检查这台设备能不能读到位置信息…"
                !probe.hasPermission ->
                    "还没有拿到「照片和视频的位置信息」权限 —— 点这一行去授权。"
                probe.scanned == 0 -> "已授权，但相册里暂时没有可检查的内容。"
                probe.withPoint > 0 ->
                    "已授权。抽查 ${probe.scanned} 张，其中 ${probe.withPoint} 张有位置信息，" +
                        "会显示在左下角。"
                probe.readFailed ->
                    "已授权，但读取位置时系统报错，这台设备的媒体库可能不支持。"
                probe.columnMissing ->
                    "已授权。系统没有返回位置字段，已改走「直读照片 EXIF」那条路，" +
                        "抽查 ${probe.scanned} 张都没有位置信息 —— 这些照片本身没记录地点。"
                else ->
                    "已授权。MediaStore 和照片 EXIF 两条路都查过，抽查 ${probe.scanned} 张" +
                        "都没有位置信息 —— 这些照片本身没记录地点。"
            }

            SettingBlock(
                title = "显示拍摄地点",
                subtitle = locationSubtitle,
                // 只在"确实还没授权"时把整行做成可点的授权入口
                onClick = if (showLocation && probe != null && !probe.hasPermission) {
                    onRequestLocationPermission
                } else {
                    null
                },
            ) {
                Switch(
                    checked = showLocation,
                    onCheckedChange = {
                        showLocation = it
                        prefs.showMediaLocation = it
                    },
                    colors = switchColors(),
                )
            }
        }

        // ---- 第 4 块（分块是为了让 LazyColumn 惰性组合；块内顺序与原来一致）----
        item {
            Spacer(Modifier.height(20.dp))
            SectionTitle("收藏")

            SettingBlock(
                title = "同步收藏到系统相册",
                subtitle = if (autoSync) {
                    "每轮整理结束会把新收藏写进系统相册的「收藏」。系统不允许应用静默修改，" +
                        "所以每次会弹一个确认框；嫌打断可以关掉，之后到收藏页手动同步。"
                } else {
                    "已关闭。收藏仍会记录在本应用内，需要时到「收藏」页一次性同步。"
                },
            ) {
                Switch(
                    checked = autoSync,
                    onCheckedChange = {
                        autoSync = it
                        prefs.autoSyncFavorites = it
                    },
                    colors = switchColors(),
                )
            }

            SettingBlock(
                title = "立即同步收藏到系统相册",
                subtitle = if (pendingFavoriteSync > 0) {
                    "有 $pendingFavoriteSync 项还没写进系统相册，点这里立刻同步。"
                } else {
                    "取舍里的收藏与系统相册已经一致。"
                },
                onClick = {
                    scope.launch { importNote = onSyncFavoritesNow() }
                },
            ) {
                Text(
                    text = if (pendingFavoriteSync > 0) "同步" else "已一致",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (pendingFavoriteSync > 0) AccentInfo else TextSecondary,
                )
            }

            SettingBlock(
                title = "同步系统收藏到这里",
                subtitle = importNote ?: "把系统相册里已经标了星的内容也收进本应用的「收藏」。" +
                    "只加不删 —— 系统里没有的不会被移除。",
                onClick = if (importingFavorites) {
                    null
                } else {
                    {
                        scope.launch {
                            importingFavorites = true
                            importNote = null
                            importNote = onImportSystemFavorites()
                            importingFavorites = false
                        }
                    }
                },
            ) {
                Text(
                    text = if (importingFavorites) "同步中…" else "同步",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (importingFavorites) TextTertiary else AccentInfo,
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("到目前为止")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface1)
                    .padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniStat(stats.reviewedPhotos.toString(), "照片", TextPrimary)
            MiniStat(stats.reviewedVideos.toString(), "视频", TextPrimary)
                MiniStat(stats.deleted.toString(), "已删除", AccentDelete)
                MiniStat(stats.favorited.toString(), "已收藏", AccentKeep)
                MiniStat(formatSize(stats.freedBytes), "瘦身", AccentInfo)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "统计数字只记录你完成了多少，不显示还剩多少没做 —— " +
                    "「还剩」是家务，「已完成」才是成果。",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .clickable(onClick = onOpenAppSettings)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "管理媒体权限",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "只给照片权限也能用，视频整理会单独提示",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = Surface3)
            Spacer(Modifier.height(16.dp))

            Text(
                text = "取舍 1.3.74",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "手势设计参考「去留 linger」的公开设计思考：\n" +
                    "照片用浏览照片的手势，视频用浏览视频的手势；\n" +
                    "导航归滑动，决策归双击与按钮。",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )

            // ---- 清零统计 ----
            //
            // 放整页最底部、用最低的文字层级，并写明作用范围 ——
            // 之前它长得和普通设置项一样（浅色大字号 + 整行可点），被误触过。
            Spacer(Modifier.height(26.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { confirmReset = true }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = "清零统计",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "只把本应用的累计数字归零（已回顾 / 已删除 / 已收藏 / 瘦身）。\n" +
                        "不会删除或恢复任何照片、视频，也不影响系统相册里的收藏。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary.copy(alpha = 0.65f),
                )
            }
            Spacer(Modifier.height(30.dp))
        }

    }

    // 二次确认。这个动作本身不可撤销，而入口又刻意做得很不起眼 ——
    // 越是不起眼的入口，越要保证"点到"不等于"执行"。
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Surface2,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("清零统计？") },
            text = {
                Text(
                    "会把本应用的累计数字全部归零：已回顾、已删除、已收藏、瘦身。\n\n" +
                        "照片和视频不会有任何变化，收藏记录也不会丢 —— " +
                        "归零的只是那几个计数。此操作无法撤销。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onStatsReset()
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
}

private const val DEFAULT_LABEL = "30"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = TextSecondary,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SettingBlock(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                }
            )
            .padding(bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Surface3.copy(alpha = 0.5f))
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentKeep else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Ink else TextSecondary,
        )
    }
}

@Composable
private fun MiniStat(value: String, label: String, accent: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = Ink,
    checkedTrackColor = AccentKeep,
    uncheckedThumbColor = TextTertiary,
    uncheckedTrackColor = Surface3,
)
