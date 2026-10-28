package com.qushe.app.ui.photo

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.LongThumbnailFetcher
import com.qushe.app.ui.common.DAY_INTRO_MS
import com.qushe.app.ui.common.rememberPageGap
import com.qushe.app.ui.common.rememberContentShape
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.ZoomableImage
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 上下两排 —— 「去留」就是这个数。多了会变成网格墙，横拨的手感就没了。 */
internal const val ROWS = 2

/**
 * 一排里格子的最大宽度。
 *
 * 等高布局下这个值决定"横图能有多宽" —— 参照「去留」：**横着的长图保留行高、
 * 把宽度放开**（4:3、16:9 这种一眼就是一张横幅），所以上限必须给得比行高还大。
 * 之前 225dp 会把一张 16:9 横图裁成中间一条竖缝，完全认不出画面。
 * 520dp 是"再宽就截断"的位置（≈ 两张竖图的宽度）。
 */
internal val CELL_MAX_WIDTH = 520.dp

/** 一排里格子的最小宽度。极端长图（全景长截图）会在这里被改成"裁一段"显示。 */
internal val CELL_MIN_WIDTH = 96.dp

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
 * 圆角半径 = 屏宽 **2.3%**（上限见 [CELL_CORNER_MAX]）。
 *
 * 间距**不在**这里 —— 它已经迁到 `ui/common/Shapes.kt` 的 `CELL_GAP_RATIO`，
 * 而且上下左右共用同一个值（见 [DayMetrics.gap]）。
 */
internal const val CELL_CORNER_RATIO = 0.023f

/** 圆角半径的上限 —— 再大就接近胶囊，照片墙会读成"药丸排"。 */
internal val CELL_CORNER_MAX = 14.dp

/**
 * 一条带子内**上下各留 2dp**，让图片不贴着带子边缘。
 *
 * 它参与格子行高的计算（`maxHeight - VERTICAL_PAD * 2`）。
 * 两排之间的间距不在这里 —— 那个和同排的左右间距共用 [DayMetrics.gap]。
 */
internal val VERTICAL_PAD = 2.dp

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
internal class DayMetrics(
    /** 上下与左右共用的间距。 */
    val gap: Dp,
    val corner: RoundedCornerShape,
)

/** 由屏宽算出这一轮布局共享的 [DayMetrics]。 */
@Composable
internal fun rememberDayMetrics(): DayMetrics {
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
internal val CELL_SHRINK_SPEC = tween<Float>(durationMillis = 560)

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
 * 另有一条：**某一格往上滑 = 标记删除**（1.3.48 起固定，没有设置项）。
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
    /** 「i」胶囊是否显示相对时间 / 具体日期（透传设置）。 */
    relativeDates: Boolean = true,
    absoluteDates: Boolean = false,
    /** 拍摄地点（有就显示在胶囊第三行）。由上层提供，拿不到返回 null。 */
    resolveLocation: suspend (MediaItem) -> String? = { null },
    /** 已经被标记删除的项（[MediaItem.storeKey] 集合）—— 这些不再出现在瀑布流里。 */
    markedKeys: Set<Long>,
    /**
     * true = 上滑删除；false = 上滑收藏。
     *
     * **现在恒为 true**：设置页那条开关 1.3.48 已删（上滑收藏是一条死路径，
     * 关掉只会弹提示什么都不做）。参数保留是为了不动调用点，别再给它接 UI。
     */
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
                relativeDates = relativeDates,
                absoluteDates = absoluteDates,
                resolveLocation = resolveLocation,
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
 * 拖到尽头时的橡皮筋状态（两排**共用一份**）。
 *
 * 为什么不做成每排各一份：手指只落在其中一排，但画面上的"一整面照片墙"
 * 必须一起被拉出去。各存一份的话就会出现一条带子动了、另一条没动的脱节。
 *
 * 字段全是可变状态/普通函数，实例本身在 `remember` 里创建一次 ——
 * 传进 [FilmstripRow] 不会因为它变化而把整排格子拖去重组。
 */
@Stable
internal class OverDrag(
    /** 手指累计的拉出量（未阻尼，px）。正 = 内容被往左拉出（右端到头）。 */
    val raw: MutableFloatState,
    /** 真正画到屏幕上的位移（已阻尼，px）。 */
    val draw: MutableFloatState,
    /** 阻尼的渐近上限（px）：拉得再狠也不会超过它。 */
    val limitPx: Float,
    /** 往"拉出"方向累加一段位移。 */
    val push: (Float) -> Unit,
    /** 松手弹回 0。 */
    val release: () -> Unit,
    /** 按下时停住回弹，从当前位置接着拉。 */
    val cancel: () -> Unit,
)

/**
 * 橡皮筋的渐进阻尼：`limit * raw / (limit + |raw|)`。
 *
 * 拉得少时几乎 1:1 跟手（raw ≪ limit 时 ≈ raw），越拉越硬，
 * 渐近线是 [limit] —— 永远拉不出这个上限。
 * 不用线性 `coerceIn` 是因为它会在到达上限那一帧突然"变硬"，很假。
 */
internal fun dampOver(raw: Float, limit: Float): Float =
    if (limit <= 0f) 0f else limit * raw / (limit + kotlin.math.abs(raw))

/**
 * 橡皮筋最多拉出多远。
 *
 * 约一屏的 1/10：够让手指收到"到头了"的反馈，又不至于把整面墙扯离屏幕。
 */
internal val OVER_DRAG_LIMIT = 96.dp

/**
 * 瀑布流两端各留的垫片。
 *
 * 8dp 只为了让第一张 / 最后一张不贴着屏幕边缘被切得难看；
 * 再大就是"滑到尽头还剩半屏空白"（用户明确不要）。
 */
internal val EDGE_PAD = 8.dp







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


/** 长图在瀑布流里的显示宽高比（固定 1:2 竖图 = 高:宽 2:1，不拉伸）。 */
internal const val LONG_DISPLAY_ASPECT = 1f / 2f





/**
 * 普通缩略图的解码比例（相对格子显示尺寸）。
 *
 * 0.6 倍：每格像素降到显示的 36%，拖刻度条时一屏几格的总解码量明显下降
 * （用户报"老机型瀑布流滑动时空白"）。
 *
 * **只影响格子里的那张图**，不影响放大：
 *  · 点开大图走 `ZoomableImage` 的独立请求，按容器 × 放大上限解码，与这里无关；
 *  · 长图走 `LongThumbnailFetcher` / 区域解码，也不看这个值。
 * 代价是格子显示时放大 1.67 倍，静止看会觉得略软 —— 滑动时的流畅优先。
 */
internal const val THUMB_DECODE_SCALE = 0.6f

/** 往上滑多少像素算"确定触发"。和整理页单张手势的量级对齐。 */
internal const val SWIPE_UP_DELETE_PX = 96

/**
 * 跟手滑出最多露出格高的这个比例。
 *
 * 露满格高会让下面那排的照片从缝里透出来，整面墙变脏；
 * 露太少又看不出底下有东西。55% 是"看得见露底、看不见下一排"的折中。
 */
internal const val SWIPE_UP_TRAVEL_FRACTION = 0.55f

/**
 * 进场定位最多重试多少帧。
 *
 * 布局通常一两帧就落地，重试到第三帧基本都能对上。
 * 20 帧（约 0.3 秒）只是防死循环的上限 —— 万一模型和真实布局真对不上，
 * 也不能让这里永远转下去，宁可停在原地也不要卡住。
 */
internal const val POSITION_MAX_TRIES = 20

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
internal const val INTRO_SETTLE_FRAMES = 20

/**
 * 放大看单张时，往下拖多远算"要回去"（占屏高的比例）。
 *
 * 0.22 是 iOS 相册 / 「去留」那一档的手感：明显是一整个下滑动作，
 * 又不会划到底才生效。再小会误触（读图时不小心蹭一下就退出去了）。
 * 另有 120dp 的绝对下限 —— 屏特别矮的时候按比例算出来的距离会短得离谱。
 */
internal const val ZOOM_DISMISS_FRACTION = 0.22f

/**
 * 下滑退出时的**甩动**门槛（px/s）。
 *
 * 和 [ZOOM_DISMISS_FRACTION] 是"或"的关系，不是"且"：
 * 慢慢拖要够距离，快速甩只要够快 —— 只判距离的话，快速一甩
 * （手指没走多远就松手）会被判成"没到阈值"，用户反馈就是"触发不了"。
 * 1200px/s 大约是"一个自然的手腕甩动"，比这更慢的就不算主动要走了。
 */
internal const val ZOOM_DISMISS_FLING_VELOCITY = 1200f

/**
 * 下滑退出时照片一起缩多少（比例）。
 *
 * 只是"稍微缩一点"的暗示 —— 让画面看起来在往后退，
 * 不是真的缩成一张卡片。0.12 再大就开始像"把照片扔掉"，太重了。
 */
internal const val ZOOM_DISMISS_SHRINK = 0.12f

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
internal const val INTRO_READY_TIMEOUT_MS = 1200L

/**
 * 拖拽增益：手指移动 1px，画面走 1.25px。
 *
 * 默认的 1:1 是最"跟手"的（手指和内容严丝合缝），但用户反馈
 * "滑动的幅度有点小，改大一点" —— 他要的是**同样的手势能走过更多内容**。
 * 1.25 是折中：明显更省力，又不至于让人觉得画面在"超车"手指。
 * 惯性起手速度用的是同一份 dx，所以增益对滑行同样生效。
 */
internal const val DRAG_GAIN = 1.25f

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
internal class DayCellUi(
    val uri: Uri,
    val kind: MediaKind,
    val durationMillis: Long,
    val storeKey: Long,
    /**
     * 动图（GIF / 动态 WebP / 动态 HEIF）。
     *
     * 存在的唯一理由是**解码路径不同**：动图不能给解码器设目标尺寸
     * （见 [DayCell] 里那段说明），所以这个标记必须进模型、
     * 而且要参与 `imageRequest` 的 `remember` 键。
     */
    val isAnimated: Boolean,
    /** 长图（长截图 / 聊天记录）—— 用来显示角标。 */
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
internal class DayCellActions(
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
internal const val SPACER_CHUNK_PX = 200_000f












