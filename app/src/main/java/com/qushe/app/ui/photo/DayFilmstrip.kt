package com.qushe.app.ui.photo

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qushe.app.core.MediaItem
import com.qushe.app.core.dayStartOf
import com.qushe.app.ui.common.DAY_INTRO_MS
import com.qushe.app.core.SystemThumbnailFetcher
import com.qushe.app.core.storeKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
internal fun DayFilmstrip(
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

    // ---- 拖到尽头时的橡皮筋 ----
    //
    // 用户的描述："滑到尽头了，再滑上下两排里更长的那一排，还是会继续滑，
    // 原地滑，形成一种接近闪烁的感觉"。
    //
    // 尽头本身由"两排内容宽必须恒等"修掉（见 `FilmstripRow` 里 `ownSpan` 的说明）。
    // 但"手指还在划、画面一点回应都没有"依然难受 —— 这里补一段 iOS 那种
    // 阻尼位移：拉出去越远越难拉，松手弹回原位。
    //
    // 状态放在**这一层**、两排共用：它们必须一起被拉出去，
    // 各存一份就会出现"一条带子出去、另一条没动"的脱节。
    val overRaw = remember { mutableFloatStateOf(0f) }   // 手指累计的"拉出量"（未阻尼）
    val overDraw = remember { mutableFloatStateOf(0f) }  // 真正画到屏幕上的位移（已阻尼）
    val overJob = remember { mutableStateOf<Job?>(null) }
    val overScope = rememberCoroutineScope()
    val overLimitPx = with(LocalDensity.current) {
        OVER_DRAG_LIMIT.roundToPx().toFloat().coerceAtLeast(1f)
    }
    val overDrag = remember(overLimitPx) {
        OverDrag(
            raw = overRaw,
            draw = overDraw,
            limitPx = overLimitPx,
            push = { d ->
                if (d != 0f) {
                    overRaw.floatValue += d
                    overDraw.floatValue = dampOver(overRaw.floatValue, overLimitPx)
                }
            },
            // 松手：按 Spring 弹回 0。DampingRatioMediumBouncy 会稍微过冲一下，
            // 就是 iOS 橡皮筋收回去时那点余震；不要用 tween —— 线性收回很假。
            release = {
                overJob.value?.cancel()
                overJob.value = overScope.launch {
                    animate(
                        initialValue = overRaw.floatValue,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { v, _ ->
                        overRaw.floatValue = v
                        overDraw.floatValue = dampOver(v, overLimitPx)
                    }
                    overRaw.floatValue = 0f
                    overDraw.floatValue = 0f
                }
            },
            // 新手势按下：停住回弹，从当前位置接着拉（不跳回 0）
            cancel = { overJob.value?.cancel() },
        )
    }

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
            over = overDrag,
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
            over = overDrag,
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
 * 只有落在"当前视口前后各 0.5 屏"范围内的格子才真正组合成 [CellSlot]，
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
internal fun FilmstripRow(
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
    /** true = 上滑删除；false = 上滑收藏。**现在恒为 true**（那条设置已删）。 */
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
     * 报的是**本排自己**的跨度；外层取两排的较大者当分母。
     * 不能直接用 `scroll.maxValue`：两排共用一个 `ScrollState`，
     * Compose 每次布局都用"最后测量的那排"覆盖它，读数会在两个值之间跳。
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
    /** 拖到尽头的橡皮筋状态（两排共用一份，见 [DayFilmstrip]）。 */
    over: OverDrag,
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
        // ---- 两端垫片：**不再留半屏** ----
        //
        // 之前两端各垫半个视口，为的是"第一张 / 最后一张也能滑到屏幕正中"。
        // 代价是滑到尽头时，最后一张停在正中、右边空出半个屏幕（用户反馈
        // "滑到最左或最右还有半屏空白"）。
        //
        // 现在改成 8dp：尽头就是内容的边。代价写在明处 ——
        // 最前面 / 最后面那几张**不能居中**了（进场定位会把目标 clamp 到
        // [0, maxValue]，靠边的那张就贴边停）。日常进场看的焦点图前后都有照片，
        // 基本碰不到这种情况；真碰上了也只是"停在边上"，不会错位。
        val edgePad = EDGE_PAD
        val density = LocalDensity.current
        val viewportPx = with(density) { maxWidth.roundToPx().toFloat() }
        val edgePadPx = with(density) { edgePad.roundToPx().toFloat() }

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
        // （不含两端那个 [EDGE_PAD]）。被标记删除的格子贡献 0 ——
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

        // 本排的**内容**总长 —— 上报给外层当刻度条的分母，
        // 也是两排"补齐到同一长度"的依据。
        //
        // ---- 必须算上 `rowOffsetPx` ----
        // 下排整体右移了半格（见下面的 `rowOffsetPx`），那半格是**真实的宽度**：
        // 它在 Row 里是一个额外的 Spacer。之前这里漏了它，于是下排的
        // `horizontalScroll` 算出的 maxValue 比上排大出半格（约 100~200px）。
        // 两排共用一个 ScrollState ⇒ 滚到尽头后，一边夹一边放，
        // 手指继续划就变成"原地一抖一抖"（用户描述"滑到尽头还在原地滑、
        // 接近闪烁"）。算进来之后两排 maxValue 恒等，尽头就真的是尽头。
        val ownSpan = cumPx.getOrElse(cumPx.size - 1) { 0f } + edgePadPx * 2f +
            rowOffsetPx
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
        val focusTargetPx = remember(cumPx, focusIndex, viewportPx, edgePadPx, rowOffsetPx, focusCellW) {
            if (focusItem == null || focusIndex < 0 || !focusRowValid ||
                cumPx.size <= 1 || viewportPx <= 0f
            ) {
                -1f
            } else {
                edgePadPx + rowOffsetPx +
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
                // 屏幕中心落在本排内容坐标里的位置。
                // 要扣掉"这一排的起点"：两端垫片 + 下排的半格错位。
                // （之前漏了垫片这一项 —— 垫片是半屏时，标题日期会偏半屏；
                //  现在垫片只有 8dp，影响很小，但既然看到了就一起修对。）
                val centerPx = offset.toFloat() + viewportPx / 2f - rowOffsetPx - edgePadPx
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
        val ownMaxScroll = remember(cumPx, viewportPx, edgePadPx, rowOffsetPx, tailPadPx) {
            val total = cumPx.getOrElse(cumPx.size - 1) { 0f } +
                edgePadPx * 2f + rowOffsetPx + tailPadPx
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
        // `seededTarget` 就是那个"本排的目标偏移"：焦点图在本排时是它的正中；
        // 不在本排就是 -1（不播种，直接用 scroll.value）。
        val visibleRange by remember(
            scroll, viewportPx, cumPx, edgePadPx, positioned, rowOffsetPx, seededTarget,
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
                val shift = rowOffsetPx + edgePadPx
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

                    // ---- 把一次位移交给滚动，滚不动的部分变成橡皮筋 ----
                    //
                    // `dispatchRawDelta` 会返回**真正消费掉的位移**。
                    // 之前这里忽略返回值，于是滑到尽头后：手指继续划、
                    // 内容一动不动 —— 用户看到的"原地滑 / 像在闪"。
                    //
                    // 现在把差值收进 `over`：只有"已经在头/尾、且这一笔确实
                    // 没被消费掉"才算拉出。加 1px 死区是因为
                    // `ScrollState` 内部按 `roundToInt` 落值，正常滚动时
                    // 每帧都会差零点几像素，不加死区会被误判成"到尽头了"。
                    val dispatchScroll: (Float) -> Unit = { delta ->
                        if (delta != 0f) {
                            val consumed = scroll.dispatchRawDelta(delta)
                            val leftover = delta - consumed
                            val atEdge = scroll.value <= 0 || scroll.value >= scroll.maxValue
                            if (scroll.maxValue > 0 && atEdge &&
                                kotlin.math.abs(leftover) >= 1f
                            ) {
                                over.push(leftover)
                            }
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 新手势一律打断上一次惯性与回弹
                        flingJob?.cancel()
                        over.cancel()
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
                                        // 竖直 → 整串事件让给格子的上滑手势。
                                        // 被中断的回弹要放回去（否则会停在半拉状态）。
                                        if (over.raw.floatValue != 0f) over.release()
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

                            // ---- 先收橡皮筋，再滚内容 ----
                            //
                            // 已经拉出去的状态下手指往回带，第一段位移应该
                            // 先把那块"橡皮筋"收回来（内容才开始跟手），
                            // 否则手指已经反向了、画面却要等皮筋归零才动，
                            // 手感会像卡了一下。
                            val delta = -dx
                            val raw = over.raw.floatValue
                            if (raw != 0f && (delta > 0f) == (raw < 0f)) {
                                val next = raw + delta
                                if ((next > 0f) != (raw > 0f)) {
                                    // 越过 0：皮筋收完，剩下的继续滚内容
                                    over.raw.floatValue = 0f
                                    over.draw.floatValue = 0f
                                    dispatchScroll(next)
                                } else {
                                    over.raw.floatValue = next
                                    over.draw.floatValue =
                                        dampOver(next, over.limitPx)
                                }
                            } else {
                                dispatchScroll(delta)
                            }
                        }
                        // 松手：橡皮筋弹回去（不论这一下有没有拖动成功，
                        // 只要还拉着就得收 —— 上一次回弹被 cancel 过）
                        if (over.raw.floatValue != 0f) over.release()
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
                                    val before = scroll.value
                                    scroll.dispatchRawDelta(-v * dtMs / 1000f)
                                    // 已经到尽头：惯性到此为止。
                                    // 继续空转不会动，只是白跑帧（也是"原地滑"的观感来源）。
                                    if (scroll.value == before &&
                                        (scroll.value <= 0 || scroll.value >= scroll.maxValue)
                                    ) {
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
                // 橡皮筋只动"画"、不动布局：translationX 由绘制层读，
                // 所以每帧拉拽不会触发任何重组（这是它敢每帧写的唯一原因）。
                // 位置必须在 `horizontalScroll` **之前** —— 之后会被滚动容器
                // 自己的 clip 裁掉，拉出去的那截根本看不见。
                .graphicsLayer { translationX = -over.draw.floatValue }
                .horizontalScroll(scroll, enabled = true),
            // 刻意**不给 spacedBy** —— 间距由每个格子自己带（见 [CellSlot]），
            // 否则某项被删除后，它那段间距会留在原地。
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 两端各垫半个视口，让首尾两张也能滑到屏幕正中。
            Spacer(Modifier.width(edgePad))

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

            Spacer(Modifier.width(edgePad))
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
internal fun splitRowsBalanced(
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
        val w = displayWidthRatio(items[i]).coerceAtLeast(0.05f)
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
internal fun firstGreater(cum: FloatArray, x: Float): Int {
    var lo = 0
    var hi = cum.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (cum[mid] > x) hi = mid else lo = mid + 1
    }
    return lo
}

/** 第一个满足 `cum[k] >= x` 的下标（cum 非降序）。 */
internal fun firstAtLeast(cum: FloatArray, x: Float): Int {
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
internal fun CellSlot(
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
                    // ---- 每一格都先用系统缩略图垫着（用户要的方案一）----
                    //
                    // 拖底部滑块时，跳过的位置不发起请求（否则请求雪崩），
                    // 松手后要现解当前屏那几格 —— 每格几十毫秒，
                    // 这段时间屏幕上就是一片空白格。
                    //
                    // 系统相册早就为每个 Uri 生成过缩略图，`loadThumbnail` 命中那份
                    // 只要 1~2ms。所以先把它铺上（最长边 512，放大后糊，但**有内容**），
                    // 高清那份到了再由请求上的 crossfade 淡入 —— 观感从
                    // "空白 → 突然出现" 变成 "模糊 → 渐渐变清"，中间不再有空洞。
                    //
                    // 长图**不垫**：
                    //  · 系统那份是整张长图按 Fit 生成的细条，铺进 2:1 格子里只剩一条糊线；
                    //  · 更关键的是 `SystemThumbnailFetcher` 对长图会主动返回 null
                    //    （见它的 min-side 检查），请求随后落到默认解码器 ——
                    //    那等于为一张 828×30656 解 101MB，绝对不能发生。
                    // 尺寸在 DayCell 里算（那里才知道格子多大），
                    // 这里只说"要不要垫底"。
                    systemPlaceholder = !cell.isLongImage,
                    focusDecodeSize = focusDecodeSize,
                )
            }
        }
    }
}

/**
 * 一格缩略图该有多宽 —— 普通图严格按原图比例，长图固定高:宽 = 2:1。
 *
 * 规则：
 *  · 竖图 / 方图：高度顶满行高，宽度 = 行高 × 比例；
 *  · 横图：宽度顶到 [CELL_MAX_WIDTH]（否则一张超宽横图会占掉大半个屏幕）；
 *  · **长图（高宽比 > 2）**：固定 高:宽 = 2:1（宽 = 行高 / 2）。原比例会让
 *    828×30656 变成一根细条，画面交给区域解码取中间一块。
 *
 * 抽成纯函数是为了让"可见区间估算"和"真实布局"用**同一套算法** ——
 * 两边只要有一点点不一致，屏幕外的占位宽度就会和真格子对不上，
 * 整排会越滚越歪。
 */
internal fun cellWidthDp(item: MediaItem, rowHeight: Dp): Dp =
    if (item.isLongImage) {
        // 长图不按原比例摆 —— 否则 828×30656 会变成一根 96dp 的细条。
        // 统一成 高:宽 = 2:1（宽度 = 行高 / 2），画面由区域解码给出中间一块。
        rowHeight * LONG_DISPLAY_ASPECT
    } else {
        cellWidthForRatio(itemRatio(item), rowHeight)
    }

/** 分排均衡用的宽度权重：长图按它实际占的 1/2 行高算，不能按原比例。 */
internal fun displayWidthRatio(item: MediaItem): Float =
    if (item.isLongImage) LONG_DISPLAY_ASPECT else itemRatio(item)

/**
 * [cellWidthDp] 的 ratio 版 —— 可见区间估算与真实布局共用。
 *
 * 之前 `DayCell` 的 `ImageRequest` 用的是裸的 `行高 × ratio`，
 * 没有 CELL_MIN/MAX 夹紧，横图多解码 2.5 倍像素、竖长图解得比显示还小。
 * 现在请求直接用格子算好的宽度，两边天然恒等。
 */
internal fun cellWidthForRatio(ratio: Float, rowHeight: Dp): Dp {
    val widthAtRowHeight = rowHeight * ratio
    return if (widthAtRowHeight > CELL_MAX_WIDTH) CELL_MAX_WIDTH
    else widthAtRowHeight.coerceAtLeast(CELL_MIN_WIDTH)
}

/** 宽高比，带上下限（防止坏数据把布局撑爆）。 */
internal fun itemRatio(item: MediaItem): Float = when {
    item.width > 0 && item.height > 0 -> item.width.toFloat() / item.height
    else -> 1f
}.coerceIn(0.25f, 4f)

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
internal fun WideSpacer(widthPx: Float) {
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
 * [cum]（每格左边缘的累计偏移，单调递增）里，第 [px] 个像素落在哪一格。
 *
 * 二分而不是 `indexOfFirst` —— 这一步挂在滚动上，
 * 一排几百格时线性扫是纯浪费。
 * 末项是"内容总长"，所以有效下标只到 `size - 2`。
 */
internal fun FloatArray.slotAt(px: Float): Int {
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
