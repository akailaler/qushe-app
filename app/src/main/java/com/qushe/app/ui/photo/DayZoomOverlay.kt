package com.qushe.app.ui.photo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.qushe.app.core.MediaItem
import com.qushe.app.ui.common.DoubleTapFlash
import com.qushe.app.ui.common.DoubleTapFlashOverlay
import com.qushe.app.ui.common.rememberContentCorner
import com.qushe.app.ui.photo.infoPillPrimary
import com.qushe.app.ui.photo.infoPillSecondary
import com.qushe.app.core.formatDayKey
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.flingTo
import com.qushe.app.ui.common.ZoomableImage
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
internal fun DayZoomView(
    item: MediaItem,
    /** 切换收藏（进系统相册那一档）。null = 不给这个动作。 */
    onToggleFavorite: (() -> Unit)?,
    /** 切换喜欢（只记在本应用里）。null = 不给这个动作。 */
    onToggleLike: (() -> Unit)?,
    isFavorite: Boolean,
    isLiked: Boolean,
    /** 胶囊第一行是否显示相对时间 / 具体日期（透传设置）。 */
    relativeDates: Boolean = true,
    absoluteDates: Boolean = false,
    /** 拍摄地点（胶囊第三行）。 */
    resolveLocation: suspend (MediaItem) -> String? = { null },
    onClose: () -> Unit,
) {
    var chromeVisible by remember { mutableStateOf(true) }
    var infoExpanded by remember { mutableStateOf(false) }
    // 拍摄地点：异步读，读到之前胶囊不占第三行
    var locationName by remember(item.storeKey()) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.storeKey()) {
        locationName = resolveLocation(item)
    }
    /** 照片是不是正被捏大 —— 放大态不认领下滑（见下面手势的说明）。 */
    var zoomed by remember { mutableStateOf(false) }
    /** 返回键的"先回到 1x"信号。 */
    var zoomResetTick by remember { mutableIntStateOf(0) }

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
        // 返回键的优先级：先收信息面板 → 再缩回 1x → 最后才退出这一屏
        when {
            infoExpanded -> infoExpanded = false
            zoomed -> {
                zoomResetTick++
                zoomed = false
                chromeVisible = true
            }
            else -> onClose()
        }
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
                .background(Ink),
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
                resetZoomSignal = zoomResetTick,
                contentDescription = item.name,
                isAnimated = item.isAnimated,
                // ---- 长图**也要传**比例 ----
                // 之前这条写的是"长图不传"，理由是"传了会锁死在 1~1.25 倍"。
                // 那个结论是**建立在 `drawnSizePx` 把宽高写反的 bug 上**的：
                // 传比例反而算错，不传（退化成"绘制区=容器"）看起来更正常。
                // Bug 修掉之后，**传比例才是对的** —— `panLimit` 和 `snapScale`
                // 全靠它算真实绘制尺寸，不传就只能按容器估，一拖就露黑底。
                contentRatio = photoRatio,
                // 上限回到 `maxScale`（8x）之后，解码尺寸会变成"容器 × 8"（一张荒谬的巨图）。
                // 用原始像素封顶，既清楚又不会白解。
                sourceSize = if (item.width > 0 && item.height > 0) {
                    androidx.compose.ui.unit.IntSize(item.width, item.height)
                } else {
                    null
                },
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
                    relativeDates = relativeDates,
                    absoluteDates = absoluteDates,
                    location = locationName,
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
internal fun BoxScope.DayZoomChrome(
    item: MediaItem,
    infoExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onBack: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
    onToggleLike: (() -> Unit)?,
    isFavorite: Boolean,
    isLiked: Boolean,
    relativeDates: Boolean,
    absoluteDates: Boolean,
    location: String?,
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
        //
        // 胶囊和按钮**同一行**（和整理照片页 / 收藏页统一）。
        // 它自己不设宽度上限，可用宽度由这层 `padding(horizontal = 56.dp)`
        // 限定在"两颗按钮之外" —— 52 = 按钮 44 + 8 间隙。
        // 之前这里夹着却写死 230dp 文字区（总宽 280dp），压住了按钮内侧，
        // 才出现"点收藏刚开始几下没反应"。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
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
                // 文案来自唯一来源（与整理照片页、收藏页预览同一套）
                primary = infoPillPrimary(item, relativeDates, absoluteDates),
                secondary = infoPillSecondary(item, relativeDates, absoluteDates),
                location = location,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp),
                onClick = onToggleExpanded,
            )
        }
    }
}
