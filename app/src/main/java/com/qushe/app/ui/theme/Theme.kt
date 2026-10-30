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
 * 浅色八套，名字取自西湖的八个时候。
 *
 * ---- 命名沿用奔驰车内氛围灯那一路 ----
 * 不叫"浅粉"「雾绿」，叫「苏堤春晓」「平湖秋月」—— 一个名字先给出一幅画，
 * 用户挑的是"今天想要哪种光"，不是"RGB 多少"。
 *
 * 主色全部压到"对底色 ≥4.5:1"：`AccentKeep` 不只是色块，底部导航的选中标签
 * （12sp）、星形图标、"已收藏"数字都用它上色，浅底上一淡就读不清。
 *
 * 分类色（信息/紫/文件夹）故意**跨色系**：首页四张卡并排，它们必须互相分得开，
 * 所以只压低饱和度服从整体调子，不把色相也统一掉。删除色永远是红（有语义）。
 */
val LightSkins: List<QusheSkin> = listOf(
    QusheSkin(
        id = 0,
        name = "西山晴雪",  // 冷白底，一抹灰蓝
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF7F9FB),
            surface = Color(0xFFFCFDFE),
            main = Color(0xFF5F7488),
            delete = Color(0xFFA2605C),
            info = Color(0xFF47728F),
            purple = Color(0xFF7B7898),
            folder = Color(0xFF8C7444),
            textPrimary = Color(0xFF2B333A),
            textSecondary = Color(0xFF616C75),
            textTertiary = Color(0xFF818B93),
        ),
    ),
    QusheSkin(
        id = 1,
        name = "苏堤春晓",  // 浅柔粉，堤上一线嫩青
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFFBF5F3),
            surface = Color(0xFFFEFAF8),
            main = Color(0xFF956369),
            delete = Color(0xFFA65A55),
            info = Color(0xFF4C817E),
            purple = Color(0xFF85708F),
            folder = Color(0xFF8E7040),
            textPrimary = Color(0xFF362B2C),
            textSecondary = Color(0xFF6F6364),
            textTertiary = Color(0xFF908485),
        ),
    ),
    QusheSkin(
        id = 2,
        name = "雷峰夕照",  // 暖橘棕，塔影压着豆沙红
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFFBF3EA),
            surface = Color(0xFFFEF8F1),
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
    QusheSkin(
        id = 3,
        name = "月泉晓彻",  // 浅金，天将亮未亮那点蓝
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFFAF7EF),
            surface = Color(0xFFFDFBF4),
            main = Color(0xFF8A6E33),
            delete = Color(0xFFA25B51),
            info = Color(0xFF4C7893),
            purple = Color(0xFF82708F),
            folder = Color(0xFF6E7A4A),
            textPrimary = Color(0xFF342C1E),
            textSecondary = Color(0xFF6F6553),
            textTertiary = Color(0xFF918773),
        ),
    ),
    QusheSkin(
        id = 4,
        name = "琼岛春阴",  // 浅芽绿，花还没开
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF5F8EE),
            surface = Color(0xFFFBFDF6),
            main = Color(0xFF67783C),
            delete = Color(0xFFA05E52),
            info = Color(0xFF50798A),
            purple = Color(0xFF9A6E7E),
            folder = Color(0xFF8E6A38),
            textPrimary = Color(0xFF2E3324),
            textSecondary = Color(0xFF68705A),
            textTertiary = Color(0xFF848D77),
        ),
    ),
    QusheSkin(
        id = 5,
        name = "曲院风荷",  // 柔绿配淡玫，水面上的风
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF4F8F2),
            surface = Color(0xFFFAFCF7),
            main = Color(0xFF5A7860),
            delete = Color(0xFFA05C58),
            info = Color(0xFF4E7A8C),
            purple = Color(0xFF96687E),
            folder = Color(0xFF8A7040),
            textPrimary = Color(0xFF2A342B),
            textSecondary = Color(0xFF647064),
            textTertiary = Color(0xFF808D80),
        ),
    ),
    QusheSkin(
        id = 6,
        name = "云栖竹径",  // 灰调青碧，竹林里没太阳
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFF2F6F3),
            surface = Color(0xFFF9FCFA),
            main = Color(0xFF4E7266),
            delete = Color(0xFF9E6058),
            info = Color(0xFF4B7383),
            purple = Color(0xFF78758E),
            folder = Color(0xFF87703F),
            textPrimary = Color(0xFF29322E),
            textSecondary = Color(0xFF5F6C66),
            textTertiary = Color(0xFF7E8B85),
        ),
    ),
    QusheSkin(
        id = 7,
        name = "平湖秋月",  // 冷灰蓝，月光落在水面上
        palette = skinPalette(
            dark = false,
            bg = Color(0xFFEFF3F7),
            surface = Color(0xFFF8FAFC),
            main = Color(0xFF42687F),
            delete = Color(0xFF9E6260),
            info = Color(0xFF3F6C8C),
            purple = Color(0xFF77799A),
            folder = Color(0xFF86703F),
            textPrimary = Color(0xFF2A3238),
            textSecondary = Color(0xFF5E6870),
            textTertiary = Color(0xFF7E878E),
        ),
    ),
)

/**
 * 深色八套，同样取自西湖，但都是"入了夜"的那一面。
 *
 * 底色是**极深但带色相**（不是纯黑）：纯黑会把照片衬得发脏，也让色相没处着落。
 * 主色比浅色版更亮 —— 夜里那一束光要自己发光，而不是白天的墨。
 *
 * 「西山晴雪」「平湖秋月」「苏堤春晓」「曲院风荷」「湖滨晴雨」「月泉晓彻」
 * 「琼岛春阴」「雷峰夕照」「洞庭秋月」都是现成的名字；
 * 深色另补的六套（断桥残雪 / 双峰插云 / 三潭印月 / 九溪烟树 / 柳浪闻莺 / 花港观鱼）
 * 也都是西湖景，同一套语汇里挑的。
 */
val DarkSkins: List<QusheSkin> = listOf(
    QusheSkin(
        id = 0,
        name = "断桥残雪",  // 冷黑底，雪还没化
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF12161A),
            surface = Color(0xFF1C2228),
            main = Color(0xFFCFD9E2),
            delete = Color(0xFFDE9098),
            info = Color(0xFF8FB2CF),
            purple = Color(0xFFA9AAC6),
            folder = Color(0xFFC0A87C),
            textPrimary = Color(0xFFEDF2F6),
            textSecondary = Color(0xFFA0ABB5),
            textTertiary = Color(0xFF6A747D),
        ),
    ),
    QusheSkin(
        id = 1,
        name = "双峰插云",  // 雾蓝灰，两座山看不见顶
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF101419),
            surface = Color(0xFF1A2027),
            main = Color(0xFFA8BCCE),
            delete = Color(0xFFDE9098),
            info = Color(0xFF8AAECE),
            purple = Color(0xFFA9A8C6),
            folder = Color(0xFFC0A478),
            textPrimary = Color(0xFFE6ECF2),
            textSecondary = Color(0xFF9BA7B2),
            textTertiary = Color(0xFF66717B),
        ),
    ),
    QusheSkin(
        id = 2,
        name = "三潭印月",  // 深蓝，三个月亮在潭里
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF0E1520),
            surface = Color(0xFF182130),
            main = Color(0xFF9DBEDC),
            delete = Color(0xFFDE9098),
            info = Color(0xFF84AED2),
            purple = Color(0xFFA6A8CC),
            folder = Color(0xFFBFA478),
            textPrimary = Color(0xFFE4EBF4),
            textSecondary = Color(0xFF99A6B6),
            textTertiary = Color(0xFF64727F),
        ),
    ),
    QusheSkin(
        id = 3,
        name = "洞庭秋月",  // 深青灰，一点暗紫
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF101A1D),
            surface = Color(0xFF182528),
            main = Color(0xFF9FC4C6),
            delete = Color(0xFFDE9088),
            info = Color(0xFF8BAACB),
            purple = Color(0xFFB49AC8),
            folder = Color(0xFFC4A470),
            textPrimary = Color(0xFFE2EDEE),
            textSecondary = Color(0xFF9BB0B2),
            textTertiary = Color(0xFF647B7D),
        ),
    ),
    QusheSkin(
        id = 4,
        name = "九溪烟树",  // 深绿灰，水汽浮在林间
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF111A16),
            surface = Color(0xFF1B2620),
            main = Color(0xFF8FB89E),
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
        id = 5,
        name = "柳浪闻莺",  // 柳黄，夜里也亮
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF141A12),
            surface = Color(0xFF1E261A),
            main = Color(0xFFB8C87E),
            delete = Color(0xFFDD9285),
            info = Color(0xFF8FAEBA),
            purple = Color(0xFFAFA6C0),
            folder = Color(0xFFC0A46A),
            textPrimary = Color(0xFFE8EEDD),
            textSecondary = Color(0xFFA5AF97),
            textTertiary = Color(0xFF6F7A64),
        ),
    ),
    QusheSkin(
        id = 6,
        name = "花港观鱼",  // 珊瑚红，灯下的鱼影
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF1C1315),
            surface = Color(0xFF281B1E),
            main = Color(0xFFE0A08C),
            delete = Color(0xFFE08A96),
            info = Color(0xFF93AABB),
            purple = Color(0xFFBBA3B8),
            folder = Color(0xFFC0996A),
            textPrimary = Color(0xFFF2E7E4),
            textSecondary = Color(0xFFB3A19E),
            textTertiary = Color(0xFF7D6A68),
        ),
    ),
    QusheSkin(
        id = 7,
        name = "南屏晚钟",  // 暖金，钟声落下来的颜色
        palette = skinPalette(
            dark = true,
            bg = Color(0xFF191410),
            surface = Color(0xFF241D17),
            main = Color(0xFFD2B080),
            delete = Color(0xFFDD8F88),
            info = Color(0xFF93A8B8),
            purple = Color(0xFFBAA4B6),
            folder = Color(0xFFC29A60),
            textPrimary = Color(0xFFF0E7DB),
            textSecondary = Color(0xFFB0A292),
            textTertiary = Color(0xFF7A6C5D),
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
