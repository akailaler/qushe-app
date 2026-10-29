package com.qushe.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.qushe.app.core.Prefs
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 一套配色。
 *
 * ---- 为什么颜色不做成常量 ----
 *
 * 这里原来是十来个 `val Ink = Color(...)` 的顶层常量，全工程约 350 处直接引用，
 * 其中相当一部分在 `Canvas` / `drawBehind` / `graphicsLayer` 这种**非 Composable**
 * 的 lambda 里 —— 也就是说不能简单改成 `@Composable get()`。
 *
 * 所以做成"一份可切换的调色板"：常量名保持不变，只是背后读的是
 * [QusheColors]，切主题时整个 App 一起换色，调用点一处都不用动。
 */
data class QushePalette(
    val ink: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentKeep: Color,
    val accentDelete: Color,
    val accentInfo: Color,
    val accentPurple: Color,
    val accentFolder: Color,
    /** 浮在内容上的控件底：深色主题是黑 40%，浅色主题是白 40%。 */
    val overlaySoft: Color,
    /** 更实的浮层（胶囊、按钮）：黑 70% / 白 70%。 */
    val overlayStrong: Color,
    /** 顶部/底部渐变，压在内容与页面之间：黑 60% / 白 60%。 */
    val overlayFade: Color,
    /** 几乎不透明的浮层（底栏渐变收口）：黑 95% / 白 95%。 */
    val overlaySolid: Color,
    /** 浮层的描边：白 15% / 黑 15%。 */
    val overlayBorder: Color,
    /** 顶部渐变压暗（整理页顶栏）。浅色主题下是**透明**。 */
    val scrimTop: Color,
    /** 底部渐变压暗（整理页底栏）。浅色主题下是**透明**。 */
    val scrimBottom: Color,
    /** 这一套是深色还是浅色 —— 系统栏图标、模糊兜底要用。 */
    val isDark: Boolean,
)

// 1.7.0 起不再手写"默认那套" —— 浅色五套、深色五套全部由 [skinPalette] 推导。
//
// 原来默认那套是一份手抄的 `QushePalette`，浮层透明度用的 0xBFFFFFFF（白 75%），
// 而其他皮肤推导出来是 78% —— 差 3% 看不出来，但意味着**同一个规则写在两处**。
// 现在只有一条规则，改一处十套一起变。

/**
 * 一套皮肤 = 一份调色板 + 一个名字。
 *
 * ---- 为什么浅色和深色是**两张表** ----
 *
 * 1.6.0 之前是"一个皮肤含浅/深两版"，切深浅时色相不变。但这两件事其实不平行：
 * 白天要的是一张纸（底色亮、字压深），夜里要的是**暗室里的一束光**
 * （底色几乎黑但带色相，主色自己会亮）。把深色做成浅色的"压暗版"，
 * 十套里总有一半是凑数的。
 *
 * 所以拆成两张表：各自五套、各选各的。跟随系统时两套都会用到，都要选。
 *
 * ---- 名字的来源 ----
 *
 * 按奔驰车内氛围灯的命名法：一个地名/场景 + 一个色（普罗旺斯紫、波罗的海蓝）。
 * 四字，读起来像一个"有光的房间"，而不是一个色号。
 */
data class QusheSkin(
    val id: Int,
    val name: String,
    val palette: QushePalette,
)

/**
 * 由"种子色"推出一整套调色板。
 *
 * 只让每套皮肤定义 11 个真正决定观感的颜色（底色、卡片、主色、删除色、
 * 三种文字色、三个分类色），其余全部按规则推导：
 *  · 卡片二/三级 = 卡片色与文字色按比例混合（深色往上提、浅色往下压）；
 *  · 浮层 = 深色用半透明黑、浅色用半透明卡片色（沿用默认皮肤的做法）；
 *  · 渐变遮罩 = 深色用底色带透明度、浅色**透明**（浅色下不要那层雾）。
 *
 * 这样加一套皮肤只要写 11 行，而且不会出现"某套的卡片色忘了调"这种漂移。
 *
 * ---- 浅色浮层为什么是"半透卡片"而不是模糊 ----
 * 试过真模糊（把照片再画一份糊掉），两个问题：
 *  · 背后是白/浅色图时，糊完还是白的，再叠一层色就"发灰、脏"；
 *  · Android 12 以下根本没有模糊，同一套 UI 在旧机上退化成死白块。
 * 现在走"通透卡片"：卡片色 78% + 正文色 8% 描边 + 极轻阴影（阴影在组件上加）。
 * 识别度靠**描边**兜底 —— 这是浅色大图上最关键的一条。
 * 深色那边不用这套：半透明黑压在照片上本来就有层次，不需要描边和阴影。
 */
private fun skinPalette(
    dark: Boolean,
    bg: Color,
    surface: Color,
    main: Color,
    delete: Color,
    info: Color,
    purple: Color,
    folder: Color,
    textPrimary: Color,
    textSecondary: Color,
    textTertiary: Color,
): QushePalette {
    val surface2 = lerp(surface, textPrimary, if (dark) 0.06f else 0.05f)
    val surface3 = lerp(surface, textPrimary, if (dark) 0.13f else 0.11f)
    return if (dark) {
        QushePalette(
            ink = bg,
            surface1 = surface,
            surface2 = surface2,
            surface3 = surface3,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textTertiary = textTertiary,
            accentKeep = main,
            accentDelete = delete,
            accentInfo = info,
            accentPurple = purple,
            accentFolder = folder,
            overlaySoft = Color(0x66000000),
            overlayStrong = Color(0xB3000000),
            overlayFade = Color(0x99000000),
            overlaySolid = Color(0xF2000000),
            overlayBorder = Color(0x26FFFFFF),
            scrimTop = bg.copy(alpha = 0.80f),
            scrimBottom = bg.copy(alpha = 0.90f),
            isDark = true,
        )
    } else {
        QushePalette(
            ink = bg,
            surface1 = surface,
            surface2 = surface2,
            surface3 = surface3,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textTertiary = textTertiary,
            accentKeep = main,
            accentDelete = delete,
            accentInfo = info,
            accentPurple = purple,
            accentFolder = folder,
            overlaySoft = surface.copy(alpha = 0.78f),
            overlayStrong = surface.copy(alpha = 0.82f),
            overlayFade = Color.Transparent,
            overlaySolid = Color.Transparent,
            overlayBorder = textPrimary.copy(alpha = 0.08f),
            scrimTop = Color.Transparent,
            scrimBottom = Color.Transparent,
            isDark = false,
        )
    }
}

/**
 * 浅色五套。
 *
 * 主色全部压到"对底色 ≥4.5:1" —— `AccentKeep` 不只是色块，底部导航的选中标签
 * （12sp）、星形图标、"已收藏"数字都用它上色，浅底上一淡就读不清。
 *
 * 分类色（信息/紫/文件夹）故意**跨色系**：首页四张卡并排，它们必须互相分得开，
 * 所以只压低饱和度服从整体调子，不把色相也统一掉。删除色永远是红（有语义）。
 */
val LightSkins: List<QusheSkin> = listOf(
    QusheSkin(
        id = 0,
        name = "月石初白",
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF7F5F1),
            surface = Color(0xFFFDFBF7),
            main = Color(0xFF6E6459),
            delete = Color(0xFFA2605B),
            info = Color(0xFF5B7A93),
            purple = Color(0xFF7C6E92),
            folder = Color(0xFF8F7340),
            textPrimary = Color(0xFF2E2A24),
            textSecondary = Color(0xFF6B655C),
            textTertiary = Color(0xFF8C857A),
        ),
    ),
    QusheSkin(
        id = 1,
        name = "波罗的海",
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF1F5F8),
            surface = Color(0xFFF9FBFD),
            main = Color(0xFF4F7288),
            delete = Color(0xFFA0665F),
            info = Color(0xFF46789F),
            purple = Color(0xFF77789F),
            folder = Color(0xFF8B7345),
            textPrimary = Color(0xFF2A343C),
            textSecondary = Color(0xFF5F6C76),
            textTertiary = Color(0xFF7E8A94),
        ),
    ),
    QusheSkin(
        id = 2,
        name = "亚马逊晨",
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF3F6EF),
            surface = Color(0xFFFAFCF6),
            main = Color(0xFF5A7050),
            delete = Color(0xFF9A6058),
            info = Color(0xFF5E7C8C),
            purple = Color(0xFF83799A),
            folder = Color(0xFF8E7342),
            textPrimary = Color(0xFF2C3328),
            textSecondary = Color(0xFF66705F),
            textTertiary = Color(0xFF838C7B),
        ),
    ),
    QusheSkin(
        id = 3,
        name = "普罗旺斯",
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF7F4F8),
            surface = Color(0xFFFCFAFD),
            main = Color(0xFF75648C),
            delete = Color(0xFF9F6470),
            info = Color(0xFF63799A),
            purple = Color(0xFF6B6BA0),
            folder = Color(0xFF91744A),
            textPrimary = Color(0xFF322C38),
            textSecondary = Color(0xFF6F6579),
            textTertiary = Color(0xFF8C8196),
        ),
    ),
    QusheSkin(
        id = 4,
        name = "托斯卡纳",
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFFBF3E9),
            surface = Color(0xFFFEF9F2),
            main = Color(0xFF96603C),
            delete = Color(0xFFA2544A),
            info = Color(0xFF5E7A8A),
            purple = Color(0xFF86708C),
            folder = Color(0xFF8A7340),
            textPrimary = Color(0xFF33291F),
            textSecondary = Color(0xFF726353),
            textTertiary = Color(0xFF91826E),
        ),
    ),
)

/**
 * 深色五套 —— 这才是"氛围灯"该在的地方。
 *
 * 底色都是**极深但带色相**（不是纯黑）：纯黑会把照片衬得发脏，也让色相没处着落。
 * 主色反过来比浅色版更亮 —— 夜里那一束光要自己发光，而不是白天的墨。
 *
 * 五套分别是：中性暖白（石墨之夜）、蓝（午夜蓝调）、紫（紫罗兰夜）、
 * 绿（森林晨雾）、酒红衬暖金（勃艮第红）。
 */
val DarkSkins: List<QusheSkin> = listOf(
    QusheSkin(
        id = 0,
        name = "石墨之夜",
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF131316),
            surface = Color(0xFF1D1D21),
            main = Color(0xFFC9C3B8),
            delete = Color(0xFFE08A94),
            info = Color(0xFF8FB4D6),
            purple = Color(0xFFB5AECD),
            folder = Color(0xFFD0AE78),
            textPrimary = Color(0xFFF0EEEA),
            textSecondary = Color(0xFFA09C97),
            textTertiary = Color(0xFF6A6663),
        ),
    ),
    QusheSkin(
        id = 1,
        name = "午夜蓝调",
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF101620),
            surface = Color(0xFF1A2230),
            main = Color(0xFFA8C8E8),
            delete = Color(0xFFE0919A),
            info = Color(0xFF7FAEDA),
            purple = Color(0xFFA8A9D6),
            folder = Color(0xFFC8A878),
            textPrimary = Color(0xFFE6ECF4),
            textSecondary = Color(0xFF9BA8B8),
            textTertiary = Color(0xFF66737F),
        ),
    ),
    QusheSkin(
        id = 2,
        name = "紫罗兰夜",
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF17121F),
            surface = Color(0xFF221A2D),
            main = Color(0xFFB49BE0),
            delete = Color(0xFFE094A6),
            info = Color(0xFF8FAFD6),
            purple = Color(0xFFD896BE),
            folder = Color(0xFFC9A878),
            textPrimary = Color(0xFFEDE7F5),
            textSecondary = Color(0xFFA79CB8),
            textTertiary = Color(0xFF6F6580),
        ),
    ),
    QusheSkin(
        id = 3,
        name = "森林晨雾",
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF111A17),
            surface = Color(0xFF1B2620),
            main = Color(0xFF9CC0A8),
            delete = Color(0xFFDD9A92),
            info = Color(0xFF8FB0B8),
            purple = Color(0xFFAFAAC6),
            folder = Color(0xFFC6A874),
            textPrimary = Color(0xFFE4EDE7),
            textSecondary = Color(0xFF9DAFA5),
            textTertiary = Color(0xFF697A71),
        ),
    ),
    QusheSkin(
        id = 4,
        name = "勃艮第红",
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF1E1214),
            surface = Color(0xFF2A1B1D),
            main = Color(0xFFD6B78F),
            delete = Color(0xFFE28C93),
            info = Color(0xFF93AABB),
            purple = Color(0xFFBBA3C0),
            folder = Color(0xFFC9A470),
            textPrimary = Color(0xFFF2E7E6),
            textSecondary = Color(0xFFB3A1A1),
            textTertiary = Color(0xFF7C6868),
        ),
    ),
)

/** 按 id 取浅色皮肤；越界回落到第一套（存量数据被改坏也不会崩）。 */
fun lightSkinOf(id: Int): QusheSkin = LightSkins.firstOrNull { it.id == id } ?: LightSkins.first()

/** 按 id 取深色皮肤。 */
fun darkSkinOf(id: Int): QusheSkin = DarkSkins.firstOrNull { it.id == id } ?: DarkSkins.first()

/**
 * 当前调色板。
 *
 * 用一个全局的快照状态：切主题时，读过这些颜色的 Composable 会重组、
 * `Canvas` 那类绘制 lambda 会重绘 —— 不用把颜色一层层往下传。
 */
object QusheColors {
    var palette by mutableStateOf(DarkSkins.first().palette)

    /**
     * 当前选中的**深色**皮肤。
     *
     * 不只是给深色模式用：[ForceDarkPalette] 也需要它 —— 浅色模式下进视频页/预览页
     * 会被强制深色，那时要用"用户选的那套深色"，而不是固定一套默认的。
     * 否则选了「紫罗兰夜」的人一进视频页就掉回石墨灰。
     */
    var darkSkin by mutableStateOf(DarkSkins.first())
}

/** 收藏 / 保留。 */
val AccentKeep: Color get() = QusheColors.palette.accentKeep

/** 删除。 */
val AccentDelete: Color get() = QusheColors.palette.accentDelete

/** 中性强调色。 */
val AccentInfo: Color get() = QusheColors.palette.accentInfo

/** 截屏分类的强调色。 */
val AccentPurple: Color get() = QusheColors.palette.accentPurple

/**
 * 「按文件夹挑」用的强调色。
 *
 * 前四个色已经和内容类型绑定了（绿=照片、紫=截屏、蓝=视频、红=删除）。
 * 文件夹不是"另一类内容"，而是"另一种抽取方式"，所以给它一个独立的色 ——
 * 复用上面任何一个都会和已有含义打架。
 */
val AccentFolder: Color get() = QusheColors.palette.accentFolder

val Ink: Color get() = QusheColors.palette.ink
val Surface1: Color get() = QusheColors.palette.surface1
val Surface2: Color get() = QusheColors.palette.surface2
val Surface3: Color get() = QusheColors.palette.surface3
val TextPrimary: Color get() = QusheColors.palette.textPrimary
val TextSecondary: Color get() = QusheColors.palette.textSecondary
val TextTertiary: Color get() = QusheColors.palette.textTertiary

/**
 * 贴在照片上的角标（视频时长 / 截屏 / 长图 / 回收站天数）。
 *
 * **刻意不跟主题翻**：底永远是压暗的黑，字永远是浅色 —— 底图是用户的照片，
 * 颜色不可控，深底浅字在任何照片上都读得出来。
 * 之前字用的是 `TextPrimary`，浅色模式下变成近黑，压在黑底上就看不见了
 * （用户报"回收站缩略图右下角角标颜色有点深看不清"）。
 */
val BadgeScrim = Color(0xB3000000)
val BadgeText = Color(0xFFF2F2F5)
val BadgeAccent = Color(0xFF8FC3FF)
/** 角标里的紧急色（回收站快到期）。压深底用，不跟主题翻。 */
val BadgeAlert = Color(0xFFFF8A9B)

val OverlaySoft: Color get() = QusheColors.palette.overlaySoft
val OverlayStrong: Color get() = QusheColors.palette.overlayStrong
val OverlayFade: Color get() = QusheColors.palette.overlayFade
val OverlaySolid: Color get() = QusheColors.palette.overlaySolid
val OverlayBorder: Color get() = QusheColors.palette.overlayBorder
val ScrimTop: Color get() = QusheColors.palette.scrimTop
val ScrimBottom: Color get() = QusheColors.palette.scrimBottom

private fun schemeOf(p: QushePalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = p.accentKeep,
        onPrimary = Color(0xFF04211A),
        secondary = p.accentInfo,
        onSecondary = Color(0xFF03182E),
        tertiary = p.accentDelete,
        background = p.ink,
        onBackground = p.textPrimary,
        surface = p.surface1,
        onSurface = p.textPrimary,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.textSecondary,
        outline = p.surface3,
        error = p.accentDelete,
        onError = Color(0xFF2A0409),
    )
} else {
    lightColorScheme(
        primary = p.accentKeep,
        onPrimary = Color.White,
        secondary = p.accentInfo,
        onSecondary = Color.White,
        tertiary = p.accentDelete,
        background = p.ink,
        onBackground = p.textPrimary,
        surface = p.surface1,
        onSurface = p.textPrimary,
        surfaceVariant = p.surface2,
        onSurfaceVariant = p.textSecondary,
        outline = p.surface3,
        error = p.accentDelete,
        onError = Color.White,
    )
}

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

/**
 * 强制这一块内容用深色。
 *
 * 视频整理页、图片/视频预览都是"看内容"的场景：周围一圈应该是暗的，
 * 浅色模式下把底色刷成浅灰反而让画面像贴在海报板上。用户也明确要求
 * "视频相关的页面都回归深色，浅色只留在收藏页的缩略图列表"。
 *
 * ---- 为什么用全局调色板而不是 CompositionLocal ----
 * 颜色常量（`Ink` / `TextPrimary`…）是**普通 getter**，会被 `Canvas`、
 * `drawBehind` 这类非 Composable 的地方读到 —— CompositionLocal 在那些地方读不到。
 * 所以这里直接改全局的那一份，靠 `DisposableEffect` 在离开时还原。
 * [QusheTheme] 也配合改成"只在模式变化时写"，否则每次重组都会把它顶回去。
 */
@Composable
fun ForceDarkPalette(content: @Composable () -> Unit) {
    val previous = QusheColors.palette
    val dark = QusheColors.darkSkin.palette
    if (QusheColors.palette !== dark) QusheColors.palette = dark
    DisposableEffect(Unit) {
        onDispose { QusheColors.palette = previous }
    }
    content()
}

/**
 * 浮层的极轻阴影（只有浅色主题有）。
 *
 * 浅色下"半透白卡片"压在照片上，如果四周没有一点点暗边，
 * 会像是图片本身的一块白斑 —— 2dp 的淡阴影刚好把它"抬起来"。
 * 深色主题返回原样：那套半透明黑本来就不需要阴影。
 */
@Composable
fun Modifier.overlayShadow(shape: Shape, elevation: androidx.compose.ui.unit.Dp = 2.dp): Modifier =
    if (QusheColors.palette.isDark) {
        this
    } else {
        shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.12f),
        )
    }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * @param mode 见 `Prefs.THEME_SYSTEM / THEME_LIGHT / THEME_DARK`。
 * @param lightSkinId 浅色皮肤 id（见 [LightSkins]）。
 * @param darkSkinId 深色皮肤 id（见 [DarkSkins]）。
 *
 * 两个 id 都传：跟随系统时白天和夜里都会用到，所以两套都得记住。
 */
@Composable
fun QusheTheme(
    mode: Int = Prefs.THEME_DARK,
    lightSkinId: Int = 0,
    darkSkinId: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        Prefs.THEME_LIGHT -> false
        Prefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val lightSkin = lightSkinOf(lightSkinId)
    val darkSkin = darkSkinOf(darkSkinId)
    val palette = if (dark) darkSkin.palette else lightSkin.palette

    // ---- 只在"模式真的变了"时写一次 ----
    //
    // 不能每次重组都写：视频页 / 预览页会临时把调色板设成深色
    // （见 [ForceDarkPalette]），这里一写就把人家的覆盖顶回去了。
    // 返回 palette 而不是 Unit：`remember { ... }` 返回 Unit 会被 lint 判为错误
    remember(mode, dark, lightSkinId, darkSkinId) {
        // 深色皮肤**无论当前什么模式**都要同步：浅色模式下进视频页要用它。
        QusheColors.darkSkin = darkSkin
        QusheColors.palette = palette
        palette
    }

    // 系统栏图标跟着**当前调色板**走，而不是跟着主题模式 ——
    // 看视频时被强制深色，图标也得是浅的。
    val current = QusheColors.palette
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !current.isDark
                isAppearanceLightNavigationBars = !current.isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = schemeOf(palette, dark),
        typography = QusheTypography,
        content = content,
    )
}
