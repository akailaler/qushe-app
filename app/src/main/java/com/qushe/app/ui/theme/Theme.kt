package com.qushe.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 收藏 / 保留。 */
val AccentKeep = Color(0xFF35D0A5)

/** 删除。 */
val AccentDelete = Color(0xFFFF5C7A)

/** 中性强调色。 */
val AccentInfo = Color(0xFF6BA8FF)

/** 截屏分类的强调色。 */
val AccentPurple = Color(0xFFAFA9EC)

val Ink = Color(0xFF0B0B0D)
val Surface1 = Color(0xFF16161A)
val Surface2 = Color(0xFF1F1F25)
val Surface3 = Color(0xFF2A2A32)
val TextPrimary = Color(0xFFF2F2F5)
val TextSecondary = Color(0xFF9A9AA5)
val TextTertiary = Color(0xFF63636E)

/**
 * 全站强制深色。
 *
 * 这是一个沉浸式回顾媒体内容的应用，深色是这类产品的正确默认值，
 * 也避免浅色主题下在相册里"跳出来"的割裂感。
 */
private val QusheColorScheme = darkColorScheme(
    primary = AccentKeep,
    onPrimary = Color(0xFF04211A),
    secondary = AccentInfo,
    onSecondary = Color(0xFF03182E),
    tertiary = AccentDelete,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = Surface3,
    error = AccentDelete,
    onError = Color(0xFF2A0409),
)

private val QusheTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun QusheTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QusheColorScheme,
        typography = QusheTypography,
        content = content,
    )
}
