package com.qushe.app.ui.common

import androidx.compose.ui.geometry.Rect

/**
 * 几何小工具：**"图在屏幕上实际占哪块"**。
 *
 * ---- 为什么必须只有这一份 ----
 * 这两个函数原来在 `FavoritesScreen.kt` 和 `PhotoSessionScreen.kt` 里**各写了一遍**
 * （逐字相同）。用途是删除动画的起点、"回到那天"时那张图飞入的起始矩形 ——
 * 都需要知道图片实际绘制的那块区域，而不是整个容器。
 *
 * 两份实现的危险在于：哪天只改了其中一处（比如加个最小边距），
 * 另一处就悄悄不一致了。这个工程已经因为"同名函数各写一份"栽过两次
 * （`SegChip`、`allFavoriteKeys()` 的编码写反），不再重复。
 */

/**
 * 算出「按 [ratio]（宽/高）等比缩放到**完整装进** [box]」之后，实际绘制的那块矩形。
 *
 * 结果居中，两侧或上下留出的部分是黑边。
 */
internal fun fitInside(box: Rect, ratio: Float): Rect {
    if (ratio <= 0f || box.width <= 0f || box.height <= 0f) return box
    val w: Float
    val h: Float
    if (box.width / box.height > ratio) {
        // 容器比图更宽 → 高度顶满
        h = box.height
        w = h * ratio
    } else {
        w = box.width
        h = w / ratio
    }
    val left = box.left + (box.width - w) / 2f
    val top = box.top + (box.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

/**
 * 把 [source] 以**中心为基准**缩放 [factor] 倍。
 *
 * 比起 `copy(left = left * factor, ...)` 那种逐边乘的写法，这个明确表达意图：
 * 中心不动，四边对称外扩 / 内收。
 */
internal fun scaledAboutCenter(source: Rect, factor: Float): Rect {
    if (factor <= 0f || factor == 1f) return source
    val cx = source.center.x
    val cy = source.center.y
    val halfW = source.width * factor / 2f
    val halfH = source.height * factor / 2f
    return Rect(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
}
