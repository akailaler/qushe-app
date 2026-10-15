package com.qushe.app.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 圆角占屏宽的比例。 */
private const val CORNER_RATIO = 0.023f

/** 圆角上限（大屏上不再跟着放大）。 */
private val CORNER_MAX = 14.dp

/**
 * 内容圆角：按屏宽算，瀑布流的格子和整理照片页的单张大图**共用这一套**。
 *
 * 照片页原来是没有圆角的。后果是左右滑的时候，整块**方角**的图连着它那片
 * 黑色底一起平移 —— 观感很硬（用户："整理照片左右滑动时会有黑色背景一起滑动，
 * 不太好看，给图片加上圆角边框就像瀑布流里的那样"）。
 *
 * 抽成共享函数而不是两处各写一遍：这两个地方的圆角本来就应该看起来是同一个东西，
 * 写两份迟早会漂移。
 */
@Composable
fun rememberContentCorner(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return remember(screenWidth) {
        (screenWidth * CORNER_RATIO).coerceAtMost(CORNER_MAX)
    }
}

/** [rememberContentCorner] 的形状版（共享同一个实例，别在格子里逐帧现造）。 */
@Composable
fun rememberContentShape(): RoundedCornerShape {
    val corner = rememberContentCorner()
    return remember(corner) { RoundedCornerShape(corner) }
}

/**
 * 照片与照片之间的间隙（「回到那天」的格子、整理页翻页时的接缝共用同一个值）。
 *
 * 提到这里是因为整理页翻页时两张图贴在一起"有点挤"（用户反馈），
 * 而合适的宽度已经有了一个 tuned 好的算法 —— 宁可复用它，
 * 也不要另写一个数字让两边慢慢漂移。
 */
private const val CELL_GAP_RATIO = 0.022f
private val CELL_GAP_RANGE = 4.dp..14.dp

/** 共享的图间间隙。 */
@Composable
fun rememberPageGap(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return remember(screenWidth) { (screenWidth * CELL_GAP_RATIO).coerceIn(CELL_GAP_RANGE) }
}
