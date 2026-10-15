package com.qushe.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 一个「不消费任何指针事件」的点击 / 双击 / 长按检测器。
 *
 * 为什么不能用现成的 detectTapGestures：
 * 它内部会 `down.consume()`。一旦按下事件被消费，同一个区域上的
 * VerticalPager（也就是"上下滑切换视频"）就拿不到拖拽，整个视频流会划不动。
 * 这两者是互斥的，必须有一个让路 —— 让路的应该是点击检测。
 *
 * 所以这里全程不调用 consume()：
 *  - 拖拽照常被 Pager 拿去用；
 *  - 点击 / 双击 / 长按由本函数自己判定。
 *
 * 判定"是不是拖拽"的依据是 isConsumed —— Pager 开始滚动时会消费位移，
 * 看到这个信号就说明本次手势属于拖拽，不再当作点击处理。
 */
suspend fun PointerInputScope.detectTapsWithoutConsuming(
    onTap: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    doubleTapWindowMillis: Long = 280L,
) {
    var lastTapUpAt = 0L

    // 待执行的单击。
    //
    // 关键点：**只要注册了双击，单击就必须延后到双击判定窗口结束再执行。**
    // 否则双击的第一下会当场被当成单击执行掉 —— 在视频页上表现为
    // "每次双击收藏，画面都会先闪一下暂停再恢复"，用户能明显感知到。
    // 延后的代价是单击（暂停/播放）会晚 280ms 生效，这是有双击手势时无法回避的取舍。
    var pendingSingleTap: Job? = null

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var dragged = false

            // 在长按超时窗口内等待抬起。返回 null 表示超时（可能成立长按），
            // 返回 true 表示干净的点击，返回 false 表示这次手势其实是拖拽。
            val settledAsTap: Boolean? =
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            released = true
                        } else {
                            if (change.isConsumed) dragged = true
                            // 同时也自己算位移，不完全依赖 isConsumed。
                            // 原因：父节点的拖拽检测在 Main 阶段是**后**于子节点拿到事件的，
                            // 所以子节点这一侧读到的 isConsumed 往往还是 false ——
                            // 结果横滑也被当成点击，把下面的缩略图栏给切了出来。
                            if (!dragged) {
                                val moved = (change.position - down.position).getDistance()
                                if (moved > viewConfiguration.touchSlop) dragged = true
                            }
                            if (!change.pressed) released = true
                        }
                    }
                    !dragged
                }

            when (settledAsTap) {
                null -> {
                    // 超时。只有"按住没动过"才算长按 —— 拖到一半停住不动也会超时，
                    // 那种情况必须放过，否则每次滑动都会误触发倍速。
                    if (!dragged) {
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                        onLongPressStart?.invoke()
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) released = true
                        }
                        onLongPressEnd?.invoke()
                    }
                }

                true -> {
                    val upAt = System.currentTimeMillis()
                    val isSecondTap = onDoubleTap != null &&
                        lastTapUpAt > 0L &&
                        upAt - lastTapUpAt <= doubleTapWindowMillis

                    if (isSecondTap) {
                        lastTapUpAt = 0L
                        // 第二下到了，把挂起的单击撤掉，只当双击处理
                        pendingSingleTap?.cancel()
                        pendingSingleTap = null
                        onDoubleTap?.invoke()
                    } else {
                        lastTapUpAt = upAt
                        if (onDoubleTap == null) {
                            onTap.invoke()
                        } else {
                            pendingSingleTap?.cancel()
                            pendingSingleTap = launch {
                                delay(doubleTapWindowMillis)
                                onTap.invoke()
                            }
                        }
                    }
                }

                else -> Unit // 拖拽，完全交给 Pager
            }
        }
    }
}

/** 把卡片甩出屏幕。用 tween 而不是 spring，甩出必须干脆、不能回弹。 */
suspend fun Animatable<Float, AnimationVector1D>.flingTo(target: Float) {
    animateTo(target, animationSpec = tween(durationMillis = 200, easing = FastOutLinearInEasing))
}

/**
 * 翻页吸附 —— 刚度与 `HorizontalPager` 的默认翻页一致
 * （`PagerDefaults` 内部就是 `spring(stiffness = StiffnessMediumLow)`）。
 *
 * ---- 为什么不能用 [flingTo] ----
 * `flingTo` 是 **tween（定时）**：不管手指当时多快，都固定 200ms 走完一整屏。
 * 翻页的判定线只有 72dp —— 轻轻一划手指可能只有 1000px/s，
 * 而画面被拉到 5400px/s（1080px ÷ 0.2s）⇒ **松手瞬间速度跳 5 倍**。
 * 这就是整理照片页左右滑"比收藏页突兀"的真正原因（收藏页走的是 pager）。
 *
 * 换成 spring 之后，`Animatable` 会把它自己记录的**当前速度**作为弹簧初速度
 * （`animateTo` 内部就是这么做的），松手前后速度连续 ⇒ 手感与 pager 一致。
 */
suspend fun Animatable<Float, AnimationVector1D>.settleToPage(target: Float) {
    animateTo(
        targetValue = target,
        // ---- 刚度比"软弹簧"高一档，代价是到达时间明显变短 ----
        // `onDecision` 是在这段动画**跑完之后**才换 index 的（换早了会出现
        // "照片瞬移回正中"的跳变），所以这段动画的时长**直接等于换页的等待时间**。
        // 用 `StiffnessMediumLow` 时走完一整屏要 400~600ms —— 单次滑动感觉不出来，
        // 连续滑动就变成"滑一张要等一下才能滑下一张"（用户反馈）。
        // 换成 `StiffnessMedium` + `visibilityThreshold = 24px`：
        // 速度依然连续（还是弹簧），等待缩到 ~300ms。
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            visibilityThreshold = 24f,
        ),
    )
}
