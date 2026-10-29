package com.qushe.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘图标。
 *
 * `material-icons-core` 只有 45 个图标，且**只有实心版**；空心版全在
 * `material-icons-extended` 里 —— 为了几个轮廓图标引入那个库，会让 debug 包
 * 明显变大（release 会被 R8 裁掉，但日常装机的是 debug）。
 *
 * 所以按同一套规格自己画：24×24 画布、1.8f 线宽、圆角端点与拐角。
 * 四个图标出自同一只手，粗细和圆角才真正一致 —— Material 的图标是不同年代画的，
 * 混在一起本来就不齐。
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

// ---------------------------------------------------------------------------
// 底部导航：房子 / 回收站 / 滑杆
//
// 这三枚的形状有圆弧和圆角，用 `path { lineTo… }` 一个个写参数太容易手误，
// 所以改用 SVG 路径字符串（和上面两颗星的坐标系一致：中心 12，直径约 19）。
// ---------------------------------------------------------------------------

private const val NAV_STROKE = 1.8f

/** 用 SVG 路径造图标。fill=true 填色，否则按线描。 */
private fun navIcon(name: String, d: String, fill: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = if (fill) SolidColor(Color.Black) else null,
            stroke = if (fill) null else SolidColor(Color.Black),
            strokeLineWidth = NAV_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            // 房子填色时要把门挖成洞：NonZero 下两个同向子路径只会并集，
            // EvenOdd 才会让重叠的那块变成空的。
            pathFillType = if (fill) PathFillType.EvenOdd else PathFillType.NonZero,
            name = name,
        )
    }.build()

private const val HOME_ROOF =
    "M2.9,10.4 L12,2.9 L21.1,10.4 V19.6 A0.9,0.9 0 0 1 20.2,20.5 " +
        "H3.8 A0.9,0.9 0 0 1 2.9,19.6 Z"

private const val HOME_DOOR = "M9.2,20.5 V14.6 H14.8 V20.5"

/** 首页：屋顶 + 一扇门。门只画三边，底下贴地，所以看着是"进去"的口。 */
val HomeIcon: ImageVector by lazy {
    navIcon("Home", "$HOME_ROOF $HOME_DOOR")
}

/** 首页选中态：同一个屋顶填满，门用 EvenOdd 挖空。 */
val HomeFilledIcon: ImageVector by lazy {
    navIcon("HomeFilled", "$HOME_ROOF $HOME_DOOR Z", fill = true)
}

private const val TRASH_BODY =
    // 左下角 sweep=0（向外凸的圆弧只有 1/4 圈）、右下角 sweep=1。
    // 两个都写 0 的话右下角会向内凹，桶底像被咬掉一块。
    "M6.0,6.4 L6.9,19.4 A1.2,1.2 0 0 0 8.1,20.5 H15.9 " +
        "A1.2,1.2 0 0 1 17.1,19.4 L18.0,6.4"

/** 回收站：盖、提手、桶身、两道竖纹。 */
val TrashIcon: ImageVector by lazy {
    navIcon(
        "Trash",
        "M3.6,6.4 H20.4 " +
            "M9.3,6.4 V5 A1.2,1.2 0 0 1 10.5,3.8 H13.5 A1.2,1.2 0 0 1 14.7,5 V6.4 " +
            TRASH_BODY +
            " M10.2,10.4 V16.4 M13.8,10.4 V16.4",
    )
}

/** 回收站选中态：盖和桶身填实，竖纹不再画（填实后看不出）。 */
val TrashFilledIcon: ImageVector by lazy {
    navIcon(
        "TrashFilled",
        "M3.6,5.4 H20.4 V7.4 H3.6 Z " +
            "M9.3,6.4 V5 A1.2,1.2 0 0 1 10.5,3.8 H13.5 A1.2,1.2 0 0 1 14.7,5 V6.4 Z " +
            "$TRASH_BODY Z",
        fill = true,
    )
}

private const val TUNE_LINES =
    "M3.2,7.4 H6.2 M11.8,7.4 H20.8 M3.2,16.6 H10.2 M15.8,16.6 H20.8"

private const val TUNE_RINGS =
    "M9,4.6 A2.8,2.8 0 1 1 9,10.2 A2.8,2.8 0 1 1 9,4.6 Z " +
        "M13,13.8 A2.8,2.8 0 1 1 13,19.4 A2.8,2.8 0 1 1 13,13.8 Z"

/**
 * 设置：两根滑杆。
 *
 * 不用齿轮：齿轮的轮廓要画八个齿 + 一个内圈，在 24dp 下要么糊、要么像太阳。
 * 两根滑杆既好认（就是"调节"），也和另外三枚的线条密度接近。
 */
val TuneIcon: ImageVector by lazy { navIcon("Tune", "$TUNE_LINES $TUNE_RINGS") }

/** 设置选中态：旋钮由空心圈变实心点。 */
val TuneFilledIcon: ImageVector by lazy {
    navIcon(
        "TuneFilled",
        "$TUNE_LINES " +
            "M9,7.4 m-2.8,0 a2.8,2.8 0 1 0 5.6,0 a2.8,2.8 0 1 0 -5.6,0 Z " +
            "M13,16.6 m-2.8,0 a2.8,2.8 0 1 0 5.6,0 a2.8,2.8 0 1 0 -5.6,0 Z",
        fill = true,
    )
}
