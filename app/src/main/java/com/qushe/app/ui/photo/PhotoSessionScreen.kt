package com.qushe.app.ui.photo

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.MotionPhoto
import com.qushe.app.core.PhotoExif
import com.qushe.app.core.SessionResult
import com.qushe.app.core.SwipeDecision
import com.qushe.app.core.dayStartOf
import com.qushe.app.core.formatSize
import com.qushe.app.core.opposite
import com.qushe.app.core.relativeDayLabel
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.ThumbnailFilmstrip
import com.qushe.app.ui.common.DAY_INTRO_MS
import com.qushe.app.ui.common.createVideoPlayerView
import com.qushe.app.ui.common.StarBorderIcon
import com.qushe.app.ui.common.ZoomableImage
import com.qushe.app.ui.common.DoubleTapFlash
import com.qushe.app.ui.common.DoubleTapFlashOverlay
import com.qushe.app.ui.common.flingTo
import com.qushe.app.ui.common.settleToPage
import com.qushe.app.ui.common.rememberContentCorner
import com.qushe.app.ui.common.rememberPageGap
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.Surface3
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import com.qushe.app.ui.theme.ScrimBottom
import com.qushe.app.ui.theme.ScrimTop
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import com.qushe.app.ui.common.fitInside
import com.qushe.app.ui.common.scaledAboutCenter

/**
 * 照片 / 截屏整理：卡片堆叠 + 滑动决策 + 缩放查看 + 缩略胶片条。
 *
 * 三个来自「去留」并决定成败的细节：
 *
 * 1. 不显示"还剩多少张待处理"。整个界面没有任何 todo 进度，
 *    只有"已看 N / 已标记 M"。前者是家务，后者是成果。
 *
 * 2. 下一张是**垫在下面**的，不是从右侧滑进来的。当前张跟手左移时，
 *    下面那张原地露出来 —— 眼睛不需要等下一张停稳就能看清。
 *
 * 3. 每一层都是全屏、不透明、不缩放。照片用 Fit 完整显示会留边，
 *    只要有一层透明或者比上层小，边缘就会透出再下面那张的内容。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoSessionScreen(
    items: List<MediaItem>,
    swipeUpDeletes: Boolean,
    deleteToTrash: Boolean,
    /** 默认静音。实况照片的片段也是"播放视频"，同样不外放。 */
    videoMutedByDefault: Boolean,
    isFavorite: (MediaItem) -> Boolean,
    onToggleFavorite: (MediaItem) -> Boolean,
    isLiked: (MediaItem) -> Boolean,
    onToggleLike: (MediaItem) -> Boolean,
    /** 双击画面 = 喜欢（true）还是收藏（false）。和视频页、设置页同一套语义。 */
    doubleTapLikes: Boolean,
    /** 「i」胶囊是否显示相对时间 / 具体日期。见设置里的两个开关。 */
    relativeDates: Boolean,
    absoluteDates: Boolean,
    onSeen: (MediaItem) -> Unit,
    buildDeleteRequest: (List<MediaItem>) -> IntentSender?,
    probeMotion: suspend (MediaItem) -> MotionPhoto.Resolution,
    resolveLocation: suspend (MediaItem) -> String?,
    readExif: suspend (MediaItem) -> PhotoExif?,
    resolveMotionOffset: suspend (MediaItem) -> Long?,
    /**
     * 「回到那天」：取某一天的**全部图片**（照片 + 截屏）。
     *
     * 刻意不带 MediaKind —— 那一天拍了什么就该看到什么，
     * 不该因为用户从"整理照片"还是"整理截屏"入口进来就只看一半（用户报）。
     */
    loadDayItems: suspend (Long) -> List<MediaItem>,
    onConclude: (SessionResult) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val vLimitPx = with(density) { 140.dp.toPx() }
    // 左右滑动的判定行程。原来是 110dp，实测手感偏长 —— 翻页是个高频动作，
    // 每次都要划过小半个屏幕才生效会很明显。72dp 依然是明确的主动位移，
    // 不会和"手滑"混淆。
    val hLimitPx = with(density) { 72.dp.toPx() }
    val upDecision = if (swipeUpDeletes) SwipeDecision.DELETE else SwipeDecision.FAVORITE

    var index by remember { mutableIntStateOf(0) }
    var marked by remember { mutableStateOf(emptyList<MediaItem>()) }
    var favAdded by remember { mutableStateOf(emptyList<MediaItem>()) }
    var favRemoved by remember { mutableStateOf(emptyList<MediaItem>()) }
    var pendingResult by remember { mutableStateOf<SessionResult?>(null) }
    var finishing by remember { mutableStateOf(false) }

    // 胶片条默认收起（用户要求"单击才弹出"）
    var filmstripVisible by remember { mutableStateOf(false) }

    // ---- chrome 的显隐：和瀑布流放大页（`DayZoomView`）**同一套** ----
    //  · 单击画面      ⇒ 切换显隐
    //  · 放大          ⇒ 隐藏（画面让位给照片）
    //  · 缩回 1x       ⇒ **显示**（不是 toggle：用户刚松手的是一次"看图"动作）
    //  · 进「回到那天」⇒ 隐藏（整个视图交接给日期视图）
    //
    // 之前只有"放大才隐藏"，单击没参与 —— 于是想安静看一张图只能先去放大，
    // 规则和瀑布流放大页对不上（用户要求统一）。
    var chromeHidden by remember { mutableStateOf(false) }

    // ---- chrome 三态：由 [advanceChromeStage] 单击推进 ----
    // ① 胶片栏收 + chrome 显（默认，专注看当前这张）
    // ② 胶片栏展 + chrome 显（要看全貌）
    // ③ 胶片栏收 + chrome 隐（沉浸，屏幕上什么都不留）
    //
    // **必须自己记一个 stage**，不能拿 `filmstripVisible` 当前值当指针：
    // 状态③（胶片栏收）和状态①（胶片栏也收）在这个变量上**取值相同**，
    // 于是"③ 之后点一下"会算成去②、回不到①（用户反馈"只在 2 和 3 之间切换"）。
    var chromeStage by remember { mutableIntStateOf(0) }

    fun advanceChromeStage() {
        val next = (chromeStage + 1) % 3
        chromeStage = next
        filmstripVisible = next == 1
        chromeHidden = next == 2
    }

    // ---- 「回到那天」----
    //
    // 触发后**直达当前这张照片所属的那一天**，不再让用户去日期列表里挑。
    //
    // 原因是意图识别错了：用户是在看某一张照片时起的这个念头，
    // 他关心的是"这一张的那天"，答案是现成的 —— 让他对着几十行日期
    // 自己去找是哪一天，等于把问题又抛回给他。
    //
    // 选中的那一天会替换**浏览列表**（list），但刻意不结束这一组：
    // 已标记 / 已收藏的都留在原地，退出时一起处理。
    // 效果上相当于"换个池子继续整理"，而不是"结束一组、再开一组"。
    var dayItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    val list = dayItems ?: items

    // 本组的内容类型（照片 / 截屏）—— 直方图与取内容都以它为口径。
    // 用原始的 items 而不是 list：换天之后类型不会变，参照物要始终稳定。
    val sessionKind = items.firstOrNull()?.kind ?: MediaKind.PHOTO

    // 日期视图的三个状态。dayViewOpen = 整屏日期视图是否在前台；
    // dayViewItems = 那一天的内容（null 表示还在读）；dayTarget = 那一天的零点。
    var dayViewOpen by remember { mutableStateOf(false) }

    /**
     * 这一屏的图标（顶栏 / 底栏 / 角标）的淡入淡出。
     *
     * 两件事会让它们消失：
     *  · 放大看局部（[chromeHidden]）—— 画面让位给照片；
     *  · 进「回到那天」（[dayViewOpen]）—— 整个视图交接给日期视图。
     *
     * 两种都要**淡出**、不能瞬间不见：日期视图那一层是渐入的，
     * 底下这些图标若"啪"地消失，那一帧就是个很明显的跳（用户要求）。
     * alpha 读在 `graphicsLayer { }` 里，动画的每一帧只走绘制、不重组。
     */
    val chromeAlpha = animateFloatAsState(
        targetValue = if (chromeHidden || dayViewOpen) 0f else 1f,
        animationSpec = tween(durationMillis = 260),
        label = "chromeAlpha",
    )
    var dayViewItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var dayTarget by remember { mutableLongStateOf(0L) }
    var daySwitching by remember { mutableStateOf(false) }
    /**
     * 本次水平拖拽的方向：+1 往右、-1 往左、0 = 没在水平拖。
     *
     * 底下那张"下一张"靠它决定从哪一侧进来（见下面的翻页实现）。
     * 必须在 [onDecision] 里清零 —— 换页之后新照片在正中，底牌该重新藏起来。
     */
    var pageDir by remember { mutableIntStateOf(0) }
    var dayNotice by remember { mutableStateOf<String?>(null) }
    // 换天之前已回顾的数量。统计时和换天后的 index 相加，别让成果数字倒退。
    var reviewedBefore by remember { mutableIntStateOf(0) }

    // 实况照片播放
    var motionJob by remember { mutableStateOf<Job?>(null) }
    var motionFile by remember { mutableStateOf<File?>(null) }
    var motionPlaying by remember { mutableStateOf(false) }
    var motionHint by remember { mutableStateOf<String?>(null) }
    // 实况片段的静音状态。默认跟设置走，用户一动系统音量就解除
    // （和视频整理页、预览页同一套）—— "默认静音"不该变成"强制静音"。
    var motionMuted by remember { mutableStateOf(videoMutedByDefault) }

    /**
     * 实况播放的**首帧门控**，和视频整理页、预览页同一套双钥匙。
     *
     * 播放视图是常驻挂载的（只用 alpha 控制显隐），而播放器全会话共用一个 ——
     * TextureView 里会一直留着**上一段实况视频的最后一帧**。
     * 长按下一张时 alpha 立刻变 1，而新视频的首帧还没渲染出来，
     * 那一帧就把上一张照片露了出来（用户报"长按播放会闪一帧上一张照片"）。
     *
     *   motionPlayKey  = 最近一次交给播放器的条目
     *   motionReadyKey = 首帧已经渲染出来的条目
     *
     * 两个钥匙对上才允许显示。翻页/停止时一起清空。
     */
    var motionPlayKey by remember { mutableStateOf<Long?>(null) }
    var motionReadyKey by remember { mutableStateOf<Long?>(null) }

    val motionPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            // 实况片段也是视频 —— "默认静音"对它同样生效。
            volume = if (videoMutedByDefault) 0f else 1f
        }
    }
    LaunchedEffect(motionMuted) {
        motionPlayer.volume = if (motionMuted) 0f else 1f
    }
    LaunchedEffect(motionMuted) {
        if (!motionMuted) return@LaunchedEffect
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE)
            as? android.media.AudioManager
            ?: return@LaunchedEffect
        val baseline = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        while (true) {
            delay(500L)
            if (audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) != baseline) {
                motionMuted = false
                break
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { motionPlayer.release() }
    }

    // 播放出错时把原因说出来，而不是让界面静默地什么都不发生 ——
    // "长按没反应"和"长按后黑屏"在用户看来一样，但原因完全不同。
    DisposableEffect(motionPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                motionHint = "实况片段播放失败：${error.errorCodeName}"
            }

            override fun onRenderedFirstFrame() {
                // 只认"最近一次交给播放器的条目" —— 它才是刚吐出来的那一帧
                motionReadyKey = motionPlayKey
            }
        }
        motionPlayer.addListener(listener)
        onDispose { motionPlayer.removeListener(listener) }
    }

    LaunchedEffect(motionHint) {
        if (motionHint != null) {
            delay(2200L)
            motionHint = null
        }
    }

    // 捏合向「回到那天」推进的进度（0..1）。0 = 没在捏。
    //
    // ---- 声明在**外层**，由 `openDayView` 负责清零 ----
    //
    // 它由 `ZoomableImage` 的手势回调写。用户捏合触发「回到那天」后
    // 照片页的手势已经结束，没人再写 0 ⇒ 提示一直挂在屏幕上，
    // 退出日期视图还能看到"继续捏合"（用户报"退出之后这张图上还有提示"）。
    //
    // 试过用 `LaunchedEffect(index) { progress = 0f }` 清零，**没用**：
    // 退出日期视图回来 `index` 还是同一张，effect 不重跑。
    // 真正可靠的时机是"触发进入"本身 —— 在 `openDayView` 里清零，
    // 一进日期视图就归零，退出回来自然是干净的。
    var pinchToDateProgress by remember { mutableFloatStateOf(0f) }

    // ---- 飞入动效的两块数据 ----
    //
    // `zoomBox`：当前这张图**容器**的窗口矩形。用 onGloballyPositioned 实测，
    // 而不是拿屏幕尺寸去估 —— 估出来的会差一点，而飞行起点的偏差在
    // 动画第一帧就看得出来（图片会先从错误的位置跳一下）。
    // `dayIntroFrom`：进入日期视图那一刻**定格**下来的起点。
    // 不能等日期视图打开后再读 zoomBox —— 那时它还会随照片页重组刷新。
    var zoomBox by remember { mutableStateOf<Rect?>(null) }
    var dayIntroFrom by remember { mutableStateOf<Rect?>(null) }
    /**
     * 当前这张图**实际会被请求的解码尺寸**（由 ZoomableImage 回调）。
     *
     * 飞入动效里要把它交给日期视图的焦点格：同一个 uri + 同一个 size ⇒
     * Coil memory cache 直接命中 ⇒ 焦点格第一帧就是同一张高清图。
     * 详见 `ZoomableImage.onRequestSize` 的说明。
     */
    var photoRequestSize by remember { mutableStateOf<IntSize?>(null) }
    /**
     * [photoRequestSize] 是**哪一张**的尺寸。
     *
     * 必须一起记：换照片时 `ZoomableImage` 的容器会先归零、下一帧才填上新值，
     * 中间那一小段 `photoRequestSize` 还是上一张的。不校验的话，
     * 快速滑到某张、立刻捏合，焦点格会拿着**上一张的尺寸**去请求 ⇒
     * 缓存必然 miss ⇒ 又要重新解码几十毫秒 —— 起手那一下的黑屏就回来了。
     */
    var photoSizeUri by remember { mutableStateOf<Uri?>(null) }
    var dayIntroSize by remember { mutableStateOf<IntSize?>(null) }

    // ---- 缩略图 Bitmap：滑到之前就先拿在手上 ----
    //
    // "用列表里的低分辨率缩略图当大图的初始画面"是对的方向，
    // 但**必须是已经拿在手上的**：只要还走请求，就还有 IO、解码、
    // 等回调那一帧 —— 在纯黑背景上，那一两帧就是"闪一下"（用户报了很多轮）。
    //
    // 所以这里在 index 变化时，把**当前 + 前后各一张**直接读成 Bitmap 存着。
    // 滑过去时是**同步取用**：没有 IO、没有解码、没有等回调，
    // 真正的零延迟。高清解出来之后自己会盖上去。
    val thumbCache = remember { mutableStateMapOf<Long, ImageBitmap>() }
    LaunchedEffect(index, list) {
        val keep = listOf(index - 1, index, index + 1).mapNotNull { i ->
            list.getOrNull(i)?.storeKey()
        }
        // 滑远的就丢掉，别让它一直长（当前这张也丢 —— 高清早就顶上来了）
        thumbCache.keys.filterNot { it in keep }.forEach { thumbCache.remove(it) }
        for (i in listOf(index - 1, index, index + 1)) {
            val item = list.getOrNull(i) ?: continue
            val key = item.storeKey()
            if (thumbCache.containsKey(key)) continue
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(item.uri, Size(512, 512), null)
                }.getOrNull()
            }
            if (bmp != null) thumbCache[key] = bmp.asImageBitmap()
        }
    }

    // 换一张就停掉正在播的实况片段
    LaunchedEffect(index) {
        motionPlaying = false
        motionFile = null
        motionPlayKey = null
        motionReadyKey = null
        motionPlayer.stop()
        motionPlayer.clearMediaItems()
    }

    // ---- 实况照片探测 ----
    //
    // 判定不能依赖查询结果：XMP 那一列受 ACCESS_MEDIA_LOCATION 约束，不能放进
    // MediaStore 的投影（小米等厂商的 Provider 会直接抛异常）。所以实况判定统一
    // 由文件头探测完成 —— 每张只读 128KB，不做全文件扫描，结果由仓库层按条目缓存。
    val current = list.getOrNull(index)
    var currentMotion by remember(current?.id) {
        mutableStateOf<MotionPhoto.Resolution?>(null)
    }
    LaunchedEffect(current?.id) {
        val item = list.getOrNull(index)
        currentMotion = if (item == null) null else probeMotion(item)
    }
    val looksLikeMotion = currentMotion?.isMotionPhoto == true

    // 拍摄地点。和视频页同一套逻辑：按需解析，拿不到就不显示这一行。
    var currentLocation by remember(current?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(current?.id) {
        val item = current ?: return@LaunchedEffect
        currentLocation = resolveLocation(item)
    }

    // 收藏状态必须能被 Compose 观察到。
    //
    // IdSetStore 内部就是个普通 HashSet，改它不会触发任何重组 ——
    // 这正是"点了收藏红心还是空心的""翻到已收藏的照片也不亮"的根因：
    // 状态确实变了，只是没有任何东西在听。
    // 用一个计数当作"收藏已变更"的信号，配合 remember 的键强制重算。
    var favoriteTick by remember { mutableIntStateOf(0) }

    // ---- 双击"喜欢/收藏"的反馈 ----
    //
    // 双击之后必须把结果**说出来**：这是个没有任何常驻控件的动作，
    // 不给反馈用户会以为没生效。刻意做成一颗小胶囊而不是视频页那种
    // 满屏大图标 —— 照片页的主体就是画面，盖住它就本末倒置了。
    var dtFlash by remember { mutableStateOf<DoubleTapFlash?>(null) }
    var dtFlashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(dtFlashTick) {
        if (dtFlash == null) return@LaunchedEffect
        delay(1200L)
        dtFlash = null
    }

    fun runDoubleTap(target: MediaItem) {
        val nowOn = if (doubleTapLikes) onToggleLike(target) else onToggleFavorite(target)
        favoriteTick++
        dtFlash = DoubleTapFlash(liked = doubleTapLikes, on = nowOn)
        dtFlashTick++
    }
    val currentIsFavorite = remember(current?.id, favoriteTick) {
        current?.let { isFavorite(it) } == true
    }
    val currentIsLiked = remember(current?.id, favoriteTick) {
        current?.let { isLiked(it) } == true
    }

    // 详情面板：点信息胶囊上的 ⓘ 才展开。
    // EXIF 也到那时才解析 —— 每张都提前读一遍没必要，那是纯粹的浪费。
    var detailVisible by remember { mutableStateOf(false) }
    var detailExif by remember { mutableStateOf<PhotoExif?>(null) }
    LaunchedEffect(detailVisible, current?.id) {
        if (!detailVisible) return@LaunchedEffect
        val item = current ?: return@LaunchedEffect
        detailExif = readExif(item)
    }

    // ---- 实况探测：**窗口化**，不整组串行扫 ----
    //
    // 之前是 `list.forEach { probeMotion(it) }` 整组扫一遍。而
    // `probeMotion` 对没有 XMP 的条目（截屏基本都没有）要读 128KB 头 +
    // 最多 4MB 尾部窗口：
    //  · 普通一组 30 张：串行读最多 ~120MB；
    //  · `confirmDay()` 之后 `list` 换成那一天的内容（几百张）：最多 ~1GB。
    // 它跑在 IO 上不卡主线程，但会**和图片加载抢 IO 带宽** ——
    // 用户反馈"进去卡一阵"里有它一笔；而且胶片条默认收起，
    // 用户不打开也白探一遍。
    //
    // 改成只探"当前张 ± 25"的窗口，且**只在胶片条可见时**探。
    // 探过的走 `motionProbe` 缓存，滑回来零 IO。
    // 代价：滑到窗口外的条目暂时没有"实况"角标（纯视觉，滑到就补上）。
    var motionKeys by remember(list) { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(list, index, filmstripVisible) {
        if (!filmstripVisible || list.isEmpty()) return@LaunchedEffect
        val known = motionKeys
        val from = (index - FILMSTRIP_PROBE_RADIUS).coerceAtLeast(0)
        val to = (index + FILMSTRIP_PROBE_RADIUS).coerceAtMost(list.lastIndex)
        // 只探没探过的
        val todo = (from..to).filter { i ->
            i in list.indices && list[i].storeKey() !in known
        }
        if (todo.isEmpty()) return@LaunchedEffect
        val found = known.toMutableSet()
        for (i in todo) {
            if (probeMotion(list[i]).isMotionPhoto) found += list[i].storeKey()
        }
        motionKeys = found
    }

    fun startMotionPlayback(item: MediaItem) {
        motionJob?.cancel()
        motionJob = scope.launch {
            val offset = resolveMotionOffset(item)
            if (offset == null || offset <= 0L) {
                motionHint = "是实况照片，但取不出嵌入的视频"
                return@launch
            }
            val file = MotionPhoto.extractVideo(
                context = context,
                uri = item.uri,
                cacheName = item.storeKey().toString(),
                offsetFromEnd = offset,
                fileSize = item.sizeBytes,
            )
            if (file == null) {
                motionHint = "实况片段抽取失败"
                return@launch
            }
            motionFile = file
            // 门控：新片段的首帧渲染出来之前不许显示（见 motionReadyKey 的注释）
            motionReadyKey = null
            motionPlayKey = item.storeKey()
            motionPlaying = true
            motionPlayer.setMediaItem(Media3Item.fromUri(Uri.fromFile(file)))
            motionPlayer.prepare()
            motionPlayer.playWhenReady = true
        }
    }

    fun stopMotionPlayback() {
        motionPlaying = false
        motionPlayKey = null
        motionReadyKey = null
        motionPlayer.stop()
        motionPlayer.clearMediaItems()
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        // 用户在系统弹窗里点了"取消"，就不该把标记项算进"已删除"的统计里
        val approved = activityResult.resultCode == Activity.RESULT_OK
        val result = pendingResult
        pendingResult = null
        if (result != null) {
            onConclude(if (approved) result else result.copy(markedDelete = emptyList()))
        }
    }

    /**
     * 结束这一组。
     *
     * 退出时**直接把已标记的项交出去**，不再多问一遍 —— 系统的删除弹窗本身就是确认，
     * 中间再插一层只会让每次退出都多两次点击。
     *
     * 同时这也修掉了"返回时已标记的没保存下来"：过去返回走的是
     * `performDelete = false` 的分支，整批标记会被直接丢弃，用户完全看不出来。
     */
    fun finish() {
        // 收尾只做一次，避免自动收尾和手动退出撞在一起、弹出两个系统弹窗
        if (finishing) return
        finishing = true

        val result = SessionResult(
            kind = sessionKind,
            reviewedCount = (reviewedBefore + index).coerceAtMost(reviewedBefore + list.size),
            markedDelete = marked,
            favoriteAdded = favAdded,
            favoriteRemoved = favRemoved,
        )
        val sender = if (marked.isNotEmpty()) {
            runCatching { buildDeleteRequest(marked) }.getOrNull()
        } else {
            null
        }
        if (sender != null) {
            pendingResult = result
            runCatching {
                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }.onFailure {
                pendingResult = null
                onConclude(result)
            }
        } else {
            onConclude(result)
        }
    }

    /**
     * 「回到那天」第一步：进入日期视图，并去读那一天的内容。
     *
     * 不需要用户先选日期 —— 日期来自**当前正在看的这张照片**。
     * 进视图这件事本身是同步的（界面立刻切过去），读内容在后台跑，
     * 所以点下去就有反应，不会先愣一下。
     */
    fun openDayView(dayStart: Long, from: Rect?, size: IntSize?) {
        if (daySwitching) return
        // 提示在这里清零：见 pinchToDateProgress 的注释
        pinchToDateProgress = 0f
        // 飞入动效的**起点**在这里定格。
        // 不能在日期视图里现读 —— 那边读到的 `zoomBox` 已经是打开之后的了，
        // 而且这中间照片页还会重组。
        dayIntroFrom = from
        dayIntroSize = size
        dayTarget = dayStart
        dayViewItems = null
        dayNotice = null
        dayViewOpen = true
        daySwitching = true
        scope.launch {
            // 不带 kind：那一天的照片和截屏都要（见 loadDayItems 的说明）
            val loaded = runCatching { loadDayItems(dayStart) }
                .getOrDefault(emptyList())
            daySwitching = false
            dayViewItems = loaded
            // 读不出来就在视图里说清楚。原来空列表和"加载失败"长得一样，
            // 用户只会以为这天本来就没东西 —— 而那是完全不同的结论。
            if (loaded.isEmpty()) dayNotice = "这一天已经读不到内容了"
        }
    }

    /**
     * 「回到那天」第二步：确认用这一天开一组整理。
     *
     * 刻意**不结束当前会话** —— 已标记、已收藏的都留在原地，退出时一起处理。
     * 相当于"换个池子继续整理"，而不是"结束一组、再开一组"。
     */
    fun confirmDay() {
        val loaded = dayViewItems ?: return
        if (loaded.isEmpty()) return
        reviewedBefore += index.coerceAtMost(list.size)
        dayItems = loaded
        index = 0
        filmstripVisible = false
        chromeHidden = false
        detailVisible = false
        dayViewOpen = false
    }

    /**
     * 收藏 / 取消收藏，并记进这一组的结果里。
     *
     * 抽出来给两处用：单张上滑（[upDecision] 指向收藏时）、以及「回到那天」
     * 瀑布流格子的上滑。两边语义必须一致 —— 否则同一个手势在两张界面上
     * 记的账不一样，退出时会对不上。
     */
    fun applyFavorite(target: MediaItem) {
        val nowFavorite = onToggleFavorite(target)
        favoriteTick++
        if (nowFavorite) {
            favRemoved = favRemoved.filterNot { it.storeKey() == target.storeKey() }
            if (favAdded.none { it.storeKey() == target.storeKey() }) {
                favAdded = favAdded + target
            }
        } else {
            favAdded = favAdded.filterNot { it.storeKey() == target.storeKey() }
            if (favRemoved.none { it.storeKey() == target.storeKey() }) {
                favRemoved = favRemoved + target
            }
        }
    }

    // ---- 放大态：返回键先回到初始大小，再按一次才退出 ----
    //
    // 这个 state 原来声明在 BoxWithConstraints 里面，返回键读不到；
    // 提到这里之后，BackHandler 才能判断"现在是放大着"。
    var zoomed by remember { mutableStateOf(false) }
    var zoomResetTick by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (zoomed) {
            zoomResetTick++
            zoomed = false
        } else {
            finish()
        }
    }

    // 最后一张滑走之后自动收尾。
    // 以前走到这里会停在一屏纯黑上，用户会以为卡死了 —— 一组看完就该退出去。
    // 等 320ms 是为了让最后一张的飞出动画播完，不要在动画中途把界面切走。
    LaunchedEffect(index, list.size) {
        if (list.isNotEmpty() && index >= list.size) {
            delay(320L)
            finish()
        }
    }

    // 翻到下一张时上一张的放大态不该留下来（新图从 1x 开始）。
    LaunchedEffect(index) {
        zoomed = false
    }

    // 用 BoxWithConstraints 是为了算「图片本身」在屏幕上的位置 ——
    // 实况 / 动图 / 截屏的角标要贴在**图片的左上角**，而不是屏幕的左上角。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        if (current != null) {
            val offsetX = remember(current.id) { Animatable(0f) }
            val offsetY = remember(current.id) { Animatable(0f) }
            // 收藏的"被收走"进度：0 = 原样，1 = 缩到 0.85 并淡出。
            // 删除仍然飞出屏幕（扔掉），收藏是收进夹子 —— 两条动线必须分开。
            val dismissProgress = remember(current.id) { Animatable(0f) }

            // ---- 垫在下面那一张：真正的"翻页" ----
            //
            // 之前它是**常驻全屏 + 按位移淡入**，用户反馈"前一张只稍微滑了一下，
            // 后面一张就立刻呈现在背景里，有点突兀"（1.3.47）。
            // 淡入的锅在阈值：位移达到"翻页判定线"的 18% 就已经全不透明了，
            // 而那条线只有 72dp ⇒ 手指走十几像素下一张就整个出现。
            //
            // 现在改成和 `HorizontalPager` 同一个模型：
            // **下一张停在旁边一整屏的位置，然后和当前这张同速移动。**
            //   · 静止时它在屏幕外 ⇒ 看不见（"第一张下面露出第二张"的老问题也不会回来）
            //   · 拖动时它从那一侧滑进来 ⇒ 和收藏页/喜欢页的翻页一模一样
            //   · 松手回弹，它跟着滑回去
            //   · 翻页成立时当前这张正好走完一屏，它正好归位
            // 纵向（上滑删除 / 收藏）不是翻页，仍用淡入，且阈值放大到 1.2 倍行程 ——
            // 删除过程中慢慢露出底牌，而不是手指一动就整张糊上来。
            val pageW = with(density) { maxWidth.toPx() }
            val pageGapPx = with(density) { rememberPageGap().toPx() }
            val vBase = maxOf(vLimitPx, 1f) * BEHIND_FADE_FRACTION
            list.getOrNull(index + 1)?.let { next ->
                // 接缝宽度：翻页开始时是完整的 [pageGapPx]，快到位时收到 0。
                //
                // 为什么**越翻越窄**而不是固定：到位那一刻它就是下一张"当前图"，
                // 而当前图是满屏的 —— 接缝不收窄的话，最后一帧会突然"长出"一道黑边。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val dx = offsetX.value
                            val dy = offsetY.value
                            if (pageDir != 0) {
                                // 同速移动：dir=-1（往左拖）时它从右侧进来，反之从左侧
                                translationX = dx - pageDir * pageW
                                alpha = 1f
                                // 进度 0 → 1 对应"刚认领" → "正好走完一屏"
                                val t = (kotlin.math.abs(dx) / pageW).coerceIn(0f, 1f)
                                val gap = pageGapPx * (1f - t)
                                // 整个盒子（连底色）一起缩，缩出来的一圈就是接缝：
                                // 两侧各内缩 gap，合起来正好一道 pageGapPx 宽的缝
                                val s = 1f - 2f * gap / pageW
                                scaleX = s
                                scaleY = s
                            } else {
                                translationX = 0f
                                val moved = maxOf(
                                    kotlin.math.abs(dy),
                                    // 收藏的"收走"是缩放淡出、不带位移，也得让底露出来
                                    dismissProgress.value * maxOf(vLimitPx, 1f),
                                )
                                alpha = (moved / vBase).coerceIn(0f, 1f)
                            }
                        },
                ) {
                    PhotoLayer(next)
                }
            }

            DraggablePhoto(
                item = current,
                offsetX = offsetX,
                offsetY = offsetY,
                dismissProgress = dismissProgress,
                vLimitPx = vLimitPx,
                hLimitPx = hLimitPx,
                upDecision = upDecision,
                onDragDir = { d -> if (pageDir != d) pageDir = d },
                // 放大态把单指手势整条让给 ZoomableImage —— 否则长图放大后拖不动
                zoomed = zoomed,
                onDecision = { decision ->
                    if (decision != SwipeDecision.NEXT) pageDir = 0
                    when (decision) {
                        SwipeDecision.DELETE -> {
                            if (marked.none { it.storeKey() == current.storeKey() }) {
                                marked = marked + current
                            }
                        }

                        SwipeDecision.FAVORITE -> applyFavorite(current)

                        else -> Unit
                    }
                    onSeen(current)
                    index = if (decision == SwipeDecision.PREV) {
                        (index - 1).coerceAtLeast(0)
                    } else {
                        index + 1
                    }
                },
            ) {
                ZoomableImage(
                    model = current.uri,
                    resetZoomSignal = zoomResetTick,
                    modifier = Modifier
                        .fillMaxSize()
                        // 记下容器的真实位置，供"飞入动效"算起点用。
                        // 判重是必须的：每次回调都写 state 会自己制造无限重组。
                        .onGloballyPositioned {
                            val r = it.boundsInWindow()
                            if (zoomBox != r) zoomBox = r
                        },
                    contentDescription = current.name,
                    // ---- 圆角：画在图上面的四角遮罩 ----
                    // 和瀑布流的格子共用同一套圆角（[rememberContentCorner]）。
                    // 1.3.44 试过"把变换层缩到图片真实矩形再 clip"，结果
                    // "图片会自动放大"——那个盒子一变小，整条链的尺寸基准就跟着变。
                    // 1.3.46 换成盖遮罩：布局、缩放、边界一个字节都没动。
                    cornerRadius = rememberContentCorner(),
                    // 遮罩要盖在图片自己的四角上，所以得知道它的宽高比
                    cornerAspect = if (current.width > 0 && current.height > 0) {
                        current.width.toFloat() / current.height
                    } else {
                        null
                    },
                    // 动图由调用方告知（Uri 里没有文件名，自己判不出来）
                    isAnimated = current.isAnimated,
                    // ---- 提前把相邻两张请求好 ----
                    //
                    // 翻页是自己实现的（拖拽 + 换 index），没有 pager 的"预加载相邻页"，
                    // 所以每张都是"滑到才开始请求 + 解码" —— 那一两帧画面是空的，
                    // 背景又是纯黑，看起来就是"闪一下黑"（用户报"动图还是会闪"）。
                    // 提前请求一遍进内存缓存，滑过去第一帧就有图。
                    preloadNeighbors = remember(list, index) {
                        listOfNotNull(list.getOrNull(index - 1), list.getOrNull(index + 1))
                    },
                    // 零延迟的那层（见 thumbCache 的说明）
                    placeholderBitmap = thumbCache[current.storeKey()],
                    // 记下这张图实际用的解码尺寸（见 photoRequestSize 的说明）。
                    // 和 uri 一起记，换照片时旧值才不会被当成新照片的。
                    onRequestSize = { size ->
                        if (photoSizeUri != current.uri || photoRequestSize != size) {
                            photoSizeUri = current.uri
                            photoRequestSize = size
                        }
                    },
                    // 传真实比例：缩放上限、拖动边界、放大时的解码尺寸
                    // 全部按"图片实际绘制尺寸"算。
                    //
                    // 这里曾经**刻意不传**，理由是"传了边界会算错、长图被锁死"——
                    // 那个结论建立在 `drawnSizePx` 宽高写反的 bug 上，bug 修掉后
                    // 不传反而会算成保守值。现在三个调用点都传。
                    contentRatio = if (current.width > 0 && current.height > 0) {
                        current.width.toFloat() / current.height.toFloat()
                    } else {
                        null
                    },
                    // 解码尺寸按原图封顶：长图放大时不会因为请求了荒谬尺寸而糊掉
                    sourceSize = if (current.width > 0 && current.height > 0) {
                        androidx.compose.ui.unit.IntSize(current.width, current.height)
                    } else {
                        null
                    },
                    // ---- 单击：推进 chrome 三态 ----
                    //
                    // **必须交给 [detectTapsWithoutConsuming] 判**：那里已经内置
                    // "延后 280ms 等有没有第二击"的机制，双击会取消这次挂起的单击。
                    // 自己放在 `DraggablePhoto` 的"没拖过"分支里虽然能"松手当帧"响应，
                    // 但**每次双击都会先切一次 chrome 再切回来**（净效果为零，
                    // 用户仍会看到 chrome 抖一下），而且完全没有双击消歧。
                    onSingleTap = { advanceChromeStage() },
                    // 长按播放实况片段，与 iOS 相册的习惯一致
                    onLongPressStart = if (looksLikeMotion) {
                        { startMotionPlayback(current) }
                    } else {
                        null
                    },
                    onLongPressEnd = { stopMotionPlayback() },
                    onZoomedChange = { isZoomed ->
                        // 注意别把参数名写成 zoomed —— 那会遮蔽外层的 var，
                        // 变成"给只读参数赋值"，编译直接报 'val' cannot be reassigned
                        zoomed = isZoomed
                        // 放大 ⇒ 收起；缩回 1x ⇒ 强制显示（不是 toggle）。
                        chromeHidden = isZoomed
                    },
                    // 手势阶梯的第三级：已回到初始大小还继续捏合 → 「回到那天」
                    //
                    // 直接取**当前这张**的日期进视图。取不到日期（少数条目的时间戳为空）
                    // 就什么都不做 —— 好过跳到一个日期显示成 1970 的页面上。
                    onPinchToDate = { pinchScale ->
                        if (current.dateTakenMillis > 0L) {
                            // 起点 = 这张图**此刻**在屏幕上占的矩形。
                            //
                            // 两步：先按原图比例做一次 ContentScale.Fit 拿到 1x 时的矩形
                            // （和 ZoomableImage 自己的 contentScale 一致），
                            // 再**按触发那一刻的缩放缩一下** —— 照片这会儿已经被捏小了
                            // （`pinchScale` 在 0.7~1 之间），起点要是还按 1x 算，
                            // 焦点格一出现就比画面上那张大一圈，看着就是"跳了一下"。
                            val r = if (current.width > 0 && current.height > 0) {
                                current.width.toFloat() / current.height
                            } else {
                                0f
                            }
                            openDayView(
                                dayStartOf(current.dateTakenMillis),
                                from = zoomBox?.let { box ->
                                    val at1x = if (r > 0f) fitInside(box, r) else box
                                    scaledAboutCenter(at1x, pinchScale)
                                },
                                // 焦点格照用这个尺寸请求 ⇒ 命中同一份缓存。
                                // 尺寸不是这一张的就别用 —— 宁可退回格子尺寸，
                                // 也不要拿错尺寸去撞一个必然 miss 的缓存。
                                size = if (photoSizeUri == current.uri) photoRequestSize else null,
                            )
                        }
                    },
                    // ---- 捏合过程的进度：用来显示"松手进入「回到那天」" ----
                    //
                    // 没有这条提示的话，用户从 1x 往里捏，画面只是跟着缩小，
                    // 完全不知道松手会发生什么 —— 只会在页面突然跳走时愣一下。
                    // 现在捏到有进度就浮出提示，松手没够就随进度归零消失。
                    onPinchToDateProgress = { p -> pinchToDateProgress = p },
                    // ---- 双击 = 喜欢/收藏 ----
                    // 给了它就不做双击缩放（见 ZoomableImage 的 onDoubleTap 说明）：
                    // 缩放已经有捏合这条路，双击让给一个更常用的动作。
                    onDoubleTap = { current?.let { runDoubleTap(it) } },
                )

                // 播放视图**常驻挂载**，只用透明度控制显隐。
                // 之前写成"播的时候才挂上"，每次长按都要新建/销毁一个视图 ——
                // 那一瞬间的黑场正是"长按播放会闪一下"的来源。
                //
                // 尺寸也和照片对齐：小米的实况照片里嵌的视频比静态图多一条底部白条
                // （厂商水印），按整屏 Fit 会让画面整体缩小、和照片对不齐。
                // 这里把它限制在**照片自身的画面区域**内居中，位置就不会跳。
                val photoRenderedHeight = if (current.width > 0 && current.height > 0) {
                    minOf(maxHeight, maxWidth * (current.height.toFloat() / current.width))
                } else {
                    maxHeight
                }
                AndroidView(
                    factory = { ctx -> createVideoPlayerView(ctx) },
                    update = { view -> view.player = motionPlayer },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(photoRenderedHeight)
                        // 显隐必须**两个钥匙对上**才放开：新片段首帧还没渲染时，
                        // TextureView 里留着的是上一段实况的最后一帧 ——
                        // 直接 alpha=1 就会闪出上一张照片（用户报的那个 bug）。
                        .graphicsLayer {
                            alpha = if (motionPlaying && motionReadyKey == motionPlayKey) 1f else 0f
                        },
                )

                // ---- 屏幕中间的"松手会发生什么"预告 ----
            //
            // 删除 / 收藏 / 取消收藏，以及捏合向「回到那天」的两级文案，
            // 都在这里 —— 位置一致，用户不用在屏幕不同地方找提示。
            //
            // 传 `Animatable` **对象**而不是 `.value`：调用点如果在主体作用域
            // 读 `.value`，会把整棵子树登记成观察者、拖动的每一帧整屏重组
            // （这是之前"滑动卡顿"最大的一笔，见函数注释）。
            // 双击反馈：和整理视频页**同一个**组件（只有一颗图标，没有文字）。
            dtFlash?.let { flash ->
                DoubleTapFlashOverlay(
                    flash = flash,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            DecisionOverlay(
                offsetX = offsetX,
                offsetY = offsetY,
                vLimitPx = vLimitPx,
                hLimitPx = hLimitPx,
                upDecision = upDecision,
                alreadyFavorite = currentIsFavorite,
                pinchToDateProgress = pinchToDateProgress,
                zoomed = zoomed,
                // 进「回到那天」时提示要跟着 chrome 一起淡掉。
                // 不淡的话：`pinchToDateProgress` 在进入后不再更新、一直停在 1，
                // 那颗"继续捏合进入回到那天"的胶囊就**一直挂在屏幕上**，
                // 直到被日期视图完全盖住 —— 中间飞行的焦点图会从它上面划过去，
                // 看起来就是"胶囊被飞行的图片盖住了"（用户的原话）。
                // 传 State 而不是值：读在 `graphicsLayer` 里，
                // 别让这 260ms 的动画把整棵子树每帧重组一遍。
                chromeFade = chromeAlpha,
            )

            // 类型角标。贴在**图片的左上角**（不是屏幕的左上角）——
                // 之前固定贴在屏幕左上角，正好和退出按钮叠在一起，被完全挡住。
                // 留白不足时必须让位给顶栏，否则换个方向还是会被压住。
                //
                // ---- 长图也给一颗角标（用户要求）----
                // 长截图 / 聊天记录放进流里就是"一根细条"，不知道是什么内容；
                // 有角标至少能看出"这是张长图、要放大读"。
                // 优先级放最后：实况 / 动图 / 截屏 的信息量都比"长"更具体。
                val badge = when {
                    looksLikeMotion -> "实况"
                    current.isAnimated -> "动图"
                    current.kind == MediaKind.SCREENSHOT -> "截屏"
                    current.isLongImage -> "长图"
                    else -> null
                }
                if (badge != null) {
                    val renderedHeight = if (current.width > 0 && current.height > 0) {
                        minOf(maxHeight, maxWidth * (current.height.toFloat() / current.width))
                    } else {
                        maxHeight
                    }
                    val imageTop = (maxHeight - renderedHeight) / 2f
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = 16.dp,
                                // 92dp ≈ 状态栏 + 顶栏的高度，再往下 12dp 是角标自身的内缩
                                top = (imageTop + 12.dp).coerceAtLeast(92.dp),
                            )
                            .graphicsLayer { alpha = chromeAlpha.value }
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }

        // ---- 顶栏 ----
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeAlpha.value }
                .background(
                    Brush.verticalGradient(listOf(ScrimTop, Color.Transparent))
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左右两格都用**同一个固定宽度**（`badgeSlotWidth`），计数独占中间
            // 那一份。之前是两个 `weight(1f)` —— 那只保证"扣掉两侧内容后的剩余空间"
            // 居中，而左边关闭按钮 48dp、右边标记宽度随数字变（25~32dp），
            // 两边不等宽 ⇒ 计数**必然偏右**（用户反馈）。
            // 右侧有没有标记都不影响：那一格宽度是固定的。
            Box(
                modifier = Modifier.width(BADGE_SLOT_WIDTH),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.Filled.Close, contentDescription = "退出", tint = TextPrimary)
                }
            }
            Text(
                text = "${(index + 1).coerceAtMost(list.size)} / ${list.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.width(BADGE_SLOT_WIDTH),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (marked.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = AccentDelete,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = marked.size.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentDelete,
                        )
                    }
                }
            }
        }

        // ---- 底部：信息 + 胶片条 + 提示 ----
        val currentForInfo = list.getOrNull(index)
        if (currentForInfo != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha.value }
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, ScrimBottom))
                    )
                    .navigationBarsPadding()
                    .padding(top = 30.dp, bottom = 12.dp),
            ) {
                // ---- 收藏按钮 + 信息胶囊 ----
                // 借鉴「去留」：收藏从"下滑手势"改成左侧这个小按钮。
                // 一个方向的手势应该留给不可逆或高频的动作 —— 上滑删除、左右翻页都值，
                // 而收藏一秒就能撤销，占掉"下滑"的代价太大，还会跟翻页抢手感。
                // 日期和地点收进中间那个带 ⓘ 的胶囊，点开才展开完整信息，不打扰看照片。
                // ---- 按钮与胶囊**同一行**（用户要求放回去）----
                //
                // 上一版为了修"点收藏没反应"把胶囊挪到了单独一行，那是对的诊断
                // 但过重的解法：真正的问题是**胶囊不知道自己有多宽**（写死了 230dp
                // 文字区 + 50dp 图标内边距 = 280dp，比两颗按钮之间剩下的还宽）。
                //
                // 现在胶囊自己不设上限，由这里给它一块**确定不会被按钮占用**的空间：
                // 外层再套一层 `padding(horizontal = 52.dp)`（按钮 44 + 8 间隙），
                // 它拿到的最大宽度就是屏幕宽减去两侧 —— 结构上不可能压住按钮。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 左下角：喜欢（心形）。只记在本应用里，绝不写进系统相册。
                    FavoriteButton(
                        selected = currentIsLiked,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        onToggleLike(currentForInfo)
                        favoriteTick++
                    }

                    // 右下角：收藏（星形）。这一项会同步进系统相册的收藏夹。
                    StarButton(
                        selected = currentIsFavorite,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        onToggleFavorite(currentForInfo)
                        favoriteTick++
                    }

                    // 中间的日期 / 地点胶囊：可用宽度 = 两侧按钮之外的那块。
                    InfoPill(
                        primary = infoPillPrimary(currentForInfo, relativeDates, absoluteDates),
                        secondary = infoPillSecondary(currentForInfo, relativeDates, absoluteDates),
                        location = currentLocation,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 52.dp),
                        onClick = { detailVisible = true },
                    )
                }

                // 「已标记 N 项 · xx MB / 结束并处理」那一条底部横幅已经去掉。
                // 标记数量在顶栏已经有（删除图标 + 数字），
                // 而"结束并处理"这个动作左上角的关闭按钮就会做 ——
                // 横幅既重复又占掉照片一大块空间。

                Spacer(Modifier.height(14.dp))

                ThumbnailFilmstrip(
                    items = list,
                    currentIndex = index,
                    visible = filmstripVisible,
                    motionKeys = motionKeys,
                    onSelect = { target ->
                        if (target != index) {
                            index = target
                            filmstripVisible = true
                        }
                    },
                )

                if (!filmstripVisible) {
                    Text(
                        text = buildString {
                            append("单击展开缩略图")
                            append(" · 双指放大")
                            append(" · 缩回后捏合回到那天")
                            if (looksLikeMotion) append(" · 长按看实况")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        motionHint?.let { hint ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }
    }

    // ---- 完整信息面板 ----
    if (detailVisible) {
        val detailItem = list.getOrNull(index)
        if (detailItem != null) {
            ModalBottomSheet(
                onDismissRequest = { detailVisible = false },
                containerColor = Surface2,
                scrimColor = Color(0x99000000),
            ) {
                Box(modifier = Modifier.padding(horizontal = 22.dp)) {
                    PhotoDetailContent(
                        item = detailItem,
                        exif = detailExif,
                        locationName = currentLocation,
                    )
                }
            }
        }
    }

    // ---- 回到那天 ----
    // 整屏日期视图。它盖在整理界面之上 —— 看完按 ✓ 才会把这一天交给整理，
    // 按 ← 则原样回到刚才那张，什么都没变。
    if (dayViewOpen) {
        DayViewScreen(
            items = dayViewItems,
            loading = daySwitching,
            notice = dayNotice,
            kindLabel = sessionKind.label,
            dayStart = dayTarget,
            // 捏合进来的那张。日期视图打开时列表页会停在这一张，
            // 但为防它已经滑走，这里现取一次当前索引，保证"跳到的就是正看着的这张"。
            focusItem = list.getOrNull(index),
            // 时间格式透传给放大页 —— 1.3.73 加了这个参数却忘了在调用处传，
            // 结果放大页的胶囊一直用默认值（永远显示相对时间），不跟随设置。
            relativeDates = relativeDates,
            absoluteDates = absoluteDates,
            resolveLocation = resolveLocation,
            // 这一组里已经标记删除的，在瀑布流里就不再出现。
            markedKeys = marked.map { it.storeKey() }.toSet(),
            // 上滑固定为删除，和这一页的单张手势同一条规则。
            swipeUpDeletes = swipeUpDeletes,
            isFavorite = isFavorite,
            // 放大页那两颗按钮要读 / 写同一个「喜欢」状态，跟整理页共用一套。
            isLiked = isLiked,
            onToggleLike = { target ->
                onToggleLike(target)
                favoriteTick++
            },
            // 飞入动效的起点：那一张图当时在屏幕上占的矩形。
            // null = 拿不到（极少数情况）⇒ 日期视图那边不做动效，直接显示。
            introFrom = dayIntroFrom,
            introImageSize = dayIntroSize,
            onBack = { dayViewOpen = false },
            onConfirm = { confirmDay() },
            // 在日期视图里上滑删除 → 直接进这一组的删除名单。
            onMarkDelete = { target ->
                if (marked.none { it.storeKey() == target.storeKey() }) {
                    marked = marked + target
                }
            },
            // 在日期视图里上滑收藏 → 和单张手势走同一个 applyFavorite。
            // **当前不会触发**：上滑已固定为删除（`upDecision` 恒为 DELETE），
            // 这条留着是为了不改回调结构。
            onMarkFavorite = { target -> applyFavorite(target) },
            // ↩ 撤销：把最后标记的那一项从删除名单里摘掉。
            // 用 lastOrNull 而不是弹栈 —— 名单本身是"集合"，顺序不重要，
            // 用户要的只是"刚才那一步反悔"。
            onUndoDelete = {
                marked.lastOrNull()?.let { last ->
                    marked = marked.filterNot { it.storeKey() == last.storeKey() }
                }
            },
            canUndo = marked.isNotEmpty(),
        )
    }
}

/**
 * 一层照片：**满屏 + 不透明底**。
 *
 * 照片用 `ContentScale.Fit` 完整显示（不裁切用户自己的内容），非本机比例的图
 * 会出现留边——横图上下留黑，竖图左右留黑。如果这一层背景是透明的，
 * 下层那张更细长的图就会从留边处透出来，看起来像"还没轮到就先显示了"。
 *
 * 缩放**不**在这一层做：翻页的接缝是外层整个盒子缩出来的（见上面 `pageGapPx` 的说明），
 * 这里只管铺满。
 */
@Composable
private fun PhotoLayer(item: MediaItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 顶层那张可以拖拽的照片。
 *
 * 用 graphicsLayer 做位移而不是 layout offset：拖拽过程中不需要重新布局，
 * 只走绘制阶段，几万张照片的列表里这个差异很明显。
 *
 * 内容由调用方注入（[content]），这样缩放和实况播放都能挂在这一层里面 ——
 * 它们作为子节点会先拿到指针事件，从而在需要时"吃掉"手势，外层就知道该让路了。
 */
@Composable
private fun DraggablePhoto(
    item: MediaItem,
    offsetX: Animatable<Float, AnimationVector1D>,
    offsetY: Animatable<Float, AnimationVector1D>,
    /** 收藏的"被收走"进度：0 = 原样，1 = 缩到 0.85 并淡出。删除/翻页不走这条。 */
    dismissProgress: Animatable<Float, AnimationVector1D>,
    vLimitPx: Float,
    hLimitPx: Float,
    upDecision: SwipeDecision,
    onDecision: (SwipeDecision) -> Unit,
    /**
     * 手势轴定死那一刻报一次方向：+1 = 往右拖，-1 = 往左拖，0 = 不是水平拖。
     *
     * 底下那张"下一张"要靠它决定**从哪一侧进来**（真正的翻页效果）。
     */
    onDragDir: (Int) -> Unit = {},
    /**
     * 照片是不是正被放大。
     *
     * **放大态这一层完全不参与单指手势**，全部让给 `ZoomableImage` ——
     * 否则长图放大后就拖不动了（用户反馈）。原因是层级：这一层是**父节点**，
     * 单指拖拽在 Main 阶段父节点先拿到事件并 `consume()`，
     * 子节点（真正实现"看局部"的 `ZoomableImage`）根本收不到。
     *
     * 放弃之后不需要"还回来"的机制：`ZoomableImage` 一次手势内不会越权，
     * 缩回 1x 之后下一次手势这一层照常接管（和收藏页预览的
     * `HorizontalPager` + `ZoomableImage` 是同一套分工）。
     */
    zoomed: Boolean = false,
    /**
     * 这一次手势**没有拖过**（就是点了一下）。
     *
     * 默认什么都不做 —— 整理页的单击交给 `ZoomableImage.onSingleTap`，
     * 它会和双击判定共存；哪一页不需要双击、只要"松手当帧响应"时才用这个。
     */
    onTap: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // PointerInputScope 只继承 Density，并不是 CoroutineScope，
    // 所以 pointerInput 内部拿不到 launch —— 必须自己带一个进来。
    val scope = rememberCoroutineScope()

    // ★ `zoomed` 必须用 state 转发，不能直读。
    //
    // 下面那个 `pointerInput(item.storeKey())` 的闭包**只在换照片时重启**，
    // 直读参数拿到的是**首次组合时**的值（永远是 false）⇒
    // "放大态放行单指"那段代码从来没执行过，长图放大后照样拖不动
    // （用户实测"还是不能上下拖"）。同理 `onTap` 也要转发，
    // 否则调用点之后改了什么，这里都还在用旧值。
    val zoomedState = rememberUpdatedState(zoomed)
    val tapState = rememberUpdatedState(onTap)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // ---- 这里**刻意不 rotation** ----
                // 以前横移时照片会跟着歪最多 8°，用户说"随心所欲的自由滑动，
                // 建议改成回收站里那种，左右滑动完全按照轨迹来"（1.3.45）。
                // 回收站那边的预览走的是 HorizontalPager，pager 从不转图 ——
                // 手指往哪走，画面就严格平移到哪，一比一，不加任何自己的花样。
                translationX = offsetX.value
                translationY = offsetY.value
                // 收藏的收走动线：原地缩到 0.85 并淡出。
                // 删除仍然是飞出屏幕（扔掉）—— 两种语义两条动线，
                // 不再用"上滑触发却往下飞"那种和手势方向打架的动画。
                val p = dismissProgress.value
                val s = 1f - 0.15f * p
                scaleX = s
                scaleY = s
                alpha = 1f - p
            }
            .pointerInput(item.storeKey()) {
                val viewWidthPx = size.width.toFloat()
                val viewHeightPx = size.height.toFloat()
                // 自己实现拖拽而不是用 detectDragGestures，就为了拿到**多指**信息：
                // detectDragGestures 一旦锁定第一根手指就一路拖到底，
                // 第二根手指落下来它也不松手 —— 于是双指捏合时照片在左右晃，
                // 捏合的量被"平移"吃掉，捏到 1x 也捏不出「回到那天」。
                // （用户反馈"进入回到那天的手势有点难触发，经常被弄成左右上下滑动"，
                //   根因就是这个：不是阈值太高，是外层把手势抢了。）
                //
                // 关键规则：**第二根手指一落下就立刻放弃这次拖拽并回弹**。
                // 放弃之后事件不再被消费，捏合就完整地归 ZoomableImage ——
                // 层级上它本来就是子节点，只是以前被父节点压着抢不到。
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // 放大态：这一层**一个事件都不消费**，单指拖动整个让给
                    // `ZoomableImage`（它才是"放大后拖动看细节"的那个）。
                    // 第二根手指照旧走下面的 multiTouch 分支去捏合。
                    if (zoomedState.value) {
                        val pointerId = down.id
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.none { it.pressed } ||
                                ev.changes.none { it.id == pointerId && it.pressed }
                            ) {
                                break
                            }
                        }
                        return@awaitEachGesture
                    }
                    val startX = down.position.x
                    val startY = down.position.y
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // 本次手势内**自己**累加的位移，snapTo 写的是**绝对值**。
                    //
                    // 以前是 `offsetX.snapTo(offsetX.value + drag.x)` ——
                    // 读的是挂起写出来的当前值，而每帧都要 `scope.launch` 一次；
                    // 一旦后一次把前一次取消掉，那一段增量就丢了，
                    // 画面表现就是"拖一点点顿一下"（用户反馈）。
                    // 记在本地、每次写绝对值，天然不会丢。
                    // ★ 起点必须是**当前实际位移**，不能是 0。
                    //
                    // 上一段翻页动画（settleToPage）可能还在跑，此刻 `offsetX.value`
                    // 已经是 -800。如果本地从 0 开始累加，第一次 `snapTo(0 + 10)`
                    // 会把照片从 -800 **瞬间拽回 -10** —— 观感是"照片跳了一下"。
                    // 取当前值做基准，新手势就能从"照片现在在哪"接着走。
                    var dragX = offsetX.value
                    var dragY = offsetY.value
                    // 一次手势只认一个轴：0 = 还没定，'x' / 'y' = 定死。
                    // 用户要"严格水平或垂直，不要自由滑动"，收藏页/喜欢页那边
                    // 走的是 HorizontalPager，斜着划也不会走成斜线。
                    var axis = ' '
                    var dragging = false
                    var multiTouch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        if (pressed.size >= 2) {
                            if (!multiTouch) {
                                multiTouch = true
                                if (dragging) {
                                    // 抢到一半的位移不能留着：照片歪着停在那儿最难看。
                                    // 回弹用动画，看起来像"让位给捏合"。
                                    scope.launch {
                                        coroutineScope {
                                            launch {
                                                offsetX.animateTo(
                                                    0f,
                                                    spring(stiffness = Spring.StiffnessMediumLow),
                                                )
                                            }
                                            launch {
                                                offsetY.animateTo(
                                                    0f,
                                                    spring(stiffness = Spring.StiffnessMediumLow),
                                                )
                                            }
                                        }
                                    }
                                }
                                dragging = false
                            }
                            // **不消费**任何事件：这一串全交给子节点的捏合。
                            // 在这里 consume 一下只会把 ZoomableImage 也挡掉。
                            continue
                        }

                        val change = pressed.first()
                        if (!dragging && !multiTouch) {
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) {
                                dragging = true
                                // ---- 越过阈值那一刻按主方向把轴定死 ----
                                // 整串手势都只能往这一个方向走，斜着划也不会走成斜线。
                                axis = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 'x' else 'y'
                                if (axis == 'x') onDragDir(if (dx > 0f) 1 else -1)
                            }
                        }
                        if (dragging) {
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val drag = change.positionChange()
                            change.consume()
                            if (axis == 'x') dragX += drag.x else dragY += drag.y
                            scope.launch {
                                // 被锁住的那一轴恒为 0。
                                offsetX.snapTo(if (axis == 'x') dragX else 0f)
                                offsetY.snapTo(if (axis == 'y') dragY else 0f)
                            }
                        }
                    }

                    if (!dragging || multiTouch) {
                        // 没真正拖过（只是点了一下）或中途来了第二根手指：
                        // 保证位移归零，不留下半截位移。
                        if (offsetX.value != 0f || offsetY.value != 0f) {
                            if (multiTouch) {
                                // 已经在上面启动过回弹动画了，别重复。
                            } else {
                                scope.launch {
                                    coroutineScope {
                                        launch {
                                            offsetX.animateTo(
                                                0f,
                                                spring(stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                        launch {
                                            offsetY.animateTo(
                                                0f,
                                                spring(stiffness = Spring.StiffnessMediumLow),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // ---- 干净的一下（没拖过、也没第二根手指）⇒ 单击 ----
                        //
                        // 整理页的单击**不走这里** —— 它交给
                        // `ZoomableImage.onSingleTap`，因为那边要和双击判定共存
                        // （延后 280ms、双击时取消单击）。
                        // 在这里"松手当帧"响应虽然更跟手，但每次双击都会
                        // 先切一次 chrome 再切回来，视觉上还是抖一下。
                        // 这个回调留给不需要双击的调用方。
                        if (!multiTouch) tapState.value.invoke()
                        return@awaitEachGesture
                    }

                    scope.launch {
                        val x = offsetX.value
                        val y = offsetY.value
                        val v = tracker.calculateVelocity()
                        // ---- 判定看"距离 **或** 速度" ----
                        //
                        // 只看距离的话，快速一甩（手指没走多远就松手）会被判成
                        // "没到阈值" ⇒ 每次都弹回去，回收站那边的 pager 也是这么判的：
                        // 位移过阈值，或者甩得够快，都算数。
                        // 一次手势只有一个轴有值，所以判定**只看那一个轴** ——
                        // 既不会"斜着划同时触发翻页和删除"，也不用再比大小猜方向。
                        val decision = when {
                            axis == 'x' -> {
                                // 左右滑都是"下一张"。双向翻页在随机流里没有意义，
                                // 而且误触回退会打断浏览节奏。
                                val far = kotlin.math.abs(x) >= hLimitPx
                                val fling = kotlin.math.abs(v.x) >= SWIPE_FLING_VELOCITY
                                if (far || fling) SwipeDecision.NEXT else SwipeDecision.NONE
                            }
                            // 下滑不再触发任何动作 —— 收藏已经移到左下角的小按钮上。
                            axis == 'y' -> {
                                if (y <= -vLimitPx ||
                                    (v.y <= -SWIPE_FLING_VELOCITY && y < 0f)
                                ) {
                                    upDecision
                                } else {
                                    SwipeDecision.NONE
                                }
                            }
                            else -> SwipeDecision.NONE
                        }

                        if (decision == SwipeDecision.NONE) {
                            coroutineScope {
                                launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            }
                        } else if (decision == SwipeDecision.FAVORITE) {
                            // 收藏 = "被收进夹子"：先轻轻回正，再原地缩放淡出。
                            // 位移和淡出一起跑，总时长约 300ms ——
                            // 用户要能看清"这张被收走了"，而不是被甩出屏幕。
                            coroutineScope {
                                launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                launch {
                                    offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                }
                                launch {
                                    dismissProgress.animateTo(
                                        1f,
                                        tween(durationMillis = 260, easing = FastOutSlowInEasing),
                                    )
                                }
                            }
                        } else {
                            val targetX = when (decision) {
                                // ---- 正好一屏，不多不少 ----
                                // 底下那张"下一张"是按 `位移 - 方向 × 屏宽` 摆的，
                                // 它俩同速移动：当前这张正好走完一屏时，下一张正好归位。
                                // 原来甩 1.3 屏，下一张会被带过头 0.3 屏，交接时错位。
                                SwipeDecision.NEXT ->
                                    if (x < 0f) -viewWidthPx else viewWidthPx
                                else -> x + x / 5f
                            }
                            val targetY = when (decision) {
                                SwipeDecision.DELETE -> -viewHeightPx * 1.2f
                                else -> y
                            }
                            coroutineScope {
                                // 翻页走 pager 同款 spring（速度连续），删除仍用干脆的 tween。
                                // 两者的区别是**语义**：翻页要接住手指的速度，
                                // 删除是"扔掉"，本来就该立刻断掉。
                                if (decision == SwipeDecision.NEXT) {
                                    launch { offsetX.settleToPage(targetX) }
                                    launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                } else {
                                    launch { offsetX.flingTo(targetX) }
                                    launch { offsetY.flingTo(targetY) }
                                }
                            }
                            // `coroutineScope` 要等两个 launch 都跑完才返回，
                            // 所以这里换页**天然是等出场动画走完之后**：
                            // 此刻底下的下一张刚好归位且完全不透明，
                            // 换过去是同一张画面、同一位置 ⇒ 看不出来换过。
                            onDecision(decision)
                        }
                    }
                }
            },
    ) {
        content()
    }
}

/**
 * 拖拽方向提示。
 *
 * 判定依据是位移的主轴：竖直位移更大就按上/下解释，否则按左/右解释。
 * 这样斜着划也不会两个提示同时亮。
 *
 * ---- 刻意收 `Animatable` 对象而不是 `Float` ----
 * 之前签名是 `(offsetX: Float, offsetY: Float)`，而调用点写的是
 * `offsetX = offsetX.value` —— 那一行位于 `if (current != null)` 的**主体作用域**，
 * 读 `.value` 会把整棵子树（DraggablePhoto + ZoomableImage + AndroidView + 角标）
 * 登记成观察者。拖动的每一帧 `snapTo` 都写它 ⇒ **每帧整屏重组**。
 *
 * `DraggablePhoto` 自己把位移读在 `graphicsLayer { }` 里的写法是对的，
 * 却被这个调用点彻底抵消 —— 这是"滑动卡顿"最大的一笔。
 *
 * 现在传对象进来，两个高频量都退到绘制阶段：
 *  · `progress`（每帧变）→ `graphicsLayer { alpha = … }`
 *  · `decision`（一次手势最多翻转两次）→ `derivedStateOf`，只有它变才重组
 */
@Composable
private fun DecisionOverlay(
    offsetX: Animatable<Float, AnimationVector1D>,
    offsetY: Animatable<Float, AnimationVector1D>,
    vLimitPx: Float,
    hLimitPx: Float,
    upDecision: SwipeDecision,
    alreadyFavorite: Boolean,
    /**
     * 捏合向「回到那天」推进的进度（0..1）。0 = 没在捏。
     *
     * >0 时**优先**显示"回到那天"提示，不再显示删除/收藏 ——
     * 捏合的时候不会同时在上滑，两者互斥。
     * 放在中间（而不是顶部）是和删除/收藏同一个位置：
     * 都是"松手会发生什么"的预告，位置一致才不用用户重新找。
     */
    pinchToDateProgress: Float,
    /** 放大态。此时单指拖动归 `ZoomableImage`，滑动手势不会触发任何动作。 */
    zoomed: Boolean,
    /**
     * chrome 的淡出系数（见调用处的说明）。
     *
     * 传 `State` 而不是 `Float`：它要读在 `graphicsLayer { }` 里，
     * 否则这 260ms 的动画会把整棵子树每帧重组一遍。
     */
    chromeFade: State<Float>,
) {
    // 派生量：`(要不要显示, 不透明度)`。
    // 只有"显示什么"这一位变化时才通知观察者（一次手势最多两次），
    // 进度那一半走Pair 的第二项，在 `graphicsLayer` 里读，不引起重组。
    val shown by remember(offsetX, offsetY, upDecision, vLimitPx, pinchToDateProgress, zoomed) {
        derivedStateOf {
            // ---- 放大态一律不显示 ----
            // 放大后单指拖动被 ZoomableImage 接管看局部，上滑手势**不会触发**。
            // 这时候弹"删除"是误导（用户报：放大后还有上滑删除标志）。
            // 捏合提示也同理 —— 放大态里捏合是缩放，不是回到那天。
            if (zoomed) return@derivedStateOf null to 0f

            // ---- 捏合向「回到那天」优先 ----
            if (pinchToDateProgress > 0.02f) {
                return@derivedStateOf SwipeDecision.NONE to
                    pinchToDateProgress.coerceIn(0f, 1f)
            }

            val x = offsetX.value
            val y = offsetY.value
            when {
                // 下滑不再触发任何动作（收藏已移到左下角的按钮）
                abs(y) >= abs(x) && y < 0f && (-y / vLimitPx) > 0.03f ->
                    upDecision to (-y / vLimitPx).coerceIn(0f, 1f)

                // 左右滑（下一张）**刻意不给提示**。
                // 翻页是个不需要解释的动作 —— 每次滑动都在画面中间弹一个"下一张"的标签，
                // 只会让画面变脏。上滑删除才是需要提示的那个：它不可逆，
                // 得让人在松手前就知道会发生什么。
                else -> SwipeDecision.NONE to 0f
            }
        }
    }
    val decision = shown.first ?: return

    // ---- 提示：**至少显示一整个动效时长** ----
    //
    // 原来 alpha 直接跟捏合进度走。可捏合是"一捏就到底"的短动作
    // （尤其熟练之后捏得飞快），提示刚浮现就跟着归零 ——
    // 用户的原话是"一闪而过，看不清"，并且要求"和飞行动画一样时长"。
    //
    // 所以不再让提示跟着捏合进度走，改成：**只要捏过就一直显示，
    // 松手之后再多留一整个 [DAY_INTRO_MS]**。这样无论捏得多快，
    // 那句提示都有完整的 560ms 可读 —— 和飞行动效同一个时长。
    var hintVisible by remember { mutableStateOf(false) }
    val hintOn = shown.second > 0.01f
    LaunchedEffect(hintOn) {
        if (hintOn) {
            hintVisible = true
        } else {
            // 松手之后的这段"余量"就是白送给用户的阅读时间。
            // 注意这个 effect 的键是 hintOn：又捏起来时会重启，
            // 于是立刻回到"显示"，不会有延迟。
            delay(DAY_INTRO_MS.toLong())
            hintVisible = false
        }
    }
    val hintAlpha by animateFloatAsState(
        targetValue = if (hintVisible) 1f else 0f,
        // 进得快（不挡手）、出得慢（读得完）
        animationSpec = tween(durationMillis = if (hintVisible) 160 else DAY_INTRO_MS),
        label = "pinchHintAlpha",
    )
    // 捏合提示没有"decision"，用这个标记区分（NONE 已经被"不显示"占用了）。
    val isPinchHint = pinchToDateProgress > 0.02f && !zoomed

    // ---- NONE 且不是捏合提示 ⇒ **整个不画** ----
    //
    // 这就是用户报的"很小的方形块"：原来这里只让 label 变成空字符串，
    // 那个 `Row` 照样画出来 —— 底色 Surface3、带 22×14 的内边距、
    // 里面既没图标也没字，于是屏幕上留下一个**深灰色的小方块**。
    //
    // 出现的时机也和用户描述对得上：
    //  · 上滑删除松手回弹 → decision 回到 NONE → 方块露出（然后整体淡出）；
    //  · 捏合松手没够 → pinchToDateProgress 归零 → 同样。
    // 平时它被"删除"/"继续捏合"那颗有内容的胶囊盖着，所以"不确定什么时候出现"。
    if (decision == SwipeDecision.NONE && !isPinchHint) return

    val accent = when (decision) {
        SwipeDecision.DELETE -> AccentDelete
        SwipeDecision.FAVORITE -> AccentKeep
        else -> if (isPinchHint) AccentKeep else Surface3
    }
    val label = when {
        // 只有一句"继续捏合进入「回到那天」"。
        //
        // 用户要求删掉"松手进入"那一级：进过「回到那天」再退出来时，
        // 这个提示会**留在屏幕上**（进度没归零），而"松手进入"听起来
        // 像是已经进入成功了 —— 观感很差。
        //
        // 根本原因是 `pinchToDateProgress` 在退出后没被清零：
        // 它由手势回调写，而退到日期视图时照片页的手势已经结束，
        // 没有人再写 0。只保留"继续捏合"就规避了这个问题 ——
        // 那句话在"没在捏"的时候看起来也只是一句引导，不会误以为已进入。
        isPinchHint -> "继续捏合进入「回到那天」"
        decision == SwipeDecision.DELETE -> "删除"
        decision == SwipeDecision.FAVORITE -> if (alreadyFavorite) "取消收藏" else "收藏"
        else -> ""
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .graphicsLayer { alpha = hintAlpha * chromeFade.value }
                .clip(DecisionChipShape)
                .background(accent)
                .padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            if (isPinchHint) {
                // 捏合是"往里收"，用向下箭头 —— 和顶部那条上滑提示的向上箭头反向。
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                when (decision) {
                SwipeDecision.DELETE -> Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )

                SwipeDecision.FAVORITE -> Icon(
                    imageVector = if (alreadyFavorite) Icons.Filled.FavoriteBorder else Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(20.dp),
                )

                else -> Unit
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * 拖拽提示胶囊的圆角 —— **顶层单例，别在组合期现算**。
 *
 * `RoundedCornerShape` 的 `equals` 比的是 `percent`/`cornerSize`，
 * 所以值相同的两个新实例不会导致裁剪路径重建；
 * 但每次重组都 `new` 一个出来是纯浪费（GC 压力），
 * 而这个组件在拖动的每一帧都会被读到。提到顶层就没有这笔开销。
 */
private val DecisionChipShape = RoundedCornerShape(16.dp)

/**
 * 实况探测的窗口半径：只探"当前张 ± 这个数"。
 *
 * 25 张约等于**前后各三屏**（胶片条一屏约 8 张），覆盖手指能滑到的范围。
 * 探过的走 `motionProbe` 缓存，不会重复读文件。
 */
private const val FILMSTRIP_PROBE_RADIUS = 25

/**
 * 滑动"甩出去"的门槛（px/s）。
 *
 * 位移阈值之外再加一条速度线：距离够**或**甩得够快都算成立
 * （回收站那边的 HorizontalPager 就是这么判的）。只看距离的话，
 * 熟练之后的快速一甩——手指在屏幕上其实没走多远——会被判成"没到阈值"，
 * 表现就是"每次都要划一大段才翻页"。
 */
private const val SWIPE_FLING_VELOCITY = 1100f

/**
 * **纵向**（上滑删除 / 收藏）时，垫底那张淡入到完全不透明需要拖多远，
 * 占竖直可滑距离的比例。
 *
 * 横向已经改成真正的翻页（从旁边一整屏滑进来），不再用这个阈值。
 * 纵向不是翻页 —— 删除过程中只是"让照片走开、底下露出来"，
 * 阈值太小就会变成"手指一动下一张整张糊上来"（用户报的就是这个）。
 * 1.2 意味着要划过整个判定行程才完全显形，中途是一条平缓的渐亮。
 */
private const val BEHIND_FADE_FRACTION = 1.2f

/**
 * 顶栏左右两格的固定宽度。
 *
 * 取 56dp：装得下 48dp 的关闭按钮（点按区不能缩），也装得下"图标 + 数字"。
 * 两格等宽 ⇒ 中间的计数才真的是几何居中。
 */
private val BADGE_SLOT_WIDTH = 56.dp
