package com.qushe.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 音量图标。
 *
 * Material 的 `material-icons-core` 里没有音量相关图标（core 一共只有 45 个图标，
 * 已核对过），而为了两个图标引入 `material-icons-extended`（几千个图标类）
 * 会让 debug 包体明显变大 —— 对一个只需要"有声/静音"两态的应用不值得。
 * 所以这里自己画，图形也更可控。
 *
 * 造型：左侧小矩形 + 向右张开的锥形（喇叭本体），
 * 有声时右侧两道渐远的声波，静音时改成一个叉。
 * 颜色统一用黑色，实际显示时由 `Icon` 的 tint 整体覆盖。
 */
private fun buildVolumeIcon(muted: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (muted) "VolumeOff" else "VolumeUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // 喇叭本体
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f)
            lineTo(7f, 9f)
            lineTo(12f, 4f)
            lineTo(12f, 20f)
            lineTo(7f, 15f)
            lineTo(3f, 15f)
            close()
        }

        if (muted) {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15.5f, 9.5f)
                lineTo(20.5f, 14.5f)
                moveTo(20.5f, 9.5f)
                lineTo(15.5f, 14.5f)
            }
        } else {
            // 两道声波。这里刻意用折线而不是贝塞尔曲线：
            // 当前版本的 PathBuilder 并没有 cubicTo / quadraticTo 这类方法
            // （已用 javap 核对过），而圆头圆角的粗描边在小尺寸下
            // 本来就会把折线"糊"成平滑的弧，视觉效果一样。
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(14.6f, 9.6f)
                lineTo(16.2f, 10.9f)
                lineTo(16.2f, 13.1f)
                lineTo(14.6f, 14.4f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(17.6f, 7.6f)
                lineTo(19.9f, 9.6f)
                lineTo(19.9f, 14.4f)
                lineTo(17.6f, 16.4f)
            }
        }
    }.build()

val VolumeUpIcon: ImageVector by lazy { buildVolumeIcon(muted = false) }

val VolumeOffIcon: ImageVector by lazy { buildVolumeIcon(muted = true) }
