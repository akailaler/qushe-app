package com.qushe.app.ui.trash

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qushe.app.core.AppContainer
import com.qushe.app.core.formatDuration
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.MediaRepository
import com.qushe.app.core.formatSize
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.MediaPreviewOverlay
import com.qushe.app.ui.common.PreviewActionButton
import com.qushe.app.ui.common.rememberContentShape
import com.qushe.app.ui.common.rememberThumbnailRequest
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.qushe.app.ui.theme.BadgeAccent
import com.qushe.app.ui.theme.BadgeScrim
import com.qushe.app.ui.theme.BadgeText
import com.qushe.app.ui.theme.QusheColors
import com.qushe.app.ui.theme.BadgeAlert
import androidx.compose.ui.graphics.Brush

/**
 * 回收站。
 *
 * 这里读的是**系统回收站**（Android 11 起提供），不是自己维护的目录 ——
 * 意味着即使本应用被卸载，内容依然按系统策略保留 30 天。
 * 自己搬文件到应用私有目录，卸载即丢失，反而更不安全。
 *
 * 部分厂商 ROM 未实现该接口，此时查询会失败；这里降级成空列表 + 说明文案，
 * 而不是让整个页面崩掉。
 */
@Composable
fun TrashScreen(
    container: AppContainer,
    /** 缩略图网格每行几列（2 或 3）。和「整理文件夹」共用设置里那一个开关。 */
    columns: Int = 3,
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(emptyList<MediaItem>()) }
    var loading by remember { mutableStateOf(true) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    /**
     * 「每一条是什么时候被删进来的」。
     *
     * 系统的 `DATE_EXPIRES` 在这台机器上给的不是到期时刻（刚删的算出来是
     * "今天到期"，用户实测发现的），所以改成读本地记账（见 [IdTimeStore]）。
     * **没有记账的条目就不显示天数角标** —— 缺一个角标比显示一个错的强。
     */
    var trashedAt by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }

    fun reload() {
        scope.launch {
            loading = true
            // 回收站列表本身不走缓存，所以这里**不要**调用 invalidate() ——
            // 那会把整个媒体库缓存丢掉，害得之后每次开组都要重扫一遍。
            items = runCatching { container.repo.trashed() }.getOrDefault(emptyList())
            trashedAt = container.sessions.trashMarksSnapshot()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        scope.launch {
            // 系统改完 MediaStore 需要一点时间，立刻重查会拿到旧数据
            delay(500L)
            reload()
        }
    }

    /**
     * 发起一次系统请求。
     *
     * [dropped] 是本次被彻底删除的项 —— 从媒体库缓存里精确摘除它们，而不是整体作废；
     * [restore] 表示内容回到了媒体库，缓存里本来就没有它们，这时才需要重新加载。
     */
    fun fire(
        sender: IntentSender?,
        dropped: List<MediaItem> = emptyList(),
        restore: Boolean = false,
    ) {
        if (sender == null) return
        if (restore) {
            container.repo.invalidate()
        } else {
            container.repo.dropFromCache(dropped)
        }
        // 彻底删除的条目不该再留着"删除时刻" —— 它已经从回收站消失了，
        // 记账留着只会让表越滚越大。（trashed 的记账在 [AppRoot.conclude] 里写，
        //  恢复走 [SessionRepository.unmarkTrashed]。）
        if (dropped.isNotEmpty()) {
            container.sessions.unmarkTrashed(dropped)
            container.persistAsync()
        }
        runCatching { launcher.launch(IntentSenderRequest.Builder(sender).build()) }
    }

    val totalBytes = items.sumOf { it.sizeBytes }
    val overflow = items.size - MediaRepository.MAX_DELETE_BATCH

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "回收站",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                // 关掉「删除进回收站」后删除走的是永久删除，
                // 内容根本不会到这里 —— 那时候这句常驻描述是假的。
                text = if (container.prefs.deleteToTrash) {
                    "删除的内容会先到这里，系统保留 30 天后自动清理"
                } else {
                    "当前是彻底删除模式：删除的内容不经过回收站，立刻释放空间且无法恢复"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )

            Spacer(Modifier.height(18.dp))
        }

        when {
            loading -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "正在读取系统回收站…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                )
            }

            items.isEmpty() -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "回收站是空的。如果这里始终没有内容，可能是这台设备的系统没有提供回收站接口，删除会直接生效",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                // 和收藏页同款：正方形缩略图墙，列数由设置决定
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                // 缩略图网格保持 4.dp —— 这是"密集小图"的间距，和大图之间的
                // 接缝（rememberPageGap）不是同一件事，别混。
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(items, key = { _, item -> item.storeKey() }) { index, item ->
                    TrashCell(
                        item = item,
                        deletedAt = trashedAt[item.storeKey()],
                        onClick = { previewIndex = index },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // 纯视频列表的预览是上下滑切换（左右滑是快进/快退），
                        // 这时候提示"左右滑动"会直接把用户甩到视频中段。
                        text = "点任意一张可以全屏预览，预览里" +
                            if (items.isNotEmpty() && items.all { it.kind == MediaKind.VIDEO }) {
                                "上下"
                            } else {
                                "左右"
                            } + "滑动看下一项",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // ---- 底部：左下角是信息，右下角是「清空」按钮 ----
        // 两者**刻意不做成一个整条**：信息是给人看的，按钮是给人按的，
        // 连成一整行会让人以为"整行都能点"，而实际上点在文字上是没反应的。
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("${items.size} 项 · ${formatSize(totalBytes)}")
                        if (overflow > 0) {
                            // 系统对单次删除请求有 2000 项上限，超出的部分要说清楚
                            append(" · 先删前 ${MediaRepository.MAX_DELETE_BATCH} 项")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    // weight 把按钮顶到右边，不需要额外的 Spacer
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentDelete,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface1)
                        .clickable {
                            // 不再自己再弹一次确认框：紧接着的系统删除弹窗本身就是确认，
                            // 两道确认同一件事只会让批量清理变啰嗦。
                            fire(
                                runCatching { container.repo.requestDeleteForever(items) }
                                    .getOrNull(),
                                dropped = items,
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }
    }

    val index = previewIndex
    if (index != null && items.isNotEmpty()) {
        MediaPreviewOverlay(
            items = items,
            initialIndex = index,
            onDismiss = { previewIndex = null },
            // 默认静音是全 App 级设置：预览里播的视频同样不外放。
            videoMutedByDefault = container.prefs.videoMuted,
            bottomContent = { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PreviewActionButton(
                        label = "恢复",
                        accent = AccentKeep,
                        modifier = Modifier.weight(1f),
                    ) {
                        previewIndex = null
                        fire(
                            runCatching { container.repo.requestRestore(listOf(item)) }.getOrNull(),
                            restore = true,
                        )
                    }
                    PreviewActionButton(
                        label = "彻底删除",
                        accent = AccentDelete,
                        modifier = Modifier.weight(1f),
                    ) {
                        previewIndex = null
                        fire(
                            runCatching {
                                container.repo.requestDeleteForever(listOf(item))
                            }.getOrNull(),
                            dropped = listOf(item),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun TrashCell(item: MediaItem, deletedAt: Long?, onClick: () -> Unit) {
    // 和收藏 / 喜欢页同一套格子：正方形、Crop 铺满、右下角一枚信息角标。
    // 回收站里最需要的是"一眼认出是哪张"，列表行那种"小图 + 文件名"
    // 反而让人得逐行读字 —— 缩略图墙才是一眼可辨的形态。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            // 和大图预览、瀑布流格子共用同一个圆角值（rememberContentCorner）
            .clip(rememberContentShape())
            .background(Surface1)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = rememberThumbnailRequest(item.uri),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // ---- 左上角：还剩几天 ----
        //
        // 系统回收站保留 30 天是**系统定的**，应用侧拿不到"到期时刻"，
        // 所以这里用**自己记下的删除时刻**倒推（见 [IdTimeStore]）。
        //
        // 向上取整：刚删进去的那一秒就算"还剩 30 天"，而不是 29 ——
        // 用户刚删就看到"29 天"会以为是少了一天。临近到期时依次是 2 天、1 天、
        // 今天到期，0 表示已过期（系统随时会清掉）。
        val daysLeft = remember(deletedAt) {
            val at = deletedAt ?: return@remember null
            val passed = System.currentTimeMillis() - at
            val used = ((passed + 86_399_999L) / 86_400_000L).toInt()
            (TRASH_RETENTION_DAYS.toInt() - used).coerceIn(0, TRASH_RETENTION_DAYS.toInt())
        }
        if (daysLeft != null) {
            // ---- 底部渐变蒙版（1.4.2）----
            //
            // 从"左上角小深底"改成压在缩略图**底部居中**的一条渐变：
            // 底部最深、向上渐隐到全透明，上沿没有硬边（渐变本身就是羽化）。
            // 白字不描边、不加阴影 —— 对比度全部由下面这层渐变提供。
            // 高度 25dp：刚好托住一行 labelSmall，再高就会吃掉缩略图的下一行内容。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(25.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.55f to Color(0x4D000000),
                                1f to Color(0x99000000),
                            ),
                        )
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    // 角标只留数字，单位已经在语境里了（回收站页 + 底部居中）。
                    text = when (daysLeft) {
                        0 -> "今天到期"
                        else -> "$daysLeft 天"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

/**
 * 系统回收站的保留天数。
 *
 * 写死 30 是因为这是 AOSP MediaStore 的默认值（也是设置页里那句话的依据）。
 * 厂商可能改，但应用侧查不到真实值 —— 与其显示一个错的数字，不如按 30 天倒推。
 */
private const val TRASH_RETENTION_DAYS = 30L
