package com.qushe.app.ui.photo

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.dayStartOf
import com.qushe.app.ui.common.DAY_INTRO_MS
import com.qushe.app.ui.common.DoubleTapFlash
import com.qushe.app.ui.common.DoubleTapFlashOverlay
import com.qushe.app.ui.common.rememberContentCorner
import com.qushe.app.ui.common.rememberPageGap
import com.qushe.app.ui.common.rememberContentShape
import com.qushe.app.core.SystemThumbnailFetcher
import com.qushe.app.core.formatDateTime
import com.qushe.app.core.formatDayKey
import com.qushe.app.core.formatDayTitle
import com.qushe.app.core.formatDuration
import com.qushe.app.core.formatSize
import com.qushe.app.core.relativeDayLabel
import com.qushe.app.core.storeKey
import com.qushe.app.core.weekdayLabel
import com.qushe.app.ui.common.RecenterIcon
import com.qushe.app.ui.common.flingTo
import com.qushe.app.ui.common.UndoIcon
import com.qushe.app.ui.common.ZoomableImage
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.Surface3
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** 上下两排 —— 「去留」就是这个数。多了会变成网格墙，横拨的手感就没了。 */
private const val ROWS = 2

/**
 * 一排里格子的最大宽度。
 *
 * 等高布局下这个值决定"横图能有多宽" —— 参照「去留」：**横着的长图保留行高、
 * 把宽度放开**（4:3、16:9 这种一眼就是一张横幅），所以上限必须给得比行高还大。
 * 之前 225dp 会把一张 16:9 横图裁成中间一条竖缝，完全认不出画面。
 * 520dp 是"再宽就截断"的位置（≈ 两张竖图的宽度）。
 */
private val CELL_MAX_WIDTH = 520.dp

/** 一排里格子的最小宽度。极端长图（全景长截图）会在这里被改成"裁一段"显示。 */
private val CELL_MIN_WIDTH = 96.dp

/**
 * 间距与圆角**按屏宽比例**算，不写死 dp。
 *
 * 用户明确要求："不要看这张截图的像素多少（图片可能会被压缩），
 * 要看这个边框的宽度占屏幕宽度的多少"。于是这两个值从「去留」截图量出来
 * 存成**比例**，任何屏宽下观感一致：
 *
 *  · 卡片间距 —— 实测两卡之间的黑缝 ≈ 屏宽 **2.0%**
 *    （1280px 宽的截图上量到 26px；26/1280 = 2.03%）
 *  · 圆角半径 —— 实测左上角过渡 ≈ 24~30px ≈ 屏宽 **2.3%**
 *
 * 之前是写死的 `5.dp` / `12.dp`：在 393dp 宽的机器上分别只占 1.3% / 3.1%，
 * 间距偏挤、圆角偏大，与参考图不是一个比例关系。
 *
 * ---- 为什么圆角必须"算一次并复用"，而不是每个格子现算 ----
 * `clip(RoundedCornerShape(r))` 的 `r` 一旦每帧变化，**每个格子都要重建
 * 裁剪节点**——用户报的"左右滑时图片有时会左右卡一下"就是这个：
 * 看起来像在重新计算边框。所以下面用 [remember] 按屏宽缓存，
 * 一轮布局里所有格子共用**同一个** [RoundedCornerShape] 实例。
 */
/**
 * 同排左右间距 = 屏宽 **2.2%**。
 *
 * 从 2.0% 提到 2.2% 的原因：用户实测"上下两排之间的间距很合适，
 * 但同一排左右有点窄"，要求两者**一样**。既然横竖要一致，
 * 就让它们共用同一个来源 —— [DayMetrics.gap] ——
 * 而不是各算一套、再祈祷两个数字碰巧接近。
 */
private const val CELL_CORNER_RATIO = 0.023f

/** 同排格子间距的下限/上限 —— 比例算出来太小时至少还留得出一道缝。 */

/** 圆角半径的上限 —— 再大就接近胶囊，照片墙会读成"药丸排"。 */
private val CELL_CORNER_MAX = 14.dp

/**
 * 一条带子内上下留白（让图片不贴着边缘）。
 *
 * 原来两排之间还有一个写死的 `ROW_GAP = 8.dp`（比同排略大）。
 * 用户实测下来"上下很合适、左右偏窄"，所以那个多出来的量已经并进
 * [CELL_GAP_RATIO] —— 现在**上下与左右用的是同一个 gap**，
 * 横竖缝一致，砖墙才真的整齐。留这个常量只为了写清来历。
 */
private val VERTICAL_PAD = 2.dp

/**
 * 一轮布局里与屏宽相关的量 —— **稳定且共享**。
 *
 * 存在的理由是 [RoundedCornerShape]：格子每天都会 `clip()` 它，
 * 而 `clip` 的参数一旦换实例就会重建裁剪节点。把它和间距一起
 * `remember` 住、一轮布局只造一次，**两排所有格子共用同一实例**，
 * 滑动时就不会有"重新计算边框"的卡顿。
 *
 * 现在这个对象在 [DayFilmstrip] 里造一次、两排一起用 ——
 * 上下间距也是它给的，所以"横竖一样"是结构保证，不是巧合。
 */
@Immutable
private class DayMetrics(
    /** 上下与左右共用的间距。 */
    val gap: Dp,
    val corner: RoundedCornerShape,
)

/** 由屏宽算出这一轮布局共享的 [DayMetrics]。 */
@Composable
private fun rememberDayMetrics(): DayMetrics {
    val density = LocalDensity.current
    // 圆角和间隙都走共享的那两个（整理照片页的大图用的是同一个）——
    // 两处本来就该看起来是同一个东西，各写一份迟早会漂移。
    val corner = rememberContentShape()
    val gap = rememberPageGap()
    return remember(density, corner, gap) {
        DayMetrics(gap = gap, corner = corner)
    }
}

/**
 * 格子收缩动画（标记删除 → 宽度收到 0）。
 *
 * 抽成顶层常量给所有格子共用**同一个实例**：动画规格每次重组都新建的话，
 * 实例每次都不一样，动画状态会被反复重建。
 *
 * 260ms 太仓促，450ms 用户仍反馈"还是太仓促，让人反应不过来"——
 * 补位是一个**需要被看见**的动作，再放到 560ms。
 */
private val CELL_SHRINK_SPEC = tween<Float>(durationMillis = 560)

/**
 * 「回到那天」的日期视图。
 *
 * 形态参照「去留」：**上下两排照片连成一条带子，横向连续滑动**。
 *
 * 为什么是两条连续带子而不是分页网格：
 * 这个动作的上下文是"我正在看某一张，突然想看看那天的全部"。
 * 一屏塞 8 张的网格是把这一天当成清单（越密越像家务），
 * 而横着拨一条带子是把它当成一段连续的记忆 —— 图片按原图比例排布、
 * 一张接一张流过，反而更像"翻相册"。
 *
 * 四条硬要求（都是用户上手后逐版指出来的）：
 *  1. **进去就定位到正在看的那张**，不是从第一张开始；
 *  2. **保留原图比例**，不要裁成方块；
 *  3. **左右滑动看更多**，而不是一屏固定 8 张；
 *  4. 点某一张 → **放大成单张**，底部有 `i` 可展开完整信息。
 *
 * 另有一条：**某一格往上滑 = 标记删除**（设置里可改成收藏）。
 * 滑动过程中画面上要给出明确提示，松手后这一格收缩消失，
 * **同一排后面的照片跟着左移补上空位**（跨排不动 ——
 * 为了填补一个空位而让整屏照片重新排布，视觉上是一次不必要的地震）。
 */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun DayViewScreen(
    /** 这一天。null = 正在读。 */
    items: List<MediaItem>?,
    loading: Boolean,
    notice: String?,
    kindLabel: String,
    dayStart: Long,
    /** 进来时瞄准的那张（用户在整理页正看着的）。null = 从头开始。 */
    focusItem: MediaItem?,
    /** 已经被标记删除的项（[MediaItem.storeKey] 集合）—— 这些不再出现在瀑布流里。 */
    markedKeys: Set<Long>,
    /** true = 上滑删除；false = 上滑收藏。和整理页共用同一条设置。 */
    swipeUpDeletes: Boolean,
    isFavorite: (MediaItem) -> Boolean,
    /** 「喜欢」状态（只记在本应用里，不写系统相册）。 */
    isLiked: (MediaItem) -> Boolean,
    /** 切换「喜欢」。 */
    onToggleLike: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    /** 某一格上滑，要求删除。真正的文件删除仍由上层在结束时统一处理。 */
    onMarkDelete: (MediaItem) -> Unit,
    /** 某一格上滑，要求收藏 / 取消收藏。 */
    onMarkFavorite: (MediaItem) -> Unit,
    /** ↩ 撤销上一次标记删除。 */
    onUndoDelete: () -> Unit,
    /** 当前是否有可撤销的标记。 */
    canUndo: Boolean,
    /**
     * 进场飞入动效的**起点**：整理照片页里那张图 1x 时占的屏幕矩形（窗口坐标）。
     *
     * 由整理照片页在捏合触发的那一刻算好传进来 —— 它才知道那张图当时显示多大。
     * null = 不是从整理照片页进来的（没起点可飞）⇒ 不做飞入，直接显示。
     */
    introFrom: Rect? = null,
    /**
     * 焦点图在整理照片页那边**实际用的解码尺寸**。
     *
     * 焦点格照用同一个 size 请求 ⇒ 同 uri + 同 size ⇒ Coil memory cache
     * 直接命中 ⇒ 第一帧就是同一张高清图（见 `ZoomableImage.onRequestSize`）。
     * null = 拿不到，退回格子自己的尺寸（会重新解码一次，但不会错）。
     */
    introImageSize: IntSize? = null,
) {
    BackHandler(enabled = true) { onBack() }

    // ---- 进场：两种进场方式，不会同时用 ----
    //
    //  · 有飞入动效（从整理照片页捏合进来）→ **整屏从透明淡入**，
    //    不缩放也不位移。这一层 alpha 从 0 开始很关键：
    //    下面那张照片还留在原地，两边的图重合着淡入淡出，衔接才是连的。
    //    以前这里是直接 `1f`（瞬间完全不透明）⇒ 一点下去整屏「啪」地全黑，
    //    加上焦点格的图还要几十毫秒才解出来 —— 就是用户报的"黑屏一下"。
    //  · 没有飞入动效（从别处进来）→ 保留原来那套缩放+升起，
    //    用户连续两轮反馈过"仓促"，幅度拉到 12%、时间放到 520ms。
    val hasIntro = introFrom != null
    val enter = remember { Animatable(if (hasIntro) 0f else 0.88f) }

    // ---- 整屏淡入**等"焦点格能飞了"才开始** ----
    //
    // 以前 `enter` 在 `LaunchedEffect(Unit)` 里一进页面就跑完 560ms，
    // 而飞入动效要等两件事：① 那一天的内容读出来（约 30ms）；
    // ② 焦点格的位置稳定（`DayFilmstrip` 里的 settle 循环，3~20 帧）。
    // 两条动画因此**错位 100~400ms**，错位的那段时间里屏幕上发生的是：
    //   · "正中转圈 + 正在取这一天的照片…" → 照片墙，**一帧硬切**，没有淡出；
    //   · 焦点格此时还整格 `alpha = 0` 藏着（终点没测到）⇒
    //     用户刚捏小的那张照片在屏幕正中**消失了几帧**，然后才突然冒出来开始飞。
    //
    // 这就是用户一直报的"捏到触发阈值那一刻跳变"。
    // 1.3.28 / 1.3.32 / 1.3.40 / 1.3.42 修的都是别的不连续点，
    // 从来没碰过这两处。
    //
    // 现在反过来：淡入等飞入可以开跑才启动。等待期间这一层 alpha 恒为 0，
    // 底下整理页那张照片**原样留在原地**（"旧内容留在原地直到新内容可画"），
    // 转圈根本不会被看到 —— 空档被藏在了透明里，而不是塞东西去填它。
    var contentReady by remember { mutableStateOf(false) }
    val onContentReady: () -> Unit = remember { { contentReady = true } }

    // 两种情况用不着等飞入：
    //  · 没有起点（拿不到矩形，本来就不做动效）⇒ 内容一到位就淡入；
    //  · 那天读不到内容（走"读不到"那一栏，照片墙根本不会被组合）
    //    ⇒ 不等，否则会永远停在透明。
    LaunchedEffect(items, hasIntro) {
        if (items != null && (!hasIntro || items.isEmpty())) contentReady = true
    }

    // 保险丝：万一 `DayFilmstrip` 永远没报告就绪（某条分支漏了回调），
    // 也不能让这一层永久透明 —— 那比跳变更糟。
    LaunchedEffect(Unit) {
        delay(INTRO_READY_TIMEOUT_MS)
        contentReady = true
    }

    LaunchedEffect(contentReady) {
        if (!contentReady) return@LaunchedEffect
        enter.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (hasIntro) DAY_INTRO_MS else 520,
                easing = FastOutSlowInEasing,
            ),
        )
    }


    // 放大看的单张。存索引而不是条目 —— 列表本身不变，索引足够。
    var zoomedAt by remember { mutableStateOf<Int?>(null) }

    // 放大页里点收藏 / 喜欢之后的自增计数：仓库不是本文件的 state，
    // 不自己 +1 一下，那两颗按钮的选中态就不会翻过来。
    var zoomActionTick by remember { mutableIntStateOf(0) }

    /**
     * 标题上显示的那一天。
     *
     * 初值是**焦点图那天**（进来时的锚点），随后跟着滚动走：
     * 滑到前一天/后一天时自己就变（用户要求）。
     * 范围现在带着前后各一天，写死在标题里反而容易和眼前的画面对不上。
     */
    var shownDay by remember(dayStart) { mutableLongStateOf(dayStart) }

    // ⟲「回到这张照片」的信号计数：+1 一次就把视野拉回焦点格。
    var recenterSignal by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ---- 这一层为什么还在 ----
            // 底下的整理界面（照片堆叠）仍留在组合树里，只是被盖住。它的
            // `detectDragGestures` 照样会收到事件 —— 手指在这张日期视图上一划，
            // 下面那张照片也跟着飞走，同时上面两排带子也滚一下，两边都乱。
            //
            // ---- 这一层只做"兜底"，不再主动抢手势 ----
            // 之前在 Initial 阶段按方向抢：竖直自己吃掉、水平放行。那套能挡住下层，
            // 但**也把 DayCell 自己的"上滑删除"手势抢走了**（父在 Initial 先接到事件，
            // 子节点还没看到就被吃掉）。现在的手势分工是：
            //  · DayCell 的 pointerInput —— 认领"竖直向上"，触发删除；
            //  · 两排带子的 horizontalScroll —— 认领"水平滑动"；
            //  · **这里** —— 在 Final 阶段把**没有子节点认领**的事件一律吃掉。
            //
            // 为什么兜底必须做：Hit-test 链会一直往下走到被盖住的整理界面，
            // 它上面的 detectDragGestures 会认领竖直拖拽 —— 于是在空白区域一划，
            // 下面那张照片就跟着飞走了。
            // 为什么放在 Final：三个 pass 的顺序是 Initial → Main → Final，
            // 子节点（DayCell / horizontalScroll）在这之前都已处理完毕，
            // 到 Final 时能准确看出"这个事件到底有没有人要"。
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { change ->
                            if (!change.isConsumed) change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                if (hasIntro) {
                    // 飞入时**只淡入**：缩放/位移会和焦点格自己的飞入变换打架
                    // （两层变换叠加，起点位置和大小都对不上）。
                    // 背景也在这一层**里面**（见下面 `.background` 的位置），
                    // 所以它跟着一起从透明渐入 —— 底下的照片会一路透到被盖住为止。
                    alpha = enter.value
                } else {
                    scaleX = enter.value
                    scaleY = enter.value
                    alpha = enter.value
                    // 从下方约 40px 处升起。位移比缩放更难被忽略 ——
                    // "没什么变化"的反馈主要就是缩放幅度太小读不出来。
                    translationY = (1f - enter.value) * 340f
                }
            }
            // ---- 背景必须画在 alpha **里面** ----
            //
            // `Modifier.background` 只影响**它之后**的那截链。原来它写在
            // `graphicsLayer { alpha }` **前面**，也就是画在 alpha 层的外面 ——
            // 于是这个纯黑底**从头到尾都是不透明的**：进场第一帧就把底下的
            // 整理页整个盖住，`alpha = 0` 等于没设。
            // 用户报了三轮的"捏合进去黑屏一下"，根子其实在这儿
            // （他说得对：背景是黑的，所以那一闪就是黑的）。
            .background(Ink),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DayViewHeader(
                items = items,
                kindLabel = kindLabel,
                // 跟着滚动走的那一天（见 shownDay）
                dayStart = shownDay,
                onBack = onBack,
                onConfirm = onConfirm,
            )

            when {
                loading || items == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentKeep, strokeWidth = 2.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "正在取这一天的${kindLabel}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }

                items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = notice ?: "这一天已经读不到内容了",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                }

                else -> DayFilmstrip(
                    items = items,
                    focusItem = focusItem,
                    markedKeys = markedKeys,
                    swipeUpDeletes = swipeUpDeletes,
                    isFavorite = isFavorite,
                    recenterSignal = recenterSignal,
                    onRecenter = { recenterSignal++ },
                    onUndoDelete = onUndoDelete,
                    canUndo = canUndo,
                    onPick = { index -> zoomedAt = index },
                    onMarkDelete = onMarkDelete,
                    onMarkFavorite = onMarkFavorite,
                    introFrom = introFrom,
                    introImageSize = introImageSize,
                    onCenterDay = { day -> if (shownDay != day) shownDay = day },
                    // 焦点格能飞的那一刻回调上来 —— 整屏淡入就等这个信号
                    // （见 `contentReady` 的说明）。
                    onContentReady = onContentReady,
                )
            }
        }

        // ---- 放大看单张----
        val shown = items
        val at = zoomedAt
        if (shown != null && at != null && at in shown.indices) {
            // 收藏 / 喜欢都存在仓库里，不是本界面的 state ——
            // 点完必须让这一屏重新组合一次，按钮的选中态才会翻过来。
            // 所以这里挂一个 tick；「读仓库」只在这一处发生。
            val zoomedItem = shown[at]
            DayZoomView(
                item = zoomedItem,
                isFavorite = remember(zoomedItem.storeKey(), zoomActionTick) {
                    isFavorite(zoomedItem)
                },
                isLiked = remember(zoomedItem.storeKey(), zoomActionTick) {
                    isLiked(zoomedItem)
                },
                onToggleFavorite = {
                    onMarkFavorite(zoomedItem)
                    zoomActionTick++
                },
                onToggleLike = {
                    onToggleLike(zoomedItem)
                    zoomActionTick++
                },
                onClose = { zoomedAt = null },
            )
        }

    }
}

/**
 * 上下两条横带 + 底部刻度条。
 *
 * 核心约束：**两条带子必须一起动**。
 *
 * 实现上不准备两个各自的 `LazyListState`（那样会各滑各的、速度还不一样），
 * 而是**两条用同一个 `ScrollState`** —— 每一排都是把格子按
 * 「总像素宽度 + 水平偏移」自己画出来的。偏移只有一份，
 * 两条想不同步都做不到。这也顺带解决了"左右不好滑"：
 * 拖动直接改这一个偏移量，没有任何跨列表同步的损耗。
 *
 * 进场定位由**上排**自己做（它拿着 `focusItem`）—— 下排传 null。
 * 两条带子共用一个滚动偏移，如果都去"把某张推到正中"，
 * 会互相覆盖，最后停在哪取决于谁后跑。
 */
@Composable
private fun DayFilmstrip(
    items: List<MediaItem>,
    focusItem: MediaItem?,
    markedKeys: Set<Long>,
    onPick: (Int) -> Unit,
    onMarkDelete: (MediaItem) -> Unit,
    onMarkFavorite: (MediaItem) -> Unit,
    swipeUpDeletes: Boolean,
    isFavorite: (MediaItem) -> Boolean,
    /** ⟲ 被点击一次就 +1，上排据此把视野拉回焦点格。 */
    recenterSignal: Int,
    onRecenter: () -> Unit,
    onUndoDelete: () -> Unit,
    canUndo: Boolean,
    /**
     * 飞入动效的起点（窗口坐标）。见 `DayViewScreen` 的 `introFrom`。
     * null = 不做动效，格子直接出现。
     */
    introFrom: Rect?,
    /** 焦点格要照用的解码尺寸（见 `DayViewScreen` 的说明）。 */
    introImageSize: IntSize?,
    /** 屏幕正中那一天的日期（跟着滚动变），往上抛给标题。 */
    onCenterDay: ((Long) -> Unit)?,
    /**
     * "焦点格现在可以飞了" —— 往上抛给 [DayViewScreen]，它据此才开始整屏淡入。
     *
     * 见 `DayViewScreen` 里 `contentReady` 的说明：两条动画必须**同时**起步，
     * 否则中间那 100~400ms 就是用户看到的跳变。
     * 所有分支（含兜底）都必须回调一次 —— 漏了就会让整屏永远停在透明。
     */
    onContentReady: () -> Unit,
) {
    // ---- 稳定模型与稳定回调：只在 items 变化时重建一次 ----
    //
    // 这两个对象是"跟手"的关键：格子的所有参数都从这里来，
    // 只要 items 没变，它们的实例就没变 —— 于是可见区间平移时，
    // 没变的格子能整片跳过重组（见 [DayCellUi] / [DayCellActions] 的注释）。
    val cells = remember(items) {
        items.map {
            DayCellUi(
                uri = it.uri,
                kind = it.kind,
                durationMillis = it.durationMillis,
                storeKey = it.storeKey(),
                ratio = itemRatio(it),
                isAnimated = it.isAnimated,
                isLongImage = it.isLongImage,
            )
        }
    }
    val byKey = remember(items) { items.associateBy { it.storeKey() } }
    // ---- 回调用 `rememberUpdatedState` 转发，不列进 `remember` 的键 ----
    //
    // 调用点（`PhotoSessionScreen` 的 `DayViewScreen(...)`）里的 lambda 是
    // **每次重组现造**的。把它们直接列进键 ⇒ 每次交互（标记删除/收藏、
    // 点开放大、撤销、收藏 tick）都换一个新的 `DayCellActions` 实例 ⇒
    // 两排共 ~50 个 `CellSlot` 的参数 `actions` 不相等 ⇒ **整屏格子函数体重跑**。
    //
    // `rememberUpdatedState` 永远持最新值、又不引起重组，正好合适。
    // （注意不能用 `remember { {…} }` 把首次 lambda 钉死 —— 那会丢掉后续变化。）
    val pickState = rememberUpdatedState(onPick)
    val markDeleteState = rememberUpdatedState(onMarkDelete)
    val markFavoriteState = rememberUpdatedState(onMarkFavorite)
    val favoriteState = rememberUpdatedState(isFavorite)
    val actions = remember(byKey) {
        DayCellActions(
            pick = { pickState.value(it) },
            markDelete = { key -> byKey[key]?.let { markDeleteState.value(it) } },
            markFavorite = { key -> byKey[key]?.let { markFavoriteState.value(it) } },
            favorite = { key -> byKey[key]?.let { favoriteState.value(it) } ?: false },
        )
    }

    val n = items.size

    // ---- 分排：**按累计宽度贪心均衡**，焦点图那一排放上排 ----
    //
    // ---- 为什么不能固定 `index % 2` ----
    // 固定交替分排时，两排的**照片张数**总是相等，但**总像素宽度**可能差很远：
    // 一排全是横图（每格 520dp 上限）、另一排全是竖图（每格 96dp 下限），
    // 末尾就会差出一大截。而两排共用一个 [ScrollState]、
    // 每排的 `horizontalScroll` 又按**自己那排**的宽度去夹 `scroll.value`，
    // 于是滑到最右侧时短的那排已经走完、留下一大片空白，
    // 长的那排还在走（用户报"一个图片很多，另一个是空的，观感不好"）。
    //
    // 1.3.17 的解法是给短排**补尾部垫片**把它撑到一样长 ——
    // maxValue 恒等了，"分开动"修好了，可那片垫片本身**就是用户看到的那片空白**。
    // 空白是摆在那里的，藏不掉。
    //
    // 真正的解法是让两排**本来就差不多长**：顺序遍历，谁累计宽度小就放谁。
    // 宽度相近时它天然就是交替（一加就换边），所以观感仍是同一条照片墙；
    // 宽度悬殊时会连续放同一侧 —— 这反而更好，
    // 总长差被压到一格以内（< 520dp），末尾几乎看不出空白。
    //
    // 贪心用**宽高比**当权重就够：两排等高 ⇒ 格子宽度 ∝ ratio，
    // 比例关系与真实像素一致，不需要把 cellHeight 传上来。
    val splitIndices = remember(items, focusItem) {
        splitRowsBalanced(items, focusItem?.storeKey())
    }
    val topIndices = splitIndices.first
    val bottomIndices = splitIndices.second

    // 进场定位是否已落地。落地的定义是"焦点格所在那排已经把 offset 推到位"。
    //
    // 在它落地之前，两条带子的可见区间都按**本排算出来的目标偏移**播种，
    // 否则第一帧会按 offset = 0 组合出视图最左端的格子，
    // 定位落地后整片推翻重来：既闪一下，又多花一倍的组合成本
    // （用户反馈"进场后还是要等一会才能响应"，这是其中的一笔）。
    val positioned = remember { mutableStateOf(false) }

    // ---- 两排**共用**一个滚动状态：offset 就是"已经卷过去多少像素" ----
    //
    // ---- 1.3.19 曾把它改成"两排各滚各的"，那次判断是错的，已回退 ----
    // 当时用**像素差**逐帧比上下两排的变化量，看到某帧上排0.00 / 下排 26.7，
    // 就断言"「去留」两排独立滚动"。改用**互相关**重测 43fps 全帧后：
    // 137 帧里上下排位移 **100% 相同，一帧都不差** —— 两排就是锁死的。
    //
    // **教训：像素差在"画面内容本身在变"时会产生假位移差。**
    // 要测平移必须测"同一段内容的位移"（互相关），不能测"变化了多少"。
    // 方法错了，结论就必然错 —— 而且我还据此改了架构，代价更大。
    //
    // 共用是对的，而且它**天然就是锁死**：偏移只有一份，
    // 两排想不同步都做不到，不需要任何额外同步机制。
    val scroll = rememberScrollState(0)

    // ---- 间距与圆角：整屏只算一次，两排共用 ----
    //
    // 上一次是每排各算一遍自己的，于是"上下间距"和"左右间距"是两个独立的数 ——
    // 用户实测发现上下合适、左右偏窄。现在上下左右都取自**同一个** [DayMetrics.gap]，
    // 横竖一致是结构上的保证。
    //
    // 而圆角必须共享实例还有第二层理由：格子每帧都 `clip()` 它，
    // 换实例就等于全量重建裁剪节点 —— 那正是用户报的
    // "左右滑时图片有时会左右卡一下、看起来像在重新计算边框"。
    val metrics = rememberDayMetrics()
    val gapDp = metrics.gap

    // ---- 刻度条用的**稳定总长**，也是两排对齐到同一长度的依据 ----
    //
    // `scroll.maxValue` 不能直接当分母：两排共用一个 ScrollState 而总宽不同，
    // Compose 每次布局都用"最后那排"覆盖 maxValue，于是它在两排两个值之间跳，
    // 指针就跟着乱跳（用户报"不管滑照片还是拖光标，光标都乱跳"）。
    //
    // 改成各排上报自身总长、外层取**较大者** —— 与布局顺序无关的稳定值。
    val spanTop = remember { mutableFloatStateOf(0f) }
    val spanBottom = remember { mutableFloatStateOf(0f) }
    val contentSpan = maxOf(spanTop.floatValue, spanBottom.floatValue)

    // ---- 飞入动效：让焦点格自己从起点飞到位 ----
    //
    // 用户要的观感是"这张图直接飞到焦点图的位置成为焦点图，然后加载两边的图"。
    //
    // 做法刻意**不是**"另画一张会飞的图"：那要再解一次码，一旦解码慢于动画，
    // 就会看到"格子已经到位、飞行图才刚出现"的错位。
    // 这里是让**焦点格自己**从起点的位置和大小开始，一路插值回自己的位置 ——
    // 全流程一份图、一次解码，交接是天然的。
    //
    // 起点来自整理照片页，终点是焦点格自己的布局矩形。两个都用窗口坐标，
    // 可以直接相减。
    val intro = remember { Animatable(0f) }
    // 用显式的 state 对象、不用 `by` 委托：这个值要在一个
    // `remember { { ... } }` 的内层 lambda 里写回（见下面的 onFocusRect）。
    val focusRectState = remember { mutableStateOf<Rect?>(null) }
    var introTo by remember { mutableStateOf<Rect?>(null) }
    var introStarted by remember { mutableStateOf(false) }
    var introDone by remember { mutableStateOf(false) }

    // 上报回调包一层 remember：不包的话每次重组都是新 lambda，
    // `FilmstripRow` 会被判定成"参数变了"而把整排几十个格子重新组合一遍。
    // 判重也是必须的 —— 布局每次回调都写 state 就是自己制造无限重组。
    val onFocusRect: (Rect) -> Unit = remember {
        { r: Rect -> if (focusRectState.value != r) focusRectState.value = r }
    }
    val contentReadyState = rememberUpdatedState(onContentReady)

    // 键里带 `items`：这一天是异步读出来的，第一帧 items 还是空、
    // 焦点格根本没被组合。items 到位后这个 effect 重跑，才开始等位置。
    LaunchedEffect(introFrom, items) {
        if (introStarted) return@LaunchedEffect
        if (introFrom == null) {
            // 没有起点 ⇒ 不做动效，格子直接显示。
            introStarted = true
            introDone = true
            contentReadyState.value.invoke()
            return@LaunchedEffect
        }
        // ---- 等焦点格的位置**稳定**（连续两帧读到同一个矩形）----
        //
        // 为什么不是"等定位落地"：定位那几帧里格子的位置还在动，
        // 取到中间值就会飞到一个错误的地方；而"内容不足一屏、根本不需要定位"
        // 的情况下定位永远不会执行，等它会白等到超时（界面上就是一片黑）。
        // 等"稳定"没有这些问题 —— 动没动过、动了几帧，都能自然收敛。
        var prev: Rect? = null
        var stable: Rect? = null
        var frames = 0
        while (frames < INTRO_SETTLE_FRAMES) {
            withFrameNanos { }
            frames++
            val r = focusRectState.value ?: continue
            if (r == prev) {
                stable = r
                break
            }
            prev = r
        }
        val to = stable ?: focusRectState.value
        if (to == null || to.width <= 0f || to.height <= 0f) {
            // 兜底：始终没测到焦点格 ⇒ 不做动效直接显示。
            // **绝不能让它卡在这儿** —— 那会让所有非焦点格永远停在 alpha = 0。
            introStarted = true
            introDone = true
            contentReadyState.value.invoke()
            return@LaunchedEffect
        }
        introStarted = true
        introTo = to
        // ---- 飞入开跑的**同一刻**放行整屏淡入 ----
        //
        // 这一行就是修复跳变的落点：两条动画从此同时起步，
        // 中间不再有"转圈硬切 + 焦点格隐形"的那段空档
        // （详见 `DayViewScreen` 里 `contentReady` 的说明）。
        contentReadyState.value.invoke()
        intro.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = DAY_INTRO_MS, easing = FastOutSlowInEasing),
        )
        // 播完就把参数摘掉：格子的 graphicsLayer 不再读进度（省掉每帧失效）。
        introDone = true
    }

    // 注意这里**不把 `introTo` 列进键**：飞入参数从"焦点格测到位置"之前就该生效，
    // 否则格子会先在最终位置闪一帧，再跳到起点开始飞。
    // 终点还没测到时传 null，格子那边表现为"暂时不可见"（见 CellSlot）。
    val introAnim = remember(introFrom, introDone, introTo) {
        val from = introFrom
        if (introDone || from == null) null else DayIntroAnim(intro, from, introTo)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        FilmstripRow(
            indices = topIndices,
            items = items,
            scroll = scroll,
            markedKeys = markedKeys,
            focusItem = focusItem,
            ownsFocus = true,
            positioned = positioned,
            recenterSignal = recenterSignal,
            cells = cells,
            actions = actions,
            swipeUpDeletes = swipeUpDeletes,
            gapDp = gapDp,
            corner = metrics.corner,
            onSpan = { spanTop.floatValue = it },
            padToPx = contentSpan,
            intro = introAnim,
            onFocusCellPositioned = onFocusRect,
            onCenterDay = onCenterDay,
            focusDecodeSize = introImageSize,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(gapDp))
        FilmstripRow(
            indices = bottomIndices,
            items = items,
            scroll = scroll,
            markedKeys = markedKeys,
            // 下排**不参与定位**：焦点图恒在上排（见 splitRowsBalanced），
            // 而两排共用一个 ScrollState —— 上排推到位，下排自然跟着到。
            // 这里原来传的是 focusItem + ownsFocus = true，于是下排也会
            // scrollTo 一次、把上排的结果覆盖掉（"有时偏左有时偏右"的根因）。
            focusItem = null,
            ownsFocus = false,
            positioned = positioned,
            recenterSignal = recenterSignal,
            cells = cells,
            actions = actions,
            swipeUpDeletes = swipeUpDeletes,
            gapDp = gapDp,
            corner = metrics.corner,
            // 错位半格（读法二）：下排整体右移半格，竖缝错开、呈砖墙感。
            offsetHalfRow = true,
            onSpan = { spanBottom.floatValue = it },
            padToPx = contentSpan,
            // 下排不认焦点格，但同样要在飞入动画后半段淡入。
            intro = introAnim,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        DayScrubber(
            scroll = scroll,
            total = n,
            span = contentSpan,
            onSeekFraction = { f ->
                // 拖动中用 `dispatchRawDelta`（非挂起、不吃 Mutex），不用
                // `animateScrollTo`。动画会让实际滚动位置**追着**手指跑，
                // 松手那一刻指针从"手指位置"切回"真实滚动位置" ⇒ 指针猛跳一下。
                // 拖动本来就是连续控制，瞬时定位才跟手。
                val target = (f * contentSpan).toInt()
                    .coerceIn(0, scroll.maxValue.coerceAtLeast(0))
                scroll.dispatchRawDelta((target - scroll.value).toFloat())
            },
            onRecenter = onRecenter,
            onUndoDelete = onUndoDelete,
            canUndo = canUndo,
        )
    }
}

/**
 * 一条横带。**占满整个宽度**，高度由外部 `weight` 决定。
 *
 * 这一排不是 `LazyRow`，而是一个 `Row` 放在 `horizontalScroll` 里：
 *  · `Row` 的内容宽度 = 这一排所有格子宽度之和（含间距）；
 *  · `horizontalScroll(sharedState)` 让"卷过去多少"由外部统一控制。
 *
 * 两条带子共享同一个 `ScrollState` 后，**它们天然一起动** ——
 * 因为"滑到哪"这件事只有一份数据，不存在谁跟谁不同步。
 *
 * ---- 为什么用 `Row` 而不是 `LazyRow` ----
 * 懒加载列表要的是"两排共用一个滚动位置"，而 `LazyListState` 是每个列表
 * 自己的、不共享 —— 真要用两个 `LazyRow`，就得一边滚一边把位置镜像给另一边，
 * 那条路必然抖动（一帧的延迟在快速滑动时肉眼可见）。共用一个 [ScrollState]
 * 才是"天生同步"。
 *
 * ---- 但 `Row` 会一次组合全部格子 ----
 * 一天可能几百张，全量组合意味着几百个 `AsyncImage`、几百个布局节点同时挂上去，
 * 首次进入必卡。所以这里**在组合层就做可见区间的裁剪**：
 * 只有落在"当前视口 ± 一屏"范围内的格子才真正组合成 [CellSlot]，
 * 屏幕外的用两个等宽 `Spacer`（前缀 / 后缀）把位置顶住。
 *
 * 占位的宽度和真格子完全一样，所以整排的总宽度、每个格子的位置都不变 ——
 * 滚动条、刻度条、两排的对齐全都照旧。于是：
 *  · 进场只组合 ~25 个格子，而不是一天的总量；
 *  · 滚动穿过一格边界时只重组这 ~25 个，而不是整排。
 *
 * （上一版的裁剪只裁掉了 `AsyncImage`，格子本身（布局节点）还是全量组合的 ——
 * 进入时几百个节点、每次边界穿越几百次重组，这就是"进去卡一阵、滑动没反应"的来源。）
 *
 * 读 `scroll.value` 走 [derivedStateOf]：滚动时只在"可见区间真的变了"那几帧
 * 才触发重组，中间那些帧不重建格子。区间本身用**累计偏移数组上的二分查找**
 * 算（[firstGreater] / [firstAtLeast]）—— 每一帧都要重算一次，
 * 线性扫描在大天数下不是零成本。
 *
 * [focusItem] 非空时做一次进场定位：把正在看的那张推到屏幕正中。
 * 用真实算出来的宽度而不是估算 —— 一天里横图竖图混着，
 * 任何"平均宽度"的假设都会偏，用户看到的就是"没对准"。
 *
 * ---- 格子间距为什么是"格子自带"而不是 Row 的 spacedBy ----
 * `Arrangement.spacedBy` 把间距记在 Row 头上：某一格被标记删除后宽度收到 0，
 * 那段 gap 还在原位留着，后面的照片补过来时会缺一截。
 * 所以改成**每个格子自己带一段尾部留白**（见 [CellSlot]），
 * 格子收缩的时候间距跟着一起收，填补才是真正无缝的。
 */
@Composable
private fun FilmstripRow(
    indices: List<Int>,
    items: List<MediaItem>,
    /** 稳定模型：格子的数据（见 [DayCellUi]）。 */
    cells: List<DayCellUi>,
    /** 稳定回调：动作 + 收藏查询（见 [DayCellActions]）。 */
    actions: DayCellActions,
    scroll: ScrollState,
    markedKeys: Set<Long>,
    focusItem: MediaItem?,
    /**
     * 焦点图是不是在**这一排**。
     *
     * 只有为 true 时才由本排执行进场定位 —— 两条带子共用一个 [ScrollState]，
     * 都去"把焦点格推到正中"会互相覆盖，最后停在哪取决于谁后跑。
     *
     * 之前这里传的是"第几排"（`index % 2` 的余数），可分排改成按宽度贪心均衡后
     * 序号与排别不再有固定关系，所以直接给结论、不再让本排自己推算。
     */
    ownsFocus: Boolean,
    /**
     * 定位是否已落地，两条带子**共享同一个**状态。
     *
     * 刻意传 [MutableState] 而不是回调：这个对象实例是稳定的，
     * 把它列为参数不会在定位落地那一帧把整排拖去重组；
     * 而"落地"要同时通知两条带子，各传一份 Boolean 反而会因为
     * 求值时机不同而闪一帧。
     */
    positioned: MutableState<Boolean>,
    /** ⟲ 被点击一次就 +1：据此把视野拉回焦点格（只在焦点图所在那排响应）。 */
    recenterSignal: Int,
    /** true = 上滑删除；false = 上滑收藏（与整理页同一条设置）。 */
    swipeUpDeletes: Boolean,
    /**
     * 本排的左右间距 —— **由外部传入，和上下间距同一个值**。
     *
     * 不在这里自己算，是为了让"横竖一致"成为结构保证：
     * 两排拿的是同一个 `DayMetrics.gap`，不存在"两个数字碰巧接近"。
     */
    gapDp: Dp,
    /** 共享的圆角形状实例 —— 换实例会让所有格子重建裁剪节点。 */
    corner: RoundedCornerShape,
    /**
     * 把本排的总长（px）上报给外层，供刻度条当**分母**。
     *
     * 分母跟着"当前那一排"走，所以这里报的是**本排自己**的跨度，
     * 不是两排的较大者 —— 两排各滚各的之后，取大者会让指针读数失真。
     */
    onSpan: (Float) -> Unit,
    /**
     * 尾部要补多少像素，把本排凑到外层算出的**统一总长**。
     *
     * 两排共用一个 [ScrollState] 时，每排的 `horizontalScroll` 都按
     * **自己那排**的内容宽去夹同一个 `scroll.value`。
     * 两排宽度不同 ⇒ 两个 maxValue 不同 ⇒ 滑到末尾时短的那排先到顶停住、
     * 长的那排还在走（用户报"一排停下来另一排还在动"）；
     * 共享值被两排反复夹来夹去，也是闪退的来源。补齐后 maxValue 恒等。
     *
     * **垫片本身要小**：分排已改成按宽度贪心均衡（见 [splitRowsBalanced]），
     * 两排总长差被压到一格以内，所以这里只补最后那点 ——
     * 屏幕上就是照片墙收尾处的自然留白。
     */
    padToPx: Float,
    /**
     * true = 这一排整体右移半格（砖墙错落，只有下排为true）。
     *
     * 「去留」那边的常态界面就是这个排布：上下两排的竖缝错开，
     * 比"起点对齐"更像一面手摆的照片墙、错落有致。
     * **只改这一排的起点**，逐格宽度与间距一律不动 ——
     * 于是滚动、惯性、定位、虚拟化全部沿用原体系，风险极低。
     */
    offsetHalfRow: Boolean = false,
    /** 进场飞入动效；null = 不做（从别处进来，或者已经播完）。 */
    intro: DayIntroAnim? = null,
    /** 焦点格上报自己的屏幕矩形 —— 飞入动画的终点。 */
    onFocusCellPositioned: ((Rect) -> Unit)? = null,
    /**
     * 屏幕正中现在是**哪一天**（那一天 0 点的毫秒值）。只给上排传。
     *
     * 标题上的日期跟着滚动走：滑到前一天/后一天时日期自己就变 ——
     * 比一句静态的"含前后各一天"准确得多（用户要求）。
     */
    onCenterDay: ((Long) -> Unit)? = null,
    /** 焦点格专用的解码尺寸（照用整理照片页那份，让 memory cache 命中）。 */
    focusDecodeSize: IntSize? = null,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    // 松手惯性用的 Job：新手势一按下就 cancel 掉上一次。
    var flingJob by remember { mutableStateOf<Job?>(null) }
    // 上一次上报给刻度条的总长 —— 用来去重，避免每帧写 state 自己制造卡顿。
    var lastReportedSpan by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val cellHeight = (maxHeight - VERTICAL_PAD * 2).coerceAtLeast(48.dp)
        // 先取出来 —— BoxWithConstraints 的作用域在 Row 的 lambda 里会被屏蔽，
        // 直接写 maxWidth 会命中 RowScope 的同名成员而报错。
        val halfViewport = (maxWidth / 2).coerceAtLeast(0.dp)
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.roundToPx().toFloat() }
        val halfViewportPx = with(density) { halfViewport.roundToPx().toFloat() }

        // 每个格子的宽度先算出来（纯函数，和真实布局用的是同一套算法）。
        val cellWidths = remember(items, indices, cellHeight) {
            indices.map { global ->
                items.getOrNull(global)?.let { cellWidthDp(it, cellHeight) } ?: CELL_MIN_WIDTH
            }
        }

        // **错位半格**（读法二）：下排整体右移半格，两排的竖缝错开、呈砖墙感。
        //
        // 位移量取"这一排最长那格的一半"—— 格子宽度不等（横图更宽），
        // 固定值会在窄图那几格旁边留一道怪缝；用排内最大值的一半，
        // 偏移量与这一排的"节拍"同源，错落才自然。
        // 只改**起点**，逐格宽度/间距一律不动 ⇒ 滚动与定位体系完全不受影响。
        val rowOffsetPx = remember(cellWidths, density) {
            if (offsetHalfRow && cellWidths.isNotEmpty()) {
                with(density) { (cellWidths.max() / 2f).roundToPx().toFloat() }
            } else {
                0f
            }
        }

        // 每个格子是否已被标记删除（标记 = 立即离场，同排左移补位）。
        val removedFlags = remember(items, indices, markedKeys) {
            BooleanArray(indices.size) { i ->
                items.getOrNull(indices[i])?.storeKey() in markedKeys
            }
        }

        // 累计偏移（px）：cumPx[i] = 第 i 格左边缘，相对第一格
        // （不含两端各半个视口的垫片）。被标记删除的格子贡献 0 ——
        // 它连同自带的尾间距一起收缩没了，总宽度、后面每格的位置都跟着变。
        //
        // ---- 必须用 `roundToPx`，不能用 `toPx` ----
        // Compose 布局时把 Dp 宽度是**取整**到像素的（`Modifier.width(dp)` 走
        // `roundToPx`）。模型这边若留着小数，每一格都会差零点几像素，
        // 一两百格累积起来就是几像素到几十像素的偏移 —— 进场定位就会"差一点"。
        // 两边用同一个取整规则，模型才和真实布局**逐格恒等**。
        val gapPx = with(density) { gapDp.roundToPx() }
        val cumPx = remember(cellWidths, removedFlags, density) {
            val cum = FloatArray(cellWidths.size + 1)
            var acc = 0f
            for (i in cellWidths.indices) {
                cum[i] = acc
                acc += if (removedFlags[i]) 0f
                else with(density) { cellWidths[i].roundToPx().toFloat() } + gapPx
            }
            cum[cellWidths.size] = acc
            cum
        }

        // 本排的**内容**总长 —— 上报给外层当刻度条的分母。
        val ownSpan = cumPx.getOrElse(cumPx.size - 1) { 0f } + halfViewportPx * 2f
        // 只在真的变了才写 state：无脑每帧写等于自己制造卡顿。
        SideEffect {
            if (kotlin.math.abs(ownSpan - lastReportedSpan) > 0.5f) {
                lastReportedSpan = ownSpan
                onSpan(ownSpan)
            }
        }
        // 尾部补齐：把本排凑到统一总长，maxValue 与另一排恒等（见 padToPx）。
        // 只有确实短的那排才需要补，不会出现"补完又超"的正反馈。
        val tailPadPx = (padToPx - ownSpan).coerceAtLeast(0f)

        // ---- 进场定位：把正在看的那张推到屏幕正中 ----
        // maxValue 要等第一次布局才知道，所以既做守卫（没布局好就再等等），
        // 又把它列为 effect 的键 —— 布局一好它就会变，effect 自然重跑。
        val focusIndex = remember(items, focusItem) {
            val key = focusItem?.storeKey() ?: return@remember -1
            items.indexOfFirst { it.storeKey() == key }
        }

        // 焦点图在哪一排，就由**那一排**执行定位。
        //
        // 分排改成按宽度贪心均衡之后，焦点图恒在上排（见 [splitRowsBalanced]），
        // 但**定位仍必须用焦点图自己那一排的 cumPx**：
        // 两排共用一个 ScrollState，偏移量只有一份，
        // 而各排的 cumPx 不同，拿错排的算式必然对不准。
        val ownsFocusRow = ownsFocus && focusIndex >= 0
        // 局部序号：焦点图在**它自己那一排**里的位置。
        // 不能写 `focusIndex / 2` —— 那只在"偶数在上排"时成立；
        // topParity = 1（上排是奇数组）时本排第 k 张的序号是 (focusIndex - 1) / 2。
        // 通用算法：在本排的 indices 里按下标找。
        val focusRowItem = remember(indices, focusIndex) {
            if (focusIndex < 0) -1 else indices.indexOfFirst { it == focusIndex }
        }

        // 目标偏移是**纯计算**，不依赖布局。
        //
        // ---- 要对齐的是格子**中心**，不是左边缘 ----
        // `cumPx[k]` 是第 k 格的**左边缘**。若直接拿它当"落在屏幕正中"，
        // 实际效果是这一格的左边缘对齐中线 ⇒ 整张图偏左了**半个格子宽**。
        // 格子宽 100~500px，肉眼就是"明显没对准"。
        // 所以必须加上该格宽度的一半：`cumPx[k] + w/2` 才是它的中心。
        //
        // ---- `focusRowItem` 可能是 -1，必须先守卫再取索引 ----
        // 它是 `indices.indexOfFirst { ... }`，找不到就是 -1；
        // 而 `BooleanArray.getOrElse(-1)` / `FloatArray.getOrElse(-1)` 在某些
        // 实现上会抛下标异常 —— 用户报过"移到最右边时闪退"，就是这个。
        // 所以下面用局部序号做**双重守卫**，任何一项不成立就直接给 0。
        val focusRowValid = ownsFocusRow && focusRowItem in indices.indices
        val focusCellW = if (
            focusRowValid && !removedFlags.getOrElse(focusRowItem) { true }
        ) {
            with(density) { cellWidths.getOrElse(focusRowItem) { 0.dp }.roundToPx().toFloat() }
        } else {
            0f
        }
        val focusTargetPx = remember(cumPx, focusIndex, viewportPx, halfViewportPx, rowOffsetPx, focusCellW) {
            if (focusItem == null || focusIndex < 0 || !focusRowValid ||
                cumPx.size <= 1 || viewportPx <= 0f
            ) {
                -1f
            } else {
                halfViewportPx + rowOffsetPx +
                    (cumPx.getOrElse(focusRowItem) { 0f } + focusCellW / 2f) -
                    viewportPx / 2f
            }
        }

        // ---- 进场目标偏移：**只有焦点图所在的那一排**才有值 ----
        //
        // 这里原来有个 else 分支：「焦点图不在本排，就按 `focusIndex / 2`
        // 折算出一个"对应的"格子，也去定位」。那是旧版 `index % 2` 交错分排
        // 时代的遗留 —— 分排改成按宽度贪心均衡之后（见 [splitRowsBalanced]），
        // 序号和排别已经没有固定关系，`focusIndex / 2` 折出来的**根本不是
        // 对应的那一张**，只是碰巧落在下标范围内而已。
        //
        // 更要命的是：两排共用一个 `ScrollState`，两边都去 `scrollTo` 就是
        // 互相覆盖，最后停在哪完全取决于**谁的 effect 后跑** ——
        // 这正是用户报了两轮的「有时候偏左、有时候偏右」。
        // （另一排本来就不用定位：滚动位置只有一份，一排推到位，两排一起到。）
        val seededTarget = remember(focusTargetPx) {
            if (focusTargetPx >= 0f) focusTargetPx else -1f
        }
    // ---- 屏幕正中的格子是哪一天 ----
    //
    // 只在**算出来的那一天真的变了**时才写 state（`distinctUntilChanged`）——
    // 每帧写一次就是自己制造重组，而这个值大部分时间是不变的。
    val onCenterDayState = rememberUpdatedState(onCenterDay)
    LaunchedEffect(cumPx, indices, items, viewportPx, rowOffsetPx) {
        if (onCenterDayState.value == null || cumPx.size <= 1 || viewportPx <= 0f) {
            return@LaunchedEffect
        }
        snapshotFlow { scroll.value }
            .map { offset ->
                // 屏幕中心落在本排内容坐标里的位置（扣掉这一排自己的起点偏移）
                val centerPx = offset.toFloat() + viewportPx / 2f - rowOffsetPx
                val slot = cumPx.slotAt(centerPx)
                val global = indices.getOrNull(slot) ?: return@map null
                items.getOrNull(global)?.dateTakenMillis?.let { dayStartOf(it) }
            }
            .distinctUntilChanged()
            .collect { day -> if (day != null) onCenterDayState.value?.invoke(day) }
    }

        // ---- 本排**自己**的滚动上界 ----
        //
        // `scroll.maxValue` 不能用：两排共用一个 ScrollState，而 Compose 每次布局
        // 都用"最后布局的那排"覆盖 maxValue ⇒ 它在两排两个值之间跳。
        // 焦点图在上排，如果那一刻 maxValue 恰好是**下排**的（更小），
        // `focusTargetPx` 就被 clamp 到一个小值 ⇒ **照片明显偏左**。
        // （用户报"焦点图有点偏左，之前有过偏右"—— 偏右是同一个病的另一面：
        //  maxValue 更大时 clamp 不生效，但下排又会用自己的值把偏移夹回去。）
        //
        // 本排上界 = 本排内容总长 - 视口。用它 clamp 才是"本排能滚到哪"。
        val ownMaxScroll = remember(cumPx, viewportPx, halfViewportPx, rowOffsetPx, tailPadPx) {
            val total = cumPx.getOrElse(cumPx.size - 1) { 0f } +
                halfViewportPx * 2f + rowOffsetPx + tailPadPx
            (total - viewportPx).toInt().coerceAtLeast(0)
        }

        // 只定位一次：用户随后滑走了，不会被重新拽回焦点。
        //
        // 守卫刻意用**局部**标志而不是共享的 positioned：positioned 还承担
        // "初始种子是否还有效"，两者混用的话，另一排的 effect 一旦抢先置位，
        // 本排的定位会被直接跳过（两条带子的 effect 执行顺序不构成保证）。
        var hasPositioned by remember { mutableStateOf(false) }
        LaunchedEffect(cumPx, focusIndex, viewportPx, ownMaxScroll, seededTarget) {
            // 布局还没好（本排总长还是 0）→ 再等等。ownMaxScroll 是 effect 的键，
            // 布局一好它就会变，这一轮自然重跑。
            if (ownMaxScroll <= 0) return@LaunchedEffect
            if (seededTarget >= 0f && !hasPositioned) {
                hasPositioned = true
                val target = seededTarget.toInt().coerceIn(0, ownMaxScroll)
                // ---- 不猜时机：每帧检查，没对上就再推一次，直到对上 ----
                //
                // 为什么不能"推一次就完事"：
                // `scroll.scrollTo(x)` 内部走 `scrollBy`，会被 `ScrollState.maxValue` 夹，
                // 而 maxValue **什么时候**才是最终值，取决于两条带子谁最后测量、
                // 以及虚拟化区间分几批落地 —— 进场那一刻它常常还是 0，
                // `scrollTo(5000)` 的结果就是 0（完全没滚动，焦点图压根没被推到中间）；
                // 或者只到位一半（`maxValue` 是另一排留下的值），
                // 于是表现为"有时候偏左、有时候偏右"（用户报了两轮）。
                //
                // 试过"等 maxValue 涨到目标值以上再推一次"，还是会偏 ——
                // 说明它到位不是一个单点事件。所以这里干脆**不依赖任何时机**：
                // 一帧一帧地看，位置不对就再推一次。布局什么时候完成都无所谓，
                // 它一完成，下一帧就把位置推到位。
                //
                // 上限 [POSITION_MAX_TRIES] 帧（约 0.3 秒）纯粹是防死循环；
                // 正常情况下 1~2 帧就结束了，用户看不见这个循环。
                var tries = 0
                while (tries < POSITION_MAX_TRIES &&
                    kotlin.math.abs(scroll.value - target) > 1
                ) {
                    scroll.scrollTo(target)
                    withFrameNanos { }
                    tries++
                }
            }
            positioned.value = true
        }

        // ---- ⟲「回到这张照片」----
        // 滑远了以后一键把焦点格拉回正中。用**信号计数**触发而不是回调：
        // 回调要从 DayViewScreen 一路传下来，信号只要一个 Int；
        // 而且它天然是"每次点击都算一次新事件"，不会被重组吞掉。
        LaunchedEffect(recenterSignal) {
            if (recenterSignal == 0 || !focusRowValid) return@LaunchedEffect
            if (focusTargetPx >= 0f && ownMaxScroll > 0) {
                // 用动画而不是瞬移：这个动作的意义就是"让你看清它回到哪儿了"。
                // 上界同样用本排自己的。
                val clamped = focusTargetPx.toInt().coerceIn(0, ownMaxScroll)
                scroll.animateScrollTo(clamped)
            }
        }

        // 可见区间：当前偏移 ± 一屏。cumPx 非降序，二分查找。
        //
        // 定位落地前用**本排算出的目标**播种，而不是 `scroll.value = 0` ——
        // 否则第一帧按 offset = 0 组合格子（视图最左端），
        // 定位落地那一刻整片推翻重来：闪一下不说，还多花一倍组合成本。
        //
        // `seededTarget` 就是那个"本排的目标偏移"：焦点图在本排时是它的正中，
        // 否则由焦点序号折算出的对应格。两排各算各的，不需要外部广播。
        val visibleRange by remember(
            scroll, viewportPx, cumPx, halfViewportPx, positioned, rowOffsetPx, seededTarget,
        ) {
            derivedStateOf {
                val n = cumPx.size - 1
                if (n <= 0) return@derivedStateOf 0..-1
                val offset = if (positioned.value || seededTarget < 0f) {
                    scroll.value.toFloat()
                } else {
                    seededTarget
                }
                // 窗口换算到"相对第一格"的坐标系：
                // cumPx 不含两端垫片，也不含错位半格那一段前置偏移。
                val shift = rowOffsetPx + halfViewportPx
                // ---- 前伸 / 后延各 0.5 屏，不再是整屏 ----
                //
                // ±1 屏时窗口宽 3 个视口，两排同时组合 ~74 个格子；
                // 收到 ±0.5 屏后约 50 个 —— 组合量、布局子节点、
                // 滑动中新建的图片请求各少 1/3。
                //
                // 0.5 屏（≈540-720px）仍大于一次掉帧（40ms）内惯性能走的
                // 最大位移：人手最快 flick ≈8000px/s × DRAG_GAIN 1.25 × 0.04s ≈ 400px。
                // 余量充足，正常滑动不会看到边缘空白。
                val left = offset - viewportPx * 0.5f - shift
                val right = offset + viewportPx * 1.5f - shift
                val from = (firstGreater(cumPx, left) - 1).coerceAtLeast(0)
                val to = (firstAtLeast(cumPx, right) - 1)
                    .coerceAtMost(n - 1)
                    .coerceAtLeast(from)
                from..to
            }
        }
        val rangeFrom = visibleRange.first
        val rangeTo = visibleRange.last

        Row(
            modifier = Modifier
                .fillMaxSize()
                // ---- 自绘横向拖拽（内建的只留布局与裁剪）----
                //
                // 为什么不能再靠 `horizontalScroll` 的手势：
                // 它的 slop 检测跑在 **Main** pass（子节点先于父节点），
                // 格子上的竖直判定会在横滑起手时先 `consume()` 掉事件，
                // 于是"整次拖拽被取消"—— 表现就是**隔一会儿能划一下、
                // 然后又滑不动**（1.2.7 日志实证过 6% 的死手就是这个模式）。
                //
                // 这一版把方向判定搬到自己手里，跑在 **Initial** pass：
                // 父级先于子节点拿到事件，判定为横向就当场认领；
                // 判成竖直就一声不吭，完全让给格子的上滑手势。
                //
                // 滚动本身走 `dispatchRawDelta` —— 不经过 Mutex，
                // 所以任何时刻按下都能立刻跟手，不受上一次惯性/动画影响。
                .pointerInput(scroll) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 新手势一律打断上一次惯性
                        flingJob?.cancel()
                        var mine = false
                        var lastX = down.position.x
                        var lastT = 0L
                        var velocity = 0f
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val totalX = change.position.x - down.position.x
                            val totalY = change.position.y - down.position.y
                            if (!mine) {
                                if (kotlin.math.abs(totalX) > slop ||
                                    kotlin.math.abs(totalY) > slop
                                ) {
                                    if (kotlin.math.abs(totalX) >= kotlin.math.abs(totalY)) {
                                        mine = true
                                        // 从"越过 slop 的这一刻"起算位移，
                                        // 前面积累的那一小段不补滚，避免起手一跳
                                        lastX = change.position.x
                                    } else {
                                        // 竖直 → 整串事件让给格子的上滑手势
                                        return@awaitEachGesture
                                    }
                                } else {
                                    continue
                                }
                            }
                            val dx = (change.position.x - lastX) * DRAG_GAIN
                            val now = System.currentTimeMillis()
                            if (lastT > 0L) {
                                velocity = dx * 1000f / (now - lastT).coerceAtLeast(1L)
                            }
                            lastT = now
                            lastX = change.position.x
                            change.consume()
                            scroll.dispatchRawDelta(-dx)
                        }
                        if (mine && kotlin.math.abs(velocity) > 200f) {
                            // ---- 松手后的惯性：指数衰减滑行 ----
                            //
                            // 用户原话："想要苹果那种滑动停下来之后还有一点惯性的感觉，
                            // 现在这个太跟手了，一停下来就直接戛然而止"。
                            // 上一版只投影 0.13 秒就走了个短动画，等于没有滑行。
                            //
                            // 现在按 iOS `UIScrollView` 的减速率来：速度每毫秒乘 0.998
                            // （`decelerationRate = .normal`），每帧把这一帧该走的位移
                            // 直接 `dispatchRawDelta` 进去 —— 不吃 Mutex、不经过动画，
                            // 所以随时按下都能立刻接住（`flingJob` 会在按下时 cancel）。
                            flingJob = scope.launch {
                                // 起手速度放大 1.6 倍：用户反馈"左右滑动使图片滑动的幅度
                                // 有点小"。**只放大松手这一脚，拖拽本身仍是 1:1 跟手** ——
                                // 直接给拖拽加增益会毁掉"跟手"这个已经调好的手感。
                                var v = velocity * 1.6f
                                var last = withFrameNanos { it }
                                while (kotlin.math.abs(v) > 60f) {
                                    val now = withFrameNanos { it }
                                    // 掉帧时按真实间隔补位移，别把速度算成 16ms 的
                                    val dtMs = ((now - last) / 1_000_000f)
                                        .coerceIn(1f, 40f)
                                    last = now
                                    v *= Math.pow(0.998, dtMs.toDouble()).toFloat()
                                    scroll.dispatchRawDelta(-v * dtMs / 1000f)
                                }
                            }
                        }
                    }
                }
                .horizontalScroll(scroll, enabled = true),
            // 刻意**不给 spacedBy** —— 间距由每个格子自己带（见 [CellSlot]），
            // 否则某项被删除后，它那段间距会留在原地。
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 两端各垫半个视口，让首尾两张也能滑到屏幕正中。
            Spacer(Modifier.width(halfViewport))

            // 错位半格：紧跟端部垫片的一段额外前置偏移。
            // 放在这里而不是塞进 cumPx，是为了让 cumPx 保持"纯格子宽度"的语义
            // ——定位、可见区间、区间二分各自减去同一个 shift 即可，不必改算法。
            if (rowOffsetPx > 0f) {
                Spacer(Modifier.width(with(density) { rowOffsetPx.toDp() }))
            }


            // 屏幕外的前缀：一个等宽占位。不组合那些格子 ——
            // 一天几百张，全组合就是几百个布局节点。
            if (rangeFrom > 0) {
                WideSpacer(cumPx[rangeFrom])
            }

            // 只组合可见区间里的格子。
            //
            // 必须按格子本身（global）给 key：区间每平移一格，所有格子的
            // **循环位置**都会跟着挪一位。没有 key 的话，位置 i 上的状态
            // （收缩动画、图片请求）会错到另一张图上 —— 表现为每越过一格
            // 边界，整屏图片请求全部重来一遍。有了 key，状态跟着格子走，
            // 每边只真实组合 / 销毁各一格。
            for (localIndex in rangeFrom..rangeTo) {
                val global = indices.getOrNull(localIndex) ?: continue
                val item = items.getOrNull(global) ?: continue
                key(global) {
                    // 焦点格在这一排里的**局部**序号就是 focusRowItem（见上面的计算）。
                    val isFocusCell = ownsFocusRow && localIndex == focusRowItem
                    CellSlot(
                        cell = cells[global],
                        removed = removedFlags[localIndex],
                        width = cellWidths[localIndex],
                        rowHeight = cellHeight,
                        gap = gapDp,
                        corner = corner,
                        index = global,
                        swipeUpDeletes = swipeUpDeletes,
                        actions = actions,
                        // 只有焦点格带飞入动效；其余格子只在动画后半段淡入
                        // （靠 alpha），所以它们也要拿到同一个 intro 对象。
                        intro = intro,
                        isFocusCell = isFocusCell,
                        onFocusCellPositioned = if (isFocusCell) onFocusCellPositioned else null,
                        // 焦点格改按照片页那个尺寸请求（缓存命中，见参数说明）
                        focusDecodeSize = if (isFocusCell) focusDecodeSize else null,
                    )
                }
            }

            // 屏幕外的后缀：同理。
            if (rangeTo < indices.lastIndex) {
                val suffixPx = cumPx[indices.size] - cumPx[rangeTo + 1]
                WideSpacer(suffixPx)
            }

            // ---- 尾部补齐垫片：把较短的那排凑到统一总长 ----
            //
            // 放在最后端垫片**之外**：它不属于"内容"，只是把这一排的总宽撑齐，
            // 不参与 cumPx 的计算，所以不会干扰定位与可见区间。
            //
            // 有了 `splitRowsBalanced` 的贪心均衡，这里补的量只有最后不到一格，
            // 不会再出现"滑到最右侧一排图片很多、另一排整片空白"。
            if (tailPadPx > 0f) {
                WideSpacer(tailPadPx)
            }

            Spacer(Modifier.width(halfViewport))
        }
    }
}

/**
 * 把照片分到上下两排，**按累计宽度贪心均衡**，并保证焦点图落在上排。
 *
 * 返回 `(上排 indices, 下排 indices)`，两排都保持原始顺序。
 *
 * ---- 为什么不是 `index % 2` ----
 * 固定交替分排只保证**张数**相等，**总像素宽度**可能差出一大截
 * （一排全是横图撞 520dp 上限、另一排全是竖图贴 96dp 下限）。
 * 两排共用一个 `ScrollState`，每排又按自己那排的宽度去夹 `scroll.value`，
 * 于是滑到末尾时短排先到顶、留下一大片空白而长排还在走 ——
 * 用户报的"滑到最右侧，一个图片很多另一个是空的，观感不好"。
 *
 * 贪心：顺序遍历，当前累计宽度小的那排接收这张照片。
 * 宽度相近时它天然退化成交替（一加就换边），观感仍是同一条照片墙；
 * 宽度悬殊时会连续放同一侧，这反而让总长差被压到**一格以内**。
 *
 * 权重用**宽高比**就够：两排等高 ⇒ 格子宽度 = f(宽高比)，
 * 比例关系与真实像素一致，不需要把 `cellHeight` 传进来。
 *
 * @param focusKey 焦点照片的 storeKey：它必须在上排（进场要"上排正中间"）。
 *                 传 null 就纯按宽度分。
 */
private fun splitRowsBalanced(
    items: List<MediaItem>,
    focusKey: Long?,
): Pair<List<Int>, List<Int>> {
    if (items.isEmpty()) return emptyList<Int>() to emptyList<Int>()
    val focusIndex = focusKey?.let { key -> items.indexOfFirst { it.storeKey() == key } } ?: -1

    val top = ArrayList<Int>()
    val bottom = ArrayList<Int>()
    var topW = 0f
    var bottomW = 0f
    for (i in items.indices) {
        // 取不到尺寸时给个中性的权重（1:1），不影响均衡的 correctness。
        val w = itemRatio(items[i]).coerceAtLeast(0.05f)
        // 焦点图直接进上排，不参与"谁短给谁"的博弈 ——
        // 否则它可能被分到下排，而进场定位要求它在上面。
        val toTop = i == focusIndex || topW <= bottomW
        if (toTop) {
            top += i
            topW += w
        } else {
            bottom += i
            bottomW += w
        }
    }
    // 焦点图已经保证在上排；这里只兜底"没给焦点 / 焦点已被删"的情况。
    return top to bottom
}

/** 第一个满足 `cum[k] > x` 的下标（cum 非降序）。 */
private fun firstGreater(cum: FloatArray, x: Float): Int {
    var lo = 0
    var hi = cum.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (cum[mid] > x) hi = mid else lo = mid + 1
    }
    return lo
}

/** 第一个满足 `cum[k] >= x` 的下标（cum 非降序）。 */
private fun firstAtLeast(cum: FloatArray, x: Float): Int {
    var lo = 0
    var hi = cum.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (cum[mid] >= x) hi = mid else lo = mid + 1
    }
    return lo
}

/**
 * 一个格子的位置槽：**格子宽度 + 尾部间距**。
 *
 * 某一格被标记删除后，它的宽度（连同自带的那段尾间距）平滑收到 0，
 * **同一排后面的格子随即左移补上空位** —— 这正是用户要的效果。
 *
 * ---- 为什么不用 `animateContentSize` ----
 * 一天几百格，`animateContentSize` 会给**每一格**都加一层测量节点；
 * 滚动穿过一格边界、整排重组时，这几百层全部跟着重新测量、重新走一遍布局。
 * 而真正需要动画的**只有被标记的那一格** —— 所以这里只给需要的格子建
 * 一份 [Animatable]：没被标记的格子目标值恒等于当前值，动画处于空闲，
 * 不触发任何重组，也没有任何多余的布局层。
 *
 * [removed] 为真时宽度和尾间距一起收到 0 —— 于是这一整段（含它自带的间距）
 * 平滑收起来，**同一排后面的格子随即左移补上空位**。进场时就已标记的格子
 * 直接是 0，不补演一次收缩。
 */
@Composable
private fun CellSlot(
    cell: DayCellUi,
    removed: Boolean,
    width: Dp,
    rowHeight: Dp,
    gap: Dp,
    /** 共享的圆角形状实例（见 [DayMetrics]）。 */
    corner: RoundedCornerShape,
    /** 这一格在"这一天"里的序号（点击时回传）。 */
    index: Int,
    swipeUpDeletes: Boolean,
    actions: DayCellActions,
    /** 进场飞入动效。null = 不做（从别处进来，或者已经播完）。 */
    intro: DayIntroAnim?,
    /** 这一格是不是焦点格 —— 只有它做飞入变换、也只有它上报自己的屏幕位置。 */
    isFocusCell: Boolean,
    /** 焦点格上报自己的屏幕矩形（进场动效的终点）。 */
    onFocusCellPositioned: ((Rect) -> Unit)?,
    /** 焦点格专用的解码尺寸（照用照片页那份，让 memory cache 命中）。 */
    focusDecodeSize: IntSize? = null,
) {
    // ---- 上滑即消失：宽度收到 0，同排后面的格子左移补上空位 ----
    //
    // （1.3.1 曾按"选中待删、留在原地"做过一版，随后确认「去留」实际也是
    //  "上滑即消失 + 退出时走系统弹窗"，于是改回这条动线。）
    val density = LocalDensity.current
    val fullPx = with(density) { width.roundToPx().toFloat() }
    val widthPx = remember(width) { Animatable(if (removed) 0f else fullPx) }
    LaunchedEffect(removed, width) {
        val target = if (removed) 0f else fullPx
        if (widthPx.value != target) {
            widthPx.animateTo(target, CELL_SHRINK_SPEC)
        }
    }
    val w = with(density) { widthPx.value.toDp() }
    // 尾间距跟着同一个比例收缩 —— 不然图缩到 0 了，那道缝还留在原地，
    // 后面的照片补过来时会缺一截。
    val frac = if (fullPx <= 0f) 0f else (widthPx.value / fullPx).coerceIn(0f, 1f)

    // ---- 占位宽度必须**恰好**等于「本格宽度 + 尾间距」 ----
    //
    // 之前写的是 `Modifier.width(w).padding(end = gap * frac)`，看着像"格子自带间距"，
    // 其实是错的：**padding 在盒子内部**，`width(w)` 把总宽钉死成 w，
    // padding 只是把图片内容压窄 gap，总宽一点没变。
    //
    // 而 [cumPx] 那边的模型是"每格步进 = 宽度 + gap"。两边对不上，
    // 于是第 k 格的实际位置比模型少了 k × gap：
    //  · 进场定位会**偏右**（滚过头了，照片落在中心右边）；
    //  · 更糟的是这个误差是 (k − 区间起点) × gap —— 可见区间每平移一格，
    //    整排就横跳一个 gap ⇒ 用户报的"左右滑时图片会左右卡一下"
    //    和"上下排会有小小错位"（两排的区间起点不同 ⇒ 偏移量不同）。
    //
    // 修法：**外层盒子占 (w + gap)，内层盒子放图片**。
    // 缩放时两者按同一比例收 ⇒ 步进与模型恒等。
    Box(
        modifier = Modifier
            .width(w + gap * frac)
            // 上报的是**布局位置**（不受 graphicsLayer 变换影响），
            // 正是飞入动画需要的"这一格最终该在哪儿"。
            .then(
                if (isFocusCell && onFocusCellPositioned != null) {
                    Modifier.onGloballyPositioned {
                        onFocusCellPositioned(it.boundsInWindow())
                    }
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                // 只有焦点格要飞 —— 其余格子**一个像素都不动**：
                // 它们跟着整屏的 `enter` 一起淡入，飞行途中就已经看得见画框了。
                if (!isFocusCell) return@graphicsLayer
                val a = intro ?: return@graphicsLayer
                // 全部读在绘制阶段 —— 每帧改写的只有绘制指令，组合不重跑。
                val k = 1f - a.progress.value
                // ---- 焦点格：从照片页那一张的位置"飞"到自己该在的位置 ----
                // k = 1 时完全还在起点，k = 0 时已经在自己的位置上，中间线性插值。
                // graphicsLayer 的缩放以**中心**为轴，所以两边都按中心对齐。
                val to = a.to
                if (to == null || to.width <= 0f || to.height <= 0f) {
                    // 终点还没测到 —— 先藏起来。**不能**按"无变换"画，
                    // 那会在最终位置闪一下再跳回起点。
                    alpha = 0f
                } else if (k > 0f) {
                    val from = a.from
                    translationX = (from.center.x - to.center.x) * k
                    translationY = (from.center.y - to.center.y) * k
                    // ---- 用**等比**缩放 ----
                    //
                    // 两头（照片页那张图 / 焦点格）的宽高比不一定严丝合缝：
                    // 格子宽度有上下限（[CELL_MAX_WIDTH] / [CELL_MIN_WIDTH]），
                    // 极端比例的照片会被夹一下。分别按宽、高算两个比例的话，
                    // 飞行途中图片会被**拉变形**（一头宽一头窄），
                    // 用户看到的就是"飞上去那一下衔接不顺畅"。
                    // 取几何平均当统一缩放，图全程不变形；位置仍然按中心对齐。
                    val s = kotlin.math.sqrt(
                        (from.width / to.width) * (from.height / to.height),
                    )
                    scaleX = 1f + (s - 1f) * k
                    scaleY = 1f + (s - 1f) * k
                }
            },
    ) {
        if (!removed) {
            Box(modifier = Modifier.width(w)) {
                DayCell(
                    cell = cell,
                    cellWidth = w,
                    rowHeight = rowHeight,
                    corner = corner,
                    index = index,
                    swipeUpDeletes = swipeUpDeletes,
                    actions = actions,
                    // 只有飞入动效的焦点格需要垫底图 —— 它一进来就在屏幕正中、
                    // 用户盯着看，空白的几十毫秒就是"黑屏一下"。
                    // 其余格子是在动画后半段淡入的，那点解码时间被动画盖住了。
                    placeholder = rememberFocusPlaceholder(
                        if (isFocusCell) cell.uri else null,
                    ),
                    focusDecodeSize = focusDecodeSize,
                )
            }
        }
    }
}

/**
 * 进场飞入动效的参数。
 *
 * 打包成一个不可变对象再往下传 —— 拆成好几个参数的话，
 * 只要有一格的参数被判定成"变了"，一整排几十个格子都会跟着重组
 * （`DayCellUi` / `DayCellActions` 当初就是为了这个才抽出来的）。
 *
 * [progress] 读在 `graphicsLayer { }` 里，所以每帧变化只走绘制阶段。
 */
@Immutable
class DayIntroAnim(
    val progress: Animatable<Float, AnimationVector1D>,
    /** 起点：整理照片页里那张图 1x 时占的屏幕矩形。 */
    val from: Rect,
    /**
     * 终点：焦点格自己的屏幕矩形。
     *
     * **可能还没测到**（布局要等一帧）。null 时焦点格暂时不可见 ——
     * 不能让它按"没有变换"画出来，那会在最终位置闪一下再跳回起点。
     */
    val to: Rect?,
)

/**
 * 一格缩略图该有多宽多高 —— **严格按原图比例**。
 *
 * 规则：
 *  · 竖图 / 方图：高度顶满行高，宽度 = 行高 × 比例；
 *  · 横图：宽度顶到 [CELL_MAX_WIDTH]，高度按比例反推（否则一张超宽横图
 *    会占掉大半个屏幕，把带子的节奏撑断）；
 *  · 极端长图：宽度不小于 [CELL_MIN_WIDTH]，免得被压成一根线。
 *
 * 抽成纯函数是为了让"可见区间估算"和"真实布局"用**同一套算法** ——
 * 两边只要有一点点不一致，屏幕外的占位宽度就会和真格子对不上，
 * 整排会越滚越歪。
 */
private fun cellWidthDp(item: MediaItem, rowHeight: Dp): Dp =
    cellWidthForRatio(itemRatio(item), rowHeight)

/**
 * [cellWidthDp] 的 ratio 版 —— **图片解码尺寸也走这一个函数**。
 *
 * 之前 `DayCell` 的 `ImageRequest` 用的是裸的 `行高 × ratio`，
 * 没有 CELL_MIN/MAX 夹紧，横图多解码 2.5 倍像素、竖长图解得比显示还小。
 * 让请求和布局共用同一个公式，两边才恒等。
 */
private fun cellWidthForRatio(ratio: Float, rowHeight: Dp): Dp {
    val widthAtRowHeight = rowHeight * ratio
    return if (widthAtRowHeight > CELL_MAX_WIDTH) CELL_MAX_WIDTH
    else widthAtRowHeight.coerceAtLeast(CELL_MIN_WIDTH)
}

/** 宽高比，带上下限（防止坏数据把布局撑爆）。 */
private fun itemRatio(item: MediaItem): Float = when {
    item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
    else -> 1f
}.coerceIn(0.25f, 4f)

/**
 * 一格缩略图。宽高都按原图比例算（算法见 [cellWidthDp]）。
 *
 * 宽度由外部传进来 —— 它必须和屏幕外那些占位用的是同一个值，
 * 否则真格子的实际宽度和预留宽度不一致，滚动位置就会漂。
 *
 * ---- 上滑 = 删除或收藏（跟着设置走）----
 * 横向的拖拽交给外面的 `horizontalScroll`（这里**不消费**水平位移），
 * 只认"竖直且明显向上"的那一段。
 *
 * **B 方案：跟手滑出、露底。** 照片跟着手指一起往上走，
 * 底下露出的是动作色（删除=红 / 收藏=绿）和动作名；
 * 松手时按位移判定 —— 够阈值就执行，不够就弹回原位。
 *
 * 为什么不是"原地泛色"（A 方案）：那个反馈是在**动手指之前**就把结果说完，
 * 用户只是被通知；而「去留」是照片**跟着手指走**、滑够了直接消失 ——
 * 手势和结果之间没有额外的认知步骤。跟手也让"我到底滑了多少"可见。
 *
 * 露底的量刻意只到**格高的 55%**（见 [SWIPE_UP_TRAVEL_FRACTION]）：
 * 露太多会让下面那排的照片从缝里露出来，观感变脏；
 * 露太少又看不出"底下有东西"。
 *
 * 认定为竖直手势后必须 `consume()`：**不消费的话，这一下的 move 会继续
 * 沿指针链往深处传，落到被这张日期视图盖住的整理界面上，
 * 于是下面那张照片也跟着往上飞走。** 这是之前踩过的坑。
 *
 * 顺序上 `.pointerInput` 写在 `.clickable` **之后**（也就是它的外层）：
 * 外层先收到 Initial pass，拖拽才抢得赢点击。
 */
@Composable
private fun DayCell(
    cell: DayCellUi,
    cellWidth: Dp,
    rowHeight: Dp,
    /** 共享的圆角形状实例（见 [DayMetrics]）。 */
    corner: RoundedCornerShape,
    /** 这一格在"这一天"里的序号（点击时回传）。 */
    index: Int,
    /** true = 上滑删除；false = 上滑收藏。 */
    swipeUpDeletes: Boolean,
    actions: DayCellActions,
    /**
     * 高清图到位之前先垫着的那张（系统缩略图）。null = 不垫。
     *
     * 只有**焦点格**会拿到它 —— 见 [rememberFocusPlaceholder] 的说明。
     */
    placeholder: Painter? = null,
    /**
     * 焦点格专用的解码尺寸 —— 照用整理照片页那份。
     *
     * 飞入动效的起点就是照片页那一张，两者**同一个 uri + 同一个 size**
     * 才会命中 Coil 的 memory cache ⇒ 焦点格第一帧就是同一张高清图，
     * 起手不会出现"糊一下再变清"的闪烁。null = 用格子自己的尺寸。
     */
    focusDecodeSize: IntSize? = null,
) {
    val ratio = cell.ratio
    val cellW = cellWidth
    // ---- 每张照片的**高度都顶满行高**，不随宽高比变 ----
    //
    // 参照「去留」（用户逐帧核对过）：一整排里所有格子的高度完全一致，
    // 宽度按宽高比伸缩、并且有上下限 ——
    //  · 竖屏长图：宽度被下限兜住，图**裁一段**显示（不是整张缩得很小）；
    //  · 横屏长图：高度不变、宽度变宽。
    // 这样一排的上下边缘是齐的，才成"一面照片墙"。
    // 之前按比例反推高度（横图会变矮），一排里高矮不齐，观感就散了。
    val cellH = rowHeight

    // ---- 缩略图按**显示尺寸**解码，而不是按原图尺寸 ----
    //
    // 不给 size 提示时，Coil 会照原图分辨率解码：一张 4000×3000 的照片就是 48MB
    // 位图，滑动时每进一张都要解一次这么大的图 —— 这就是用户报的
    // "滑动时加载小图预览有点迟缓"的根因。
    // 给上单元格的像素尺寸后，解码器直接降采样到刚好够用的像素，
    // 内存和解码耗时都会掉一个量级。
    val imgContext = LocalContext.current
    val imgDensity = LocalDensity.current
    // 有垫底图（= 焦点格）时要开淡入：那一下是"缩略图 → 高清"的交接，
    // 硬切会像闪了一下；其余格子关掉（见下面的说明）。
    val fadeIn = placeholder != null
    val imageRequest = remember(
        cell.uri, cellH, cell.ratio, cell.isAnimated, imgDensity, fadeIn, focusDecodeSize,
    ) {
        // ---- 请求尺寸必须和 `cellWidthDp` 用**同一个**公式 ----
        //
        // 之前这里是裸的 `hPx * ratio`，没有 CELL_MIN/MAX 夹紧：
        //  · 横图（ratio 顶到 4）请求约 3600px 宽，格子只显示 1430px，
        //    `ContentScale.Crop` 把多余的裁掉 —— **多解码 2.5 倍像素**；
        //  · 竖长图（ratio 0.25）请求 225px 宽，格子要显示 264px，
        //    **解码得比显示还小，缩略图发虚**。
        // 两头不讨好。抽一个 ratio 版给两边共用，请求与布局才恒等。
        val hPx = with(imgDensity) { cellH.roundToPx() }
        val wPx = with(imgDensity) { cellWidthForRatio(ratio, cellH).roundToPx() }
            .coerceAtLeast(1)
        ImageRequest.Builder(imgContext)
            .data(cell.uri)
            // ---- 缩略图不要交叉淡入 ----
            //
            // ImageLoader 全局开了 `crossfade(true)`，于是每个格子加载完
            // 都多一层 `CrossfadeDrawable`（双绘制 + 200ms 动画器逐帧失效）。
            // 瀑布流一屏 ~25 格、滑动时还在进出 —— 46dp 的缩略图上
            // 淡入本就看不出来，纯付开销。请求级关掉，全屏查看器不受影响。
            .crossfade(false)
            // ---- 动图**不要**给目标尺寸 ----
            //
            // 用户报过一次"动图进入回到那天触发闪退"。根因在这里：
            // 静态图给 `size()` 是让解码器降采样，安全；
            // 但动图走的是 `ImageDecoderDecoder`，它会把 `size` 翻译成
            // `ImageDecoder.setTargetSize()` —— 对**动画**源这么设，
            // 系统解码器会在合成帧时抛异常（帧尺寸与目标尺寸不一致的路径），
            // 于是整页崩掉。动图本身帧数就多、体积小，不降采样也负担得起。
            if (cell.isAnimated) {
                ImageRequest.Builder(imgContext).data(cell.uri).build()
            } else {
                ImageRequest.Builder(imgContext)
                    .data(cell.uri)
                    // 焦点格用**照片页那个尺寸** —— 同 uri + 同 size ⇒
                    // memory cache 命中 ⇒ 立即出图、且是同一张高清图。
                    // 其余格子按显示尺寸解（降采样，省内存也更快）。
                    .apply {
                        if (focusDecodeSize != null) {
                            size(focusDecodeSize.width, focusDecodeSize.height)
                        } else {
                            size(wPx, hPx)
                        }
                    }
                    // 见上面 fadeIn：只有垫了缩略图的焦点格需要这一段淡入
                    .crossfade(fadeIn)
                    .build()
            }
    }

    // ---- B 方案：跟手滑出，底下露动作色 ----
    //
    // `slidePx` 是**当前实际滑出去的像素**（正值，向上）。
    // 它直接喂给 `graphicsLayer { translationY = -slidePx }`，
    // 所以画面位移和手指位移是 1:1 的 —— 这就是"跟手"。
    //
    // 用 Animatable 而不是 mutableFloat：松手时要做**弹回**动画，
    // 而弹回必须能中断（用户弹回一半又按下继续滑）。
    // Animatable 的 `snapTo` 不吃 Mutex、可以在手势回调里直接调。
    val slide = remember { Animatable(0f) }
    // 触发阈值：滑够了就成立。用固定像素而不是"格高比例" ——
    // 比例会随格子高度变，用户在不同照片上要学不同的力度。
    val slideThreshold = with(LocalDensity.current) { SWIPE_UP_DELETE_PX.dp.toPx() }
    // 删除触发之后就不 respond touches 了 —— 免得重复回调。
    // （收藏模式不设它：格子还在，用户可以再滑一次取消。）
    var fired by remember { mutableStateOf(false) }
    // 收藏模式松手后的确认闪烁。删除不需要 —— 格子当场就消失了。
    //
    // 文案在**触发那一刻**定格：松手后收藏状态当场就翻转，事后再读
    // isFavorite 会把"刚刚收藏"显示成"已取消收藏" —— 正好说反。
    var flashText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(flashText) {
        if (flashText != null) {
            delay(700L)
            flashText = null
        }
    }
    val slideScope = rememberCoroutineScope()
    // 露底只到格高的这个比例（见 DayCell 的注释）。
    val maxSlidePx = with(LocalDensity.current) { (cellH * SWIPE_UP_TRAVEL_FRACTION).toPx() }

    val favoriteMode = !swipeUpDeletes
    val alreadyFavorite = favoriteMode && actions.favorite(cell.storeKey)
    val accent = if (favoriteMode) AccentKeep else AccentDelete

    Box(
        modifier = Modifier
            .width(cellW)
            .height(cellH)
            .clip(corner)
            .background(Surface2)
            .clickable { actions.pick(index) }
            // alreadyFavorite 也必须进键：pointerInput 的 lambda 捕获的是
            // **上一次启动时**的值。不把它列进键的话，同一格连滑两次
            // （收藏 → 取消），第二次的提示文案会用第一次的旧状态，说反。
            // 删除模式下它恒为 false，键不变，不会有任何多余重启。
            .pointerInput(cell.storeKey, fired, alreadyFavorite) {
                if (fired) return@pointerInput
                // 判定阈值用**系统 touchSlop**，不再是写死的 6px。
                // 6px 远小于 slop（这台机器上约 22px）：横滑起手时一丁点
                // 向上抖就会被这里判成"竖直手势"并 consume —— 而 scrollable 的
                // slop 检测一看到被消费的事件就**取消整次拖拽**
                // （1.2.7 日志实证：直接拖动约 6% 完全无响应，死手全在这个模式）。
                // 对齐到 slop 之后，横滑起手不再被误抢，竖滑也照样归这一格。
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startY = down.position.y
                    val startX = down.position.x
                    var decided = false // true = 认定是竖直手势，开始自行处理
                    var triggered = false
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            val dy = startY - change.position.y // 向上为正
                            val dx = kotlin.math.abs(change.position.x - startX)
                            val absDy = kotlin.math.abs(dy)

                            if (!decided && (absDy > slop || dx > slop)) {
                                // 横向为主 → 交还给外面的 horizontalScroll，这里撒手。
                                if (dx >= absDy) return@awaitEachGesture
                                // 只有向上才算；往下拖不当回事。
                                if (dy <= 0f) return@awaitEachGesture
                                decided = true
                            }
                            if (decided) {
                                // 竖直必须消费：不然会穿透到下层，把下面那张照片拖走。
                                change.consume()
                                // ---- 触发之后就不再跟手了 ----
                                // 照片这时已经交给"飞出去"的动画（见下面 triggered 分支），
                                // 这里的 `snapTo` 会**取消那个动画**，把它拽回手指上。
                                // 所以 `triggered` 之后一次都不许再写 slide。
                                if (!triggered) {
                                    // 跟手：位移直接就是手指位移，只在超过露底上限时
                                    // 加一点**渐进阻尼**（同 iOS 的橡皮筋）——
                                    // 完全线性的话，滑过上限后照片会一路滑出格子边界，
                                    // 观感变成"照片飘走了"，而不是"我要把它拿掉"。
                                    val raw = dy.coerceIn(0f, maxSlidePx * 1.6f)
                                    val eased = if (raw <= maxSlidePx) {
                                        raw
                                    } else {
                                        val over = raw - maxSlidePx
                                        maxSlidePx + maxSlidePx * 0.30f * (1f - 1f / (over / (maxSlidePx * 0.30f) + 1f))
                                    }
                                    slideScope.launch { slide.snapTo(eased) }

                                    if (dy >= slideThreshold) {
                                        triggered = true
                                        if (favoriteMode) {
                                            // 文案取触发前的状态 —— 见 flashText 的注释
                                            flashText =
                                                if (alreadyFavorite) "已取消收藏" else "已收藏"
                                            actions.markFavorite(cell.storeKey)
                                        } else {
                                            fired = true
                                            // ---- 先让它**飞出去**，飞完再真正标记删除 ----
                                            //
                                            // 原来是滑够就当场 `markDelete` ⇒ CellSlot 立刻把
                                            // 这一格剔除，照片"啪"地消失、没有任何交代
                                            // （用户要求改成整理照片页那种"看着它被扔掉"的动线）。
                                            //
                                            // 顺序不能反：先标记就会先删格，这段动画根本没人看得见。
                                            // `markDelete` 放进 `finally` 而不是跟在 animateTo 后面：
                                            // 万一这一格在动画中途被移出组合（翻页、退出日期视图），
                                            // 协程会被取消，普通写法会让这一滑**白滑**；
                                            // 放进 finally 则取消也照样落实。
                                            slideScope.launch {
                                                try {
                                                    slide.animateTo(
                                                        // maxSlidePx 是格高的 0.55，
                                                        // 乘 2 就是格高的 1.1 倍 —— 整张图完全滑出格子，
                                                        // 配合外层 clip，看到的就是"被扔出去了"。
                                                        targetValue = maxSlidePx * 2f,
                                                        animationSpec = tween(
                                                            durationMillis = 200,
                                                            // 先慢后快：起步接着手指的速度，
                                                            // 末段加速离开，像被甩出去。
                                                            easing = FastOutLinearInEasing,
                                                        ),
                                                    )
                                                } finally {
                                                    actions.markDelete(cell.storeKey)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // ---- 松手：没滑够就弹回原位 ----
                    //
                    // 弹回用动画而不是瞬移：瞬移在松手那帧会有一次可见的跳变，
                    // 而"没滑够就回来"本身就是 B 方案承诺的手感 ——
                    // 用户必须能确信"没滑到位就不会删"。
                    val shouldCommit = slide.value >= slideThreshold
                    if (!shouldCommit) {
                        slideScope.launch {
                            slide.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        }
                    } else if (favoriteMode) {
                        // 收藏不改列表 ⇒ 格子还在，得自己弹回去。
                        slideScope.launch {
                            slide.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                            )
                        }
                    }
                    // 删除模式滑够了：格子已经被 CellSlot 剔除，这一段动画没人看得见。
                }
            },
    ) {
        // ---- 露底：动作色 + 动作名，铺满整格 ----
        //
        // 它在**下面**，照片盖在上面并跟着手指往上滑——
        // 所以滑开露出来的这块底色就是"接下来会发生什么"的答案。
        //
        // ---- 必须**真的有位移**才画，不能只靠 alpha=0 隐掉 ----
        //
        // 这一层始终存在（它是照片的"底"）。之前的写法把条件去掉了，
        // 只靠 `alpha = 0.20f + ...` 让它"看不见"—— 但：
        //  · 已加载的照片把它完全盖住，看不出问题；
        //  · **还没解码出来的格子（AsyncImage 还没出图、像素全透明）就整片
        //    露出红底 + "删除"两个字** —— 横向滑动时一格一格闪出来，
        //    正是用户报的"滑到没加载出来的图的时候就是红色的删除"。
        //
        // 所以判断放在**组合期**（`slide > 0`），让整个节点真的不进组合。
        // 这个判断必须包 derivedStateOf：直读`slide.value > 0f` 会在滑动的
        // 每一帧重组整格（观察者被登记），那就把上面 drawBehind 省下的又还回去了。
        // derivedStateOf 只在布尔值**翻转**时通知 —— 一次手势最多两次。
        val slid by remember(slide) { derivedStateOf { slide.value > 0f } }
        if (slid) {
            // 底色浓度走 `drawBehind`：滑动时只有这一个盒子的绘制指令变，
            // 组合完全不重跑（直读 slide.value 算 alpha 会让整格每帧重组）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // 越接近阈值底色越浓：这是一个"充能"式的提示，
                        // 而不是突然出现的开关。
                        val p = (slide.value / slideThreshold).coerceIn(0f, 1f)
                        drawRect(accent.copy(alpha = 0.20f + 0.35f * p))
                    },
                // ---- 偏下，不居中 ----
            // 手指正在往上滑，提示落在画框**下缘**才不会被指尖压住；
            // 视线本来也是从下往上扫过这一格的。
            contentAlignment = Alignment.BottomCenter,
            ) {
                // 图标 + 文字，和整理照片页那颗胶囊同一个构成 ——
                // 那边是"红底 + 回收站图标 + 删除"，这里受格子尺寸限制做不了胶囊底，
                // 但**图标不能少**：一眼认出"这是删除"靠的就是它，
                // 光两个字在小格子上还是要读一下。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Icon(
                        imageVector = if (favoriteMode) {
                            if (alreadyFavorite) Icons.Filled.FavoriteBorder
                            else Icons.Filled.Favorite
                        } else {
                            Icons.Filled.Delete
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = when {
                            favoriteMode && alreadyFavorite -> "取消收藏"
                            favoriteMode -> "收藏"
                            else -> "删除"
                        },
                        // 原来用的是 `labelMedium`，在一格缩略图上小到几乎读不出来。
                        // 提到 `titleMedium` + Bold —— 对齐整理照片页那颗胶囊的醒目程度。
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ---- 照片本体：跟着手指走 ----
        //
        // `graphicsLayer` 里读 Animatable 而不是把它放进组合期 ——
        // 每帧重组一格缩略图是纯浪费，绘制阶段读就够了。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = -slide.value
                    // 滑出时略微缩小：强化"这张要被拿走了"的语义，
                    // 也让露出的底色在四周露得更明显。
                    val p = (slide.value / maxSlidePx).coerceIn(0f, 1f)
                    val s = 1f - 0.06f * p
                    scaleX = s
                    scaleY = s
                },
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                // 焦点格的垫底图（系统缩略图，见 rememberFocusPlaceholder）。
                // 请求本身没关 crossfade ⇒ 高清到了会淡入，交接是平滑的。
                placeholder = placeholder,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

// 角标。视频给时长、截屏给"截屏"、长图给"长图"。
                // 长图进瀑布流就是一根细条，没角标完全看不出该怎么读。
                // 优先级：时长最具体 > 长图 > 截屏（截屏也可能就是长截图，
                // 但"截屏"已经在说来源，"长图"说的是读法，后者更有用）。
                val tag = when {
                    cell.kind == MediaKind.VIDEO -> formatDuration(cell.durationMillis)
                    cell.isLongImage -> "长图"
                    cell.kind == MediaKind.SCREENSHOT -> "截屏"
                    else -> null
                }
            // 角标在开始滑动时就该让位给露底 —— 但这个判断**不能每帧读** slide.value
            // （那会让整格每帧重组）。它只在"从 0 变成非 0"这一刻需要重组一次，
            // 所以派生出来的 Boolean 只翻转一次。
            val showTag = remember(slide) {
                derivedStateOf { slide.value < 1f }
            }
            if (tag != null && flashText == null) {
                // 角标本身画在照片盒子里，但用 alpha 淡出而不是增删节点 ——
                // 增删会走一次组合/反组合，而淡出只是绘制指令。
                Box(
                    modifier = Modifier
                        // 角标挪到**左上角** —— 参照「去留」：实况角标就在那儿。
                        // 右下角在照片本身有浅色内容时容易被吃掉。
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .graphicsLayer {
                            val p = (slide.value / 12f).coerceIn(0f, 1f)
                            alpha = if (showTag.value) 1f - p else 0f
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (cell.kind == MediaKind.VIDEO) AccentInfo else TextPrimary,
                    )
                }
            }
        }

        // ---- 收藏的确认闪烁：短暂盖一层动作色 ----
        // 文案在**触发那一刻**定格（见 flashText 的注释），所以这里读 state
        // 而不是重新去问 isFavorite。
        if (flashText != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = flashText.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 往上滑多少像素算"确定触发"。和整理页单张手势的量级对齐。 */
private const val SWIPE_UP_DELETE_PX = 96

/**
 * 跟手滑出最多露出格高的这个比例。
 *
 * 露满格高会让下面那排的照片从缝里透出来，整面墙变脏；
 * 露太少又看不出底下有东西。55% 是"看得见露底、看不见下一排"的折中。
 */
private const val SWIPE_UP_TRAVEL_FRACTION = 0.55f

/**
 * 进场定位最多重试多少帧。
 *
 * 布局通常一两帧就落地，重试到第三帧基本都能对上。
 * 20 帧（约 0.3 秒）只是防死循环的上限 —— 万一模型和真实布局真对不上，
 * 也不能让这里永远转下去，宁可停在原地也不要卡住。
 */
private const val POSITION_MAX_TRIES = 20

// 进场飞入的时长和照片页那句捏合提示**共用同一个常量**（见 [DAY_INTRO_MS]）——
// 两边不一致会显得"提示还没读完人就飞走了"。

/**
 * 等焦点格位置稳定最多看几帧。
 *
 * "稳定"= 连续两帧读到同一个矩形。正常情况第二帧就成立；
 * 定位要跨几帧的话会顺延几帧。20 帧（约 0.33 秒）是上限 ——
 * 到点还没稳定就用当前值（或者干脆放弃动效，见调用处），
 * **绝不能让这个循环把界面卡在一片空白上**。
 */
private const val INTRO_SETTLE_FRAMES = 20

/**
 * 放大看单张时，往下拖多远算"要回去"（占屏高的比例）。
 *
 * 0.22 是 iOS 相册 / 「去留」那一档的手感：明显是一整个下滑动作，
 * 又不会划到底才生效。再小会误触（读图时不小心蹭一下就退出去了）。
 * 另有 120dp 的绝对下限 —— 屏特别矮的时候按比例算出来的距离会短得离谱。
 */
private const val ZOOM_DISMISS_FRACTION = 0.22f

/**
 * 下滑退出时的**甩动**门槛（px/s）。
 *
 * 和 [ZOOM_DISMISS_FRACTION] 是"或"的关系，不是"且"：
 * 慢慢拖要够距离，快速甩只要够快 —— 只判距离的话，快速一甩
 * （手指没走多远就松手）会被判成"没到阈值"，用户反馈就是"触发不了"。
 * 1200px/s 大约是"一个自然的手腕甩动"，比这更慢的就不算主动要走了。
 */
private const val ZOOM_DISMISS_FLING_VELOCITY = 1200f

/**
 * 下滑退出时照片一起缩多少（比例）。
 *
 * 只是"稍微缩一点"的暗示 —— 让画面看起来在往后退，
 * 不是真的缩成一张卡片。0.12 再大就开始像"把照片扔掉"，太重了。
 */
private const val ZOOM_DISMISS_SHRINK = 0.12f

/**
 * 整屏淡入等"焦点格能飞"最多等多久（毫秒），到点无条件放行。
 *
 * 这是**保险丝**，不是正常路径：正常情况 `DayFilmstrip` 会在飞入开跑那一帧
 * 回调上来。只有当某条分支漏了回调（或者内容本身读不出来）时才轮到它。
 *
 * 为什么必须有：淡入被门控之后，一旦回调没来，这一层就**永远停在 alpha = 0**
 * —— 用户看到的是"捏合之后界面整个不动了"，比跳变严重得多。
 * 宁可让它退化成"没有飞入动效"，也不能卡住。
 */
private const val INTRO_READY_TIMEOUT_MS = 1200L

/**
 * 拖拽增益：手指移动 1px，画面走 1.25px。
 *
 * 默认的 1:1 是最"跟手"的（手指和内容严丝合缝），但用户反馈
 * "滑动的幅度有点小，改大一点" —— 他要的是**同样的手势能走过更多内容**。
 * 1.25 是折中：明显更省力，又不至于让人觉得画面在"超车"手指。
 * 惯性起手速度用的是同一份 dx，所以增益对滑行同样生效。
 */
private const val DRAG_GAIN = 1.25f

/**
 * 一格缩略图需要的全部数据 —— **稳定模型**，这是"跟手"的关键。
 *
 * 原来格子直接吃 `MediaItem`，而它带 `Uri`（编译器判定为不稳定类型），
 * 后果是：**拿着它的 composable 一律不可跳过重组**。滚动每越过一格边界，
 * 可见区间就平移一格 —— 那一整排几十个格子的函数体都会被重新执行一遍，
 * 哪怕其中只有两张真的换了。这正是用户说的"不跟手"。
 *
 * 换成这个标了 `@Immutable` 的小模型之后，参数的相等性只看这几个字段，
 * 没变的格子直接跳过，只有真正进出画面的那两张会重组。
 */
@Immutable
private class DayCellUi(
    val uri: Uri,
    val kind: MediaKind,
    val durationMillis: Long,
    val storeKey: Long,
    /** 宽高比（已经收敛到 [0.25, 4]），格子的宽高和遮罩都用它。 */
    val ratio: Float,
    /**
     * 动图（GIF / 动态 WebP / 动态 HEIF）。
     *
     * 存在的唯一理由是**解码路径不同**：动图不能给解码器设目标尺寸
     * （见 [DayCell] 里那段说明），所以这个标记必须进模型、
     * 而且要参与 `imageRequest` 的 `remember` 键。
     */
    val isAnimated: Boolean,
    /** 长图（长截图 / 聊天记录）—— 只用来显示角标。 */
    val isLongImage: Boolean = false,
)

/**
 * 一组回调 + 收藏查询的**稳定包装**。
 *
 * 直接把 lambda 当参数传同样会失去跳过能力：它们捕获的东西
 * （`AppContainer` 之类）在编译器眼里不稳定。包成一个 `@Stable` 的类之后，
 * 参数类型就是稳定的了 —— 里面的 lambda 是否稳定反而无关紧要。
 *
 * 全部用 `storeKey` / 序号做参数，不再传 `MediaItem`：模型稳定了，
 * 回调里也就没必要再搬不稳定对象。
 */
@Stable
private class DayCellActions(
    /** 点开单张：参数是"这一天的第几张"。 */
    val pick: (Int) -> Unit,
    /** 上滑要求删除 / 收藏：参数是 storeKey。 */
    val markDelete: (Long) -> Unit,
    val markFavorite: (Long) -> Unit,
    /** 查某一条现在是不是已收藏（决定提示语说"收藏"还是"取消"）。 */
    val favorite: (Long) -> Boolean,
)

/**
 * 单个占位节点允许的最大宽度（px）。
 *
 * Compose 的 `Constraints` 内部按 18 位打包，**单个节点最大只能到 262143px**，
 * 超了会抛 `IllegalArgumentException: Can't represent a width of ... in Constraints`
 * 直接闪退。一天几百张照片时，"屏幕外前缀"的累计宽度轻易就超过它
 * （实测崩溃现场是 333384px，约 840 张）。
 *
 * 200000px 留足安全余量（2.625 密度下约 76200dp）。
 */
private const val SPACER_CHUNK_PX = 200_000f

/**
 * 表示一段"很宽的空档"，**必要时拆成多个占位**。
 *
 * 关键点：262143px 的限制是**每个节点**的，`Row` 自己的总宽只是 Int 求和，
 * 没有这个上限。所以把一段超宽空档拆成若干个 ≤ [SPACER_CHUNK_PX] 的
 * `Spacer` 就能既保持总宽正确、又不触发限制。
 *
 * 这是"回到那天"第二次进入会闪退的根因修复（用户报的"滑远了再进就闪退"）。
 */
@Composable
private fun WideSpacer(widthPx: Float) {
    if (widthPx <= 0f) return
    val density = LocalDensity.current
    var remaining = widthPx
    while (remaining > 0.5f) {
        val chunk = remaining.coerceAtMost(SPACER_CHUNK_PX)
        Spacer(Modifier.width(with(density) { chunk.toDp() }))
        remaining -= chunk
    }
}

/**
 * 底部刻度条 —— 「刻度不动、指针动」。
 *
 * ```
 *      10月13日 · 周二
 *  ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼ ┼      ← 刻度固定在这条轨道上，永远不动
 *              ▲                        ← 绿色指针在轨道上左右移动
 *         共 42 张
 * ```
 *
 * **形态的演变**：上一版做成了"指针固定、刻度整体平移"，用户这一轮明确
 * 改口为「**刻度条不动，通过指针动来滑动瀑布流**」。
 *
 * 这个说法其实更准：刻度是**标尺**（位置是绝对的、不动的），
 * 指针是**读数**。标尺跟着手指跑会让人失去"一共多长"的稳定参照 ——
 * 这跟上一轮"背景在刻度条上滑动"的抱怨是同一个病根：**参照物不该动**。
 *
 * 交互：手指在轨道上任何位置按下 / 拖动，指针就移到哪儿，
 * 同时把瀑布流滚到对应比例。指针不要求被精确抓住才生效 ——
 * 精确点一根 2dp 宽的线太苛刻了，整条轨道都是热区。
 */
@Composable
private fun DayScrubber(
    scroll: ScrollState,
    total: Int,
    /**
     * 稳定的滚动总长（px），由两排各自上报后取大者。
     *
     * **不能用 `scroll.maxValue`**：两排共用一个 ScrollState 而总宽不同，
     * Compose 每次布局都用"最后那排"覆盖 maxValue，于是它在两排两个值之间跳，
     * 指针就跟着乱跳（用户报"不管滑照片还是拖光标，光标都乱跳"）。
     */
    span: Float,
    onSeekFraction: (Float) -> Unit,
    /** ⟲ 重新定位到"正在看的那张"（滑远了以后一键回来）。 */
    onRecenter: () -> Unit,
    /** ↩ 撤销上一次标记删除。 */
    onUndoDelete: () -> Unit,
    canUndo: Boolean,
) {
    var rulerWidth by remember { mutableStateOf(0f) }
    var draggingFraction by remember { mutableStateOf<Float?>(null) }

    // ---- 指针位置：**只在绘制阶段读滚动值** ----
    //
    // 原来把 fraction 在组合期算好（`derivedStateOf { scroll.value / maxValue }`），
    // 结果滚动时整个 DayScrubber（刻度 Canvas + 胶囊底 + 指针）**每帧重组一次** ——
    // 这是用户反馈的"不跟手"里很实在的一笔开销。
    // 现在把 `scroll.value` 的读挪进 `graphicsLayer { }` 的 lambda：那是绘制阶段，
    // 滚动只会让这一层重绘，不再触发任何重组。

    // ---- 底栏：⟨⟲⟩ ⟨刻度尺⟩ ⟨↩⟩ ----
    // 参照「去留」录屏的形态：两枚圆形按钮把刻度尺夹在中间。
    // 刻度尺用 weight(1f) 吃掉剩余宽度 —— 之前固定 62% 太窄，
    // 用户反馈"有点窄也有一点不明显"。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayCircleButton(
            icon = RecenterIcon,
            contentDescription = "回到这张照片",
            enabled = true,
            onClick = onRecenter,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Surface1)
                .onSizeChanged { rulerWidth = it.width.toFloat() }
                .pointerInput(total, rulerWidth) {
                    if (total <= 1 || rulerWidth <= 0f) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 轨道两端各留一点内边距，指针不会贴到胶囊边缘。
                        val inset = 12.dp.toPx()
                        val usable = (rulerWidth - inset * 2).coerceAtLeast(1f)
                        // 刻意用 val lambda 而不是局部 fun：后者会踩到 lint 的
                        // SuspiciousIndentation（它不把函数声明当作"上一条语句"，
                        // 于是下一行调用被误判成异常缩进，lintDebug 直接失败）。
                        val seek = { x: Float ->
                            val f = ((x - inset) / usable).coerceIn(0f, 1f)
                            draggingFraction = f
                            onSeekFraction(f)
                        }
                        seek(down.position.x)
                        var event: PointerEvent
                        do {
                            event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                change.consume()
                                seek(change.position.x)
                            }
                        } while (event.changes.any { it.pressed })
                        draggingFraction = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // ---- 刻度层：**固定不动**。均匀铺满一条静止的轨道 ----
            Ticks()

            // ---- 指针：按 fraction 在轨道上左右移动 ----
            // 在**绘制阶段**读 fraction：滚动时只有这一层重绘，
            // 不会把整个 DayScrubber 拖着每帧重组一遍。
            //
            // `contentAlignment = CenterStart` 而不是 Center：
            //  · 要 Center（垂直居中）是因为 BoxWithConstraints 内容默认贴左上，
            //    不居中的话指针挂在胶囊**顶上**；
            //  · 但水平方向必须是 **Start** —— 用 Center 的话指针会先被水平居中
            //    （偏出半个胶囊宽），`translationX` 再从那个位置继续偏移，
            //    结果就是"指针不跟手、总在触点右边一段"（用户报的就是这个）。
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val usable = (maxWidth - 24.dp).coerceAtLeast(0.dp)
                val usablePx = with(LocalDensity.current) { usable.toPx() }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            // 拖刻度条时跟手指；松手后跟**实际滚动偏移**
                            // （所以滑带子时指针也会同步走，而不是停在原处）。
                            val f = draggingFraction ?: run {
                                // 分母用外部给的稳定总长，不是 scroll.maxValue。
                                val max = span
                                if (max <= 0f) 0f
                                else (scroll.value.toFloat() / max).coerceIn(0f, 1f)
                            }
                            // 再减掉指针自身半个宽度：轨道位置指的是**指针中心**，
                            // 而 translationX 移的是它的左边缘。
                            translationX = 12.dp.toPx() + usablePx * f - 1.5.dp.toPx()
                        }
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        // 去留那边指针是白的；这里保留一点绿色，作为全 App 的强调色。
                        .background(AccentKeep),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        DayCircleButton(
            icon = UndoIcon,
            contentDescription = "撤销删除",
            enabled = canUndo,
            onClick = onUndoDelete,
        )
    }
}

/**
 * 「回到那天」里统一的圆形图标按钮：**深色圆底 + 图标**。
 *
 * 参照「去留」截屏：顶栏（‹ / ≡ / ✓）和底栏（⟲ / ↩）都是这种圆按钮 ——
 * 白图标压在半透明深色圆底上，不论底下是黑是白都看得清。
 * 禁用时压暗而不是隐藏，位置感不能丢。
 */
@Composable
private fun DayCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    /** true = 图标用强调色（如"确认"可用时）。 */
    highlight: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            // 和整理照片页左下红心 / 右下五星**完全同一套**（`StarButton`）：
            // 44dp 圆 + 黑 40% 半透明底 + 1dp 极淡的白描边 + 22dp 图标。
            // 上一版改成"白 12% 浅圆"是为了解决"看不见"，但对比之下确实过亮了 ——
            // 真正要的是这一档：圈看得见，但整体重量压在照片之下。
            .background(Color(0x66000000))
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> TextTertiary.copy(alpha = 0.45f)
                highlight -> AccentKeep
                else -> TextPrimary
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 刻度层 —— **静止的轨道**。
 *
 * 等间距短竖线平铺，**不随任何东西平移**。它就是一把尺子：
 * 位置固定，用户才有稳定的空间参照，读数（指针）才可信。
 *
 * **不画任何底色** —— 只画刻度线本身，让背景（Ink 深色）透出来。
 *
 * 用 `Canvas` 一次画完而不是摆一堆 `Box`：一天可能几百张刻度，
 * 摆 Box 会在组合阶段就开销掉；画布只是绘制，几乎零成本。
 */
@Composable
private fun Ticks() {
    // 刻度再提亮一档：Surface3 → TextTertiary → **TextSecondary**。
    // 用户两次反馈"太暗、看不清刻度" —— 它在纯黑底上本来就是最弱的一层，
    // 弱到这个程度就不叫内敛，叫看不见。
    val tickColor = TextSecondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        // 刻度更密更短 —— 参照「去留」的胶囊尺：8dp 一根太稀，
        // 5dp 才是那边"密而细"的观感。
        val gap = 5.dp.toPx()
        val inset = 12.dp.toPx()
        val usableWidth = (size.width - inset * 2).coerceAtLeast(0f)
        val tickH = 7.dp.toPx()
        var x = inset
        var i = 0
        while (x <= inset + usableWidth + 0.5f) {
            drawLine(
                color = tickColor,
                start = Offset(x, size.height / 2f - tickH / 2f),
                end = Offset(x, size.height / 2f + tickH / 2f),
                strokeWidth = 1.dp.toPx(),
            )
            x += gap
            i++
            if (i > 600) break
        }
    }
}

/**
 * 顶部：返回 / 居中「回到那天」+ 大号日期 + 星期与条数 / 确认。
 *
 * 两行标题：日期是这一屏真正的主语，做大字；
 * "回到那天"四个字只解释"这一屏为什么存在"，小一档跟在上面。
 */
@Composable
private fun DayViewHeader(
    items: List<MediaItem>?,
    kindLabel: String,
    dayStart: Long,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            enabled = true,
            onClick = onBack,
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                // 范围不再写在这里 —— 换成"滑到哪天、日期就跟到哪天"，
                // 比一句静态说明准确得多（用户要求）。
                text = "回到那天",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // 「去留」的日期写法：2023/1/19 —— 斜杠、不带前导零。
                // 比"2023年1月19日"短一截，大字下更稳；也比 ISO 的
                // "2023-01-19"更像给人看的日期。
                text = formatDayTitle(dayStart),
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
        }

        val ready = items?.isNotEmpty() == true
        DayCircleButton(
            icon = Icons.Filled.Check,
            contentDescription = "用这一天开一组整理",
            enabled = ready,
            onClick = onConfirm,
        )
    }
}

/**
 * 放大看单张。
 *
 * **刻意不做左右翻页。** 从这个界面进来的意图是"把这一张看清楚"，
 * 而不是"接着翻下一张" —— 想翻就退回去拨带子。
 * 少了翻页这条，这一屏就没有任何横滑手势负担，
 * 也就不会和系统的返回手势打架。
 *
 * 底部有一颗 `i`：点它把完整信息（日期 / 文件夹 / 大小 / 时长 / 尺寸）展开，
 * 收起时只留一颗小圆点 —— 想看的能看到全部，不想看的也不被常驻信息挡着画面。
 * 点画面任意处也能整体收起 / 唤回上下的 chrome。
 */
@Composable
private fun DayZoomView(
    item: MediaItem,
    /** 切换收藏（进系统相册那一档）。null = 不给这个动作。 */
    onToggleFavorite: (() -> Unit)?,
    /** 切换喜欢（只记在本应用里）。null = 不给这个动作。 */
    onToggleLike: (() -> Unit)?,
    isFavorite: Boolean,
    isLiked: Boolean,
    onClose: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(true) }
    var infoExpanded by remember { mutableStateOf(false) }
    /** 照片是不是正被捏大 —— 放大态不认领下滑（见下面手势的说明）。 */
    var zoomed by remember { mutableStateOf(false) }

    // ---- 双击 = 喜欢 ----
    //
    // 和整理照片页、整理视频页同一个共享组件（[DoubleTapFlashOverlay]），
    // 语义也一样：只弹一颗图标，不压文字。
    var dtFlash by remember { mutableStateOf<DoubleTapFlash?>(null) }
    var dtFlashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(dtFlashTick) {
        if (dtFlash == null) return@LaunchedEffect
        delay(1200L)
        dtFlash = null
    }

    // ---- 下滑返回瀑布流 ----
    //
    // 「去留」和 iOS 相册都是这个手势：这一屏是**浮**在瀑布流上面的，
    // 往下一划就该掉回去。没有它只能去够左上角的返回键 ——
    // 而这一屏本来就是单手握着在看，够左上角并不顺手（用户要求）。
    //
    // ---- 只在 **1x** 时认领 ----
    // 放大之后竖直拖动是 `ZoomableImage` 在看局部，那时候它已经把事件消费掉了，
    // 这里收不到（父子手势分工：子节点先认领，父拿剩下的）。
    // 就算收得到也不能抢 —— 放大状态下随便划一下就退出，
    // 等于把"仔细看"和"退出"这两件事混在一起。
    //
    // ---- 退出的观感 ----
    // 照片跟着手指往下走、同时缩小、整层渐隐；
    // 松手时**距离够 或者 甩得够快**都算要退出；
    // 否则弹回原位。全程用 `snapTo` 跟手，不跑补间。
    //
    // ---- 为什么必须看速度，不能只看距离 ----
    // 只判 `dismissY >= 阈值` 的话，快速一甩（手指在屏幕上其实没走多远就松手了）
    // 会被判成"没到阈值"，于是每次都弹回去 —— 用户反馈"触发不了"就是这个。
    // 滑动类手势的通行判据就是"距离 **或** 速度"，缺一半就会有一半手势不响应。
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dismissY = remember { Animatable(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = maxOf(boxHeightPx * ZOOM_DISMISS_FRACTION, with(density) { 120.dp.toPx() })
    // 放大态必须走 **state 转发**：pointerInput 的块只在首次组合时捕获闭包，
    // 直接读 `var zoomed` 的话这块代码永远看到 false，放大后下滑照样会退出。
    val zoomedState = rememberUpdatedState(zoomed)

    BackHandler(enabled = true) {
        if (infoExpanded) infoExpanded = false else onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxHeightPx = it.height.toFloat() }
            .pointerInput(boxHeightPx) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val startY = down.position.y
                    var lastY = startY
                    // 本次手势内**自己**累加的位移，snapTo 写的是**绝对值**。
                    //
                    // 以前是 `dismissY.snapTo(dismissY.value + delta)` ——
                    // 读的是挂起写出来的当前值，而每帧都要 `scope.launch` 一次；
                    // 一旦后一次把前一次取消掉，那一段增量就丢了，
                    // 画面表现就是"往下滑一点点图片就顿住了"（用户反馈）。
                    // 记在本地、每次写绝对值，天然不会丢。
                    var dragY = 0f
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        // 第二根手指落下 ⇒ 交给 `ZoomableImage` 去捏合，这里收手。
                        // 放大态同理：那一层已经把竖直拖动消费掉了，这里看到的位移没有意义。
                        if (pressed.size >= 2 || zoomedState.value) break
                        val change = pressed.first()
                        if (change.id != down.id) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val totalY = change.position.y - startY
                        if (!claimed) {
                            // 越过 touchSlop 才判方向 —— 没到阈值之前不认领，
                            // 否则会把单击、横滑、以及 `ZoomableImage` 的捏合一起吃掉。
                            if (kotlin.math.abs(totalY) <= slop) continue
                            // 只有**向下**才归我；其余方向一律放手（一个事件都不消费）
                            if (totalY <= 0f) break
                            claimed = true
                            // 认领的这一帧把已经越过 slop 的那段补上，
                            // 不然手指已经走了一小截、画面还停在原地，下一帧才突然跟上。
                            //
                            // `snapTo` 是挂起函数，而 `awaitEachGesture` 的闭包是
                            // **受限协程作用域**（不能调成员挂起函数）⇒ 必须丢进
                            // `scope.launch`。工程里 `DraggablePhoto` 用的是同一套写法。
                            dragY = totalY
                            scope.launch { dismissY.snapTo(dragY) }
                        } else {
                            dragY += change.position.y - lastY
                            scope.launch { dismissY.snapTo(dragY) }
                        }
                        lastY = change.position.y
                        change.consume()
                    }
                    if (!claimed) return@awaitEachGesture
                    // 距离够 **或** 甩得够快，都算"要回去"
                    val flung = tracker.calculateVelocity().y >= ZOOM_DISMISS_FLING_VELOCITY
                    if (dismissY.value >= dismissThresholdPx || flung) {
                        // 越界 ⇒ 顺着这个方向飞出去，飞完再关掉这一屏。
                        // 关掉放在动画**之后**：这一屏一撤，瀑布流自然就在下面接上了。
                        // 用 [flingTo]（tween 200ms + FastOutLinearInEasing）——
                        // 和整理照片页「上滑删除」的收尾**完全同一套参数**，滑出来才一样干脆。
                        // 回弹同理换成 StiffnessMediumLow 的 spring（默认那个更硬，手感发木）。
                        scope.launch {
                            dismissY.flingTo(boxHeightPx * 1.15f)
                            onClose()
                        }
                    } else {
                        scope.launch {
                            dismissY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                }
            },
    ) {
        // 三层的分工（顺序 = 绘制顺序 = 底 → 顶）：
        //   ① 黑色底   —— 只淡出
        //   ② 照片     —— 只位移 + 缩小，**不淡出**
        //   ③ 上下 chrome —— 只淡出
        //
        // 为什么要拆开：之前是整层一起淡 + 一起走，照片在退场动画的前 20% 就淡没了，
        // 剩下那一百多毫秒屏幕上什么都没有 —— 用户反馈"不够丝滑"就是这个。
        // 现在照片全程可见地飞出去，底和 chrome 提前淡掉把瀑布流让出来。
        //
        // chrome 必须排在照片**之后**（画在它上面），否则返回键和底部胶囊
        // 会被整屏的照片盖住、点不到。
        val fadeP = if (dismissThresholdPx <= 0f) {
            0f
        } else {
            (dismissY.value / dismissThresholdPx).coerceIn(0f, 1f)
        }

        // ① 黑色底
        //
        // `background` 必须写在 `graphicsLayer{alpha}` **之后**：`Modifier.background`
        // 只影响它**之后**那截链，写在前面 = 底色画在 alpha 层外面 = 永远不透明。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - fadeP }
                .background(Color.Black),
        )

        // ② 照片层：只位移 + 缩小，不淡出
        // 之前是整屏 Fit：图会被撑到屏幕边缘，读起来像"换壁纸"而不是"翻看一张照片"。
        // 现在按图片自身宽高比给它一个居中的圆角容器 —— 四周留一圈黑边、四角有圆角，
        // 视觉上就是"一张卡片摆在那儿"。裁切用 Crop + 容器比例和图片一致，
        // 所以圆角正好落在图片四角上（Fit 的留白会让圆角落空）。
        val photoRatio = if (item.width > 0 && item.height > 0) {
            item.width.toFloat() / item.height.toFloat()
        } else {
            1f
        }
                Box(
            modifier = Modifier
                .fillMaxSize()
                // ---- 照片自己往下走 + 稍微缩小 ----
                //
                // 只动照片这一层，黑色底留在原地淡掉（底在上面那一层的 alpha 里）。
                // 这样看着是"照片被抽走了、瀑布流露出来"，
                // 而不是"整块黑色界面一起滑下去"。
                .graphicsLayer {
                    val p = if (dismissThresholdPx <= 0f) {
                        0f
                    } else {
                        (dismissY.value / dismissThresholdPx).coerceIn(0f, 1f)
                    }
                    // 只位移 + 缩小，**不淡出** ——
                    // 淡出归黑色底那一层（见下面）。之前整层一起淡，
                    // 照片在退场动画前 20% 就没了，剩下那一百多毫秒是干等，
                    // 观感就是"啪一下消失然后卡一下"。
                    translationY = dismissY.value
                    val s = 1f - ZOOM_DISMISS_SHRINK * p
                    scaleX = s
                    scaleY = s
                }
                .clickable(
                    // 关掉涟漪：这里点的是"整幅画面"，弹一圈水波纹会很怪
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    // 信息展开时，点画面先收信息；否则收放上下 chrome。
                    if (infoExpanded) infoExpanded = false else chromeVisible = !chromeVisible
                },
        ) {
            // 直接复用整理照片页那套 [ZoomableImage]：
            // 双指捏合缩放、放大后单指拖动、双击在 1x↔铺满 之间切换、
            // 以及"接近吸合边界"的渐进阻尼（见 ZoomableImage 里的 rubber）。
            //
            // ---- 容器必须是**整屏**，不能是圆角卡片 ----
            //
            // 1.3.19 把照片装进一个按宽高比裁出来的圆角卡片里（四周留黑边），
            // 结果缩放**被限制在画框内**：`ZoomableImage` 的边界全部按
            // `container` 尺寸算，容器是那张小卡片 ⇒ 放大上限 = 卡片尺寸、
            // 到边就停，和外面整理照片页"能放大到满屏"完全不是一个手感（用户报）。
            //
            // 现在容器给整屏，**圆角由图片自己那一层裁**：
            //  · 1x 时图片 Fit 显示，圆角落在图片四角（外层 padding 留出黑边）；
            //  · 放大后图片撑满整屏，圆角自然被推出屏幕外，看不见也不碍事。
            // 两者都是"该有的时候有、不该有的时候没有"，不需要额外判断。
            ZoomableImage(
                model = item.uri,
                contentDescription = item.name,
                isAnimated = item.isAnimated,
                contentRatio = photoRatio,
                modifier = Modifier.fillMaxSize(),
                // 和整理照片页、收藏/喜欢页预览同一套圆角（屏宽 × 2.3%，上限 14dp）
                cornerRadius = rememberContentCorner(),
                // 遮罩要盖在图片自己的四角上，所以得知道它的宽高比
                cornerAspect = photoRatio,
                onZoomedChange = { isZoomed ->
                    // **只在状态真的跨过去时才动 chrome。**
                    //
                    // 之前是无条件 `chromeVisible = !isZoomed`，而 `ZoomableImage`
                    // 在每次手势结束都会报一次 —— 于是 1x 下单击时：
                    //   clickable 先把 chrome 切掉，紧接着这次回报又把它写成"显示"，
                    //   下一次单击再切掉、再被写回显示 ⇒ **看起来就是"单击只收不回来"**。
                    //
                    // 判了"是否跨过"之后，两条路就各管各的：
                    //   单击 ⇒ clickable 切显隐；放大/缩回 ⇒ 这里收起/显示。
                    val wasZoomed = zoomed
                    zoomed = isZoomed
                    if (isZoomed != wasZoomed) {
                        // 放大时收起；**缩回 1x 时强制显示**（不是 toggle）——
                        // 用户刚松手的是一次"看图"动作，不是"收起 chrome"。
                        chromeVisible = !isZoomed
                    }
                },
                // **不给 onSingleTap**：单击切 chrome 由外层那个 `.clickable` 负责。
                //
                // 两条路都在的话，一次单击会把 chrome 切两次（外层立刻切、
                // 这里在双击判定窗口结束后再切一次）⇒ 净效果为零，
                // 表现就是"图标和小字不稳定、像是按了没反应"（用户反馈）。
                // 双击 = 喜欢。给了它就不做双击缩放（缩放还有捏合）。
                onDoubleTap = {
                    onToggleLike?.invoke()
                    // 切换后的状态就是取反 —— 此刻 isLiked 还是切换前那一版。
                    dtFlash = DoubleTapFlash(liked = true, on = !isLiked)
                    dtFlashTick++
                },
            )
        }

        // ③ 上下 chrome：只淡出
        if (chromeVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - fadeP },
            ) {
                DayZoomChrome(
                    item = item,
                    infoExpanded = infoExpanded,
                    onToggleExpanded = { infoExpanded = !infoExpanded },
                    onBack = onClose,
                    onToggleFavorite = onToggleFavorite,
                    onToggleLike = onToggleLike,
                    isFavorite = isFavorite,
                    isLiked = isLiked,
                )
            }
        }

        // ④ 双击反馈：画在**最上面**（chrome 之后），所以不会被照片或胶囊挡住。
        // 上一版漏了这一段 —— 状态会切、但什么都不显示。
        dtFlash?.let { flash ->
            DoubleTapFlashOverlay(flash, modifier = Modifier.align(Alignment.Center))
        }
    }
}

/**
 * 放大看单张时的上下 chrome（顶栏 + 底栏）。
 *
 * 单独抽出来是为了让它们和黑色底住在**同一层**里 ——
 * 下滑退出时"淡出"归那一层，"位移"归照片那一层（见 [DayZoomView]）。
 */
@Composable
private fun BoxScope.DayZoomChrome(
    item: MediaItem,
    infoExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onToggleLike: (() -> Unit)?,
    isFavorite: Boolean,
    isLiked: Boolean,
) {
    // 顶栏：日期居中
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = formatDayKey(item.dateTakenMillis),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(48.dp))
    }

    // 底栏：和「直接整理照片」那边一样的一颗 InfoPill
    // （日期 + 次要信息 + ⓘ）。不点它就能看到最要紧的几项，
    // 点开 InfoPill 右侧的 ⓘ 才展开完整信息。
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (infoExpanded) {
            DayInfoSheet(item = item)
            Spacer(Modifier.height(12.dp))
        }

        // 喜欢（左，心形）/ 收藏（右，星形）——
        // 直接复用整理照片页那一对组件，不另造一套外观。
        // 收藏会写进系统相册的收藏夹，所以给的是星星；
        // 喜欢只记在本应用里，给的是心形 —— 两者的分量一眼能分清。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            if (onToggleLike != null) {
                FavoriteButton(
                    selected = isLiked,
                    modifier = Modifier.align(Alignment.CenterStart),
                    onClick = onToggleLike,
                )
            }
            if (onToggleFavorite != null) {
                StarButton(
                    selected = isFavorite,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = onToggleFavorite,
                )
            }
            InfoPill(
                primary = relativeDayLabel(item.dateTakenMillis),
                secondary = dayPillSecondary(item),
                modifier = Modifier.align(Alignment.Center),
                onClick = onToggleExpanded,
            )
        }
    }
}

/**
 * InfoPill 第二行：**文件夹 · 大小**。
 *
 * ---- 全 App 统一的胶囊规则 ----
 * 胶囊只有一行、空间有限，所以**只放"日期 · 文件夹 · 大小"**这三样
 * （日期在 primary 那一行）。分辨率、时长、地点、EXIF 这些一律留给详情面板 ——
 * 硬塞进胶囊的结果是挤成一团再被省略号截断，等于什么都看不清。
 * 用户明确要求过这条规则（1.3.66）。
 */
private fun dayPillSecondary(item: MediaItem): String = buildString {
    val bucket = item.bucket.trim()
    if (bucket.isNotEmpty()) {
        append(bucket)
        append(" · ")
    }
    append(formatSize(item.sizeBytes))
}

/** 这一项到底是什么：实况 / 动图 / 视频 / 截屏 / 照片。 */
private fun dayKindLabel(item: MediaItem): String = when {
    item.isMotionPhoto -> "实况"
    item.isAnimated -> "动图"
    item.kind == MediaKind.VIDEO -> "视频"
    item.kind == MediaKind.SCREENSHOT -> "截屏"
    else -> "照片"
}

/**
 * `i` 展开的详细信息。
 *
 * 和底部 `InfoPill` **不重复** —— 那儿已经给了日期、类型、尺寸、大小，
 * 这里只补"想深挖时才看"的那些：文件名、文件夹、精确到分秒的拍摄时间、
 * 格式、媒体库 ID、实况偏移、是否长图。
 */
@Composable
private fun DayInfoSheet(item: MediaItem) {
    val rows = buildList {
        add("文件名" to item.name)
        if (item.bucket.isNotBlank()) add("文件夹" to item.bucket)
        add("拍摄时间" to formatDateTime(item.dateTakenMillis))
        if (item.mimeType.isNotBlank()) add("格式" to item.mimeType)
        add("媒体库 ID" to item.id.toString())
        item.motionOffsetFromEnd?.let { off ->
            add("实况视频" to if (off < 0) "有（偏移待读）" else "有（尾 -${off}B）")
        }
        add("长图" to if (item.isLongImage) "是" else "否")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "详细信息",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
        )
        rows.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = k,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    text = v,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 焦点格的垫底图：**系统缩略图**。
 *
 * 高清图要几十毫秒才解得出来（读原图 → 降采样 → 上屏），而飞入动效第一帧就开始了 ——
 * 这段时间格子里是空的，就是用户看到的"黑屏一下"。
 *
 * 系统缩略图 1~2ms 就能拿到（相册扫描时早就生成好了，见 [SystemThumbnailFetcher]），
 * 用它顶住这几十毫秒；高清到了 Coil 会自己淡入（这个请求没关 crossfade），
 * 交接是平滑的、不会"啪"地换一张。
 *
 * `uri` 为 null（不是焦点格）时直接返回 null —— 一格都不多请求。
 */
@Composable
private fun rememberFocusPlaceholder(uri: Uri?): Painter? {
    if (uri == null) return null
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .setParameter(SystemThumbnailFetcher.REQUEST_KEY, true)
            .crossfade(false)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}


/**
 * [cum]（每格左边缘的累计偏移，单调递增）里，第 [px] 个像素落在哪一格。
 *
 * 二分而不是 `indexOfFirst` —— 这一步挂在滚动上，
 * 一排几百格时线性扫是纯浪费。
 * 末项是"内容总长"，所以有效下标只到 `size - 2`。
 */
private fun FloatArray.slotAt(px: Float): Int {
    if (size <= 1) return 0
    var lo = 0
    var hi = size - 2
    var ans = 0
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (this[mid] <= px) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}
