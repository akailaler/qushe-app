package com.qushe.app.ui.favorites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.qushe.app.core.formatSize
import com.qushe.app.core.relativeDayLabel
import com.qushe.app.ui.photo.InfoPill
import com.qushe.app.ui.photo.infoPillPrimary
import com.qushe.app.ui.photo.infoPillSecondary
import com.qushe.app.core.PhotoExif
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.qushe.app.ui.photo.PhotoDetailContent
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.qushe.app.core.AppContainer
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.formatDuration
import com.qushe.app.core.storeKey
import com.qushe.app.ui.common.MediaPreviewOverlay
import com.qushe.app.ui.common.RailAction
import com.qushe.app.ui.common.RoundActionButton
import com.qushe.app.ui.common.StarBorderIcon
import com.qushe.app.core.dayStartOf
import com.qushe.app.ui.common.rememberContentShape
import com.qushe.app.ui.photo.DayViewScreen
import com.qushe.app.ui.common.rememberThumbnailRequest
import com.qushe.app.ui.photo.FavoriteButton
import com.qushe.app.ui.photo.StarButton
import com.qushe.app.ui.theme.AccentInfo
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch

/**
 * 收藏夹。
 *
 * 下滑收藏这个动作本身不带"保存文件"的语义 —— 它只是把这条从"待处理"里捞出来，
 * 形成一个可以反复看的集合。所以这里不做任何文件移动，只是视图。
 *
 * 两点实现上的讲究：
 *  - 读取走 `byKeys` 精确定位，**不扫全库**。否则在几万条视频的设备上要等好几秒，
 *    页面看起来就像"空的"—— 这正是之前的 bug。
 *  - 点击进**应用内**全屏预览，而不是跳外部应用。在这里只是想再看一眼、顺手决定去留，
 *    被丢到另一个 App 里会完全打断节奏。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    container: AppContainer,
    refreshToken: Int,
    pendingSync: Int,
    /**
     * 收藏状态刚刚变过（点过收藏 / 取消收藏）。
     *
     * 外层要据此刷新「待同步 N 项」—— 那个数是**问系统问出来的**，
     * 不刷新的话按钮的 `enabled = pendingSync > 0` 会停在旧值上，
     * 表现就是"刚收藏完，同步按钮还是灰的"。
     */
    onFavoritesChanged: () -> Unit,
    /**
     * 时间格式（相对时间 = true）。
     *
     * 由外层持有 state（见 `AppRoot.relativeDates`）—— **不能在这里直接读
     * `prefs.relativeDates`**：SharedPreferences 读不是 Compose 的可观察状态，
     * 设置改完不会触发重组，胶囊要切换几下才跟上（用户反馈）。
     */
    relativeDates: Boolean,
    autoSyncEnabled: Boolean,
    onRequestSync: () -> Unit,
    /** 详情面板里的地名；null = 没授权 / 没解析出来 / 还在读。 */
    resolveLocationLabel: suspend (MediaItem) -> String?,
    /** 详情面板里的 EXIF。 */
    readExif: suspend (MediaItem) -> PhotoExif?,
    /**
     * 「回到那天」：读某一天的图片，返回那天（0 点毫秒）。
     * 由 `AppRoot` 提供 —— 仓库在那一层。
     */
    loadDayItems: suspend (Long) -> List<MediaItem>,
    /** 确认"用这一天开一组整理"。 */
    onOpenDaySession: (Long) -> Unit,
    /** 某一格被标记删除（在瀑布流里上滑）。这里直接从当前列表里拿掉。 */
    // 「喜欢 / 收藏」不再做成页内切换 —— 它们是两种不同的浏览意图，
    // 塞在一个小开关里既难发现也容易被误解为同一件事。
    // 现在由调用方决定：「喜欢」是独立页面（从首页右滑进入），「收藏」就是本页。
    likeMode: Boolean = false,
) {
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(emptyList<MediaItem>()) }
    var loading by remember { mutableStateOf(true) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }

    // ---- 预览里的「ⓘ」详细信息 ----
    //
    // 和整理照片页共用 [PhotoDetailContent]。面板必须走
    // `MediaPreviewOverlay` 的 `overlayContent`（**同一个 Dialog 窗口**）——
    // 用 ModalBottomSheet 会被那个窗口整个压住，
    // 这正是日期视图当初"打开了却什么都看不见"的原因。
    var detailItem by remember { mutableStateOf<MediaItem?>(null) }
    // 详情面板的另外两路数据。点「ⓘ」时才去读（都是 suspend + 有 IO），
    // 读回来之前面板先出来，不让用户等 IO。
    var detailExif by remember { mutableStateOf<PhotoExif?>(null) }
    var detailLocation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(detailItem) {
        val target = detailItem ?: return@LaunchedEffect
        detailExif = null
        detailLocation = null
        detailLocation = resolveLocationLabel(target)
        detailExif = readExif(target)
    }

    // ---- 预览里的标记（喜欢 / 收藏）当场生效，列表等退出浏览才刷新 ----
    //
    // 当场生效：点一下图标就该翻，不然像点了没反应。
    // 列表延后刷新：否则一点就少一张、后面的跟着往前挪，
    // 正在翻的这一组顺序全变了 —— 之前那个 bug 就是这个形状。
    // 想反悔就再点一下，按钮一直在那儿；比"退出才落实"更好懂。
    //
    // sessions 里的集合不是 Compose State，改完要靠 markTick 自增
    // 把这一列动作重新组合一次（视频页也是这么做的）。
    var markTick by remember { mutableIntStateOf(0) }
    var listDirty by remember { mutableStateOf(false) }

    /**
     * 浏览过程中**待删除**的条目（[MediaItem.storeKey] 集合）。
     *
     * ---- 为什么删除不立刻结算 ----
     *
     * 「喜欢」「收藏」是**可逆**的，当场生效没问题（点错了再点一下）。
     * 删除不是：它会弹系统窗、真的动到相册里的文件。浏览的时候一张张点过去，
     * 很容易手滑点中旁边那颗 —— 当场结算就来不及反悔了。
     *
     * 所以浏览期间只记在这里（图标变红表示"待删"），**退出浏览时统一提交**：
     * 那时弹一次系统窗、一次确认，既不容易误删，也少几次打断。
     * 中途反悔就是再点一下那颗图标（和「喜欢/收藏」同一个手感）。
     */
    var pendingDelete by remember { mutableStateOf(emptySet<Long>()) }

    // ---- 「回到那天」----
    //
    // 从预览里捏合进来：先把那一天读出来，再用和整理照片页同一套
    // [DayViewScreen] 呈现（两排瀑布流 + 刻度条 + 相同的进出场）。
    // 起点与解码尺寸由预览层实测后回调过来，所以飞入动效也是真的。
    var dayItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var dayTarget by remember { mutableLongStateOf(0L) }
    var dayLoading by remember { mutableStateOf(false) }
    var dayIntroFrom by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var dayIntroSize by remember { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }
    var dayFocus by remember { mutableStateOf<MediaItem?>(null) }
    var dayNotice by remember { mutableStateOf<String?>(null) }
    var dayMarked by remember { mutableStateOf(emptySet<Long>()) }

    fun openDay(dayStart: Long, focus: MediaItem, from: androidx.compose.ui.geometry.Rect?, size: androidx.compose.ui.unit.IntSize?) {
        dayTarget = dayStart
        dayFocus = focus
        dayIntroFrom = from
        dayIntroSize = size
        dayItems = null
        dayNotice = null
        dayMarked = emptySet()
        dayLoading = true
        // **刻意不去掉预览**。之前是顺手把 previewIndex 置 null，
        // 结果日期视图一旦没能盖住（进场还在等内容、或起点算不出来），
        // 用户看到的就是"动画一过直接退回预览小图"——像是手势没生效（用户反馈）。
        // 现在让日期视图**盖在**预览上面：它是不透明的，盖住就看不见；
        // 万一它没起来，预览还在，用户最多是"什么都没发生"而不是"被踢回去"。
        scope.launch {
            val loaded = runCatching { loadDayItems(dayStart) }.getOrDefault(emptyList())
            dayItems = loaded
            dayLoading = false
            if (loaded.isEmpty()) dayNotice = "这一天已经读不到内容了"
        }
    }

    // 照片 / 视频分开看。收藏多起来之后混在一起会很难找。
    var showVideos by remember { mutableStateOf(false) }
    // 纯观看模式：只浏览，不动内容
    var viewOnly by remember { mutableStateOf(container.prefs.favoritesViewOnly) }

    val shown = remember(items, showVideos) {
        // 截屏也算图片，和照片放一起
        if (showVideos) items.filter { it.kind == MediaKind.VIDEO }
        else items.filter { it.kind.isImage }
    }

    fun reload() {
        scope.launch {
            loading = true
            items = if (likeMode) {
                container.sessions.likedItems()
            } else {
                container.sessions.favoriteItems()
            }
            loading = false
        }
    }

    LaunchedEffect(refreshToken, likeMode) { reload() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        // 系统弹窗处理完再刷新一次，被删掉的那条才会从格子里消失
        reload()
    }

    /**
     * 批量删除（走系统弹窗）。
     *
     * 收 List 而不是单个：浏览期间攒下的待删条目是**一次提交**的，
     * 用户只该看到一次系统确认，而不是每张弹一次。
     */
    fun delete(targets: List<MediaItem>) {
        if (targets.isEmpty()) return
        val sender = runCatching {
            if (container.prefs.deleteToTrash) {
                container.repo.requestTrash(targets)
            } else {
                container.repo.requestDeleteForever(targets)
            }
        }.getOrNull() ?: return
        runCatching { deleteLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
    }

    fun remove(item: MediaItem) {
        // 两套数据是两个独立概念，别串了 —— 在喜欢页里点的应该是"取消喜欢"，
        // 而不是去动系统的收藏
        if (likeMode) container.sessions.toggleLike(item)
        else container.sessions.toggleFavorite(item)
        // 落盘异步化，别在点击回调里等一次文件写
        container.persistAsync()
        reload()
    }

    /** 「回到那天」里上滑标记的那些，在退出时一次性提交（走系统弹窗）。 */
    fun commitDayDeletes() {
        if (dayMarked.isEmpty()) return
        val doomed = dayItems?.filter { it.storeKey() in dayMarked }.orEmpty()
        dayMarked = emptySet()
        if (doomed.isNotEmpty()) delete(doomed)
    }

    /**
     * 预览里切换「喜欢」/「收藏」。
     *
     * 当场改会话数据（图标立刻翻），但**不 reload** ——
     * 列表要等退出浏览再刷，否则翻到一半少一张、顺序全乱。
     */
    fun mark(item: MediaItem, like: Boolean) {
        if (like) container.sessions.toggleLike(item)
        else container.sessions.toggleFavorite(item)
        container.persistAsync()
        listDirty = true
        markTick++
        // 待同步数要重新问一次系统（见 onFavoritesChanged 的说明）
        onFavoritesChanged()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = if (likeMode) "喜欢" else "收藏",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    loading -> "正在读取…"
                    items.isEmpty() && likeMode -> "还没有喜欢的内容。整理时点左下角的红心。"
                    items.isEmpty() -> "整理时点右下角的星形，就会收藏到这里。"
                    viewOnly -> "${shown.size} 项 · 纯观看模式，不会改动任何内容"
                    likeMode -> "${shown.size} 项 · 喜欢只记在本应用里，不会同步到系统相册"
                    // 跟随「同步收藏到系统相册」开关 ——
                    // 关掉时这句"会同步"就是假话（下面 235 行的卡片还说着"已关闭"）。
                    else -> "${shown.size} 项 · " +
                        if (autoSyncEnabled) {
                            "取舍的收藏是你的清单，同时写进系统相册"
                        } else {
                            "取舍的收藏是你的清单，暂不写系统相册"
                        }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )

            // ---- 照片 / 视频 分开看 ----
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegChip("照片", selected = !showVideos) { showVideos = false }
                SegChip("视频", selected = showVideos) { showVideos = true }
                Spacer(Modifier.weight(1f))
                // 纯观看模式：翻看自己攒下来的东西时不想误删，就开这个
                Text(
                    text = if (viewOnly) "纯观看中" else "可编辑",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (viewOnly) AccentInfo else TextTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            viewOnly = !viewOnly
                            container.prefs.favoritesViewOnly = viewOnly
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            // 喜欢不涉及系统相册，这一整块在喜欢页不该出现 ——
            // 留着它会让人以为"喜欢"也会同步进相册，正好和这个功能的定位相反。
            if (!likeMode) {
            // ---- 系统相册同步 ----
            //
            // 系统的收藏标记应用改不了，只能发起请求让用户在系统弹窗里点确认。
            // 所以这里做成显式的批量动作，而不是在每次收藏时打断手势。
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .clickable(enabled = pendingSync > 0, onClick = onRequestSync)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        // 没有待写入 ≠ 和系统相册一致 —— 系统相册里另外收藏的东西
                        // 取舍这边根本没有（`IS_FAVORITE` 只认走 API 收藏的，
                        // 相册里手动点星的不算）。所以不能写"一致"，
                        // 要把「反向捞回来」的入口指出来，和设置页那条对齐。
                        text = if (pendingSync > 0) {
                            "写入 $pendingSync 项到系统相册"
                        } else {
                            "取舍这边没有待写入的"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (pendingSync > 0) AccentKeep else TextSecondary,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            pendingSync > 0 && autoSyncEnabled ->
                                "每轮整理结束自动写入，也可以现在手动跑一次"
                            pendingSync > 0 ->
                                "自动写入已关闭，需要手动点这里"
                            autoSyncEnabled ->
                                "系统相册里另有收藏？到「设置 → 同步系统收藏到这里」收进来"
                            else ->
                                "自动写入已关闭；系统相册里另有收藏，可到设置里收进来"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            }
            }

            Spacer(Modifier.height(18.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentKeep, strokeWidth = 2.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        // 喜欢页标题是「喜欢」，加载文案不能还写"收藏"。
                        text = if (likeMode) "正在读取喜欢…" else "正在读取收藏…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary,
                    )
                }
            }

            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (items.isEmpty()) {
                        "这里还是空的。\n去整理一组，把想留的挑出来。"
                    } else {
                        "这个分类下还没有收藏。\n换到另一个分类看看？"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                // 缩略图网格保持 4.dp —— 这是"密集小图"的间距，和大图之间的
                // 接缝（rememberPageGap）不是同一件事，别混。
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(shown, key = { _, item -> item.storeKey() }) { index, item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            // 和大图预览、瀑布流格子共用同一个圆角值（rememberContentCorner）
                            .clip(rememberContentShape())
                            .background(Surface1)
                            .combinedClickable(
                                // 点开应用内预览，而不是把用户丢给外部应用
                                onClick = { previewIndex = index },
                                // 纯观看模式下长按也不动内容
                                onLongClick = { if (!viewOnly) remove(item) },
                            ),
                    ) {
                        AsyncImage(
                            model = rememberThumbnailRequest(item.uri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.kind == MediaKind.VIDEO) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xB3000000))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = formatDuration(item.durationMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentInfo,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // **刻意不在这儿过滤已标记删除的项**。
    //
    // `DayViewScreen` 自己会用 `markedKeys` 把那些格子宽度收到 0（`removedFlags`
    // → `CellSlot` 收缩），这才是整理照片页那条"格子飞出去、同排左移补位"的动线。
    // 在外面先把项从 `items` 里剔掉，等于换成了另一套删除机制 ——
    // 整排会重新布局，偶发留下收不干净的红底（用户反馈"上滑之后没有正常消失，
    // 经常残留红色底"）。这里和整理页保持**逐字一致**的用法。
    val dayShown = dayItems

    val index = previewIndex
    if (index != null && shown.isNotEmpty()) {
        MediaPreviewOverlay(
            items = shown,
            initialIndex = index,
            onDismiss = {
                previewIndex = null
                // ---- 待删除的在这里**统一结算** ----
                //
                // 浏览期间只改了图标颜色（见 pendingDelete 的说明），
                // 到这一步才真的去弹系统窗。一次提交、一次确认。
                val doomed = shown.filter { it.storeKey() in pendingDelete }
                if (doomed.isNotEmpty()) {
                    pendingDelete = emptySet()
                    delete(doomed)
                }
                // 退出浏览 = 刷新一次列表。取消掉的收藏在这一刻才从格子里消失，
                // 浏览过程中列表保持不动（见 mark 的注释）。
                // 删除走的是系统弹窗，它的回调里还会再刷一次 —— 这一句管的是
                // 喜欢/收藏；两件事可能同时发生，所以不做 else。
                if (listDirty) {
                    listDirty = false
                    reload()
                }
            },
            // 捏合 = 回到那天（和整理照片页同一个手势、同一个日期视图）
            onPinchToDate = { cur, scale, from, size ->
                if (cur.dateTakenMillis > 0L) {
                    val ratio = if (cur.width > 0 && cur.height > 0) {
                        cur.width.toFloat() / cur.height
                    } else {
                        0f
                    }
                    val start = if (from != null && ratio > 0f) {
                        val at1x = fitInside(from, ratio)
                        scaledAboutCenter(at1x, scale)
                    } else {
                        null
                    }
                    openDay(dayStartOf(cur.dateTakenMillis), cur, start, size)
                }
            },
            // 「回到那天」盖在预览**之上**。必须走 overlayContent ——
            // 预览跑在一个独立 Dialog 窗口里，在这里之外画的东西会被它整个压住。
            overlayContent = {
                if (dayShown != null || dayLoading) {
                    DayViewScreen(
                        items = dayShown,
                        loading = dayLoading,
                        notice = dayNotice,
                        kindLabel = "照片",
                        dayStart = dayTarget,
                        focusItem = dayFocus,
                        markedKeys = dayMarked,
                        swipeUpDeletes = container.prefs.swipeUpDeletes,
                        isFavorite = { container.sessions.isFavorite(it) },
                        isLiked = { container.sessions.isLiked(it) },
                        onToggleLike = { container.sessions.toggleLike(it); markTick++ },
                        // 退出时统一提交：和整理照片页"退出这一组才结算"是同一条规矩，
                        // 也复用了本页预览已有的系统弹窗通道（delete()）。
                        onBack = {
                            commitDayDeletes()
                            dayItems = null
                            dayLoading = false
                        },
                        onConfirm = {
                            val t = dayTarget
                            commitDayDeletes()
                            dayItems = null
                            dayLoading = false
                            onOpenDaySession(t)
                        },
                        // 上滑 = 删除（swipeUpDeletes 已钉死为 true）。
                        // 这里**只标记**、让格子飞出去消失；真正动文件在退出时提交。
                        onMarkDelete = { target ->
                            dayMarked = dayMarked + target.storeKey()
                        },
                        onMarkFavorite = { target ->
                            if (likeMode) container.sessions.toggleLike(target)
                            else container.sessions.toggleFavorite(target)
                            container.persistAsync()
                            markTick++
                        },
                        onUndoDelete = { dayMarked = dayMarked.drop(1).toSet() },
                        canUndo = dayMarked.isNotEmpty(),
                        introFrom = dayIntroFrom,
                        introImageSize = dayIntroSize,
                    )
                }

                // ---- 详细信息面板（和整理照片页那颗 i 同一份内容）----
                detailItem?.let { detail ->
                    // 点面板外面关闭；面板本身吃掉点击，否则点里面任何位置都会关掉它。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xB3000000))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { detailItem = null },
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Surface2)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {}
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        PhotoDetailContent(item = detail, exif = detailExif, locationName = detailLocation)
                    }
                }
            },
            // 默认静音是全 App 级设置：预览里播的视频同样不外放。
            videoMutedByDefault = container.prefs.videoMuted,
            // ---- 动作区：视频照整理视频页、图片照整理照片页 ----
            //
            // 两个页面**都同时给喜欢和收藏两颗** —— 在喜欢页里也能顺手收藏，
            // 在收藏页里也能顺手喜欢，不用为了换个标记先退出来再进另一个页面。
            railOnRight = container.prefs.railOnRight,
            videoActions = if (viewOnly) null else {
                { item ->
                    val (liked, faved) = rememberMarkState(container, item, markTick)
                    RailAction(
                        icon = if (liked) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Filled.FavoriteBorder
                        },
                        label = "喜欢",
                        tint = if (liked) AccentKeep else TextPrimary,
                        emphasized = liked,
                    ) { mark(item, like = true) }
                    RailAction(
                        icon = if (faved) Icons.Filled.Star else StarBorderIcon,
                        label = "收藏",
                        tint = if (faved) AccentKeep else TextPrimary,
                        emphasized = faved,
                    ) { mark(item, like = false) }
                    val doomed = item.storeKey() in pendingDelete
                    RailAction(
                        icon = Icons.Filled.Delete,
                        // 文字恒定，状态只交给颜色（和整理视频页同一条规矩）。
                        label = "删除",
                        tint = if (doomed) AccentDelete else TextPrimary,
                        emphasized = doomed,
                    ) {
                        // 只改颜色、**不结算**，也不退出浏览 —— 见 pendingDelete 的说明
                        val key = item.storeKey()
                        pendingDelete = if (doomed) pendingDelete - key
                        else pendingDelete + key
                    }
                }
            },
            photoActions = if (viewOnly) null else {
                { item ->
                    val (liked, faved) = rememberMarkState(container, item, markTick)
                    // 用 Box 而不是 Row：胶囊要的是**相对整屏**居中，
                    // 而 Row 里的 weight 只能做到"在剩余空间里居中"——
                    // 左边两颗按钮、右边一颗按钮，剩余空间被三个 weight 平分，
                    // 整行看过去胶囊必然偏（用户反馈"没有居中"）。
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // 左边并排两颗：喜欢（心）+ 收藏（星）。
                        // 就是整理照片页那两颗（左下心、右下星），只是并到了一起 ——
                        // 那边靠"左 vs 右"区分，这里靠"心 vs 星"区分，
                        // 形状本来就不同，并排也不会认错。
                        //
                        // ⚠️ 这两颗必须自己套一层 Row：Box 的子节点**默认堆叠**
                        // 在左上角，不套 Row 的话心形和星会叠在一起（用户反馈）。
                        Row(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FavoriteButton(selected = liked) { mark(item, like = true) }
                            Spacer(Modifier.width(10.dp))
                            StarButton(selected = faved) { mark(item, like = false) }
                        }
                    }
                    // ---- 正中的「ⓘ 胶囊」 ----
                    //
                    // 单独一层铺满 + `align(Center)`，和整理照片页完全一样。
                    // 之前它夹在一行 `Row` 里、靠 `weight` 凑中间 ——
                    // 而左边有两颗按钮占位，右边有一颗，剩余空间被三个 weight 平分，
                    // 结果整行看过去胶囊并不在屏幕正中（用户反馈"没有居中"）。
                    InfoPill(
                        // 文案来自唯一来源（与另外两处同一套）
                        primary = infoPillPrimary(item, relativeDates),
                        secondary = infoPillSecondary(item),
                        modifier = Modifier.align(Alignment.Center),
                        onClick = { detailItem = item },
                    )
                    val doomed = item.storeKey() in pendingDelete
                    RoundActionButton(
                        icon = Icons.Filled.Delete,
                        label = "删除",
                        tint = if (doomed) AccentDelete else TextPrimary,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        // 同视频那排：只改颜色，退出时才统一结算
                        val key = item.storeKey()
                        pendingDelete = if (doomed) pendingDelete - key
                        else pendingDelete + key
                    }
                }
            },
            bottomContent = { _ ->
                // 纯观看模式不提供任何改动入口，只让你安静地看
                if (viewOnly) {
                    Text(
                        text = "纯观看模式 · 不会改动任何内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                }
            },
        )
    }
}

/**
 * 「照片 / 视频」那个二选一的小胶囊。
 *
 * 做成胶囊而不是开关：开关逼用户把"关闭"在脑子里翻译成"那就是视频"，
 * 胶囊把两个选项都摆在明面上。
 */
@Composable
private fun SegChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 配色与圆角**逐项照抄设置页的 `Chip`**（那边叫 `SettingsScreen.Chip`）——
    // 两个都是二选一胶囊，长得不一样就会像两套控件。数值改一处记得改另一处。
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentKeep else Surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Ink else TextSecondary,
        )
    }
}

/**
 * 读出这一项当前的"喜欢 / 收藏"状态。
 *
 * 集合不是 Compose State（改了不触发重组），所以要把 [tick] 列进键里，
 * 让点过之后这一列能重新算一次 —— 否则按钮的选中态永远不翻。
 */
@Composable
private fun rememberMarkState(
    container: AppContainer,
    item: MediaItem,
    tick: Int,
): Pair<Boolean, Boolean> = remember(item.storeKey(), tick) {
    container.sessions.isLiked(item) to container.sessions.isFavorite(item)
}

/** 在 [box] 里按 [ratio] 居中放一张图之后那张图占的矩形（= `ContentScale.Fit`）。 */
private fun fitInside(box: androidx.compose.ui.geometry.Rect, ratio: Float): androidx.compose.ui.geometry.Rect {
    if (ratio <= 0f || box.width <= 0f || box.height <= 0f) return box
    val w: Float
    val h: Float
    if (box.width / box.height > ratio) {
        h = box.height
        w = h * ratio
    } else {
        w = box.width
        h = w / ratio
    }
    val left = box.left + (box.width - w) / 2f
    val top = box.top + (box.height - h) / 2f
    return androidx.compose.ui.geometry.Rect(left, top, left + w, top + h)
}

/** 以中心为基准把矩形缩放到 [factor] 倍（捏合时照片是缩着被触发的）。 */
private fun scaledAboutCenter(
    source: androidx.compose.ui.geometry.Rect,
    factor: Float,
): androidx.compose.ui.geometry.Rect {
    if (factor <= 0f || factor == 1f) return source
    val cx = source.center.x
    val cy = source.center.y
    val halfW = source.width * factor / 2f
    val halfH = source.height * factor / 2f
    return androidx.compose.ui.geometry.Rect(
        cx - halfW, cy - halfH, cx + halfW, cy + halfH,
    )
}
