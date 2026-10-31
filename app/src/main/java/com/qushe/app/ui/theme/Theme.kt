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
    val light: QushePalette,
    val dark: QushePalette,
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
 * 全部皮肤。**一套 = 浅色 + 深色**，一套就是一个"房间"：白天是纸，夜里是灯。
 *
 * ---- 为什么是一对，而不是分开选 ----
 *
 * 试过"浅色八套 + 深色八套各选各的"，两个问题：
 *  · 挑配色变成做两道题，而且两套撞在一起会打架（浅色是灰紫、深色是暖橙，很怪）；
 *  · 同一套的浅深本来是**同一个调子换一种光**，配对才有"整天都是这个氛围"的意思。
 *
 * 所以回到配色为一套、色卡一张卡里左右各半 —— 一眼看到白天和夜里，一次选定。
 *
 * ---- 配色上仍然是三条硬规矩 ----
 * 1. 主色要能当文字读：`AccentKeep` 同时是底部导航选中标签（12sp）的颜色，
 *    浅色版对底色 ≥4.5:1。
 * 2. 分类色跨色系：首页四张卡并排，照片/截屏/视频/文件夹必须互相分得开，
 *    只压低饱和度服从整体调子，不把色相也统一掉。
 * 3. 删除色永远是红（有语义），而且要和主色拉开 —— 统计行里"已删除"和"已收藏"并排。
 *
 * 名字都是典故：王维的空山、王子猷的雪夜、王勃的落霞、贾岛的月下门、
 * 陆羽的茶烟、柳宗元的寒江、林逋的梅影、黄公望的富春山 —— 一个名字先给一幅画。
 */
val QusheSkins: List<QusheSkin> = listOf(
    QusheSkin(
        id = 0,
        name = "空山新雨",  // 雨后松针的绿，纸是湿的
        light = skinPalette(
            dark = false,
            bg = Color(0xFFF5F7F1),
            surface = Color(0xFFFBFCF8),
            main = Color(0xFF4E6A48),
            delete = Color(0xFF9C5F55),
            info = Color(0xFF5A7A8C),
            purple = Color(0xFF7E7595),
            folder = Color(0xFF8A6E3C),
            textPrimary = Color(0xFF2B3026),
            textSecondary = Color(0xFF616857),
            textTertiary = Color(0xFF828972),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF0F1B12),
            surface = Color(0xFF19261C),
            main = Color(0xFF9CC2A0),
            delete = Color(0xFFDE938C),
            info = Color(0xFF8FB2C0),
            purple = Color(0xFFAFA7C6),
            folder = Color(0xFFC6A46E),
            textPrimary = Color(0xFFE4EDE4),
            textSecondary = Color(0xFF9DAF9F),
            textTertiary = Color(0xFF687A6B),
        ),
    ),
    QusheSkin(
        id = 1,
        name = "雪夜访戴",  // 雪光映着的蓝，冷而干净
        light = skinPalette(
            dark = false,
            bg = Color(0xFFF6F8FA),
            surface = Color(0xFFFCFDFE),
            main = Color(0xFF4A6E85),
            delete = Color(0xFFA16060),
            info = Color(0xFF42739B),
            purple = Color(0xFF77799F),
            folder = Color(0xFF8C7444),
            textPrimary = Color(0xFF29323A),
            textSecondary = Color(0xFF5F6B75),
            textTertiary = Color(0xFF7F8992),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF0D1521),
            surface = Color(0xFF17202D),
            main = Color(0xFFA8C8E6),
            delete = Color(0xFFDE909A),
            info = Color(0xFF7FAEDA),
            purple = Color(0xFFA6A8CC),
            folder = Color(0xFFC0A478),
            textPrimary = Color(0xFFE6ECF4),
            textSecondary = Color(0xFF9AA8B6),
            textTertiary = Color(0xFF66737F),
        ),
    ),
    QusheSkin(
        id = 2,
        name = "落霞孤鹜",  // 晚照的橘褐，暖得发闷
        light = skinPalette(
            dark = false,
            bg = Color(0xFFFBF3EC),
            surface = Color(0xFFFEF9F3),
            main = Color(0xFF955B39),
            delete = Color(0xFFA25248),
            info = Color(0xFF5E7A8A),
            purple = Color(0xFF86708C),
            folder = Color(0xFF8C6E3A),
            textPrimary = Color(0xFF33291F),
            textSecondary = Color(0xFF736353),
            textTertiary = Color(0xFF91826F),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF241610),
            surface = Color(0xFF301F18),
            main = Color(0xFFE0A070),
            delete = Color(0xFFE08A8A),
            info = Color(0xFF93A8BA),
            purple = Color(0xFFBBA0B4),
            folder = Color(0xFFC09660),
            textPrimary = Color(0xFFF2E6DC),
            textSecondary = Color(0xFFB3A191),
            textTertiary = Color(0xFF7D6B5C),
        ),
    ),
    QusheSkin(
        id = 3,
        name = "月下叩门",  // 月色压在门上，紫灰
        light = skinPalette(
            dark = false,
            bg = Color(0xFFF7F5F9),
            surface = Color(0xFFFCFBFD),
            main = Color(0xFF6E6284),
            delete = Color(0xFF9F6470),
            info = Color(0xFF5F7A95),
            purple = Color(0xFF6B64A0),
            folder = Color(0xFF8F7448),
            textPrimary = Color(0xFF2F2B37),
            textSecondary = Color(0xFF68626F),
            textTertiary = Color(0xFF878191),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF15101F),
            surface = Color(0xFF201829),
            main = Color(0xFFB49CE0),
            delete = Color(0xFFE094A6),
            info = Color(0xFF8FAFD6),
            purple = Color(0xFFC89BD0),
            folder = Color(0xFFC6A478),
            textPrimary = Color(0xFFEDE7F5),
            textSecondary = Color(0xFFA79CB8),
            textTertiary = Color(0xFF6F6580),
        ),
    ),
    QusheSkin(
        id = 4,
        name = "松风煮茗",  // 茶烟里的金褐
        light = skinPalette(
            dark = false,
            bg = Color(0xFFFAF6EE),
            surface = Color(0xFFFDFAF4),
            main = Color(0xFF856A33),
            delete = Color(0xFFA05B51),
            info = Color(0xFF57788C),
            purple = Color(0xFF82708F),
            folder = Color(0xFF7E6A3E),
            textPrimary = Color(0xFF322B1F),
            textSecondary = Color(0xFF6E6453),
            textTertiary = Color(0xFF908674),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF1D1709),
            surface = Color(0xFF28200F),
            main = Color(0xFFD4B280),
            delete = Color(0xFFDD8F88),
            info = Color(0xFF93A8B8),
            purple = Color(0xFFBAA4B6),
            folder = Color(0xFFC29A60),
            textPrimary = Color(0xFFF0E7DB),
            textSecondary = Color(0xFFB0A292),
            textTertiary = Color(0xFF7A6C5D),
        ),
    ),
    QusheSkin(
        id = 5,
        name = "寒江独钓",  // 江雾的青，几乎无色
        light = skinPalette(
            dark = false,
            bg = Color(0xFFF2F6F5),
            surface = Color(0xFFF9FCFB),
            main = Color(0xFF46706B),
            delete = Color(0xFF9C625C),
            info = Color(0xFF4C7383),
            purple = Color(0xFF78758E),
            folder = Color(0xFF8A7040),
            textPrimary = Color(0xFF28322F),
            textSecondary = Color(0xFF5E6B68),
            textTertiary = Color(0xFF7D8A86),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF0A1A19),
            surface = Color(0xFF142624),
            main = Color(0xFF8FC4BC),
            delete = Color(0xFFDE9088),
            info = Color(0xFF8AAAC9),
            purple = Color(0xFFA9A6C8),
            folder = Color(0xFFC0A470),
            textPrimary = Color(0xFFE2EFEC),
            textSecondary = Color(0xFF9BB2AE),
            textTertiary = Color(0xFF64807C),
        ),
    ),
    QusheSkin(
        id = 6,
        name = "疏影暗香",  // 梅影的白里透一点粉
        light = skinPalette(
            dark = false,
            bg = Color(0xFFFBF4F4),
            surface = Color(0xFFFEF9F9),
            main = Color(0xFF925F66),
            delete = Color(0xFFA5574F),
            info = Color(0xFF5C7890),
            purple = Color(0xFF8A6E96),
            folder = Color(0xFF8E7040),
            textPrimary = Color(0xFF352B2C),
            textSecondary = Color(0xFF716264),
            textTertiary = Color(0xFF908183),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF1F1114),
            surface = Color(0xFF2B1A1D),
            main = Color(0xFFDCA0A6),
            delete = Color(0xFFE08A8E),
            info = Color(0xFF93A8BC),
            purple = Color(0xFFC2A0C0),
            folder = Color(0xFFC09670),
            textPrimary = Color(0xFFF2E7E7),
            textSecondary = Color(0xFFB3A1A1),
            textTertiary = Color(0xFF7D6B6C),
        ),
    ),
    QusheSkin(
        id = 7,
        name = "富春山居",  // 旧绢上的橄榄与土黄
        light = skinPalette(
            dark = false,
            bg = Color(0xFFF7F5EC),
            surface = Color(0xFFFCFBF4),
            main = Color(0xFF77703A),
            delete = Color(0xFF9E6050),
            info = Color(0xFF5E7A80),
            purple = Color(0xFF837C94),
            folder = Color(0xFF8A6A34),
            textPrimary = Color(0xFF302D22),
            textSecondary = Color(0xFF6C6752),
            textTertiary = Color(0xFF8E8872),
        ),
        dark = skinPalette(
            dark = true,
            bg = Color(0xFF17180D),
            surface = Color(0xFF212215),
            main = Color(0xFFC4BC7C),
            delete = Color(0xFFDD9386),
            info = Color(0xFF8FAEB2),
            purple = Color(0xFFADA6C0),
            folder = Color(0xFFC4A060),
            textPrimary = Color(0xFFEDEADB),
            textSecondary = Color(0xFFA8A38F),
            textTertiary = Color(0xFF75705C),
        ),
    ),
)

/** 按 id 取皮肤；越界一律回落到第一套。 */
fun skinOf(id: Int): QusheSkin = QusheSkins.firstOrNull { it.id == id } ?: QusheSkins.first()

/**
 * 当前调色板。
 *
 * 用一个全局的快照状态：切主题时，读过这些颜色的 Composable 会重组、
 * `Canvas` 那类绘制 lambda 会重绘 —— 不用把颜色一层层往下传。
 */
object QusheColors {
    var palette by mutableStateOf(QusheSkins.first().dark)

    /**
     * 当前皮肤（浅深成对）。
     *
     * [ForceDarkPalette] 需要它 —— 浅色模式下进视频页/预览页会被强制深色，
     * 那时要用"当前这套的深色版"，而不是固定一套默认的。
     */
    var skin by mutableStateOf(QusheSkins.first())
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
    val dark = QusheColors.skin.dark
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
 * @param mode 见 `Prefs.THEME_SYSTEM / Prefs.THEME_LIGHT / Prefs.THEME_DARK`。
 * @param skinId 皮肤 id（见 [QusheSkins]）。深浅决定用这套的哪一版，皮肤决定"什么光"。
 */
@Composable
fun QusheTheme(
    mode: Int = Prefs.THEME_DARK,
    skinId: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        Prefs.THEME_LIGHT -> false
        Prefs.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val skin = skinOf(skinId)
    val palette = if (dark) skin.dark else skin.light

    // ---- 只在"模式真的变了"时写一次 ----
    //
    // 不能每次重组都写：视频页 / 预览页会临时把调色板设成深色
    // （见 [ForceDarkPalette]），这里一写就把人家的覆盖顶回去了。
    // 返回 palette 而不是 Unit：`remember { ... }` 返回 Unit 会被 lint 判为错误
    remember(mode, dark, skinId) {
        QusheColors.skin = skin
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
