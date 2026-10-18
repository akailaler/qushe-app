package com.qushe.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.qushe.app.ui.theme.AccentKeep

/**
 * 一条可拖动定位的播放进度条。
 *
 * ---- 为什么必须只有这一份 ----
 * 原来 `MediaPreviewOverlay.kt`（收藏/喜欢页预览视频）和 `VideoSessionScreen.kt`
 * （整理视频页）里**各写了一遍，逐字相同**。
 * 进度条这种"手感型"组件尤其怕分叉：改一处（比如把触摸区从 26dp 加高、或加个拖动时的放大）
 * 另一处就会变成两个 App 的手感。**统一一份，改就一起改。**
 *
 * @param ratio 0..1 的播放进度。
 * @param onScrubStart 按下时回调（调用方通常借此暂停自动进度刷新）。
 * @param onScrub 拖动中回调，参数是 0..1 的目标位置。
 * @param onScrubEnd 松手 / 手势取消时回调（恢复跟随播放状态）。
 */
@Composable
internal fun VideoProgressBar(
    ratio: Float,
    modifier: Modifier = Modifier,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
) {
    Box(
        modifier = modifier
            // 26dp 是"能被拇指按到"的最小高度；视觉上的线只有 2dp，
            // 触摸区靠这层透明留白撑起来，否则根本拖不动。
            .height(26.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onScrubStart()
                        onScrub((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onScrub((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0x4DFFFFFF)),
        )
        Box(
            Modifier
                // 必须显式指定 CenterStart。父容器用了 Alignment.Center 是为了让
                // 2dp 的细线在 26dp 的触摸区里垂直居中，但那同时也把子元素横向居中了，
                // 结果进度就从中间往两边长。这里把它按回左边。
                .align(Alignment.CenterStart)
                .fillMaxWidth(ratio.coerceIn(0.001f, 1f))
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(AccentKeep),
        )
    }
}
