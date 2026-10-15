package com.qushe.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 「回到那天」底部那两枚圆按钮的图标。
 *
 * `material-icons-core` 一共只有 45 个图标，没有"回到这张照片"和"撤销"这两种，
 * 为它俩引入 `material-icons-extended` 会让包体明显变大 —— 自己画。
 *
 * **这两枚都按「去留」截屏的形状重做过**：
 * 上一版撤销用的是"一段弧 + 两条斜线"拼的描边图形，放大看就是**残缺的半成品**
 * （用户原话"有点不够完整，不好看"）。现在改用标准字形 ——
 * 路径取自 Material 的 refresh / undo（都是**实心填充**，没有描边断口问题）。
 */

private fun builder(name: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
)

/**
 * ◎「回到这张照片」：**准星**（用户从中选的 C 方案 + 一处加强）。
 *
 * 形状：一圈圆环 + 四段短线 + 中心点。
 * **四段短线穿过圆环、环内环外各占一半** —— 就是 FPS 里开镜（ADS）时那种
 * "十字压在准星上"的观感（用户原话"这个效果就和 ad 重叠一样"）。
 *
 * 上一版曾用 Material 的 refresh 字形（顺时针圆箭头），用户要的是准星，改回来。
 */
val RecenterIcon: ImageVector by lazy {
    builder("Recenter").apply {
        val stroke = SolidColor(Color.Black)

        // 圆环：r = 7，圆心 (12,12)。两段半圆拼成整圆，不留缺口。
        path(
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 5f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 19f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12f, 5f)
        }

        // 四段短线：从 r = 3.5 到 r = 10.5，正好把 r = 7 的圆环**穿在中间**。
        path(
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 1.5f); lineTo(12f, 8.5f)
            moveTo(12f, 15.5f); lineTo(12f, 22.5f)
            moveTo(1.5f, 12f); lineTo(8.5f, 12f)
            moveTo(15.5f, 12f); lineTo(22.5f, 12f)
        }

        // 中心点：用"零长度线段 + 圆头笔帽"画圆点 —— 比拼两段弧省事且一定圆。
        path(
            stroke = stroke,
            strokeLineWidth = 2.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 12f)
            lineTo(12f, 12f)
        }
    }.build()
}

/**
 * ↩「撤销删除」：**实心的回弯箭头**（Material undo 的字形）。
 *
 * 一条从右侧回卷到左边的实心带 + 左端一个三角箭头。
 * 实心字形不会像描边那样在细处断掉，小尺寸下也干净。
 */
val UndoIcon: ImageVector by lazy {
    builder("Undo").apply {
        val fill = SolidColor(Color.Black)
        path(fill = fill) {
            // M12.5 8
            moveTo(12.5f, 8f)
            // c-2.65 0-5.05.99-6.9 2.6
            curveToRelative(-2.65f, 0f, -5.05f, 0.99f, -6.9f, 2.6f)
            // L2 7 v9 h9 l-3.62-3.62
            lineTo(2f, 7f)
            lineToRelative(0f, 9f)
            lineToRelative(9f, 0f)
            lineToRelative(-3.62f, -3.62f)
            // c1.39-1.16 3.16-1.88 5.12-1.88
            curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
            // c3.54 0 6.55 2.31 7.6 5.5
            curveToRelative(3.54f, 0f, 6.55f, 2.31f, 7.6f, 5.5f)
            // l2.37-.78 C21.08 11.03 17.15 8 12.5 8 z
            lineToRelative(2.37f, -0.78f)
            curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
            close()
        }
    }.build()
}
