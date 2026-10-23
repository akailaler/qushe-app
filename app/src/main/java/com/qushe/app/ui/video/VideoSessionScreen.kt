package com.qushe.app.ui.video

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.SessionResult
import com.qushe.app.core.formatDuration
import com.qushe.app.core.formatSize
import com.qushe.app.core.relativeDayLabel
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.FullscreenIcon
import com.qushe.app.ui.common.FullscreenPill
import com.qushe.app.ui.common.ScreenLevelVideoLayer
import com.qushe.app.ui.common.StarBorderIcon
import com.qushe.app.ui.common.applyFullscreenOrientation
import com.qushe.app.ui.common.findActivity
import com.qushe.app.ui.common.VolumeOffIcon
import com.qushe.app.ui.common.VolumeUpIcon
import com.qushe.app.ui.common.DoubleTapFlash
import com.qushe.app.ui.common.DoubleTapFlashOverlay
import com.qushe.app.ui.common.detectTapsWithoutConsuming
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import com.qushe.app.ui.theme.OverlaySoft
import kotlinx.coroutines.delay
import com.qushe.app.ui.common.RailAction
import com.qushe.app.ui.common.VideoProgressBar

/**
 * 视频整理：抖音式上下滑。
 *
 * 手势分层的核心决定 ——
 * 上下滑已经被"切换视频"占满了，所以删除和收藏**不能再押在滑动上**。
 * 这里的解法是把手势分成两层：
 *   导航（换视频）  -> 滑动
 *   决策（收藏）    -> 双击
 *   临时动作（快进）-> 长按
 *   危险动作（删除）-> 侧边按钮（危险操作本来就不该是手势，误触代价太高）
 *
 * 这个分工是抄「去留」验证过的方案，也是本页之所以能同时装下五六个功能的唯一原因。
 */
@Composable
fun VideoSessionScreen(
    items: List<MediaItem>,
    deleteToTrash: Boolean,
    videoMutedByDefault: Boolean,
    autoPlay: Boolean,
    railOnRight: Boolean,
    /**
     * 双击的含义：true = 喜欢（默认），false = 收藏。
     *
     * 两个动作的区别对用户是可见的：喜欢只记在本应用里，收藏会同步进系统相册。
     * 所以双击之后必须把结果**说出来**（见 [actionFlash]）——
     * 之前双击收藏了却什么都不显示，用户只能靠侧栏图标变化去猜。
     */
    doubleTapLikes: Boolean,
    isFavorite: (MediaItem) -> Boolean,
    onToggleFavorite: (MediaItem) -> Boolean,
    isLiked: (MediaItem) -> Boolean,
    onToggleLike: (MediaItem) -> Boolean,
    onSeen: (MediaItem) -> Unit,
    buildDeleteRequest: (List<MediaItem>) -> IntentSender?,
    resolveLocation: suspend (MediaItem) -> String?,
    shareItem: (MediaItem) -> Unit,
    onConclude: (SessionResult) -> Unit,
) {
    val context = LocalContext.current

    var index by remember { mutableIntStateOf(0) }
    var marked by remember { mutableStateOf(emptyList<MediaItem>()) }
    var favAdded by remember { mutableStateOf(emptyList<MediaItem>()) }
    var favRemoved by remember { mutableStateOf(emptyList<MediaItem>()) }
    var pendingResult by remember { mutableStateOf<SessionResult?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(videoMutedByDefault) }
    var speedUp by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    // ---- 双击反馈 ----
    //
    // 双击是个"盲操作"：手指落下时没有任何提示，做完之后侧栏那个图标的变化也不一定
    // 落在视线里。用户反馈"双击没反应、不知道是喜欢还是收藏"就是在这里丢的。
    // 所以每次双击都在画面正中弹一个心形/星形的大图标。
    var actionFlash by remember { mutableStateOf<DoubleTapFlash?>(null) }
    var actionFlashTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(actionFlashTick) {
        if (actionFlash == null) return@LaunchedEffect
        delay(1200L)
        actionFlash = null
    }

    // 全屏：把 Activity 转成横屏。横屏视频在竖屏里只占中间一条，
    // 把手机（或界面）转过来才真正看得清。
    var fullscreen by remember { mutableStateOf(false) }

    // 左右滑动快进 / 快退
    var seeking by remember { mutableStateOf(false) }
    var seekBaseMs by remember { mutableLongStateOf(0L) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }

    // 播放器的"首帧门控"用**两个 key** 而不是一个 boolean：
    // boolean 在翻页瞬间还会保留"上一页已就绪"的旧值，会有整整一帧把上一页的画面
    // 画到新页的位置上；用 key 比较是纯推导，翻页的**同一帧**就失效。
    //   playKey  = 最近一次交给播放器的条目
    //   readyKey = 首帧已经吐出来的条目
    var playKey by remember { mutableStateOf<Long?>(null) }
    var readyKey by remember { mutableStateOf<Long?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubRatio by remember { mutableFloatStateOf(0f) }

    // 播放器**解码出来的**真实画面尺寸。它已经把 rotation 算进去了，
    // 是判断横竖屏的最终依据 —— MediaStore 的 WIDTH/HEIGHT 记的是
    // "编码存储的尺寸"，手机竖着拍、按横幅格式存的视频列里是 1920×1080，
    // 直接比大小会把竖屏误判成横屏，于是竖屏视频错误地长出「全屏观看」按钮。
    // （预览页 1.2.3 就修过这个，整理页当时漏掉了 —— 用户报"偶发"。）
    var decodedWidth by remember { mutableIntStateOf(0) }
    var decodedHeight by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { items.size })

    // 整个页面只养一个播放器，翻页时换 mediaItem。
    // 本地文件 prepare 几乎瞬时，不需要为一屏之外的内容各开一个解码器 —— 那是 OOM 的经典来源。
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = autoPlay
        }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(pagerState.currentPage, items.size) {
        val item = items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        index = pagerState.currentPage
        // 换片即换钥匙。此刻 readyKey 还指向上一页 → 播放器自动隐形、露出缩略图，
        // 直到新的一条真正渲染出画面（readyKey 跟上）才重新显示。
        playKey = item.storeKey()
        player.setMediaItem(Media3Item.fromUri(item.uri))
        player.prepare()
        player.playWhenReady = autoPlay
        onSeen(item)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                // 只认"最近一次交给播放器的条目" —— 它的首帧才是刚吐出来的这一帧
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

    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(speedUp) { player.setPlaybackSpeed(if (speedUp) 2f else 1f) }

    // 切后台就暂停，切回来自动接着播。
    // 但前提是「切后台之前本来就在播」—— 用户自己按了暂停的，回来不该被强行恢复。
    // 视频在后台继续播既费电又可能突然出声，必须停。
    var pausedForBackground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (player.isPlaying) {
            pausedForBackground = true
            player.pause()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (pausedForBackground) {
            pausedForBackground = false
            player.play()
        }
    }

    val activity = LocalContext.current.findActivity()
    DisposableEffect(Unit) {
        onDispose {
            // 离开这一页务必还原方向与系统栏，否则整个应用会卡在横屏
            applyFullscreenOrientation(activity, false)
        }
    }

    fun applyFullscreen(on: Boolean) {
        fullscreen = on
        applyFullscreenOrientation(activity, on)
    }

    // 默认静音，但用户一旦动了系统音量，就说明他此刻想听 —— 自动解除静音。
    //
    // 这里刻意**不去拦音量键**，而是直接看系统音量：
    //  1. 音量面板的滑块同样能改音量，而拦按键拿不到那种情况；
    //  2. Activity.dispatchKeyEvent 在 AndroidX 里是 @RestrictedApi，
    //     从应用侧覆写会被 Lint 判错。
    // 只在"当前是静音"时轮询，一旦解除静音循环就退出，开销可以忽略。
    LaunchedEffect(muted) {
        if (!muted) return@LaunchedEffect
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return@LaunchedEffect
        val baseline = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        while (true) {
            delay(500L)
            if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) != baseline) {
                muted = false
                break
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        while (true) {
            if (!scrubbing) {
                position = player.currentPosition.coerceAtLeast(0L)
                val d = player.duration
                duration = if (d > 0L) d else 0L
            }
            isPlaying = player.isPlaying
            delay(220L)
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
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
     * 退出时直接把已标记的项交出去，不再多问一遍 —— 系统的删除弹窗本身就是确认。
     * 这也修掉了"返回时已标记的没保存"：过去返回走 `performDelete = false` 分支，
     * 整批标记会被静默丢弃。
     */
    fun finish() {
        // 收尾只做一次。最后一屏可能连续触发多次滑动回调，不拦的话会弹出好几个系统弹窗。
        if (finishing) return
        finishing = true

        val result = SessionResult(
            kind = MediaKind.VIDEO,
            reviewedCount = (index + 1).coerceAtMost(items.size),
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

    // 系统返回：全屏时先退出全屏，再按一次才收尾结束这一组。
    // 这样"侧边返回退出全屏"就是自然的结果，不需要在画面上留常驻的退出按钮。
    BackHandler(enabled = true) {
        if (fullscreen) applyFullscreen(false) else finish()
    }

    val current = items.getOrNull(pagerState.currentPage)

    /**
     * 这条视频是不是横向的。
     *
     * **不能只看 MediaStore 的 WIDTH / HEIGHT** —— 那两列记的是"编码存储的尺寸"，
     * 不考虑 rotation。手机竖着拍、按横幅格式存（再靠 rotation 元数据转回来）的视频，
     * 存的是 1920×1080，`width > height` 会被判成横屏，于是**竖屏视频错误地
     * 长出一个「全屏观看」按钮**。这就是用户报的"偶发"—— 只有这类编码方式的
     * 视频才会中招，所以时灵时不灵。
     *
     * 所以先问播放器要解码后的真实尺寸（它一定对），播放器还没就绪时才
     * 退回 MediaStore 那组数做初判。（预览页 1.2.3 已修，本页同款。）
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

    // 拍摄地点。按需解析（经纬度是受权限约束的敏感列），拿不到就不显示这一行。
    var currentLocation by remember(current?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(current?.id) {
        val item = current ?: return@LaunchedEffect
        currentLocation = resolveLocation(item)
    }

    // 收藏状态必须能被 Compose 观察到 —— 和照片页同一个坑：
    // IdSetStore 内部就是普通 HashSet，改它不触发重组，所以侧栏的红心点了也不会变。
    var favoriteTick by remember { mutableIntStateOf(0) }
    val currentIsFavorite = remember(current?.id, favoriteTick) {
        current?.let { isFavorite(it) } == true
    }
    val currentIsLiked = remember(current?.id, favoriteTick) {
        current?.let { isLiked(it) } == true
    }

    // 在最后一屏继续往上滑 —— 说明这一组看完了，自动收尾退出，
    // 而不是停在那儿一动不动让人以为卡死了。
    val finishRef = rememberUpdatedState { finish() }
    val endOfListConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // available.y < 0：还想继续上滑，但已经没有可滚动的余量了
                if (available.y < -6f && !pagerState.canScrollForward) {
                    finishRef.value()
                }
                return Offset.Zero
            }
        }
    }

    /**
     * 切换收藏，并记账 —— 返回**切换之后的状态**（true = 已收藏）。
     *
     * 返回值是必需的：双击反馈要说出"已添加 / 已取消"，只靠 Unit 没法区分。
     */
    fun toggleFavorite(item: MediaItem): Boolean {
        val nowFavorite = onToggleFavorite(item)
        if (nowFavorite) {
            favRemoved = favRemoved.filterNot { it.storeKey() == item.storeKey() }
            if (favAdded.none { it.storeKey() == item.storeKey() }) favAdded = favAdded + item
        } else {
            favAdded = favAdded.filterNot { it.storeKey() == item.storeKey() }
            if (favRemoved.none { it.storeKey() == item.storeKey() }) favRemoved = favRemoved + item
        }
        return nowFavorite
    }

    /**
     * 标记 / **取消标记**删除。
     *
     * 强调"取消"是因为它原来只进不出：按一下变红标上"已标记"，
     * 想反悔却没有出口 —— 而这里的标记本来就只是记账，退出时才走系统弹窗，
     * 中间完全来得及反悔。所以做成**同一个按钮按第二下就撤销**
     * （用户要求"删除按钮再按一下应该可以取消删除"）。
     */
    fun markDelete(item: MediaItem) {
        val key = item.storeKey()
        marked = if (marked.any { it.storeKey() == key }) {
            marked.filterNot { it.storeKey() == key }
        } else {
            marked + item
        }
    }

    /**
     * 双击要做的那件事，以及做完之后怎么告诉用户。
     *
     * 语义由设置里的 [doubleTapLikes] 决定：
     *  - 双击 = 喜欢：只在本应用内记一笔，不进系统相册，点错了没有副作用；
     *  - 双击 = 收藏：会同步进系统相册的收藏夹（所以记账走 favAdded / favRemoved）。
     *
     * 两种情况的反馈都要给足 —— 图标形状 + 文字 + 状态，缺一个用户就得靠猜。
     */
    fun runDoubleTap(item: MediaItem) {
        val nowOn = if (doubleTapLikes) {
            onToggleLike(item)
        } else {
            toggleFavorite(item)
        }
        favoriteTick++

        // 只有图标，没有文字 —— 用户要求"单纯的图标动画就够了，要简约"。
        actionFlash = DoubleTapFlash(liked = doubleTapLikes, on = nowOn)
        actionFlashTick++
    }

    // 用 BoxWithConstraints 是为了拿到屏幕尺寸 —— 全屏胶囊要贴在**视频画面的下边缘**，
    // 而不是固定在屏幕某个位置。横屏视频在竖屏里是一条居中的横带，
    // 胶囊贴在横带下沿才读得通（抖音就是这么摆的）。
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        val pillTop = if (current != null) {
            // 画面高度用**真实渲染尺寸**算。解码前先用 MediaStore 的尺寸估，
            // 就绪后用实际解码尺寸，免得胶囊位置在就绪瞬间跳一下。
            val w = if (decodedWidth > 0) decodedWidth else current.width
            val h = if (decodedHeight > 0) decodedHeight else current.height
            if (w > 0 && h > 0) {
                val renderedHeight = minOf(maxHeight, maxWidth * (h.toFloat() / w))
                (maxHeight + renderedHeight) / 2f + 14.dp
            } else {
                maxHeight
            }
        } else {
            maxHeight
        }

        VerticalPager(
            state = pagerState,
            // 提前组好相邻的一页，让它们的缩略图先load起来 ——
            // 否则快速上下滑时新页还没缩略图，会看到一次空白
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(endOfListConnection),
        ) { page ->
            val item = items[page]
            val isCurrent = page == pagerState.currentPage
            VideoPage(
                item = item,
                modifier = if (isCurrent) {
                    Modifier
                        // 左右滑动快进 / 快退。竖直方向留给 Pager 翻页，两者互不干扰。
                        .pointerInput(item.storeKey()) {
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
                                    // 满屏宽 ≈ 跳转整个视频；长视频封顶 3 分钟，免得一划就跳过太多
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
                        .pointerInput(item.storeKey()) {
                            detectTapsWithoutConsuming(
                                onTap = {
                                    if (player.isPlaying) player.pause() else player.play()
                                },
                                // 单击已经由检测器延后到双击窗口之后才执行，
                                // 所以这里不需要再补偿性地切回来 ——
                                // 以前那样写会让每次双击都先闪一下暂停。
                                //
                                // 双击的语义由设置决定（喜欢 / 收藏），并且一定会给出
                                // 可见反馈 —— 用户之前反馈"双击没反应"就是这个缺口。
                                onDoubleTap = { runDoubleTap(item) },
                                onLongPressStart = { speedUp = true },
                                onLongPressEnd = { speedUp = false },
                            )
                        }
                } else {
                    Modifier
                },
            )
        }

        // ---- 屏幕级单视图播放器 ----
        // 整个页面只有这一份播放器视图，**永不建 / 毁**，位置由 pager 的当前页偏移驱动。
        // 这是"闪 vs 残留"那个取舍的第三条路：
        //   每页各挂一个 → 离屏页的 TextureView 留着旧帧（画面正中一块旧视频）；
        //   视图随页面建 / 毁 → 切页瞬间重绑 Surface，闪一下；
        //   屏幕级唯一一份 → 两个问题一起消失。
        //
        // visible 是纯推导：当前页的钥匙与"首帧已就绪"的钥匙对上才显示。
        // 翻页的同一帧 readyKey 还指着上一页 → 立即隐形、露出新页的缩略图，
        // 不存在"晚一帧才藏起来"的窗口。
        val currentKey = current?.storeKey()
        ScreenLevelVideoLayer(
            player = player,
            visible = currentKey != null && readyKey == currentKey,
            pagerState = pagerState,
            horizontal = false,
        )

        if (current != null) {
            // ---- 顶栏 ----
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.Filled.Close, contentDescription = "退出", tint = TextPrimary)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pagerState.currentPage + 1} / ${items.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                // 与左侧关闭按钮等宽，保证序号真正居中（静音开关已挪到右侧操作栏）
                Spacer(Modifier.size(48.dp))
            }

            // ---- 操作栏 ----
            // 全屏时整组隐藏：横屏下要的是干净的画面。
            // 退出全屏的方式是系统返回手势，不需要在画面上留常驻按钮。
            if (!fullscreen) {
                Column(
                    modifier = Modifier
                        .align(if (railOnRight) Alignment.BottomEnd else Alignment.BottomStart)
                        .padding(
                            start = if (railOnRight) 0.dp else 8.dp,
                            end = if (railOnRight) 8.dp else 0.dp,
                            bottom = 128.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 喜欢：心形，只记在本应用里，不写进系统相册（放在收藏上面）
                    val isFav = currentIsLiked
                    RailAction(
                        icon = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        label = "喜欢",
                        tint = if (isFav) AccentKeep else TextPrimary,
                        emphasized = isFav,
                    ) {
                        onToggleLike(current)
                        favoriteTick++
                    }

                    // 收藏：星形，会同步进系统相册的收藏夹（放在喜欢下面）
                    val isLikedNow = currentIsFavorite
                    RailAction(
                        icon = if (isLikedNow) Icons.Filled.Star else StarBorderIcon,
                        label = "收藏",
                        tint = if (isLikedNow) AccentKeep else TextPrimary,
                        emphasized = isLikedNow,
                    ) {
                        onToggleFavorite(current)
                        favoriteTick++
                    }

                    val markedCurrent = marked.any { it.storeKey() == current.storeKey() }
                    RailAction(
                        icon = Icons.Filled.Delete,
                        // ---- 文字**恒定**是动作名，状态只交给图标 ----
                        //
                        // 试过让它变（"已标记" / "取消删除"），两种都不对：
                        // 前者是状态不是动作，后者虽然准确但啰嗦，
                        // 而且一列按钮里只有它在变字，反而更乱。
                        // 用户的意思很直接：**颜色变了就够了**。
                        label = "删除",
                        tint = if (markedCurrent) AccentDelete else TextPrimary,
                        emphasized = markedCurrent,
                    ) { markDelete(current) }

                    RailAction(
                        icon = Icons.Filled.Share,
                        label = "分享",
                        tint = TextPrimary,
                    ) { shareItem(current) }

                    RailAction(
                        icon = if (muted) VolumeOffIcon else VolumeUpIcon,
                        label = if (muted) "已静音" else "有声",
                        tint = if (muted) TextTertiary else TextPrimary,
                    ) { muted = !muted }
                }
            }

            // ---- 全屏入口 ----
            // 贴在视频画面下边缘的居中胶囊，位置按当前视频的实际渲染高度算出来的。
            // 只有横屏视频才给：竖屏视频本来就撑满，全屏没有意义。
            // 用 **isLandscape**（播放器解码后的真实方向），不是 MediaStore 那两列 ——
            // 后者不带 rotation，会把"竖着拍、横着存"的视频误判成横屏。
            if (isLandscape && !fullscreen) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = pillTop.coerceIn(0.dp, maxHeight - 90.dp)),
                ) {
                    FullscreenPill { applyFullscreen(true) }
                }
            }

            // ---- 底部信息 + 进度 ----
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 28.dp, bottom = 14.dp),
            ) {
                // 全屏时把文字信息收起来，横屏下要的是干净的画面
                if (!fullscreen) {
                    Text(
                        text = relativeDayLabel(current.dateTakenMillis),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            if (current.bucket.isNotBlank()) {
                                append(current.bucket)
                                append(" · ")
                            }
                            append(formatSize(current.sizeBytes))
                            append(" · ")
                            append(formatDuration(current.durationMillis))
                            if (speedUp) append(" · 2× 快进")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (speedUp) AccentKeep else TextTertiary,
                    )

                    // 拍摄地点。没有位置信息（下载来的视频基本都没有）或没授权时不显示。
                    currentLocation?.let { place ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "地点 · $place",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 进度条 + 全屏按钮。
                // 全屏按钮放在**视频下方**，不在侧边栏 —— 它和"播放控制"是一类操作，
                // 和收藏 / 删除 / 分享不是一类。抖音也是把它摆在进度条这一行。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDuration(if (scrubbing) (duration * scrubRatio).toLong() else position),
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

                // 「已标记 N 项 / 结束并处理」那一条底部横幅已经去掉（和照片页一致）。
                // 标记数量在侧栏的删除按钮上就有（会变成"已标记"），
                // 而"结束并处理"由左上角的关闭按钮完成 —— 横幅纯属重复。
            }

            // ---- 快进 / 快退提示 ----
            if (seeking) {
                val durationMs = player.duration.coerceAtLeast(0L)
                val targetMs = (seekBaseMs + seekDeltaMs).coerceIn(0L, durationMs)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
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

            // ---- 暂停指示 ----
            if (!isPlaying) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(OverlaySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "播放",
                            tint = TextPrimary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            // ---- 长按倍速提示 ----
            if (speedUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentKeep)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "2× 快进",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // ---- 双击反馈 ----
            // 必须画在**所有内容之上**，而且刻意放在画面正中偏上：
            // 侧栏那边同时也在响应（图标会变），但用户的视线通常在画面中央，
            // 不在屏幕边角上。这一块就是用来"把双击的结果说出来"的。
            actionFlash?.let { flash ->
                DoubleTapFlashOverlay(flash, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** 一次双击的结果快照，用于渲染反馈。 */
/**
 * 一屏视频的"静态层"：缩略图 + 手势。
 *
 * 播放器**不在这里** —— 它是屏幕级唯一的一份（见 [ScreenLevelVideoLayer]），
 * 这里只负责垫底的缩略图和接收手势。
 *
 * 缩略图永远铺在底层：它是视频的第一帧，因此播放器接管的瞬间几乎没有视觉跳变，
 * 而不是先黑屏再出画面。这一层是"切换不闪黑"的关键。
 */
@Composable
private fun VideoPage(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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


/** 左右滑动跳转的上限：满屏宽最多跳 3 分钟，避免长视频一划就过头。 */
private const val MAX_SEEK_SPAN_MS = 3 * 60 * 1000L


