package com.qushe.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.ui.photo.FavoriteButton
import com.qushe.app.ui.photo.StarButton
import com.qushe.app.core.formatDuration
import com.qushe.app.core.formatSize
import com.qushe.app.core.relativeDayLabel
import com.qushe.app.core.storeKey
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.qushe.app.ui.common.flingTo

/**
 * 全屏媒体预览。
 *
 * 左右（或上下）滑动切换上/下一项，照片点一下可以收起上下信息栏（看得更干净），
 * 视频点一下播放/暂停、**长按 2× 倍速**。纯视频列表里视频还支持
 * **横滑快进 / 快退**和**拖动进度条定位**（和视频整理页同一套）；
 * 混排照片时横滑要留给翻页，快进手势不挂，但进度条照样能拖。
 * 音量默认跟设置里的"默认静音"走，顶栏右上角可即时切换。
 * 底部操作区由调用方通过 [bottomContent] 注入，
 * 这样同一个预览可以服务于回收站、收藏夹等不同场景。
 *
 * 实现要点：
 *  - 每一页都是**不透明黑底**。照片用 Fit 完整显示会留边，页面若透明，
 *    相邻页的内容会从留边处透出来。
 *  - 整个预览只养**一个** ExoPlayer，翻页时换 mediaItem。
 *    为每一页各开一个解码器是 OOM 的经典来源。
 *  - 横滑快进与翻页共用横轴，二者只能存其一：纯视频列表翻页走
 *    VerticalPager（竖滑），横滑才空闲，给快进；混排时翻页走
 *    HorizontalPager，横滑归它。强行两个都挂会让翻页时灵时不灵。
 */
/** 下滑退出：滑过多少屏高算数（和 `DayZoomView` 同一档手感）。 */
private const val PREVIEW_DISMISS_FRACTION = 0.22f

/** 下滑退出：甩动速度门槛（px/s）。只判距离的话，快速一甩会判成"没到"，每次都弹回去。 */
private const val PREVIEW_DISMISS_FLING = 1200f

@Composable
fun MediaPreviewOverlay(
    items: List<MediaItem>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    bottomContent: @Composable (MediaItem) -> Unit,
    /** 默认静音（设置里那条）。对所有从预览里播的视频都生效。 */
    videoMutedByDefault: Boolean = false,
    /**
     * **视频条目**的动作栏 —— 和整理视频页同款的侧边竖排（喜欢/收藏/删除三颗）。
     * null = 视频上没有任何动作入口。
     *
     * 位置跟随设置里的「操作栏放在右侧」。
     *
     * 参数是**当前这一页**的条目 —— 预览里可以左右翻页，
     * 动作必须作用在正在看的这一张上，不能拿打开时的那一张。
     */
    videoActions: (@Composable (MediaItem) -> Unit)? = null,
    /**
     * **图片条目**的动作 —— 和整理照片页同款的底部圆钮一排。
     * null = 图片上没有任何动作入口。
     *
     * 为什么两套分开：用户要的是"视频照整理视频页的样子，图片照整理照片页的样子"。
     * 这两套视觉本来就是为各自的场景调出来的 —— 视频页是竖排带文字的侧栏
     * （视频画面通常上下留白，侧边正好空着），照片页是底部两颗圆钮
     * （照片撑满屏幕，只有底栏能放东西）。混用哪一套都会有一边别扭。
     */
    photoActions: (@Composable BoxScope.(MediaItem) -> Unit)? = null,
    /** 视频动作栏贴右边还是左边 —— 直接透传设置项。 */
    railOnRight: Boolean = true,
    /**
     * 捏合触发「回到那天」。参数 = (当前这一页的条目, 触发时的缩放, 飞入起点, 解码尺寸)。
     * null = 不支持这个手势。
     *
     * 起点用 `boundsInWindow()` 实测（**包含 pager 的位移和居中偏移**），
     * 不用"容器尺寸去估" —— 估出来的起点会有偏差，而飞行动画第一帧就看得出来。
     */
    onPinchToDate: ((MediaItem, Float, Rect?, IntSize?) -> Unit)? = null,
    /**
     * 画在**预览之上、但仍在同一个 Dialog 窗口里**的一层。
     *
     * ---- 为什么必须在这里，而不能由调用方在外面盖 ----
     * 本组件整体跑在一个 `Dialog`（独立窗口）里。调用方再往上加东西，
     * 画的是**Activity 窗口**的内容，会被这个 Dialog 整个盖住 ——
     * 症状就是"代码明明打开了，界面上却什么都看不到"。
     * （用户反馈"收藏/喜欢页捏合还是进不去「回到那天」"就是这个原因：
     *   日期视图当时确实打开了，只是被预览窗口压在下面。）
     *
     * 所以要盖在预览上面，就必须由这里往同一个窗口里塞。
     */
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val activity = context.findActivity()
    var fullscreen by remember { mutableStateOf(false) }

    // 屏幕尺寸，用来把全屏胶囊贴到视频画面的下边缘
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // ---- 「回到那天」的起点与解码尺寸 ----
    //
    // 起点必须是**实测**的窗口矩形：预览里的照片被 pager 平移过、
    // 还可能带一个居中偏移，用容器尺寸去估会偏，而偏在第一帧就能看出来。
    val pageGap = rememberPageGap()
    var pinchBox by remember { mutableStateOf<Rect?>(null) }
    var pinchSize by remember { mutableStateOf<IntSize?>(null) }
    val pinchToDate = rememberUpdatedState(onPinchToDate)

    fun enterFullscreen() {
        fullscreen = true
        applyFullscreenOrientation(activity, true)
    }

    fun exitFullscreen() {
        fullscreen = false
        applyFullscreenOrientation(activity, false)
    }

    // 侧边返回手势：全屏时先退出全屏，否则才关掉预览。
    // 注意这段必须写在 fullscreen 和 exitFullscreen 的**声明之后** ——
    // Kotlin 的局部变量和局部函数都要求"先声明后使用"，不像类成员可以随便放。
    BackHandler(enabled = true) {
        if (fullscreen) exitFullscreen() else onDismiss()
    }

    // 退出预览时务必还原方向和系统栏，否则整个应用会卡在横屏
    DisposableEffect(Unit) {
        onDispose { applyFullscreenOrientation(activity, false) }
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.lastIndex),
        pageCount = { items.size },
    )

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }

    // ---- 音量：默认静音（和整理视频页同一条设置）----
    // 这一条以前只管得到整理视频页，预览里的视频照旧外放 ——
    // 用户反馈"适用范围应该扩展到所有播放视频的时候"。
    // 预览顶栏右上角给一个切换按钮：默认静音不该变成强制静音。
    var muted by remember { mutableStateOf(videoMutedByDefault) }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    // 用户一旦动了系统音量，就说明他此刻想听 —— 自动解除静音（和整理视频页同一套）。
    // 刻意**不拦音量键**而是轮询系统音量：音量面板的滑块同样能改音量，
    // 拦按键拿不到那种情况；且覆写 dispatchKeyEvent 会被 Lint 判错。
    // 只在静音期间轮询，解除即退出，开销可以忽略。
    LaunchedEffect(muted) {
        if (!muted) return@LaunchedEffect
        // 拆成两行写，别用 `as? ... ?: return` 的多行链 ——
        // lint 的 SuspiciousIndentation 会把续行当成同级语句报警。
        val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
            as? android.media.AudioManager
        val audio = manager ?: return@LaunchedEffect
        val baseline = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        while (true) {
            delay(500L)
            if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) != baseline) {
                muted = false
                break
            }
        }
    }

    // ---- 长按倍速（和整理视频页同一套）----
    var speedUp by remember { mutableStateOf(false) }
    LaunchedEffect(speedUp) { player.setPlaybackSpeed(if (speedUp) 2f else 1f) }

    // ---- 快进 / 快退与进度条拖动（和视频整理页同一套）----
    //
    // 但**横滑快进只在纯视频列表里启用**：那种情况翻页走 VerticalPager，
    // 横轴是空闲的，横滑归快进；混着照片时翻页走 HorizontalPager，
    // 横滑是它的命脉，两边都抢只会让翻页失灵 —— 那种排版下视频
    // 仍然可以拖动进度条定位，只是没有整屏横扫快进。
    var seeking by remember { mutableStateOf(false) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubRatio by remember { mutableFloatStateOf(0f) }

    // **首帧就绪前不要显示播放器。**
    //
    // 原因很具体：播放器刚启动时还不知道视频的宽高比（videoSize 是 0×0），
    // 这时 AspectRatioFrameLayout 会先按"填满整个视图"渲染 ——
    // 于是你会看到一帧被拉伸到整屏、然后猛地跳回正确比例。
    // 表现为"闪一下"，而且横竖屏都会（图片不走播放器所以不会）。
    //
    // 门控用**两个 key** 而不是一个 boolean：boolean 在翻页瞬间会保留
    // "上一页已就绪"的旧值 → 有一帧把上一页的画面画到新页位置上；
    // 用 key 比较是纯推导，翻页的**同一帧**就失效。
    //   playKey  = 最近一次交给播放器的条目
    //   readyKey = 首帧已经吐出来的条目
    var playKey by remember { mutableStateOf<Long?>(null) }
    var readyKey by remember { mutableStateOf<Long?>(null) }

    // 播放器**解码出来的**真实画面尺寸。它已经把 rotation 算进去了，
    // 是判断横竖屏的最终依据 —— MediaStore 那些列没有（见下面 `isLandscape`）。
    var decodedWidth by remember { mutableIntStateOf(0) }
    var decodedHeight by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                readyKey = playKey
            }

            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                decodedWidth = size.width
                decodedHeight = size.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var chromeVisible by remember { mutableStateOf(true) }

    val current = items.getOrNull(pagerState.currentPage)

    /**
     * 这条视频是不是横向的。
     *
     * **不能只看 MediaStore 的 WIDTH / HEIGHT** —— 那两列记的是"编码存储的尺寸"，
     * 不考虑 rotation。手机竖着拍、按横幅格式存（再靠 rotation 元数据转回来）的视频，
     * 存的是 1920×1080，`width > height` 会被判成横屏，于是**竖屏视频错误地
     * 长出一个「全屏观看」按钮**。这是用户报的偶发 bug，规律就在这里。
     *
     * 所以先问播放器要解码后的真实尺寸（它一定对），播放器还没就绪时才
     * 退回 MediaStore 那组数做初判。
     */
    val isLandscape by remember(decodedWidth, decodedHeight, current) {
        derivedStateOf {
            if (decodedWidth > 0 && decodedHeight > 0) {
                decodedWidth > decodedHeight
            } else {
                val w = current?.width ?: 0
                val h = current?.height ?: 0
                w > 0 && h > 0 && w > h
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        // 翻页即放弃任何进行中的拖动态。正常路径上手势都会自己收尾
        // （onDragEnd / onDragCancel），这里是保险：万一节点在手势中途
        // 离开组合树，提示胶囊和拖动读数不会跨页残留。
        seeking = false
        scrubbing = false
        seekDeltaMs = 0L
        // 倍速也一样：长按倍速是"按住期间"的状态，翻页必须复位，
        // 否则下一条视频会莫名其妙以 2× 开播。
        if (speedUp) {
            speedUp = false
            player.setPlaybackSpeed(1f)
        }
        val item = items.getOrNull(pagerState.currentPage)
        if (item == null || item.kind != MediaKind.VIDEO) {
            // 翻到图片就把播放器收起来，别让后台还在解码。
            // 两个 key 一并清空 —— 否则再滑回这条视频时，陈旧的 readyKey
            // 会让播放器在"还没重新吐出首帧"的窗口里就被显示出来（会被拉伸）。
            player.stop()
            player.clearMediaItems()
            playKey = null
            readyKey = null
            isPlaying = false
            position = 0L
            duration = 0L
            return@LaunchedEffect
        }
        playKey = item.storeKey()
        player.setMediaItem(Media3Item.fromUri(item.uri))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(pagerState.currentPage) {
        while (true) {
            isPlaying = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            duration = if (d > 0L) d else 0L
            delay(220L)
        }
    }

    // 切到后台就暂停，别让视频在后台继续播 —— 既费电，也可能突然出声。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
    }

    // **必须放在 Dialog 里**，不能让它是调用方布局树里的普通 Composable。
    // 收藏 / 喜欢页现在住在首页那个横向分页容器的某一页里，
    // 普通 Composable 会被关在页内、跟着页面一起被平移走 ——
    // 表现出来就是"点了完全没反应，但滑到隔壁空白页会闪一下"。
    // Dialog 有自己独立的窗口，和调用它的容器再没有任何布局关系。
    //
    // decorFitsSystemWindows = false 是**必须的**，不是可选项：
    // 宿主 Activity 开了 enableEdgeToEdge()，但 Dialog 的窗口默认仍然
    // decorFitsSystemWindows = true。这种"半个 edge-to-edge"的状态下，
    // statusBarsPadding() / navigationBarsPadding() 读到的 insets 是 0（或只有一部分），
    // 顶栏就会紧贴屏幕顶边 —— 表现出来正是用户报的"顶栏被挡住了一半"。
    // 显式关掉它，Dialog 窗口才会和 Activity 一样铺满，insets 也才会正确分发下来。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
    // ---- 把"居中"校正回**屏幕**正中 ----
    //
    // 实测（小米 HyperOS，1080×2400 机型）：这个 Dialog 窗口的内容框并不与屏幕重合 ——
    // 它从状态栏下沿（≈104px）开始，还越过屏幕下沿，于是框的中心落在 ≈1300，
    // 而不是屏幕中心 1200。预览里所有 `fillMaxSize + Center` 的元素
    // （视频画面、暂停按钮、快进胶囊、全屏胶囊）因此整体偏下约 100px ——
    // 用户报"看这些视频都有点偏下、一直存在"就是它。
    //
    // 顶栏 / 底栏看着正常是个巧合：窗口下移的距离恰好等于状态栏高度，
    // 而 Dialog 里 statusBarsPadding() 读到 0，两者正好抵消。所以**不能整体位移**，
    // 只挪"居中层"，chrome 留在原处。
    //
    // 校正量是**量出来的**，不是写死的常数：读内容视图在屏幕上的真实位置与高度，
    // 和屏幕中心比一下。窗口真的居中时它等于 0（什么都不动），
    // 换 ROM、换机型、换横竖屏也会自动对上。
    val composeView = LocalView.current
    var centerFixPx by remember { mutableIntStateOf(0) }
    val centerFix = with(LocalDensity.current) { (-centerFixPx).toDp() }
    // 贴底元素（底栏、右下角按钮）的校正量：窗口底边伸出屏幕的那一截。
    // 同一个窗口偏移，对"居中"和"贴底"是两笔不同的账：
    //  · 居中要减去 locY + boxH/2 - screenH/2，
    //  · 贴底要减去 locY + boxH - screenH。
    // 上一版只修了居中，于是底栏（含新加的两个角按钮）仍然整体低 108px、
    // 在小屏上会被屏幕下边缘切掉 —— 用户报的"偏下、甚至显示不全"就是它。
    var bottomFixPx by remember { mutableIntStateOf(0) }
    val bottomFix = with(LocalDensity.current) { (-bottomFixPx).toDp() }
    // 窗口内容框的顶边在屏幕上的位置 + 屏幕总高。
    //
    // 这两个是给 [overlayContent] 用的：这个 Dialog 的内容框**并不与屏幕重合**
    // （上面那段实测：顶边在状态栏下沿、底边还越过屏幕下沿），
    // 所以任何 `fillMaxSize` 的东西都会跟着偏 —— 底部那截直接跑到屏幕外，
    // 用户看到的是"刻度条和按钮只显示上半部分"。
    var windowTopPx by remember { mutableIntStateOf(0) }
    var screenHeightPx by remember { mutableIntStateOf(0) }

    // ---- 下滑退出 ----
    //
    // 和瀑布流点开大图（`DayZoomView`）同一套手感：跟手位移 + 同时淡出，
    // 松手时**距离够 或 甩得够快**都算退出，否则弹回。阈值也沿用那边（0.22 屏高 / 1200px/s）。
    //
    // ⚠️ 这一屏里还有 `HorizontalPager`（左右翻页）和 `ZoomableImage`（放大后拖动看局部），
    // 所以认领手势前必须过三道关，否则会把它们全吃掉：
    //   ① 越过 touchSlop 后判**主轴** —— 横向优先的一律放手（交给 pager）；
    //   ② 只有**向下**才归我（向上放手）；
    //   ③ `change.isConsumed` 为真 ⇒ 子节点已在处理（放大态的 `ZoomableImage`
    //      会消费竖直拖动），立刻放手。子节点在 Main pass 里先拿到事件，
    //      所以这个判断是可靠的 —— 比额外维护一个 zoomed 状态更不会漏。
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val dismissY = remember { Animatable(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }
    val dismissThresholdPx = maxOf(
        boxHeightPx * PREVIEW_DISMISS_FRACTION,
        with(density) { 120.dp.toPx() },
    )
    val fadeP = if (dismissThresholdPx <= 0f) 0f
    else (dismissY.value / dismissThresholdPx).coerceIn(0f, 1f)

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
                    val startX = down.position.x
                    var lastY = startY
                    // 本次手势内自己累加，`snapTo` 写绝对值 —— 增量式写法会丢帧。
                    var dragY = 0f
                    var claimed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        // 第二根手指 / 子节点已消费 ⇒ 放手（捏合、放大后拖动都归子节点）
                        if (pressed.size >= 2) break
                        val change = pressed.first { it.id == down.id }
                        if (change.isConsumed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val totalY = change.position.y - startY
                        val totalX = change.position.x - startX
                        if (!claimed) {
                            if (kotlin.math.abs(totalY) <= slop) continue
                            // ① 主轴是横向 ⇒ 放手，交给 pager
                            if (kotlin.math.abs(totalX) > kotlin.math.abs(totalY)) break
                            // ② 只有向下算"退出"
                            if (totalY <= 0f) break
                            claimed = true
                            dragY = totalY
                            scope.launch { dismissY.snapTo(dragY) }
                        } else {
                            dragY += change.position.y - lastY
                            scope.launch { dismissY.snapTo(dragY) }
                        }
                        lastY = change.position.y
                        change.consume()
                    }
                    if (!claimed || dismissing) return@awaitEachGesture
                    val flung = tracker.calculateVelocity().y >= PREVIEW_DISMISS_FLING
                    if (dismissY.value >= dismissThresholdPx || flung) {
                        dismissing = true
                        scope.launch {
                            dismissY.flingTo(maxOf(boxHeightPx, 1f) * 1.15f)
                            onDismiss()
                        }
                    } else {
                        scope.launch {
                            dismissY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                }
            }
            // alpha/位移写在 `background` **之前**：`Modifier.background` 只影响它
            // 之后的那截链 —— 反过来写的话黑底在 alpha 层外面，永远不透明。
            .graphicsLayer {
                translationY = dismissY.value
                alpha = 1f - fadeP
            }
            .background(Color.Black)
            // ---- 校正量在这里**每次布局后都重读一次** ----
            //
            // 曾经只在 onSizeChanged 里算一次：那一刻窗口可能刚挂上、
            // `getLocationOnScreen` 还返回 0，算出来的校正是 -1px（等于没修）——
            // 这正是"内容整体偏下约一个状态栏高度"一直没被修掉的原因。
            // 现在改成每次布局都读，窗口真正落位后的值一定会被用上；
            // 位置没变时赋同一个值，不会引起多余重组。
            .onGloballyPositioned { coords ->
                val loc = IntArray(2)
                composeView.getLocationOnScreen(loc)
                val screenH = composeView.context.resources.displayMetrics.heightPixels
                val fix =
                    if (screenH > 0) loc[1] + coords.size.height / 2 - screenH / 2 else 0
                if (fix != centerFixPx) centerFixPx = fix
                val bottomOver = if (screenH > 0) {
                    (loc[1] + coords.size.height - screenH).coerceAtLeast(0)
                } else {
                    0
                }
                if (bottomOver != bottomFixPx) bottomFixPx = bottomOver
                if (loc[1] != windowTopPx) windowTopPx = loc[1]
                if (screenH != screenHeightPx) screenHeightPx = screenH
            },
    ) {
        // 全是视频时改用上下滑 —— 和视频整理页的手感保持一致。
        // 混着照片时不这么做：竖着翻照片会让人下意识以为在翻列表。
        val allVideos = remember(items) { items.all { it.kind == MediaKind.VIDEO } }

        val previewPage: @Composable (Int) -> Unit = { page ->            val item = items[page]
            val isCurrent = page == pagerState.currentPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    // 随窗口校正上移 —— 缩略图必须和播放层严格重合，
                    // 否则首帧渲染前后画面会跳一下。
                    .offset(y = centerFix),
            ) {
                if (item.kind == MediaKind.VIDEO) {
                    // 缩略图永远垫在底层：它是视频首帧，播放器接管时画面不跳变。
                    // 播放器视图**不在这里** —— 它是屏幕级唯一的一份，见 ScreenLevelVideoLayer。
                    AsyncImage(
                        model = item.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // 横滑快进 / 快退。**只有纯视频列表才挂**：
                                // 那时翻页走 VerticalPager，横轴空闲；混排时横滑归
                                // HorizontalPager 翻页，见上面 seeking 处的说明。
                                // 竖直方向不碰 —— 留给 Pager 翻页，两者互不干扰。
                                .then(
                                    if (allVideos) {
                                        Modifier.pointerInput(item.storeKey()) {
                                            detectHorizontalDragGestures(
                                                onDragStart = {
                                                    seeking = true
                                                    seekBaseMs = player.currentPosition.coerceAtLeast(0L)
                                                    seekDeltaMs = 0L
                                                },
                                                onHorizontalDrag = { change, amount ->
                                                    change.consume()
                                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                                    val durationMs = player.duration.coerceAtLeast(0L)
                                                    // 满屏宽 ≈ 跳转整个视频；长视频封顶 3 分钟，
                                                    // 免得一划就跳过太多（与整理视频页同一上限）
                                                    val span = minOf(durationMs, MAX_SEEK_SPAN_MS).toFloat()
                                                    seekDeltaMs += (amount / width * span).toLong()
                                                    val target = (seekBaseMs + seekDeltaMs)
                                                        .coerceIn(0L, durationMs)
                                                    player.seekTo(target)
                                                },
                                                onDragEnd = { seeking = false },
                                                onDragCancel = { seeking = false },
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                                // 不消费事件的点击检测：拖拽被上面认领后这里不会再触发，
                                // 纯点按时则照旧播放 / 暂停。
                                // 长按 = 2× 倍速，和整理视频页同一套 —— 松手复原。
                                .pointerInput(item.storeKey()) {
                                    detectTapsWithoutConsuming(
                                        onTap = {
                                            if (player.isPlaying) player.pause() else player.play()
                                        },
                                        onLongPressStart = { speedUp = true },
                                        onLongPressEnd = { speedUp = false },
                                    )
                                },
                        )
                    }
                } else {
                    // 照片与截屏走可缩放视图：双指放大看长图细节，双击在 1x/2.5x 间切换。
                    // 它只吃双指和"已放大后的单指"，其余滑动照样传给 Pager 翻页。
                    ZoomableImage(
                        model = item.uri,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                val r = it.boundsInWindow()
                                if (pinchBox != r) pinchBox = r
                            },
                        contentDescription = item.name,
                        isAnimated = item.isAnimated,
                        onRequestSize = { size -> if (pinchSize != size) pinchSize = size },
                        onPinchToDate = { sc ->
                            pinchToDate.value?.invoke(item, sc, pinchBox, pinchSize)
                        },
                        // 和整理照片页、瀑布流放大页同一套圆角（屏宽 × 2.3%，上限 14dp）
                        cornerRadius = rememberContentCorner(),
                        // 遮罩要盖在图片自己的四角上，所以得知道它的宽高比
                        cornerAspect = if (item.width > 0 && item.height > 0) {
                            item.width.toFloat() / item.height
                        } else {
                            null
                        },
                        onSingleTap = { chromeVisible = !chromeVisible },
                    )
                }
            }
        }

        // ---- 分页（页面本身）----
        // 图层顺序统一为：页面 → 播放器 → 信息层（和整理视频页同一套）。
        // 旧顺序把 Pager 声明在信息层之后，按 Compose 的绘制顺序，页面会盖在
        // 顶栏 / 底栏之上，那些按钮实际是看不见也点不到的。
        if (allVideos) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { previewPage(it) }
        } else {
            HorizontalPager(
                state = pagerState,
                // 大图之间的接缝：和「回到那天」的格子、整理页翻页用的是同一个值
                // （rememberPageGap）—— 三处看起来才是一套。
                pageSpacing = pageGap,
                modifier = Modifier.fillMaxSize(),
            ) { previewPage(it) }
        }

        // ---- 屏幕级单视图播放器 ----
        // 全局一份、永不建 / 毁，位置跟着当前页偏移走 —— 不闪、也不留残留帧。
        // 首帧就绪前保持透明，露出页面里的缩略图：宽高比未定的那一帧永远不会被看到。
        // visible 是纯推导（当前页钥匙 vs 首帧钥匙），翻页同一帧即失效。
        val currentKey = current?.storeKey()
        ScreenLevelVideoLayer(
            player = player,
            visible = currentKey != null && readyKey == currentKey,
            pagerState = pagerState,
            horizontal = !allVideos,
            modifier = Modifier.offset(y = centerFix),
        )

        // 暂停指示。必须放在播放器**之上** —— 它是唯一直接盖在画面上的交互元素，
        // 留在页面里会被屏幕级播放器挡住。
        if (current?.kind == MediaKind.VIDEO && !isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = centerFix),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        }

        // ---- 快进 / 快退提示 ----
        // 拖拽中居中显示方向与目标时间，松手即消失。和视频整理页同一只胶囊，
        // 放在暂停指示之后绘制 —— 暂停时拖拽，胶囊盖在播放图标上层才对。
        if (seeking) {
            val durationMs = player.duration.coerceAtLeast(0L)
            val targetMs = (seekBaseMs + seekDeltaMs).coerceIn(0L, durationMs)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = centerFix)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (seekDeltaMs >= 0) "快进" else "快退",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (seekDeltaMs >= 0) AccentKeep else AccentInfo,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${formatDuration(targetMs)} / ${formatDuration(durationMs)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                }
            }
        }

        // ---- 长按倍速提示 ----
        // 和整理视频页同一只小胶囊。位置在顶栏**下方**一点（58dp）——
        // 预览的顶栏一直在，贴在最顶上会和它重叠。
        if (speedUp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 58.dp)
                    .offset(y = centerFix)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentKeep)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "2× 快进中",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (chromeVisible) {
            // ---- 顶栏 ----
            // 渐变刻意压得很轻（0x99 而不是全黑）：这是浏览场景，不是控制面板 ——
            // 关闭按钮和计数是"需要时能找到"就够了，不该在每一张照片上都抢一次注意力。
            // 文字也跟着降到次级色，整条栏的存在感比画廊里的照片低一档。
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭预览",
                        tint = TextSecondary,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary,
                )
                Spacer(Modifier.weight(1f))
                // 与左侧关闭按钮等宽，保证序号真正居中。
                // 视频时这个位置不给空占位，给**音量切换** ——
                // 默认静音是全App级设置，但预览里得留一个即时出口，
                // 否则"默认静音"在这里就变成了"强制静音"。
                if (current?.kind == MediaKind.VIDEO) {
                    IconButton(onClick = { muted = !muted }) {
                        Icon(
                            imageVector = if (muted) VolumeOffIcon else VolumeUpIcon,
                            contentDescription = if (muted) "取消静音" else "静音",
                            tint = TextSecondary,
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            // ---- 底栏 ----
            if (current != null) {
                // 动作区按条目类型分派："视频照整理视频页、图片照整理照片页"。
                // 这条要在底栏和侧栏两处都读，所以提到这里算一次。
                val isVideo = current.kind == MediaKind.VIDEO
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        // 窗口底边伸出屏幕的那一截要补回来，否则整条底栏
                        // （含左右下角的动作按钮）会低 108px、小屏上被切掉。
                        .offset(y = bottomFix)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xF2000000))
                            )
                        )
                        // 系统栏 inset **和**一段保底下边距同时给，两者取和而不是任选其一：
                        // 手势导航机型上 navigationBarsPadding() 只有十几 dp，
                        // 而屏幕底部往往还有圆角 —— 光靠 inset 会让按钮贴到圆角里，
                        // 看起来就是"被切掉一半"。多留这一截是给圆角和误触的余地。
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 32.dp, bottom = 26.dp),
                ) {
                    // 视频在播 → 把文件名 / 日期 / 大小这些信息收起来，
                    // 只留一根进度条；暂停（或图片）才显示。
                    //
                    // 这是"纯观看"该有的样子：**画面才是主体，说明文字不是。**
                    // 而且这些信息本来也不需要一直读 —— 想知道的时候停一下就有了。
                    val infoVisible = current.kind != MediaKind.VIDEO || !isPlaying
                    val alpha by animateFloatAsState(
                        targetValue = if (infoVisible) 1f else 0f,
                        animationSpec = tween(durationMillis = 180),
                        label = "previewInfoFade",
                    )
                    Column(modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append(relativeDayLabel(current.dateTakenMillis))
                                append(" · ")
                                append(formatSize(current.sizeBytes))
                                if (current.kind == MediaKind.VIDEO &&
                                    current.durationMillis > 0L
                                ) {
                                    append(" · ")
                                    append(formatDuration(current.durationMillis))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }

                    if (current.kind == MediaKind.VIDEO) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                // 拖动中显示"将要跳到的时间"，不是播放器当前位置 ——
                                // 否则手指在条上划，读数还停在老地方，对不上。
                                text = formatDuration(
                                    if (scrubbing) (duration * scrubRatio).toLong() else position
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.width(10.dp))
                            VideoProgressBar(
                                ratio = if (scrubbing) scrubRatio else {
                                    if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                },
                                modifier = Modifier.weight(1f),
                                onScrubStart = { scrubbing = true },
                                onScrub = { r ->
                                    scrubRatio = r
                                    if (duration > 0L) player.seekTo((duration * r).toLong())
                                },
                                onScrubEnd = { scrubbing = false },
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    bottomContent(current)

                    // ---- 图片条目：整理照片页那套底排圆钮 ----
                    // 喜欢（心）和收藏（星）并排放在左边、删除放右边 ——
                    // 和整理照片页同一个规格、同一个次序，用户在两个页面之间
                    // 来回也不用重新找按钮。
                    if (!isVideo && photoActions != null) {
                        Spacer(Modifier.height(14.dp))
                        // 包一层 `Box` 把 **BoxScope** 交给调用方 ——
                        // 否则调用方拿不到 `align`，就没法把某颗按钮/胶囊
                        // 相对整屏居中或贴边（用户反馈"胶囊没有居中"）。
                        Box(modifier = Modifier.fillMaxWidth()) {
                            photoActions(current)
                        }
                    }
                }

                // ---- 视频条目：侧边动作栏（和整理视频页同款）----
                // 喜欢、收藏、删除三颗竖排贴屏幕一侧，位置跟随设置里的
                // 「操作栏放在右侧」。**只给视频** —— 图片用上面底栏那一排圆钮。
                //
                // 挂在外层 Box 而不是底栏 Column 里：它贴的是**屏幕边**，
                // 跟着底栏的渐变一起上上下下就成"底栏的一部分"了。
                // 也不再自己加 align 之外的 padding —— 贴边距离由 ActionRail 内部统一管。
                if (isVideo && videoActions != null) {
                    ActionRail(
                        onRight = railOnRight,
                        modifier = Modifier
                            .align(
                                if (railOnRight) Alignment.BottomEnd
                                else Alignment.BottomStart
                            )
                            // 和底栏同一个修正量：窗口底边伸出屏幕的那一截要补回来，
                            // 否则整列按钮会低 108px、小屏上被切掉。
                            .offset(y = bottomFix)
                            .navigationBarsPadding(),
                    ) {
                        videoActions(current)
                    }
                }

                // 全屏入口。和整理视频页同一套位置：贴在**视频画面下边缘**的居中胶囊，
                // 而不是固定在屏幕某个位置 —— 横屏视频在竖屏里是一条居中的横带，
                // 胶囊贴着横带下沿才读得通。只有横屏视频才给。
                // 用 **isLandscape**（播放器解码后的真实方向），不是 MediaStore 那两列 ——
                // 后者不带 rotation，会把"竖着拍、横着存"的视频误判成横屏，
                // 于是竖屏视频错误地长出一个「全屏观看」按钮。
                if (current.kind == MediaKind.VIDEO &&
                    isLandscape &&
                    !fullscreen
                ) {
                    // 画面高度用**真实渲染尺寸**算。解码前先用 MediaStore 的尺寸估，
                    // 就绪后用实际解码尺寸，免得胶囊位置在一开始跳一下。
                    val w = if (decodedWidth > 0) decodedWidth else current.width
                    val h = if (decodedHeight > 0) decodedHeight else current.height
                    val safeW = if (w > 0) w else 1
                    val safeH = if (h > 0) h else 1
                    val renderedHeight = minOf(
                        screenHeight,
                        screenWidth * (safeH.toFloat() / safeW.toFloat()),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = centerFix)
                            .padding(
                                top = ((screenHeight + renderedHeight) / 2f + 14.dp)
                                    .coerceIn(0.dp, screenHeight - 90.dp)
                            ),
                    ) {
                        FullscreenPill { enterFullscreen() }
                    }
                }
            }
        }
        // ---- 调用方要塞在预览之上的那一层（「回到那天」就是走这里） ----
        //
        // 必须放在**这个窗口的最外层**、所有预览内容之后 ——
        // 它是独立窗口，调用方在外面盖的任何东西都会被整个压住（见 overlayContent 的说明）。
        //
        // ---- 高度为什么不能直接 fillMaxSize ----
        // 这个 Dialog 的内容框比屏幕高（顶边在状态栏下沿、底边还越过屏幕下沿），
        // `fillMaxSize` 填的是**窗口**而不是屏幕 ⇒ 底部那截落在屏幕外，
        // 用户看到"刻度条和按钮只显示上半部分"。所以显式给"屏幕上还看得见的那一段"。
        // 顶边不动：Dialog 里 `statusBarsPadding()` 读到 0，窗口那点偏移正好补上。
        if (overlayContent != null) {
            val visibleH = with(LocalDensity.current) {
                (screenHeightPx - windowTopPx).toFloat().coerceAtLeast(1f).toDp()
            }
            Box(modifier = Modifier.fillMaxWidth().height(visibleH)) {
                overlayContent()
            }
        }


    }
    }
}

/** 左右滑动跳转的上限：满屏宽最多跳 3 分钟，避免长视频一划就过头。 */
private const val MAX_SEEK_SPAN_MS = 3 * 60 * 1000L

