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
