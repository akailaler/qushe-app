package com.qushe.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 空心五角星。
 *
 * `material-icons-core` 里有 `Icons.Filled.Star`，但**没有空心版**
 * （core 一共只有 45 个图标，已核对）。为这一个图标引入
 * `material-icons-extended` 会让包体明显变大，所以自己画。
 *
 * 「喜欢」用心形、「收藏」用星形，两个状态的形态必须能一眼区分：
 * 心形那边是 Filled / FavoriteBorder，星形这边就得配一个描边版，
 * 否则"没收藏"和"已收藏"只能靠颜色分辨，色弱用户会分不出来。
 *
 * 顶点坐标按外径 10、内径 4、中心 (12, 12) 算出来的，五角星的十个顶点。
 */
/**
 * 实心星形。
 *
 * **顶点和 [StarBorderIcon] 完全一样**，只是描边换成填充。
 *
 * 之前用的是 Material 自带的 `Icons.Filled.Star`：它在 24dp 画布里
 * 只占约 20dp（四周留白更多），换到"已收藏"状态时视觉上**缩了一圈**
 * （用户："实心的比空心的小一圈"）。自己画同一组顶点，大小就一致了。
 */
val StarFilledIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StarFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 同色描边：空心那颗的轮廓是"顶点 + 半个描边"，填充这颗如果只到顶点，
        // 视觉直径会小 1.8f（用户："实心的还是小一圈"）。加同样宽度、同样颜色的
        // 描边，两者的外沿就完全重合了。
        path(
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2f)
            lineTo(14.35f, 8.76f)
            lineTo(21.51f, 8.91f)
            lineTo(15.80f, 13.24f)
            lineTo(17.88f, 20.09f)
            lineTo(12f, 16f)
            lineTo(6.12f, 20.09f)
            lineTo(8.20f, 13.24f)
            lineTo(2.49f, 8.91f)
            lineTo(9.65f, 8.76f)
            close()
        }
    }.build()
}

val StarBorderIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StarBorder",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        path(
            stroke = stroke,
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 2f)
            lineTo(14.35f, 8.76f)
            lineTo(21.51f, 8.91f)
            lineTo(15.80f, 13.24f)
            lineTo(17.88f, 20.09f)
            lineTo(12f, 16f)
            lineTo(6.12f, 20.09f)
            lineTo(8.20f, 13.24f)
            lineTo(2.49f, 8.91f)
            lineTo(9.65f, 8.76f)
            close()
        }
    }.build()
}
