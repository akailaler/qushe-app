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

val DarkPalette = QushePalette(
    ink = Color(0xFF0B0B0D),
    surface1 = Color(0xFF16161A),
    surface2 = Color(0xFF1F1F25),
    surface3 = Color(0xFF2A2A32),
    textPrimary = Color(0xFFF2F2F5),
    textSecondary = Color(0xFF9A9AA5),
    textTertiary = Color(0xFF63636E),
    accentKeep = Color(0xFF35D0A5),
    accentDelete = Color(0xFFFF5C7A),
    accentInfo = Color(0xFF6BA8FF),
    accentPurple = Color(0xFFAFA9EC),
    accentFolder = Color(0xFFF0B65C),
    overlaySoft = Color(0x66000000),
    overlayStrong = Color(0xB3000000),
    overlayFade = Color(0x99000000),
    overlaySolid = Color(0xF2000000),
    overlayBorder = Color(0x26FFFFFF),
    scrimTop = Color(0xCC0B0B0D),
    scrimBottom = Color(0xE60B0B0D),
    isDark = true,
)

/**
 * 浅色那套。
 *
 * 强调色不是原样搬过来的 —— 深色下的马卡龙色放到白底上对比度不够
 * （比如 `#35D0A5` 在白底上几乎看不清），所以整体压暗、提饱和。
 */
private val LightPalette = QushePalette(
    ink = Color(0xFFF5F5F7),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFEFF2),
    surface3 = Color(0xFFDDDDE3),
    textPrimary = Color(0xFF17171B),
    textSecondary = Color(0xFF5C5C66),
    textTertiary = Color(0xFF888892),
    // ---- 强调色都要"能当文字用" ----
    //
    // `accentKeep` 不只是色块：底部导航的选中标签、星形图标、"已收藏"数字
    // 都用它上色。所以它必须对底色够黑 —— 原来的 #0E9C77 在 #F5F5F7 上只有
    // 3.2:1（12sp 小字远不够），压深到 #0B8062 才到 4.5:1。
    // 其余几个分类色同理，只是阈值按用途放宽（数字是大字、色条是装饰）。
    accentKeep = Color(0xFF0B8062),
    accentDelete = Color(0xFFD8324F),
    accentInfo = Color(0xFF2C6FD1),
    accentPurple = Color(0xFF6259BE),
    accentFolder = Color(0xFFA56D1B),
    // 浅色下浮层反过来：白底 + 深色描边。里面的图标/文字用 TextPrimary，
    // 会跟着一起翻，所以不必单独定义前景色。
    // ---- 浅色浮层：半透浅白 + 极淡描边（不做模糊）----
    //
    // 之前试过真模糊（把照片再画一份糊掉），问题有两个：
    //  · 背后是白/浅色图时，糊完还是白的，再叠一层色就"发灰、脏"；
    //  · Android 12 以下根本没有模糊，同一套 UI 在旧机上退化成死白块。
    // 现在走"通透卡片"：白 75% + 黑 8% 描边 + 极轻阴影（阴影在组件上加）。
    // 识别度靠**描边**兜底 —— 这是浅色大图上最关键的一条。
    overlaySoft = Color(0xBFFFFFFF),
    overlayStrong = Color(0xC4FFFFFF),
    // 顶部/底部的渐变雾：**浅色下不要**（用户明确要求）。
    overlayFade = Color.Transparent,
    overlaySolid = Color.Transparent,
    overlayBorder = Color(0x14000000),
    scrimTop = Color.Transparent,
    scrimBottom = Color.Transparent,
    isDark = false,
)

/**
 * 一套皮肤：**一个色相**，含浅色与深色两版。
 *
 * ---- 为什么按"皮肤"组织，而不是再加几套 light/dark ----
 * 深浅是"看的时候环境光怎样"，皮肤是"这个 App 长什么样"——两件事正交。
 * 所以皮肤同时提供 light / dark，用户切深浅时色相不变。
 * 这样 10 套皮肤 × 2 种深浅只需要 10 份定义，而不是 20 份互不相干的调色板。
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
 * 所有皮肤。**第 0 套必须是默认**（[LightPalette] / [DarkPalette]）。
 *
 * ---- 名字为什么是四个字 ----
 *
 * 这一页是"选一种心情"，不是"选一个参数"。三个字的色名（暖桃、奶绿）读起来像
 * 商品标签；四个字能带一个画面（暖桃初雪、睡莲静水），选的时候先想到的是感觉。
 *
 * ---- 配色上的三条硬规矩 ----
 * 1. **主色要能读字**：主色同时用于"选中态的文字与图标"（底部导航、星形、勾选），
 *    所以不能直接用很浅的粉/绿当主色 —— 浅底上会糊成一片。
 *    设计稿里给的柔桃 `#E8BFB0`、奶绿 `#B9D4C7` 都压深了一档。
 * 2. **删除色单独一档**：每个皮肤都配一个低饱和的红，避免"整套色调统一但删除键炸眼"。
 * 3. **每套只有一个色相是自己挑的**：底色/卡片/文字定调子，四个分类色
 *    （照片/截屏/视频/文件夹）只做小幅偏移，不从别的色系借色 ——
 *    否则 10 套皮肤会互相串味（"雾蓝"里冒出一个橙，就像别人的界面）。
 *    唯一的例外是删除色，它有语义，必须是红。
 *
 * ---- 后五套的来源 ----
 *
 * 参考的是"降饱和之后再比明度"的那一路：莫兰迪的灰粉、莫奈的睡莲池、
 * 塞尚的赭石山丘、高更的塔希提暖沙，以及苔庭的湿绿。
 * 油画的颜色其实很艳，直接取色会变成糖水；这里取的是它们的**明度关系**，
 * 把彩度压到能长时间看着不累的程度。
 */
private val PeachSkin = QusheSkin(
    id = 1,
    name = "暖桃初雪",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFFFF9F4),
        surface = Color(0xFFFFF6EF),
        main = Color(0xFF9C6454),
        delete = Color(0xFFA9675C),
        info = Color(0xFF537FAA),
        purple = Color(0xFF8E7AB2),
        folder = Color(0xFF9F723A),
        textPrimary = Color(0xFF4A3B36),
        textSecondary = Color(0xFF7E6C65),
        textTertiary = Color(0xFF988983),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF1F1D24),
        surface = Color(0xFF2B2831),
        main = Color(0xFFA8867C),
        delete = Color(0xFFB47A73),
        info = Color(0xFF7D97B8),
        purple = Color(0xFF9A8CB8),
        folder = Color(0xFFB08A55),
        textPrimary = Color(0xFFE4E0E2),
        textSecondary = Color(0xFFA89CA0),
        textTertiary = Color(0xFF736C74),
    ),
)

private val GreenSkin = QusheSkin(
    id = 2,
    name = "新茶初雨",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF4F9F4),
        surface = Color(0xFFFAFCFA),
        main = Color(0xFF54796A),
        delete = Color(0xFFA7655E),
        info = Color(0xFF5B7E98),
        purple = Color(0xFF887BA5),
        folder = Color(0xFF967543),
        textPrimary = Color(0xFF2F3A35),
        textSecondary = Color(0xFF66726C),
        textTertiary = Color(0xFF838D88),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF1D2424),
        surface = Color(0xFF262E2D),
        main = Color(0xFF86A79B),
        delete = Color(0xFFB5837D),
        info = Color(0xFF7C97A8),
        purple = Color(0xFF9488AE),
        folder = Color(0xFFAE8B5B),
        textPrimary = Color(0xFFE1E7E4),
        textSecondary = Color(0xFFA0ABA6),
        textTertiary = Color(0xFF6B7671),
    ),
)

private val OatSkin = QusheSkin(
    id = 3,
    name = "燕麦秋声",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF7F3ED),
        surface = Color(0xFFFBF8F3),
        main = Color(0xFF7E6B59),
        delete = Color(0xFF9E665E),
        info = Color(0xFF657A8C),
        purple = Color(0xFF897899),
        folder = Color(0xFF927147),
        textPrimary = Color(0xFF3A352E),
        textSecondary = Color(0xFF736B62),
        textTertiary = Color(0xFF8F877E),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF222120),
        surface = Color(0xFF2C2B29),
        main = Color(0xFFA79C90),
        delete = Color(0xFFAE7D76),
        info = Color(0xFF8595A3),
        purple = Color(0xFF9C90A8),
        folder = Color(0xFFAE8A5E),
        textPrimary = Color(0xFFE6E2DC),
        textSecondary = Color(0xFFA8A199),
        textTertiary = Color(0xFF736C64),
    ),
)

private val MistSkin = QusheSkin(
    id = 4,
    name = "远山如黛",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF3F6F9),
        surface = Color(0xFFFAFCFE),
        main = Color(0xFF567389),
        delete = Color(0xFFA26660),
        info = Color(0xFF4D7CAC),
        purple = Color(0xFF7A7CB0),
        folder = Color(0xFF927447),
        textPrimary = Color(0xFF2C353D),
        textSecondary = Color(0xFF626E78),
        textTertiary = Color(0xFF808992),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF191D22),
        surface = Color(0xFF232830),
        main = Color(0xFF8FAEC6),
        delete = Color(0xFFAF7C77),
        info = Color(0xFF7EA3C9),
        purple = Color(0xFF9A9CC8),
        folder = Color(0xFFA98A5F),
        textPrimary = Color(0xFFDFE5EA),
        textSecondary = Color(0xFF9DA9B4),
        textTertiary = Color(0xFF6A757E),
    ),
)

/** 莫兰迪的灰玫瑰：粉里带灰，暖但不甜。 */
private val RoseSkin = QusheSkin(
    id = 5,
    name = "烟粉旧梦",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF8F3F2),
        surface = Color(0xFFFCF8F7),
        main = Color(0xFF90646C),
        delete = Color(0xFFA4635E),
        info = Color(0xFF667A8F),
        purple = Color(0xFF8A7A93),
        folder = Color(0xFF93724E),
        textPrimary = Color(0xFF3D3335),
        textSecondary = Color(0xFF75686B),
        textTertiary = Color(0xFF8E8185),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF1E1A1C),
        surface = Color(0xFF2A2427),
        main = Color(0xFFC39AA0),
        delete = Color(0xFFC08A85),
        info = Color(0xFF93A7BC),
        purple = Color(0xFFA99BB3),
        folder = Color(0xFFB99B72),
        textPrimary = Color(0xFFE9E2E3),
        textSecondary = Color(0xFFB0A3A5),
        textTertiary = Color(0xFF7C7174),
    ),
)

/** 莫奈的睡莲池：水绿托着一点蓝紫。 */
private val LilySkin = QusheSkin(
    id = 6,
    name = "睡莲静水",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF0F4F2),
        surface = Color(0xFFF7FAF8),
        main = Color(0xFF517670),
        delete = Color(0xFF9E6465),
        info = Color(0xFF547B9E),
        purple = Color(0xFF797AA6),
        folder = Color(0xFF8C744C),
        textPrimary = Color(0xFF2B3634),
        textSecondary = Color(0xFF61706D),
        textTertiary = Color(0xFF7C8A87),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF17201F),
        surface = Color(0xFF202A29),
        main = Color(0xFF8FB5AC),
        delete = Color(0xFFBB8481),
        info = Color(0xFF8AAAC9),
        purple = Color(0xFFA3A2C6),
        folder = Color(0xFFB39A6E),
        textPrimary = Color(0xFFDEE8E5),
        textSecondary = Color(0xFFA2B0AD),
        textTertiary = Color(0xFF6E7C79),
    ),
)

/** 塞尚的圣维克多山：赭石、赤陶、石灰蓝。 */
private val ClaySkin = QusheSkin(
    id = 7,
    name = "赤陶晚照",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF8F1E9),
        surface = Color(0xFFFCF7F0),
        main = Color(0xFF946245),
        delete = Color(0xFF9E5B52),
        info = Color(0xFF637A8B),
        purple = Color(0xFF89788F),
        folder = Color(0xFF966F38),
        textPrimary = Color(0xFF3B3028),
        textSecondary = Color(0xFF746558),
        textTertiary = Color(0xFF8E7F71),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF201B17),
        surface = Color(0xFF2B2520),
        main = Color(0xFFC9946F),
        delete = Color(0xFFC08579),
        info = Color(0xFF93A9B8),
        purple = Color(0xFFA79AB0),
        folder = Color(0xFFC09052),
        textPrimary = Color(0xFFEBE2D8),
        textSecondary = Color(0xFFB3A697),
        textTertiary = Color(0xFF7E7365),
    ),
)

/** 高更的塔希提：日光般的暖黄，配热带海的青。 */
private val SandSkin = QusheSkin(
    id = 8,
    name = "椰影暖沙",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFFAF4E8),
        surface = Color(0xFFFDF9F0),
        main = Color(0xFF826D3C),
        delete = Color(0xFFA2654E),
        info = Color(0xFF5A7E89),
        purple = Color(0xFF8B7A96),
        folder = Color(0xFF9A6F3F),
        textPrimary = Color(0xFF38301F),
        textSecondary = Color(0xFF73684F),
        textTertiary = Color(0xFF8E8368),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF1E1C15),
        surface = Color(0xFF292720),
        main = Color(0xFFC4AF74),
        delete = Color(0xFFC0876E),
        info = Color(0xFF85A8AF),
        purple = Color(0xFFA69AB0),
        folder = Color(0xFFC29157),
        textPrimary = Color(0xFFEAE4D3),
        textSecondary = Color(0xFFB2A98F),
        textTertiary = Color(0xFF7C7461),
    ),
)

/** 苔庭：湿石与青苔，最"低存在感"的一套。 */
private val MossSkin = QusheSkin(
    id = 9,
    name = "青苔石阶",
    light = skinPalette(
        dark = false,
        bg = Color(0xFFF5F6F0),
        surface = Color(0xFFFAFBF6),
        main = Color(0xFF5C7052),
        delete = Color(0xFF9A6058),
        info = Color(0xFF647C8B),
        purple = Color(0xFF887B98),
        folder = Color(0xFF927345),
        textPrimary = Color(0xFF2F342A),
        textSecondary = Color(0xFF6A7161),
        textTertiary = Color(0xFF848B7B),
    ),
    dark = skinPalette(
        dark = true,
        bg = Color(0xFF1B1F19),
        surface = Color(0xFF242A23),
        main = Color(0xFF9FB594),
        delete = Color(0xFFC08A80),
        info = Color(0xFF90A9B6),
        purple = Color(0xFFABA0B8),
        folder = Color(0xFFBC9664),
        textPrimary = Color(0xFFE5E9DD),
        textSecondary = Color(0xFFA9B0A0),
        textTertiary = Color(0xFF727A6C),
    ),
)

/**
 * 全部皮肤，顺序即色卡页的顺序。
 *
 * 顺序是"从最中性到最有色"：默认 → 暖桃 → 奶绿 → 燕麦 → 雾蓝 → 后五套。
 * 色卡页是两列网格，10 套刚好 5 行，不用滚动就能看全（小屏除外）。
 *
 * 再加一套的代价：这里加一个 [QusheSkin]、`Prefs.SKIN_MAX` 加一，
 * 色卡页和切换逻辑都是按 id 走的，不用动。
 */
val QusheSkins: List<QusheSkin> = listOf(
    QusheSkin(0, "素白玄墨", LightPalette, DarkPalette),
    PeachSkin,
    GreenSkin,
    OatSkin,
    MistSkin,
    RoseSkin,
    LilySkin,
    ClaySkin,
    SandSkin,
    MossSkin,
)

/** 按 id 取皮肤；越界一律回落到默认。 */
fun skinOf(id: Int): QusheSkin = QusheSkins.firstOrNull { it.id == id } ?: QusheSkins.first()

/**
 * 当前调色板。
 *
 * 用一个全局的快照状态：切主题时，读过这些颜色的 Composable 会重组、
 * `Canvas` 那类绘制 lambda 会重绘 —— 不用把颜色一层层往下传。
 */
object QusheColors {
    var palette by mutableStateOf(DarkPalette)

    /**
     * 当前皮肤。
     *
     * [ForceDarkPalette] 需要它 —— 强制深色时要用**当前皮肤的深色版**，
     * 而不是默认那套，否则浅色皮肤的视频页会突然变成蓝黑色调。
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
 * @param mode 见 `Prefs.THEME_SYSTEM / THEME_LIGHT / THEME_DARK`。
 * @param skinId 皮肤 id（见 [QusheSkins]）。深浅决定"用哪一版"，皮肤决定"什么色相"。
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
