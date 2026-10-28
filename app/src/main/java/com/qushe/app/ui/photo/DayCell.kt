package com.qushe.app.ui.photo

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.qushe.app.core.MediaKind
import com.qushe.app.core.LongThumbnailFetcher
import com.qushe.app.core.SystemThumbnailFetcher
import com.qushe.app.core.formatDuration
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.ZoomableImage
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Surface2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.qushe.app.ui.common.MediaBadge

/**
 * 一格缩略图。普通图宽高按原图比例算，长图固定高:宽 = 2:1（见 [cellWidthDp]）。
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
internal fun DayCell(
    cell: DayCellUi,
    cellWidth: Dp,
    rowHeight: Dp,
    /** 共享的圆角形状实例（见 [DayMetrics]）。 */
    corner: RoundedCornerShape,
    /** 这一格在"这一天"里的序号（点击时回传）。 */
    index: Int,
    /** true = 上滑删除；false = 上滑收藏。**现在恒为 true**。 */
    swipeUpDeletes: Boolean,
    actions: DayCellActions,
    /**
     * 高清图到位之前先垫着的那张（系统缩略图）。null = 不垫。
     *
     * 除长图外每一格都要垫 —— 见 [rememberSystemThumbnailPlaceholder] 的说明。
     */
    systemPlaceholder: Boolean = false,
    /**
     * 焦点格专用的解码尺寸 —— 照用整理照片页那份。
     *
     * 飞入动效的起点就是照片页那一张，两者**同一个 uri + 同一个 size**
     * 才会命中 Coil 的 memory cache ⇒ 焦点格第一帧就是同一张高清图，
     * 起手不会出现"糊一下再变清"的闪烁。null = 用格子自己的尺寸。
     */
    focusDecodeSize: IntSize? = null,
) {
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
    // 格子的像素尺寸：**解码请求和垫底图必须用同一份**，
    // 否则两者取景不同，高清接上去时画面会挪一下（看起来就是闪）。
    val hPx = with(imgDensity) { cellH.roundToPx() }.coerceAtLeast(1)
    val wPx = with(imgDensity) { cellW.roundToPx() }.coerceAtLeast(1)
    val placeholder = if (systemPlaceholder) {
        rememberSystemThumbnailPlaceholder(cell.uri, wPx, hPx)
    } else {
        null
    }
    // ---- 不做交叉淡入（用户反馈"轻微闪烁、像从暗变亮"）----
    //
    // Coil 的 crossfade 不是"上层淡入"，而是 **底层淡出 + 目标淡入** 两条
    // alpha 同时跑。中间那几帧两层都只有半透明，叠在一起的总不透明度
    // 只有约 75% —— 观感就是**先暗一下再亮回来**，比硬切更像"闪"。
    //
    // 之前只有焦点格开淡入（它一进来就在屏幕正中，硬切容易被看见），
    // 1.3.86 给每格都垫了系统缩略图后，`placeholder != null` 恒成立，
    // 于是所有格子都开了 —— 用户立刻看到整片轻微闪烁。
    //
    // 现在全部关掉：底下已经有系统缩略图兜着，硬切只是"糊 → 清"，
    // 内容位置完全一致，不存在空白，反而比淡入干净。
    val fadeIn = false
    val imageRequest = remember(
        cell.uri, cellH, cellW, cell.isAnimated, cell.isLongImage,
        imgDensity, fadeIn, focusDecodeSize,
    ) {
        // ---- 请求尺寸必须和布局用**同一个**值 ----
        //
        // 之前这里是裸的 `hPx * ratio`，没有 CELL_MIN/MAX 夹紧：
        //  · 横图（ratio 顶到 4）请求约 3600px 宽，格子只显示 1430px，
        //    `ContentScale.Crop` 把多余的裁掉 —— **多解码 2.5 倍像素**；
        //  · 竖长图（ratio 0.25）请求 225px 宽，格子要显示 264px，
        //    **解码得比显示还小，缩略图发虚**。
        // 现在直接用格子自己的宽（它由 `cellWidthDp` 统一算出），请求与布局恒等。
        if (cell.isAnimated) {
            // ---- 动图**不要**给目标尺寸 ----
            //
            // 用户报过一次"动图进入回到那天触发闪退"。根因在这里：
            // 静态图给 `size()` 是让解码器降采样，安全；
            // 但动图走的是 `ImageDecoderDecoder`，它会把 `size` 翻译成
            // `ImageDecoder.setTargetSize()` —— 对**动画**源这么设，
            // 系统解码器会在合成帧时抛异常（帧尺寸与目标尺寸不一致的路径），
            // 于是整页崩掉。
            ImageRequest.Builder(imgContext).data(cell.uri).build()
        } else {
            ImageRequest.Builder(imgContext)
                .data(cell.uri)
                .apply {
                    if (cell.isLongImage) {
                        // ---- 长图：只解原图中间一块 2:1，绝不解整张 ----
                        //
                        // 828×30656 请求 292×1080 的格子时，`inSampleSize` 只能降到 2
                        // ⇒ 每格解 414×15328 ≈ 25MB，滑几格就 OOM（拖底部进度条闪退）；
                        // 而且整张缩进格子只是一根 6px 宽的细条。
                        // 交给 LongThumbnailFetcher 区域解码（828×1656），再缩到格子尺寸：
                        // 内存 ~2MB，画面正好铺满 2:1 的格子（不拉伸、不裁成细条）。
                        setParameter(LongThumbnailFetcher.REQUEST_KEY, true)
                        size(wPx, hPx)
                    } else if (focusDecodeSize != null) {
                        // 焦点格用**照片页那个尺寸** —— 同 uri + 同 size ⇒
                        // memory cache 命中 ⇒ 立即出图、且是同一张高清图。
                        // 其余格子按显示尺寸解（降采样，省内存也更快）。
                        //
                        // ---- 封顶到"显示尺寸的 2 倍" ----
                        // `ZoomableImage` 的 decodeSize 对超长图可能是原图，
                        // 照搬会让格子解一张巨大的图 ⇒ 一进"回到那天"一片空白。
                        // 格子终究只是缩略图，2 倍已经足够清晰。
                        val capW = (wPx * 2).coerceAtLeast(1)
                        val capH = (hPx * 2).coerceAtLeast(1)
                        val k = minOf(
                            1f,
                            capW.toFloat() / focusDecodeSize.width,
                            capH.toFloat() / focusDecodeSize.height,
                        )
                        size(
                            (focusDecodeSize.width * k).toInt().coerceAtLeast(1),
                            (focusDecodeSize.height * k).toInt().coerceAtLeast(1),
                        )
                    } else {
                        // ---- 普通缩略图按 0.75 倍解码 ----
                        //
                        // 瀑布流一屏十几格，每格按显示尺寸（1440×1080）解出来是 6MB；
                        // 快速拖刻度条时十几个格子瞬间进出，峰值能到一百多 MB。
                        // 0.75 倍后每格约 3.4MB，显示时放大 1.33 倍 —— 缩略图上
                        // 肉眼几乎看不出差别，但这是"拖到最边上闪退"的主要来源。
                        size(
                            (wPx * THUMB_DECODE_SCALE).toInt().coerceAtLeast(1),
                            (hPx * THUMB_DECODE_SCALE).toInt().coerceAtLeast(1),
                        )
                    }
                }
                // fadeIn 恒为 false（原因见上面那段注释），保留这个参数只是为了
                // 让"为什么不淡入"留在调用点上，而不是消失在一个字面量里。
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
            // ---- 垫底图单独一层，常驻不动 ----
            //
            // 之前是把它当 Coil 的 `placeholder`：那是一个"换图"动作 ——
            // 高清就绪时会先把 placeholder 卸掉、再挂上新 painter，
            // 中间可能有一帧什么都不画；而且两者取景不同（见
            // `SystemThumbnailFetcher.targetSize` 的说明），换的瞬间会"挪一下"。
            // 现在它是一层常驻的底图，高清只是盖在上面、硬切 —— 中间没有空帧。
            if (placeholder != null) {
                Image(
                    painter = placeholder,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

// 角标。视频给时长、截屏给"截屏"、长图给"长图"。
                // 长图进瀑布流就是一根细条，没角标完全看不出该怎么读。
                // 优先级：时长最具体 > 长图 > 截屏（截屏也可能就是长截图，
                // 但"截屏"已经在说来源，"长图"说的是读法，后者更有用）。
                val tag = when {
                    cell.kind == MediaKind.VIDEO -> formatDuration(cell.durationMillis)
                    // **截屏优先于长图**（用户明确要求："既是截屏又是长图时标截屏"）。
                    // 之前反过来，理由是"长图说的是读法、信息量更大"—— 但那是我的判断，
                    // 不是用户的：截屏是**来源**，用户更想一眼看出这张是从哪来的。
                    cell.kind == MediaKind.SCREENSHOT -> "截屏"
                    cell.isLongImage -> "长图"
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
                MediaBadge(
                    text = tag,
                    accent = cell.kind == MediaKind.VIDEO,
                    modifier = Modifier
                        // 角标挪到**左上角** —— 参照「去留」：实况角标就在那儿。
                        // 右下角在照片本身有浅色内容时容易被吃掉。
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .graphicsLayer {
                            val p = (slide.value / 12f).coerceIn(0f, 1f)
                            alpha = if (showTag.value) 1f - p else 0f
                        },
                )
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

/**
 * 每一格的垫底图：**系统相册已经生成好的缩略图**。
 *
 * 高清图要几十毫秒才解得出来（读原图 → 降采样 → 上屏）。这段时间格子里是空的，
 * 拖底部刻度条快速滑过时就是一整片空白格；进场时焦点格那几十毫秒则是"黑屏一下"。
 *
 * 系统缩略图 1~2ms 就能拿到（见 [SystemThumbnailFetcher]），用它顶住这段时间。
 * 高清到了是**硬切**，不做交叉淡入 —— crossfade 是"底层淡出 + 目标淡入"，
 * 中间几帧两层都半透明、叠起来发暗，看起来像闪（见 `DayCell` 里 `fadeIn` 的说明）。
 *
 * `uri` 为 null（长图格子）时返回 null：系统那份对长图是整张按 Fit 的细条，
 * 而且 [SystemThumbnailFetcher] 会主动拒绝这类请求。
 */
@Composable
internal fun rememberSystemThumbnailPlaceholder(
    uri: Uri?,
    widthPx: Int,
    heightPx: Int,
): Painter? {
    if (uri == null) return null
    val context = LocalContext.current
    val request = remember(uri, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(uri)
            // 传格子自己的尺寸：系统会按这个比例裁，取景才和主图一致
            .size(widthPx, heightPx)
            .setParameter(SystemThumbnailFetcher.REQUEST_KEY, true)
            .crossfade(false)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}
