package com.qushe.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.imageLoader
import com.qushe.app.ui.theme.Ink
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import com.qushe.app.core.MediaItem
import com.qushe.app.core.SystemThumbnailFetcher

/**
 * 可缩放的照片。双指捏合放大、放大后单指平移、双击在 1x 与 2.5x 之间切换。
 *
 * **手势归属是这个组件唯一需要小心的地方。**
 * 它总是被放在一个负责"左右滑切换、上下滑删除/收藏"的容器里面，
 * 如果它把事件都吃掉，容器就再也收不到滑动，整页操作会瘫痪。
 * 所以它只在两种情况消费事件：
 *   1. 两根手指 —— 捏合缩放，这一定是用户的缩放意图；
 *   2. 已经处于放大状态下的单指拖动 —— 此时用户的意图是看局部，不是切照片。
 * 其余情况一律不消费，单指滑动原样放行给外层。
 *
 * 双击与单击由 [detectTapsWithoutConsuming] 处理，同样不消费事件。
 *
 * ---- 长条图：放大之后自动居中 + 自动吸合 ----
 * 用户要的是"**放大之后**自动居中、自动吸合，放大之后上下滑动也保持吸合"。
 *
 * 关键区分（这里走过一次弯路）：
 *  · **显示比例**属于"没放大的时候"—— 1x 一律 [ContentScale.Fit]，
 *    长条图完整显示、上下留黑边。这是正常的照片观看状态，
 *    也保证上滑删除、左右翻页不被它吃掉。
 *  · **吸合**属于"放大之后"—— 双指捏合放大，图在容器里溢出，这时才需要能拖；
 *    拖到边界就停住，边界由 [drawnSizePx] 的溢出量给出。
 *
 * 上一版把两件事合并成"1x = 按宽度铺满"，结果长条图一进来就是放大且溢出、
 * 上下滑被图片吃掉、上滑删除失灵（用户两次反馈）。现在拆开了。
 */
@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    maxScale: Float = 8f,
    /**
     * 图片自身的宽高比（宽 / 高）。
     *
     * **只用来算平移边界，不改变 1x 的显示方式。**
     *
     * 上一版把它当成"1x = 按宽度铺满"的开关（`ContentScale.FillWidth`），
     * 结果长条图一进来就是放大状态 —— 用户反馈"没办法上滑删除了"。
     * 那是把两件事混成了一件：显示比例属于"没放大时"，
     * 吸合属于"放大之后"。所以现在 1x 一律 [ContentScale.Fit]（原样显示整张），
     * 只有捏合放大之后才需要按实际绘制矩形算边界、才谈得上吸合。
     *
     * 传 null 时退回"容器即绘制区"的保守估计（边界偏大，最多少拖一点黑边）。
     */
    contentRatio: Float? = null,
    onSingleTap: () -> Unit = {},
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    onZoomedChange: (Boolean) -> Unit = {},
    /**
     * 手势阶梯的第三级：已经回到初始大小、还在继续往里捏 —— 触发「回到那天」。
     *
     * 只有"**从初始大小开始**的捏合"才有资格触发：从放大状态缩回 1x 的那一次不算，
     * 否则每次把照片缩回原尺寸都会误触发一次日期面板。
     *
     * 参数是**触发那一刻的缩放**。触发时照片已经被捏小了（0.7x ~ 1x 之间），
     * 调用方若要做"从这张图飞过去"的转场，起点必须按这个大小算 ——
     * 按 1x 算的话飞行起点会比画面上那张大一圈，看起来就是"跳了一下"。
     */
    onPinchToDate: ((Float) -> Unit)? = null,
    /**
     * 把**实际会被请求的解码尺寸**回调出去。
     *
     * ---- 为什么需要它 ----
     *
     * 整理照片页 → 「回到那天」的飞入动效里，焦点格如果按**自己的格子尺寸**请求，
     * Coil 的 memory cache key 和照片页那张就对不上 ⇒ **必然 miss** ⇒
     * 焦点格要重新解码几十毫秒，飞行第一帧是空的。
     * 上一版垫系统缩略图顶这段空档，反而引入了"高清 → 糊 → 高清"的清晰度跳变
     * （用户看到的"起手闪烁"就是它）。
     *
     * 拿到这个尺寸之后，焦点格照用**同一个 size** 请求 ⇒ 同 uri + 同 size
     * ⇒ **缓存直接命中** ⇒ 第一帧就是同一张高清图，起手那一下严丝合缝。
     */
    onRequestSize: ((IntSize) -> Unit)? = null,
    /**
     * 这张图**是不是动图**（由调用方从 `MediaItem.isAnimated` 带过来）。
     *
     * ---- 为什么不能在这里靠 Uri 判 ----
     *
     * MediaStore 的 Uri 是 `content://media/external/images/media/12345`，
     * `lastPathSegment` 就是一串数字 —— `endsWith(".gif")` **永远不会成立**。
     * 也就是说 1.3.23 那次"动图关掉 crossfade"的修复**从来没生效过**，
     * 用户报的"整理照片页滑到动图会黑屏一下"也就一直还在
     * （这次他直接问"和之前原因一样吗"——一样，只是上次没修对）。
     */
    isAnimated: Boolean = false,
    /**
     * 顺带**提前请求**的相邻几张（翻页前把下一张备好）。
     *
     * 整理照片页的翻页是自己实现的（拖拽 + 换 index），没有 pager 的
     * "预加载相邻页" —— 不预加载的话，滑过去那一两帧画面是空的，
     * 而背景是纯黑，看起来就是"闪一下黑"（用户报"滑到动图还是会闪"）。
     *
     * 尺寸用**本组件自己算出来的那个**（`decodeSize ?: container`），
     * 这样滑过去时能直接命中 memory cache；动图不能给 size
     * （`ImageDecoderDecoder` 会翻成 `setTargetSize()`，对动画源不安全），
     * 所以那一类按原样请求 —— 和主请求的 cache key 同样是"无 size"，也对得上。
     */
    preloadNeighbors: List<MediaItem> = emptyList(),
    /**
     * **已经拿在手上**的一张缩略图。
     *
     * 这一屏的背景是纯黑，所以"图还没就绪"看起来就是"黑屏闪一下"。
     * 前面几版试过：现取系统缩略图（要 1~5ms IO + 一帧回调）、预加载相邻页
     * （要解码时间）、把上一张留在下面（内容不对，划走时更突兀）——
     * **都还是在赌"来得及"**。
     *
     * 只有这个是**零延迟**的：它是一张已经在内存里的 `ImageBitmap`，
     * 组合完当场就能画，没有 IO、没有解码、没有等回调那一帧。
     * 由调用方在 index 变化时提前读好（见 `PhotoSessionScreen` 的缩略图缓存）。
     */
    placeholderBitmap: ImageBitmap? = null,
    /**
     * 捏合向「回到那天」推进的进度：0f = 没在捏，1f = 已触发。
     *
     * 用来在**手指还按着的时候**就显示"松手进入「回到那天」"——
     * 否则用户捏到一半不知道会发生什么，只有触发之后才看见页面跳走。
     * 阈值（[PINCH_TO_DATE_AMOUNT]）是累计量，这里换算成 0..1 的进度给 UI。
     */
    onPinchToDateProgress: ((Float) -> Unit)? = null,
    /**
     * 这张图的**原始像素尺寸**（媒体的 width/height）。
     *
     * 只用来给解码尺寸封顶：`decodeSize` 原本是"容器 × 放大上限"，
     * 而放大上限可能远大于原图（长图不传 `contentRatio` 时上限是 8），
     * 于是会向 Coil 请求一个比原图大好几倍的荒谬尺寸 ——
     * 轻则被 clamp（白跑一趟），重则解码失败回落到容器尺寸，
     * **放大之后就是插值的糊图**（用户反馈"长图放大后看的不够清晰"）。
     *
     * 有原图尺寸时按它封顶：解码最多到原图大小，
     * "放大到原始像素"这一段一定是清晰的，再往大才是插值（物理上无法更清楚）。
     */
    sourceSize: IntSize? = null,
    /**
     * 图片自身四角的圆角（1x 时）。放大后图片会超出这个圆角，
     * 那时就不再裁 —— 否则放大看到的是"圆角里的一块方图"，很怪。
     */
    cornerRadius: Dp = 0.dp,
    /**
     * 图片自身的宽高比，**只用来算四角遮罩该盖在哪**（1x 时）。
     *
     * ---- 为什么必须是单独一个参数 ----
     * [contentRatio] 还管着缩放上限（吸合）、拖动边界、放大时的解码尺寸。
     * 它现在只给长图传 —— 把它推广到所有照片，那三件事会一起变。
     * 这里只要一个纯几何信息。
     *
     * ---- 为什么是"盖遮罩"而不是 clip ----
     * 试过把变换层 `aspectRatio` 缩到图片 Fit 之后的真实矩形再 clip：
     * 圆角是准了，但**那个盒子一变小，整条链的尺寸基准就跟着变了**，
     * 用户反馈"图片会自动放大"（1.3.44 引入、1.3.45 撤回）。
     * 布局基准是别人的隐式依赖，装饰效果不该去动它。
     * 现在改成在图**上面**盖四块和页面底色同色的角 —— 视觉上就是圆角，
     * 而布局、缩放、边界一个字节都没变。
     *
     * null = 不画遮罩（调用方给不出宽高比时的安全退路）。
     */
    cornerAspect: Float? = null,
    /**
     * 双击。**给了它就不再做"双击缩放"**，改成回调出去。
     *
     * 整理照片页要双击"喜欢/收藏"（和视频页、设置里的「双击画面做什么」同一套语义），
     * 缩放已经有捏合和双击放大两条路可用，让位给一个更常用的动作。
     * null = 保持原来的双击缩放。
     */
    onDoubleTap: (() -> Unit)? = null,
) {
    // key 用 model：换了一张照片就自动回到原始缩放与位置
    var scale by remember(model) { mutableFloatStateOf(1f) }
    var offset by remember(model) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    // ---- 补间动画的 Job：新手势一按就cancel ----
    //
    // 不管理会出两个真问题（都是"手感突然不对"那种）：
    //  ① 捏合松手正在 `animateOffsetTo` 吸边界，用户马上又捏一下 ——
    //     补间还在每帧写 offset，和手势**同时写同一个值**，
    //     画面会朝两个方向抽搐；
    //  ② 双击缩回的补间与拖动吸边界的补间可能同时跑。
    //
    // `Animatable` 会自动取消前一个动画，`offset` 这里是普通 state，
    // 必须自己管。
    var tweenJob by remember(model) { mutableStateOf<Job?>(null) }
    val maxScaleState = rememberUpdatedState(maxScale)
    val pinchToDateState = rememberUpdatedState(onPinchToDate)
    val pinchProgressState = rememberUpdatedState(onPinchToDateProgress)
    val ratioState = rememberUpdatedState(contentRatio)

    // 容器尺寸。缩放与平移都要按它算边界，所以只存尺寸、不存别的。
    var container by remember(model) { mutableStateOf(IntSize.Zero) }

    /**
     * 当前缩放下的**实际绘制尺寸**。
     *
     * 长条图在 1x 时是"按宽度铺满"，纵向溢出 ⇒ 绘制高度 > 容器高度；
     * 缩放后按比例放大。普通照片在 1x 时 Fit 铺满，最长边等于容器。
     * 两者都由 [drawnSizePx] 统一算出来，边界才有唯一依据。
     */
    fun drawn(s: IntSize, currentScale: Float): Pair<Float, Float> =
        drawnSizePx(s, currentScale, ratioState.value)

    /** 平移边界：图比容器小的那一轴为 0（不许拖出黑边），大的那一轴是溢出的一半。 */
    fun panLimit(containerPx: Int, drawnPx: Float): Float =
        ((drawnPx - containerPx) / 2f).coerceAtLeast(0f)

    /**
     * **吸合上限**：放大到"图铺满容器"就不再放大了。
     *
     * ---- 依据是「去留」录屏的实测值 ----
     * 录屏里捏合放大一张长图，逐帧量白色纸面的宽度（屏宽 220）：
     *   · 1x 完整显示时纸宽 65px
     *   · 捏合过程中 65 → 187 → 219px
     *   · 到 219px（≈ 屏宽 220）时**顶到左右边缘后停住**，之后 1.5s 完全不动
     * 219 / 65 = **3.37** —— 正好是"图宽铺满屏宽"所需的倍数。
     *
     * 所以上限 = **绘制矩形刚好覆盖容器**的倍数：
     * ```
     * cap = max(容器宽 / 图宽, 容器高 / 图高)
     * ```
     * 对长图取的是宽度那一项（65 → 220 = 3.38，与实测 3.37 吻合）。
     *
     * ---- 这里踩过一个坑，值得记下来 ----
     * 上一版写的是"取**长边**那一项"，理由是"短边贴满的那轴由 panLimit 天然封住"。
     * 结果是**长图和普通照片都放不大**：
     *  · 长图 1x 的绘制矩形里 `dh0 == ch`（高度已经贴边），于是
     *    `屏高/dh0 == 1.0` ⇒ 直接不能捏；
     *  · 普通照片 `contentRatio` 传 null，`drawn` 退化成"绘制区 = 容器"，
     *    两个比值都是 1.0 ⇒ 同样不能捏。
     * **教训：`ratio == null` 时根本推不出上限，必须退回 `maxScale`。**
     */
    fun snapScale(size: IntSize): Float {
        val ratio = ratioState.value
        // 拿不到宽高比就**不下限**，老实用 maxScale ——
        // 宁可放大过头，也不能把"不能放大"当成设计。
        if (ratio == null || ratio <= 0f) return maxScaleState.value
        if (size.width <= 0 || size.height <= 0) return maxScaleState.value
        val (dw0, dh0) = drawn(size, 1f)
        if (dw0 <= 0f || dh0 <= 0f) return maxScaleState.value
        val coverW = size.width.toFloat() / dw0
        val coverH = size.height.toFloat() / dh0
        val cover = maxOf(coverW, coverH)
        // ---- 1x 就已经铺满容器 ⇒ 别把它当"上限是 1x" ----
        //
        // 长图（比例远小于屏幕）在 Fit 之后**高度正好等于容器高度、宽度正好等于
        // 容器宽度** ⇒ coverW = coverH = 1 ⇒ 上限算成 1x。
        // 于是捏合被 `coerceIn(1, 1)` 夹住、永远放不大，也就永远进不了
        // "放大后单指拖动看细节"那个分支（用户反馈"长图放大后不能自由查看"）。
        //
        // "吸边界"的本意是"别放大到看不清"，而长图放大到 2~3x 恰恰是**看细节**
        // 需要的（1x 时整张长图已经缩到一屏高，本来就没有可看的局部）。
        // 所以这种情况直接回落到 maxScale：宁可放大过头，也不能把功能锁死。
        if (cover < 1.02f) return maxScaleState.value
        return cover.coerceAtLeast(1f)
    }

    /**
     * 真正允许放到的最大倍率。
     *
     * ⚠️ 原来是 `minOf(maxScale, snapScale)` —— 那等于**把"铺满屏幕"当成了上限**，
     * 于是超长图永远只能放到刚铺满（比如 16.6 倍被 8 倍截断 ⇒ 宽度只有 520px，
     * 左右一圈黑底、也看不清细节）。
     *
     * 现在取 `maxOf`：`snapScale` 的意义变成"**铺满屏幕所需的最小倍率**"（回弹下限），
     * 而 `maxScale`（默认 8）是"最多能放多大"。长图能放到铺满、也能继续放大看局部。
     */
    fun effectiveMax(size: IntSize): Float =
        maxOf(maxScaleState.value, snapScale(size))

    /**
     * 放大后要用的**解码尺寸**。
     *
     * ---- 为什么不能让 Coil 按"显示尺寸"解码 ----
     *
     * `AsyncImage(model = uri)` 会自动挂 `ConstraintsSizeResolver`，请求尺寸就是容器尺寸。
     * 可**放大是 `graphicsLayer` 做的缩放，解码器完全不知道**：
     * 长图 1x 只显示成两百多 px 宽，Coil 按这个算采样率、解出来就是两三百 px 的小图；
     * 放大到铺满屏幕等于把这个小 Bitmap 硬拉四五倍 —— 一放大全糊
     * （用户报"整理照片页长图放大之后看不清"）。
     *
     * 所以按 **容器尺寸 × 放大上限** 请求：放大到顶时正好 1:1，一个像素不多要。
     * 不会因此爆内存 —— Coil 的解码输出只会**不低于**请求尺寸、
     * 但绝不会超过原图（它不做 upscale）；所以"请求得大、原图没那么大"的
     * 结果就落在原图上。真正需要大图的长图，本来就得解原尺寸才看得清。
     *
     * 拿不到宽高比时**不猜**，返回 null 让 Coil 走自己的默认路径：
     * 那种情况根本推不出真正的放大上限（`snapScale` 会退回 `maxScale` = 8），
     * 照着算就是"容器 ×8"的白解一张巨图。
     */
    /** 垫底那层要用到 Uri；`model` 是别的东西（极少见）时它为空，垫底层自动跳过。 */
    val uri = model as? Uri

    val knownRatio = ratioState.value
    val decodeSize = remember(container, knownRatio, sourceSize) {
        if (container.width <= 0 || container.height <= 0) return@remember null
        // 放大上限。没传 `contentRatio` 时它就是 `maxScale`（可能 8 倍）——
        // 那时候必须靠 `sourceSize` 封顶，否则解码尺寸会远超原图。
        val cap = effectiveMax(container)
        val w = (container.width * cap).roundToInt().coerceAtLeast(1)
        val h = (container.height * cap).roundToInt().coerceAtLeast(1)
        val capped = sourceSize?.let { src ->
            // 容器是 1080x2400、长图原图 1080x5400 时：
            // 不封顶 → 8640x19200（Coil 只能 clamp，白跑）；封顶 → 1080x5400（正好原图）。
            IntSize(w.coerceAtMost(src.width.coerceAtLeast(1)), h.coerceAtMost(src.height.coerceAtLeast(1)))
        } ?: IntSize(w, h)
        // 最后再卡一道总像素（见 [MAX_DECODE_PIXELS]）：单张图的解码内存不能把
        // Coil 的缓存整个吃掉，否则同页其它缩略图全被淘汰 ⇒ 一进去一片空白。
        val kept = capped.width.toLong() * capped.height.toLong()
        val finalSize = if (kept > MAX_DECODE_PIXELS) {
            val k = kotlin.math.sqrt(MAX_DECODE_PIXELS.toDouble() / kept.toDouble()).toFloat()
            IntSize(
                (capped.width * k).toInt().coerceAtLeast(1),
                (capped.height * k).toInt().coerceAtLeast(1),
            )
        } else {
            capped
        }
        // 封顶之后可能已经小于容器 —— 那就没有"放大后要更清楚"这件事，按容器解。
        if (finalSize.width <= container.width && finalSize.height <= container.height) {
            return@remember null
        }
        finalSize
    }

    /**
     * **橡皮筋阻尼**：把越界的位移按递减增益压回来。
     *
     * ---- 为什么不能只有硬边界 ----
     * 硬边界（`coerceIn(-limit, limit)`）在 1.3.16 之前一直都在，
     * 但用户反馈的是"**放大到接近吸合就吸合**的功能完全没有了" ——
     * 缺的不是那条硬边，而是**接近它时的渐进减速**。
     *
     * 硬边界的手感是：1px 都是 1:1 跟手，到了边界**戛然而止**。
     * 用户要的是 iOS 那种"越接近越沉、最后轻轻停住"的阻尼。
     *
     * 做法：对超出边界的部分 `over` 用 `c * (1 - 1/(over/c + 1))` 映射 ——
     * 这是 UIKit 的橡皮筋公式。它从 0 开始以递减斜率增长、渐近于 `c`，
     * 所以图最多被拉出 `c` 像素（永远拉不出大片黑边），
     * 而用户能明显感到"越拖越沉"。松手再动画吸回边界（见下面的 settle）。
     */
    fun rubber(raw: Float, limit: Float): Float {
        if (limit <= 0f) return 0f
        val over = kotlin.math.abs(raw) - limit
        if (over <= 0f) return raw
        // 橡皮筋的"弹性长度"：随边界大小自适应，边界越大越"能拉一点"。
        val c = limit * 0.32f + 24f
        val damped = c * (1f - 1f / (over / c + 1f))
        return if (raw < 0f) -(limit + damped) else limit + damped
    }

    /**
     * 把位移夹回合法范围（**带阻尼**）。
     *
     * ---- 这里**不能**再按"缩放 ≤ 1 就归零"处理 ----
     * 上一版写着 `if (currentScale <= 1.001f) return Offset.Zero`，
     * 而长条图在 1x 时已经是"按宽度铺满、纵向溢出"—— 手势分支照样消费了事件，
     * 位移却被这个归零**当场抹掉**。表现就是用户报的：
     * "一进来就直接吸合了，导致没办法上下滑也没办法左右滑"。
     *
     * 正确做法：边界完全由 [drawnSizePx] 的溢出量决定 ——
     * 图在某轴上不溢出，那一轴的边界自然就是 0，位移自然夹回 0。
     * 不需要（也不该）另加一条"缩放小于 1 就清零"的特例。
     */
    fun clamp(raw: Offset, currentScale: Float, size: IntSize): Offset {
        if (size.width <= 0 || size.height <= 0) return Offset.Zero
        val (dw, dh) = drawn(size, currentScale)
        return Offset(
            x = rubber(raw.x, panLimit(size.width, dw)),
            y = rubber(raw.y, panLimit(size.height, dh)),
        )
    }

    /** 硬边界：真正"吸住"的合法范围（橡皮筋之外的那部分不算）。 */    fun hardClamp(raw: Offset, currentScale: Float, size: IntSize): Offset {
        if (size.width <= 0 || size.height <= 0) return Offset.Zero
        val (dw, dh) = drawn(size, currentScale)
        return Offset(
            x = raw.x.coerceIn(-panLimit(size.width, dw), panLimit(size.width, dw)),
            y = raw.y.coerceIn(-panLimit(size.height, dh), panLimit(size.height, dh)),
        )
    }

    /**
     * 把缩放动画到 [target]，同时把位移收到 [offsetTarget]。
     *
     * 双击缩回 1x 用它（录屏实测 0.66s），而不是瞬移。
     * 用**同一个进度**同时插值 scale 与 offset，两者是同步的 ——
     * 分开跑两条曲线会看到"图一边缩一边飘"。
     */
    suspend fun animateScaleTo(target: Float, offsetTarget: Offset) {
        val fromScale = scale
        val fromOffset = offset
        if (fromScale == target && fromOffset == offsetTarget) return
        val start = withFrameNanos { it }
        // 缩放跨度越大给越久，但封顶在 520ms —— 再慢就让人觉得"卡住了"。
        val durationMs = (260f + 260f * kotlin.math.abs(target - fromScale) / 4f)
            .coerceIn(260f, 520f)
        while (true) {
            val t = withFrameNanos { it }
            val p = ((t - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
            val e = p * p * p * (p * (p * 6f - 15f) + 10f)
            scale = fromScale + (target - fromScale) * e
            offset = Offset(
                x = fromOffset.x + (offsetTarget.x - fromOffset.x) * e,
                y = fromOffset.y + (offsetTarget.y - fromOffset.y) * e,
            )
            if (p >= 1f) break
        }
    }

    /**
     * 把 [offset] 动画到 [target]（松手时的"吸回去"）。
     *
     * 自己按帧插值而不引 `Animatable`：位移是二维的，
     * 两轴一起走同一条缓动曲线才不会看出先后。`offset` 本身仍是普通 state，
     * 拖动时是每帧直接赋值（跟手），只有松手这一下走动画。
     */
    suspend fun animateOffsetTo(target: Offset) {
        val from = offset
        if (from == target) return
        val start = withFrameNanos { it }
        // 约 220ms 的短促回弹：吸合要"轻轻停住"，拖得越远回得略久。
        val dist = kotlin.math.hypot(target.x - from.x, target.y - from.y)
        val durationMs = (160f + dist * 0.25f).coerceIn(160f, 320f)
        while (true) {
            val t = withFrameNanos { it }
            val p = ((t - start) / 1_000_000f / durationMs).coerceIn(0f, 1f)
            // FastOutSlowIn 的近似（1-(1-p)^3 起步偏快，这里用 smootherstep 更"停得住"）
            val e = p * p * p * (p * (p * 6f - 15f) + 10f)
            offset = Offset(
                x = from.x + (target.x - from.x) * e,
                y = from.y + (target.y - from.y) * e,
            )
            if (p >= 1f) break
        }
    }

    val context = LocalContext.current
    /** 主图请求。提成变量是因为下面几层垫底也要用到同一个实例。 */
    val displayRequest = rememberDisplayRequest(model, decodeSize, isAnimated)

    // ---- 中间层：按**容器尺寸**解码的那一版 ----
    //
    // 只在高清尺寸明显更大的图上才值得插这一层（长图就是这种：
    // `decodeSize` 是"容器 × 放大上限"，对它来说等于原图几千万像素）。
    //
    // 为什么需要它：系统缩略图对长图来说**太糊** —— 整张长图会被压成
    // 46×512 那种，铺到全屏等于放大二十多倍，用户看到的就是"糊图一闪"
    // （他这次明确说了是"糊"不是"黑"）。
    // 这一层按容器尺寸解，清楚得多（长图在屏上本来也就那么宽），
    // 解码只要几十毫秒，正好夹在"很糊"和"很清楚"中间。
    val previewRequest = remember(uri, container, decodeSize) {
        if (uri == null || container.width <= 0 || decodeSize == null) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(uri)
                .size(container.width, container.height)
                .crossfade(false)
                .build()
        }
    }

    // 实际会被请求的尺寸：需要放大时是"容器 × 放大上限"，
    // 否则就是容器本身（对应 `AsyncImage` 自动挂的 `ConstraintsSizeResolver`）。
    val effectiveRequestSize = decodeSize ?: container
    val requestSizeState = rememberUpdatedState(onRequestSize)
    LaunchedEffect(effectiveRequestSize) {
        if (effectiveRequestSize.width > 0 && effectiveRequestSize.height > 0) {
            requestSizeState.value?.invoke(effectiveRequestSize)
        }
    }

    // ---- 提前把相邻几张请求好 ----
    //
    // 只请求、不显示：请求完成后就躺在 ImageLoader 的内存缓存里，
    // 用户滑过去时第一帧就命中，不再有"空的一两帧"。
    val preloadState = rememberUpdatedState(preloadNeighbors)
    LaunchedEffect(preloadState.value, effectiveRequestSize) {
        val size = effectiveRequestSize
        val targets = preloadState.value
        if (targets.isEmpty()) return@LaunchedEffect
        val loader = context.imageLoader
        targets.forEach { item ->
            val builder = ImageRequest.Builder(context).data(item.uri)
            // 静态图照用主图那个尺寸；动图不给 size（原因见参数说明）
            if (!item.isAnimated && size.width > 0 && size.height > 0) {
                builder.size(size.width, size.height)
            }
            loader.enqueue(builder.build())
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            // ---- `background(Ink)` **不在这里** ----
            //
            // 它原来画在这一层，也就是画在**圆角的外面**。于是左右滑的时候，
            // 用户看到的是"一整块**方角**的黑底连着照片一起平移"
            // （用户原话："整理照片左右滑动时会有黑色背景一起滑动，不太好看"）。
            // 圆角只裁得住它里面的内容，裁不到外层的这张底。
            // 现在它挪到内层、圆角**之内**（见下面），方角那片黑就没了。
            .onSizeChanged { container = it }
            .pointerInput(model) {
awaitEachGesture {
                        // 新手势一律打断在飞的补间，否则两者会同时写offset/scale。
                        tweenJob?.cancel()
                        tweenJob = null
                        awaitFirstDown(requireUnconsumed = false)
                        // 这一串手势是不是"从初始大小开始"的（见 onPinchToDate 的说明）
                        val startedAtMin = scale <= 1.01f
                        /**
                         * 起手时两指的间距。见下面触发量那段说明 ——
                         * 用它做基准比逐帧累加稳得多。
                         */
                        var pinchStartSpan = 0f
                        var overPinch = 0f
                        var pinchTriggered = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            if (pressed.size >= 2) {
                                val zoom = event.calculateZoom()
                                // ---- 捏合在 1x 以下时，跟着手指继续缩小 ----
                                //
                                // 之前 `coerceIn(1f, ...)` 把 scale 死死钉在 1.0：
                                // 用户在 1x 状态继续往里捏，画面**一动不动**，
                                // 只有累计量够大时才突然触发「回到那天」——
                                // 手势和结果完全脱节，用户的感觉就是"没反应、坏了"。
                                //
                                // 现在允许 scale 跌破 1.0（最小 `PINCH_TO_DATE_MIN_SCALE`）：
                                // 照片跟着手指一起缩，缩到阈值时再触发。
                                // 松手时若没触发（没捏够），弹回 1x。
                                // ---- 触发之后**不要再碰 scale** ----
                                //
                                // 这一条就是用户报的"捏到触发阈值那一刻跳变"的根因：
                                // 触发时 `pinchTriggered` 翻成 true，`lowerBound` 随之
                                // 从 0.72 回到 1f，于是 `coerceIn(1f, ...)` 立刻把
                                // 已经捏到 0.85 的照片**硬抬回 1.0** ——
                                // 画面就在"刚好捏够"的一瞬间猛地弹大一下，
                                // 而且飞入动效的起点又是按 0.85 算的，两边还对不上。
                                //
                                // 现在让它停在触发时的大小，交给飞入动效接着走。
                                val nextScale = if (pinchTriggered) {
                                    scale
                                } else {
                                    val lowerBound = if (
                                        pinchToDateState.value != null && startedAtMin
                                    ) {
                                        PINCH_TO_DATE_MIN_SCALE
                                    } else {
                                        1f
                                    }
                                    (scale * zoom).coerceIn(lowerBound, effectiveMax(size))
                                }

                                // 手势阶梯的第三级：已贴到最小缩放、还在继续往里捏。
                                //
                                // ---- 触发量改用「两指间距比起手时缩短了多少」----
                                //
                                // 原来是逐帧累加 `(1 - zoom)`，往外张的帧再扣回去。
                                // 问题是**真实的手势根本不是单调的**：手指落下、
                                // 调整姿势的过程里会有很多往外张的帧，那些帧会把
                                // 攒起来的量一次次扣掉 —— 于是"明明捏了半天却没反应"，
                                // 用户的感觉就是"要试好几次才进得去"（用户报）。
                                //
                                // 改成拿**两指间距**和起手间距比：这是个**单调量**，
                                // 中间怎么抖都不影响它，捏够了就是捏够了。
                                val toDate = pinchToDateState.value
                                if (toDate != null && !pinchTriggered && startedAtMin &&
                                    scale <= 1.01f
                                ) {
                                    // 两指间距（直接算两点距离 —— 比调计算重心的工具函数直观，也不依赖版本）
                                    val span = if (pressed.size >= 2) {
                                        (pressed[0].position - pressed[1].position).getDistance()
                                    } else {
                                        0f
                                    }
                                    if (pinchStartSpan <= 0f) {
                                        // 这一串手势里第一次出现双指 —— 就此定基准。
                                        pinchStartSpan = span
                                    } else if (span > 0f) {
                                        overPinch = (1f - span / pinchStartSpan)
                                            .coerceAtLeast(0f)
                                        // 把进度同步给 UI（0..1），让提示跟着出现
                                        pinchProgressState.value?.invoke(
                                            (overPinch / PINCH_TO_DATE_AMOUNT).coerceIn(0f, 1f),
                                        )
                                        if (overPinch >= PINCH_TO_DATE_AMOUNT) {
                                            pinchTriggered = true
                                            overPinch = 0f
                                            pinchProgressState.value?.invoke(1f)
                                            // 把**当前缩放**一起带出去：照片这时已经
                                            // 被捏小了（0.7x~1x），起点得按那个大小算 ——
                                            // 否则焦点格会突然从 1x 的尺寸开始飞，
                                            // 和画面上那张差一截，看起来就是跳了一下。
                                            toDate(scale)
                                        }
                                    }
                                }

                                // ---- 越过 1.01 那一帧就报出去，**不要等松手** ----
                                //
                                // 只在手势结束时报的话，"放大时收起 chrome"会晚一整段时间 ——
                                // 用户已经捏大了、图也铺满了，chrome 还杵在上面
                                // （用户反馈"放大松手之后才隐藏"）。
                                //
                                // 只在**跨越**的那一帧报（前后状态不同），否则每一帧都回调，
                                // 调用方就得自己去判重。
                                val wasZoomed = scale > 1.01f
                                scale = nextScale
                                val nowZoomed = scale > 1.01f
                                if (nowZoomed != wasZoomed) onZoomedChange(nowZoomed)
                                offset = clamp(
                                    if (nextScale > 1f) offset + event.calculatePan() else Offset.Zero,
                                    nextScale,
                                    size,
                                )
                                event.changes.forEach { it.consume() }
                            } else if (scale > 1.01f) {
                                // 放大之后单指拖动是"看局部"，必须消费掉，否则外层会当成翻页手势
                                val change = pressed.first()
                                offset = clamp(offset + change.positionChange(), scale, size)
                                change.consume()
                            }
                            // 1x（没放大）时**一个事件都不消费** ——
                            // 上滑删除、左右翻页都是这个层级的手势，全靠放行。
                            // 曾经在这里加过"长条图 1x 也让拖"，
                            // 结果长条图一进来就处于溢出状态、上下滑被它吃掉，
                            // 上滑删除直接失灵（用户反馈"没办法上滑删除了"）。
                        }

                        // ---- 松手：长图横向吸合（动画回到水平居中）----
                        //
                        // 用户要求："长图双指放大到左右两边超过屏幕以后，
                        // 松手的时候动画回到两边吸合的状态。"
                        //
                        // 长图（长截图 / 聊天记录）放大后宽度会超过屏幕 ⇒ 横向可以拖。
                        // 但读长图的动作是**纵向滚动**，横向拖动只是"刚才捏合时顺手带偏的" ——
                        // 松手还停在偏左/偏右的位置，下一屏读起来就是斜的。
                        // 所以横向一律吸回 0（水平居中，两边都不露黑边）。
                        //
                        // 纵向**不吸**：读长内容就是要停在当前看到的位置，
                        // 吸回中间等于把用户正在读的那段内容拽走。
                        //
                        // 只有在"横向确实可拖"时才吸（`panLimit > 0`）——
                        // 图比屏幕窄时 offset.x 本来就是 0，没有可吸的东西。
                        if (scale > 1.01f) {
                            // ---- 放大态的下限是「铺满屏幕」 ----
                            //
                            // `snapScale` 返回"短边正好等于屏幕"所需的倍数。
                            // 放大到它以下时，图在某一轴比视口窄 ⇒ 四周必然露黑底。
                            // 所以松手时**弹到铺满**（用户要求：短边回弹到屏幕尺寸，
                            // 此时整屏被图片填满，长边方向可以滑动）。
                            //
                            // 缩放倍率按比例放大 offset，才能"停在同一处内容"上；
                            // 否则弹一次，正在看的那段就被推到别处了。
                            val cover = snapScale(size)
                            if (cover > 1.02f && scale < cover - 0.01f) {
                                val target = minOf(cover, effectiveMax(size))
                                val keep = offset * (target / scale)
                                tweenJob = scope.launch {
                                    animateScaleTo(target, hardClamp(keep, target, size))
                                }
                                if (!pinchTriggered) pinchProgressState.value?.invoke(0f)
                                onZoomedChange(true)
                                return@awaitEachGesture
                            }
                        }
                        if (scale > 1.01f) {
                            val (dw, _) = drawn(size, scale)
                            val xLimit = panLimit(size.width, dw)
                            if (xLimit > 0f && offset.x != 0f) {
                                val settled = hardClamp(offset, scale, size)
                                tweenJob = scope.launch {
                                    animateOffsetTo(Offset(0f, settled.y))
                                }
                            } else if (offset != Offset.Zero) {
                                // 横向不可拖（普通照片 / 竖长图未溢出）：
                                // 只把橡皮筋拉出去的纵向那一段吸回边界。
                                val settled = hardClamp(offset, scale, size)
                                if (settled != offset) {
                                    tweenJob = scope.launch {
                                        animateOffsetTo(settled)
                                    }
                                }
                            }
                        }
                        // ---- 松手：没捏够就弹回 1x ----
                        //
                        // 捏合过程中 scale 允许跌破 1（跟手缩小，见 PINCH_TO_DATE_MIN_SCALE）。
                        // 没到阈值就松手 ⇒ 图缩在 1x 以下停着会显得"照片突然变小了"，
                        // 必须弹回去。已触发的不用管 —— 页面已经跳走了。
                        if (!pinchTriggered && scale < 0.999f) {
                            tweenJob = scope.launch {
                                animateScaleTo(1f, offsetTarget = Offset.Zero)
                            }
                        }
                        if (!pinchTriggered) pinchProgressState.value?.invoke(0f)
                        onZoomedChange(scale > 1.01f)
                    }
            }
            .pointerInput(model) {
                detectTapsWithoutConsuming(
                    onTap = onSingleTap,
                    onDoubleTap = if (onDoubleTap != null) {
                        { onDoubleTap.invoke() }
                    } else {
                        {
                        tweenJob = scope.launch {
                            if (scale > 1.01f) {
                                // ---- 缩回 1x 要**动画**，不是瞬移 ----
                                //
                                // 录屏 25.17s→25.83s 实测：缩回时纸宽从 182 一路收到 65，
                                // **用了 0.66s**，逐帧平滑收。
                                // 之前这里是 `scale = 1f; offset = Offset.Zero` 瞬移——
                                // 画面"啪"地一下缩回中间，和吸合的手感完全两回事。
                                animateScaleTo(1f, offsetTarget = Offset.Zero)
                            } else {
                                // 双击放大到**刚好铺满容器**（录屏里量到的吸合上限），
                                // 而不是写死的 2.5x —— 长图 2.5x 仍不够宽，
                                // 看着就还是"没放大够"。
                                // offset 归零 ⇒ 图回到正中，这就是用户要的"自动居中"。
                                animateScaleTo(effectiveMax(size), offsetTarget = Offset.Zero)
                            }
                            onZoomedChange(scale > 1.01f)
                        }
                        }
                    },
                    onLongPressStart = onLongPressStart,
                    onLongPressEnd = onLongPressEnd,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // ---- 变换层：包住**所有**垫底图 + 主图 ----
        //
        // 这个 graphicsLayer 原来挂在**主图那一层**上。后果有两个（用户都报了）：
        //  · 捏合缩小时：主图缩成中间一小块，而底下几层垫底图**没缩**，
        //    四周就露出一圈没缩的旧图 —— 他说的"中间出现一个很小的方形块"；
        //  · 长图拖动时：主图在动、垫底层不动，边缘留下没跟着走的残影 ——
        //    他说的"缩放自由移动的时候会有残留"。
        // 提到这一层之后，四层永远同步。
        Box(
            modifier = Modifier
                .fillMaxSize()
                // ---- 这里既不裁圆角、也不画背景 ----
                //
                // 圆角试过一版、撤掉了：要让圆角落在图片四角上，就得先知道
                // 图片 Fit 之后的真实矩形（`aspectRatio`）。而这个盒子一变小，
                // 整条链的尺寸基准就跟着变了 —— 用户反馈"图片会自动放大"。
                // 为一个装饰性效果去动布局基准，不划算。
                //
                // 背景也不能画在这里：它会变成一整块方角的暗色跟着照片平移
                // （用户原话："整理照片左右滑动时会有黑色背景一起滑动"）。
                // 现在这一层是**透明**的，图之外露出来的是页面自己的底色。
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
        // ---- 最底下：外部递进来的那张缩略图（唯一真正零延迟的一层）----
        val ph = placeholderBitmap
        if (ph != null) {
            Image(
                bitmap = ph,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // ---- 垫底：系统缩略图 ----
        //
        // 高清图解码要时间（超长图要几十上百毫秒，动图更慢），这段时间画面是空的 ——
        // 而这一屏的背景是**纯黑**，"空"看起来就是"黑屏闪一下"（用户报了三轮，
        // 这次他说到点子上了："因为背景就是黑色，所以闪一下颜色都是黑屏"）。
        //
        // 系统缩略图 1~2ms 就能拿到（相册扫描时早就生成好了），拿它垫住这段空档：
        // 先有画面，再慢慢变清楚 —— 闪的是"清晰度"，不再是"有没有"。
        //
        // 对**动图**尤其关键：动图请求不能给 size
        // （`ImageDecoderDecoder` 会把它翻成 `setTargetSize()`，对动画源不安全），
        // 所以只能按原分辨率解，是最慢的一类。而缩略图是**静态**的，正好绕开那条路。
        AsyncImage(
            model = rememberThumbRequest(uri),
            contentDescription = null,
            // 和主图同一个 ContentScale，内容才对得齐（切换时不会"跳一下"）
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // ---- 中间层（只有长图这类才有）----
        previewRequest?.let { req ->
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AsyncImage(
            // ---- 动图显式构造请求，把 crossfade 关掉 ----
            //
            // ImageLoader 全局开了 `crossfade(true)`，动图因此要先解出第一帧、
            // 再跑 200ms 淡入才显示 —— 而 `ImageDecoderDecoder` 解完第一帧
            // 到动画真正启动之间还有一段静止时间。两者叠加就是用户报的
            // "滑到动图会黑屏一下子然后才开始播放"。
            //
            // 关掉淡入后第一帧立刻可见，动画随即开始，那一"下"就没了。
            // 静态图不受影响（本来也没有这段静止期）。
            model = displayRequest,
            contentDescription = contentDescription,
            // **一律 Fit。** 长条图也不例外 ——
            // 让 1x 就按宽度铺满（FillWidth）会让长图一进来就是"放大且溢出"，
            // 上下滑被图片吃掉、上滑删除手势也就没了（用户反馈）。
            // 吸合是**放大之后**才需要的行为，1x 就该老老实实把整张显示出来。
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                // 圆角是**画在图上面**的四角遮罩（见下面），不裁这一层 ——
                // 裁这一层等于裁整屏，竖图上下那些黑边被裁、照片四角根本不在边上。
                .fillMaxSize(),
        )
        }

        // ---- 最后盖上四角的遮罩 ----
        //
        // 排在所有图之上、变换层之内 ⇒ 它跟着照片一起缩放平移，
        // 圆角始终长在图片自己的四角上。
        // 只画**四个角**、不画"图片以外整片盖掉" —— 上下那些黑边必须留透明，
        // 否则翻页时底下那张正在淡入的下一张会被这片遮罩挡住。
        if (cornerRadius > 0.dp && cornerAspect != null && cornerAspect > 0f &&
            scale <= 1.01f && container.width > 0 && container.height > 0
        ) {
            val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
            val mask = remember(container, cornerAspect, radiusPx) {
                cornerMaskPath(
                    container.width.toFloat(),
                    container.height.toFloat(),
                    radiusPx,
                    cornerAspect,
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawPath(mask, Ink)
            }
        }
    }

    // 容器尺寸变化（旋转、分屏）时按新尺寸重新夹一次，
    // 否则旧 offset 可能超出新边界，画面会露出黑边 —— 吸合就破了。
    //
    // ---- `scale` 刻意**不作为键** ----
    // 它在协程体内读。把每帧都变的量列为键，等于捏合的每一帧都
    // 取消旧协程 + 新建一个（一次捏合约 18 次），而且里面跑的 clamp
    // 与手势分支里的 clamp 完全是重复劳动 —— 手势已经夹过了。
    // 键只保留真正会让边界失效的量。
    LaunchedEffect(container, contentRatio) {
        if (container.width > 0 && container.height > 0) {
            val cur = offset
            if (cur != Offset.Zero) {
                val fixed = clamp(cur, scale, container)
                if (fixed != cur) offset = fixed
            }
        }
    }
}

/**
 * 图片在当前缩放下、容器里的**实际绘制尺寸**（px）。
 *
 * 1x 一律 [ContentScale.Fit]（见 [ZoomableImage] 里 contentScale 的说明），
 * 所以 1x 的绘制矩形就是"把图片完整放进容器"的那个矩形：
 * 取容器宽高按比例**内接**（contain）后的那一块。
 *
 * `ratio == null` 时退回"绘制区 = 容器"，这是保守估计：
 * 边界会偏大（最多让用户拖出一丁点黑边），但不会把内容锁死。
 *
 * 放大后绘制矩形按倍数放大，于是图在某轴上超过容器 ⇒ 该轴可以拖 ⇒
 * 滑到边界就停住，这就是"吸合"。**吸合是放大的结果，不是 1x 的状态。**
 */
private fun drawnSizePx(
    size: IntSize,
    scale: Float,
    ratio: Float?,
): Pair<Float, Float> {
    if (size.width <= 0 || size.height <= 0) return 0f to 0f
    val cw = size.width.toFloat()
    val ch = size.height.toFloat()
    val (w, h) = if (ratio != null && ratio > 0f) {
        // Fit（contain）：整张图放进容器，至少一边贴边。
        //
        // ⚠️ 这里曾经把**宽高写反**（两个分支都是）：容器 1080×2400、图 828×30656
        // 时算出「2400 × 64.8」，而正确是「64.8 × 2400」。
        // 后果极其隐蔽 —— 因为 `panLimit` 和 `snapScale` 全都用这个值：
        // 长图 1x 时被当成"宽度已经 2400 宽"，于是放大后横向允许拖 1000+ 像素，
        // 而实际图只有 65 宽 ⇒ 一拖就露出大片黑底（用户报"视口里出现空白背景"）。
        if (cw / ch > ratio) {
            // 容器更宽 → 高度贴边，宽度按比例（变窄）
            (ch * ratio) to ch
        } else {
            // 容器更高（更"竖"）→ 宽度贴边，高度按比例（变高）
            cw to (cw / ratio)
        }
    } else {
        cw to ch
    }
    return (w * scale) to (h * scale)
}

/**
 * 触发「回到那天」需要累计的捏合幅度（按手指间距的缩小比例累加）。
 *
 * 这个量测的是「两指间距比起手时**缩短了百分之几**」。
 *
 * 一开始是 0.12，但那个数是在"逐帧累加、往外张的帧会扣回去"的算法下调出来的
 * —— 那个算法下用户实际要捏得比 12% 深得多才能触发（每次手指调整的抖动都在扣分），
 * 所以表现为"要试好几次才进得去"（用户报）。
 * 换成间距基准之后是**单调**的，10% 就是实打实的 10%：
 * 两指从 500px 收到 450px 就到了。
 *
 * 再小会开始误触（手指落下时的自然张开也算数），10% 是"要真的往里捏"的下限。
 */
/**
 * 单张图解码的像素上限（约 96 MB ARGB_8888）。
 *
 * 超长图（828×30656 = 25MP ≈ 100MB）如果不限，Coil 的 memory cache 会被
 * 一张图整个占满 ⇒ 同页面其它缩略图全被淘汰、重新解码
 * （用户反馈 1.3.79 进"回到那天"一片空白）。
 * 24MP 是折中：828×30656 只缩到 800×29600（差 3%，肉眼无感）。
 */
private const val MAX_DECODE_PIXELS = 24_000_000L

private const val PINCH_TO_DATE_AMOUNT = 0.10f

/**
 * 捏合向「回到那天」时，scale 允许跌破 1 的下限。
 *
 * 0.72 是试出来的：再小照片缩成一团、看不出在动；再大又"缩得不够明显"。
 * 手指一旦往内捏，照片就跟着缩到这个区间里，松手弹回 1x。
 */
private const val PINCH_TO_DATE_MIN_SCALE = 0.72f

/**
 * 构造显示用的请求。两种情况需要显式构造，其余原样返回（交给 Coil 自己处理）：
 *
 * 1. **动图 → 关掉 crossfade**。ImageLoader 全局开着 `crossfade(true)`，
 *    动图因此要先解出第一帧、再跑 200ms 淡入才显示 —— 而
 *    `ImageDecoderDecoder` 解完第一帧到动画真正启动之间还有一段静止时间。
 *    两者叠加就是用户报的"滑到动图会黑屏一下子然后才开始播放"。
 *
 * 2. **静态图 → 按 [decodeSize]（容器 × 放大上限）解码**，让放大之后也清楚。
 *    详见 `decodeSize` 的说明。
 *    （动图**不能**给 size —— `ImageDecoderDecoder` 会把它翻成
 *    `ImageDecoder.setTargetSize()`，对动画源不安全，会闪退。这是踩过的坑。）
 *
 * 请求上显式指定的 size 不会被 `AsyncImage` 覆盖 ——
 * 它只在 `request.defined.sizeResolver == null` 时才挂 canvas 尺寸
 * （见 Coil 源码 `AsyncImagePainter.updateRequest`）。
 *
 * 是不是动图由**调用方**传进来（见 `isAnimated` 参数的说明）——
 * `ZoomableImage` 只拿到 `model`（Uri），而 MediaStore 的 Uri 里没有文件名，
 * 自己判是判不出来的。
 */
@Composable
private fun rememberDisplayRequest(
    model: Any?,
    decodeSize: IntSize?,
    isAnimated: Boolean,
): Any? {
    val uri = model as? Uri ?: return model
    val context = LocalContext.current
    // 静态图和动图都不需要特事特办时，原样返回 ——
    // 显式构造会让 Coil 丢掉"按布局尺寸解码"这个默认好处。
    if (!isAnimated && decodeSize == null) return model
    return remember(uri, decodeSize, isAnimated) {
        ImageRequest.Builder(context)
            .data(uri)
            .apply {
                if (isAnimated) {
                    crossfade(false)
                } else {
                    decodeSize?.let { size(it.width, it.height) }
                }
            }
            .build()
    }
}


/**
 * 垫底用的系统缩略图请求。
 *
 * 不给 size —— 让 `AsyncImage` 自己挂 canvas 尺寸，
 * `SystemThumbnailFetcher` 会按它取一个合适的档位（最长边 512 封顶）。
 */
@Composable
private fun rememberThumbRequest(uri: Uri?): Any? {
    if (uri == null) return null
    val context = LocalContext.current
    return remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .setParameter(SystemThumbnailFetcher.REQUEST_KEY, true)
            .crossfade(false)
            .build()
    }
}

/**
 * 四角遮罩的路径：图片 Fit 之后的真实矩形，四角各挖掉一个 quarter-round。
 *
 * 画的是**四个独立的小块**（每块 = 角上的一个正方形减去它那个四分之一圆），
 * 不是"图片以外整片盖掉" —— 后者会把上下黑边也填上，翻页时
 * 底下正在淡入的下一张就被挡住了。
 *
 * 挖角用二次贝塞尔、控制点落在角上，曲线因此朝角的方向鼓出去，
 * 剩下的那块就是遮罩。路径按几何量 remember，拖动时不会每帧重建。
 */
private fun cornerMaskPath(
    boxW: Float,
    boxH: Float,
    radius: Float,
    aspect: Float,
): Path {
    // Fit 的矩形：和 drawnSizePx 里那一套是同一个算法。
    val (iw, ih) = if (boxW / boxH > aspect) {
        boxH to (boxH * aspect)
    } else {
        boxW to (boxW / aspect)
    }
    val left = (boxW - iw) / 2f
    val top = (boxH - ih) / 2f
    val r = radius.coerceAtMost(kotlin.math.min(iw, ih) / 2f)
    if (r <= 0f) return Path()
    val right = left + iw
    val bottom = top + ih
    return Path().apply {
        moveTo(left, top)
        lineTo(left + r, top)
        quadraticTo(left, top, left, top + r)
        close()
        moveTo(right, top)
        lineTo(right - r, top)
        quadraticTo(right, top, right, top + r)
        close()
        moveTo(left, bottom)
        lineTo(left + r, bottom)
        quadraticTo(left, bottom, left, bottom - r)
        close()
        moveTo(right, bottom)
        lineTo(right - r, bottom)
        quadraticTo(right, bottom, right, bottom - r)
        close()
    }
}
