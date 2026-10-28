package com.qushe.app.ui.photo

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qushe.app.core.MediaItem
import com.qushe.app.core.formatDayTitle
import com.qushe.app.ui.common.RecenterIcon
import com.qushe.app.ui.common.UndoIcon
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface3
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import com.qushe.app.ui.theme.OverlayBorder
import com.qushe.app.ui.theme.OverlaySoft
import com.qushe.app.ui.theme.overlayShadow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 底部刻度条 —— 「刻度不动、指针动」。
 *
 * ```
 *      10月13日 · 周二
 *  ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼      ← 刻度固定在这条轨道上，永远不动
 *              ▲                        ← 绿色指针在轨道上左右移动
 *         共 42 张
 * ```
 *
 * **形态的演变**：上一版做成了"指针固定、刻度整体平移"，用户这一轮明确
 * 改口为「**刻度条不动，通过指针动来滑动瀑布流**」。
 *
 * 这个说法其实更准：刻度是**标尺**（位置是绝对的、不动的），
 * 指针是**读数**。标尺跟着手指跑会让人失去"一共多长"的稳定参照 ——
 * 这跟上一轮"背景在刻度条上滑动"的抱怨是同一个病根：**参照物不该动**。
 *
 * 交互：手指在轨道上任何位置按下 / 拖动，指针就移到哪儿，
 * 同时把瀑布流滚到对应比例。指针不要求被精确抓住才生效 ——
 * 精确点一根 2dp 宽的线太苛刻了，整条轨道都是热区。
 */
@Composable
internal fun DayScrubber(
    scroll: ScrollState,
    total: Int,
    /**
     * 稳定的滚动总长（px），由两排各自上报后取大者。
     *
     * **不能用 `scroll.maxValue`**：两排共用一个 ScrollState 而总宽不同，
     * Compose 每次布局都用"最后那排"覆盖 maxValue，于是它在两排两个值之间跳，
     * 指针就跟着乱跳（用户报"不管滑照片还是拖光标，光标都乱跳"）。
     */
    span: Float,
    onSeekFraction: (Float) -> Unit,
    /** ⟲ 重新定位到"正在看的那张"（滑远了以后一键回来）。 */
    onRecenter: () -> Unit,
    /** ↩ 撤销上一次标记删除。 */
    onUndoDelete: () -> Unit,
    canUndo: Boolean,
) {
    var rulerWidth by remember { mutableFloatStateOf(0f) }
    var draggingFraction by remember { mutableStateOf<Float?>(null) }
    // 节流用：上次真正推动滚动的时间 / 还没提交的最终位置
    var lastSeekAt by remember { mutableLongStateOf(0L) }
    var pendingSeek by remember { mutableStateOf<Float?>(null) }

    // ---- 指针位置：**只在绘制阶段读滚动值** ----
    //
    // 原来把 fraction 在组合期算好（`derivedStateOf { scroll.value / maxValue }`），
    // 结果滚动时整个 DayScrubber（刻度 Canvas + 胶囊底 + 指针）**每帧重组一次** ——
    // 这是用户反馈的"不跟手"里很实在的一笔开销。
    // 现在把 `scroll.value` 的读挪进 `graphicsLayer { }` 的 lambda：那是绘制阶段，
    // 滚动只会让这一层重绘，不再触发任何重组。

    // ---- 底栏：⟨⟲⟩ ⟨刻度尺⟩ ⟨↩⟩ ----
    // 参照「去留」录屏的形态：两枚圆形按钮把刻度尺夹在中间。
    // 刻度尺用 weight(1f) 吃掉剩余宽度 —— 之前固定 62% 太窄，
    // 用户反馈"有点窄也有一点不明显"。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayCircleButton(
            icon = RecenterIcon,
            contentDescription = "回到这张照片",
            enabled = true,
            onClick = onRecenter,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Surface1)
                .onSizeChanged { rulerWidth = it.width.toFloat() }
                .pointerInput(total, rulerWidth) {
                    if (total <= 1 || rulerWidth <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 轨道两端各留一点内边距，指针不会贴到胶囊边缘。
                        val inset = 12.dp.toPx()
                        val usable = (rulerWidth - inset * 2).coerceAtLeast(1f)
                        // 刻意用 val lambda 而不是局部 fun：后者会踩到 lint 的
                        // SuspiciousIndentation（它不把函数声明当作"上一条语句"，
                        // 于是下一行调用被误判成异常缩进，lintDebug 直接失败）。
                        val seek = { x: Float ->
                            val f = ((x - inset) / usable).coerceIn(0f, 1f)
                            draggingFraction = f
                            pendingSeek = f
                            // ---- 节流到 ~25fps ----
                            //
                            // 拖动时每个 pointer event 都改一次 scroll，会让可见区间
                            // 每帧整体换一批（十几格同时进出组合 + 解码）。手指移动
                            // 再快也不必 60fps 重排；40ms 一次肉眼完全跟得上。
                            val now = SystemClock.uptimeMillis()
                            if (now - lastSeekAt >= 40L) {
                                lastSeekAt = now
                                onSeekFraction(f)
                            }
                        }
                        seek(down.position.x)
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                change.consume()
                                seek(change.position.x)
                            }
                        } while (event.changes.any { it.pressed })
                        // 松手时把最后一次位置补上（节流可能刚好漏掉它）
                        pendingSeek?.let(onSeekFraction)
                        pendingSeek = null
                        draggingFraction = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // ---- 刻度层：**固定不动**。均匀铺满一条静止的轨道 ----
            Ticks()

            // ---- 指针：按 fraction 在轨道上左右移动 ----
            // 在**绘制阶段**读 fraction：滚动时只有这一层重绘，
            // 不会把整个 DayScrubber 拖着每帧重组一遍。
            //
            // `contentAlignment = CenterStart` 而不是 Center：
            //  · 要 Center（垂直居中）是因为 BoxWithConstraints 内容默认贴左上，
            //    不居中的话指针挂在胶囊**顶上**；
            //  · 但水平方向必须是 **Start** —— 用 Center 的话指针会先被水平居中
            //    （偏出半个胶囊宽），`translationX` 再从那个位置继续偏移，
            //    结果就是"指针不跟手、总在触点右边一段"（用户报的就是这个）。
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val usable = (maxWidth - 24.dp).coerceAtLeast(0.dp)
                val usablePx = with(LocalDensity.current) { usable.toPx() }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            // 拖刻度条时跟手指；松手后跟**实际滚动偏移**
                            // （所以滑带子时指针也会同步走，而不是停在原处）。
                            val f = draggingFraction ?: run {
                                // 分母用外部给的稳定总长，不是 scroll.maxValue。
                                val max = span
                                if (max <= 0f) 0f
                                else (scroll.value.toFloat() / max).coerceIn(0f, 1f)
                            }
                            // 再减掉指针自身半个宽度：轨道位置指的是**指针中心**，
                            // 而 translationX 移的是它的左边缘。
                            translationX = 12.dp.toPx() + usablePx * f - 1.5.dp.toPx()
                        }
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        // 去留那边指针是白的；这里保留一点绿色，作为全 App 的强调色。
                        .background(AccentKeep),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        DayCircleButton(
            icon = UndoIcon,
            contentDescription = "撤销删除",
            enabled = canUndo,
            onClick = onUndoDelete,
        )
    }
}

/**
 * 「回到那天」里统一的圆形图标按钮：**深色圆底 + 图标**。
 *
 * 参照「去留」截屏：顶栏（‹ / ≡ / ✓）和底栏（⟲ / ↩）都是这种圆按钮 ——
 * 白图标压在半透明深色圆底上，不论底下是黑是白都看得清。
 * 禁用时压暗而不是隐藏，位置感不能丢。
 */
@Composable
internal fun DayCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    /** true = 图标用强调色（如"确认"可用时）。 */
    highlight: Boolean = false,
) {
    Box(
        modifier = Modifier
            // 48dp：和 RoundActionButton 保持一致（Material 最小触摸目标）
            .size(48.dp)
            // 浅色下加一点阴影，和整理页的圆钮同一套观感
            .overlayShadow(CircleShape)
            .clip(CircleShape)
            // 和整理照片页左下红心 / 右下五星**完全同一套**（`StarButton`）：
            // 48dp 圆 + 黑 40% 半透明底 + 1dp 极淡的白描边 + 22dp 图标。
            // 上一版改成"白 12% 浅圆"是为了解决"看不见"，但对比之下确实过亮了 ——
            // 真正要的是这一档：圈看得见，但整体重量压在照片之下。
            .background(OverlaySoft)
            .border(1.dp, OverlayBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> TextTertiary.copy(alpha = 0.45f)
                highlight -> AccentKeep
                else -> TextPrimary
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 刻度层 —— **静止的轨道**。
 *
 * 等间距短竖线平铺，**不随任何东西平移**。它就是一把尺子：
 * 位置固定，用户才有稳定的空间参照，读数（指针）才可信。
 *
 * **不画任何底色** —— 只画刻度线本身，让背景（Ink 深色）透出来。
 *
 * 用 `Canvas` 一次画完而不是摆一堆 `Box`：一天可能几百张刻度，
 * 摆 Box 会在组合阶段就开销掉；画布只是绘制，几乎零成本。
 */
@Composable
internal fun Ticks() {
    // 刻度再提亮一档：Surface3 → TextTertiary → **TextSecondary**。
    // 用户两次反馈"太暗、看不清刻度" —— 它在纯黑底上本来就是最弱的一层，
    // 弱到这个程度就不叫内敛，叫看不见。
    val tickColor = TextSecondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        // 刻度更密更短 —— 参照「去留」的胶囊尺：8dp 一根太稀，
        // 5dp 才是那边"密而细"的观感。
        val gap = 5.dp.toPx()
        val inset = 12.dp.toPx()
        val usableWidth = (size.width - inset * 2).coerceAtLeast(0f)
        val tickH = 7.dp.toPx()
        var x = inset
        var i = 0
        while (x <= inset + usableWidth + 0.5f) {
            drawLine(
                color = tickColor,
                start = Offset(x, size.height / 2f - tickH / 2f),
                end = Offset(x, size.height / 2f + tickH / 2f),
                strokeWidth = 1.dp.toPx(),
            )
            x += gap
            i++
            if (i > 600) break
        }
    }
}

/**
 * 顶部：返回 / 居中「回到那天」+ 大号日期 / 确认。
 *
 * 两行标题：日期是这一屏真正的主语，做大字；
 * "回到那天"四个字只解释"这一屏为什么存在"，小一档跟在上面。
 */
@Composable
internal fun DayViewHeader(
    items: List<MediaItem>?,
    kindLabel: String,
    dayStart: Long,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            enabled = true,
            onClick = onBack,
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                // 范围不再写在这里 —— 换成"滑到哪天、日期就跟到哪天"，
                // 比一句静态说明准确得多（用户要求）。
                text = "回到那天",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // 「去留」的日期写法：2023/1/19 —— 斜杠、不带前导零。
                // 比"2023年1月19日"短一截，大字下更稳；也比 ISO 的
                // "2023-01-19"更像给人看的日期。
                text = formatDayTitle(dayStart),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }

        val ready = items?.isNotEmpty() == true
        DayCircleButton(
            icon = Icons.Filled.Check,
            contentDescription = "在这一天开一组整理",
            enabled = ready,
            onClick = onConfirm,
        )
    }
}
