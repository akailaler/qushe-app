package com.qushe.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 全屏 / 退出全屏图标。
 *
 * `material-icons-core` 里没有这类图标（core 一共只有 45 个，已核对），
 * 为两个图标引入 `material-icons-extended` 会让包体明显变大，所以自己画。
 *
 * 造型是最容易认出来的「四角括号」：进入全屏时括号朝外张开，退出时朝内收拢。
 * 只用直线段就能画清楚，不需要弧形。
 */

private fun buildFullscreenIcon(exit: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (exit) "FullscreenExit" else "Fullscreen",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 括号的每条边都是一段直角折线，四个角共用一个画法
        val stroke = SolidColor(Color.Black)
        if (exit) {
            // 朝内收拢
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 9f); lineTo(9f, 9f); lineTo(9f, 4f)
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(15f, 4f); lineTo(15f, 9f); lineTo(20f, 9f)
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(20f, 15f); lineTo(15f, 15f); lineTo(15f, 20f)
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(9f, 20f); lineTo(9f, 15f); lineTo(4f, 15f)
            }
        } else {
            // 朝外张开
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 10f); lineTo(4f, 4f); lineTo(10f, 4f)
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f)
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f)
            }
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(10f, 20f); lineTo(4f, 20f); lineTo(4f, 14f)
            }
        }
    }.build()

val FullscreenIcon: ImageVector by lazy { buildFullscreenIcon(exit = false) }

val FullscreenExitIcon: ImageVector by lazy { buildFullscreenIcon(exit = true) }
