package com.qushe.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.storeKey
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Surface2

private val THUMB_WIDTH = 46.dp
private val THUMB_HEIGHT = 62.dp
private val THUMB_GAP = 5.dp

/**
 * 底部横向缩略胶片条，交互对齐 iOS 相册。
 *
 * - 手指横向拖动即可滚动（LazyRow 原生支持）
 * - 点击缩略图直接跳到那张
 * - 当前项用高亮描边 + 不压暗来标记，其余项压暗，一眼能看出"我在哪"
 * - 当前项**自动居中**：靠给 LazyRow 加左右等宽的 contentPadding 实现 ——
 *   这样 scrollToItem(index) 天然就是居中，不需要自己算偏移量
 */
@Composable
fun ThumbnailFilmstrip(
    items: List<MediaItem>,
    currentIndex: Int,
    visible: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 已确认是实况照片的条目 key 集合。
     *
     * 不能直接用 `item.isMotionPhoto` —— 那个标记来自查询阶段的 XMP 列，而那一列
     * 受权限约束不能查（见 MediaRepository 的说明），所以实况判定统一由
     * 调用方通过文件头探测得出后传进来。
     */
    motionKeys: Set<Long> = emptySet(),
) {
    val listState = rememberLazyListState()

    var wasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex, items.size, visible) {
        // key 里**必须带上 visible** —— 否则缩略图栏重新显示时 currentIndex 没变，
        // 这个效果不会重跑，栏就停在原来的滚动位置，不会定位到正在看的那张。
        if (!visible || items.isEmpty()) {
            wasVisible = false
            return@LaunchedEffect
        }
        val target = currentIndex.coerceIn(0, items.lastIndex)
        // 刚展开时直接跳（不然会从位置 0 一路滚过去，看着很怪）；
        // 已经开着的时候才用动画，翻页时有跟随感。
        if (wasVisible) {
            listState.animateScrollToItem(target)
        } else {
            listState.scrollToItem(target)
        }
        wasVisible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 },
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 左右各留半屏减去半个缩略图，滚到第 i 项时它正好落在正中间
            val sidePadding = ((maxWidth - THUMB_WIDTH) / 2).coerceAtLeast(10.dp)

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = sidePadding),
                horizontalArrangement = Arrangement.spacedBy(THUMB_GAP),
            ) {
                itemsIndexed(items, key = { _, item -> item.storeKey() }) { index, item ->
                    FilmstripThumb(
                        item = item,
                        selected = index == currentIndex,
                        badge = badgeTextFor(item, item.storeKey() in motionKeys),
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilmstripThumb(
    item: MediaItem,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.55f,
        animationSpec = tween(160),
        label = "thumbAlpha",
    )
    val borderWidth = if (selected) 2.dp else 1.dp
    val borderColor = if (selected) AccentKeep else Color(0x1FFFFFFF)

    Box(
        modifier = Modifier
            .width(THUMB_WIDTH)
            .height(THUMB_HEIGHT)
            .graphicsLayer {
                // 当前项略微放大，形成 iOS 那种"选中浮起"的感觉
                val s = if (selected) 1.1f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(6.dp))
            .background(Surface2)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = rememberThumbnailRequest(item.uri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
        )

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * 缩略图角标：让用户一眼知道这张是什么特殊类型。
 *
 * 优先级和瀑布流那边**保持一致**（见 `DayViewScreen` 里的 tag）：
 * 视频 > 长图 > 截屏，实况和动图排在前面是因为它们在缩略图上完全看不出来
 * （静态缩略图看不出会不会动、有没有声音），信息量比"长图/截屏"更大。
 *
 * 长图这一条是用户点名要的：长图在胶片条里就是一根细条，
 * 没有角标完全看不出它和普通竖图的区别，点进去才知道要竖着读半天。
 */
private fun badgeTextFor(item: MediaItem, isMotion: Boolean): String? = when {
    item.kind == MediaKind.VIDEO -> "▶"
    isMotion -> "实况"
    item.isAnimated -> "动"
    item.isLongImage -> "长"
    item.kind == MediaKind.SCREENSHOT -> "截"
    else -> null
}
