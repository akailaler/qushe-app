package com.qushe.app.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.content.IntentSender
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qushe.app.core.AppContainer
import com.qushe.app.core.MediaItem
import com.qushe.app.core.MediaKind
import com.qushe.app.core.Prefs
import com.qushe.app.core.SessionResult
import com.qushe.app.core.Stats
import com.qushe.app.core.PhotoExifReader
import com.qushe.app.core.hasLocationPermission
import com.qushe.app.core.hasPermissionFor
import com.qushe.app.core.permissionsFor
import com.qushe.app.core.storeKey
import com.qushe.app.ui.favorites.FavoritesScreen
import com.qushe.app.ui.home.HomeScreen
import com.qushe.app.ui.photo.PhotoSessionScreen
import com.qushe.app.ui.settings.SettingsScreen
import com.qushe.app.ui.theme.AccentDelete
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import com.qushe.app.ui.trash.TrashScreen
import com.qushe.app.ui.video.VideoSessionScreen
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    // 标签叫「首页」而不是「整理」：这一页其实是整站的第一屏 ——
    // 三个整理入口 + 累计成果 + 收藏 / 回收站 / 设置的落脚点。
    // 叫「整理」会让人以为这一页本身在做整理，而它只是个入口页。
    Review("首页", Icons.Filled.Home),
    // 星形，不是心形 —— 和「回到那天」里的实心星、整理页的五星是同一个符号。
    // "收藏"这个概念在本 App 里统一用星；心形留给「喜欢」（另一条独立列表）。
    Favorites("收藏", Icons.Filled.Star),
    Trash("回收站", Icons.Filled.Delete),
    Settings("设置", Icons.Filled.Settings),
}

/**
 * 一次系统收藏确认窗最多带多少项。
 *
 * 一次 `createFavoriteRequest` 塞几百个 URI，系统弹窗会直接弹不出来
 * （实测这台机器上 276 个 URI 一次都没成功）。20 是保守取值：
 * 弹窗能正常渲染，用户也不会觉得要确认十几次。
 */
private const val FAVORITE_BATCH = 20

private const val TAG = "qushe"

private sealed interface SessionUi {
    data object None : SessionUi
    data class Loading(val kind: MediaKind) : SessionUi
    data class Running(val kind: MediaKind, val items: List<MediaItem>) : SessionUi

    /** [queryFailed] 用来区分「确实没有内容」和「内容读不出来」，两者的提示必须不同。 */
    data class Empty(val kind: MediaKind, val queryFailed: Boolean) : SessionUi
}

@Composable
fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var permissionTick by remember { mutableIntStateOf(0) }
    val photoAllowed = remember(permissionTick) { hasPermissionFor(context, MediaKind.PHOTO) }
    val videoAllowed = remember(permissionTick) { hasPermissionFor(context, MediaKind.VIDEO) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionTick++ }

    var tab by remember { mutableStateOf(Tab.Review) }

    // 共 5 页：第 0 页是「喜欢」，1..4 对应底部四个 tab。
    //
    // 把「喜欢」当成"首页左边的一页"，是这里最省事的解法 ——
    // 这样"在首页右滑进入喜欢"天然成立，而四个 tab 之间的左右滑也不冲突，
    // 一个 Pager 就把两件事一起解决了，不需要额外的手势判断。
    // `beyondViewportPageCount = 1`：**提前把相邻页组合好**。
    //
    // 默认是 0 —— 只组合当前页。于是"从首页滑向收藏页"这一下，
    // 收藏页的网格、设置页那一长列都要在**手势进行中**现组合 + 现测量，
    // 掉帧就在这里（用户反馈"首页左右滑动到其他页面感觉有些卡顿"）。
    //
    // 改成 1 之后：那些成本挪到"你还没开始滑"的时候（停留时组合），
    // 滑动过程里只做绘制位移 ⇒ 手感顺。
    // 5 页的规模，多组合一页的内存代价可以忽略。
    val pagerState = rememberPagerState(initialPage = Tab.Review.ordinal + 1, pageCount = { 5 })
    val pagerScope = rememberCoroutineScope()

    fun goToTab(target: Tab) {
        tab = target
        // 点按要**直接切过去**，不要滑 —— 点按是明确指定，滑过去反而像在等动画。
        // 滑动切换本身仍然由 Pager 负责，两条路径互不影响。
        pagerScope.launch { pagerState.scrollToPage(target.ordinal + 1) }
    }

    // 滑动结束后把底部栏的选中态同步过来
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in 1..Tab.entries.size) {
            val shown = Tab.entries[page - 1]
            if (shown != tab) tab = shown
        }
    }
    var session by remember { mutableStateOf<SessionUi>(SessionUi.None) }
    var stats by remember { mutableStateOf(container.prefs.stats()) }
    var favoriteCount by remember { mutableStateOf(container.sessions.favoriteCount()) }
    // ---- 首页三个计数：null = 还在数，不等于0 ----
    //
    // 之前初值是 `0`，而真正的数量要等`LaunchedEffect(permissionTick)` 里
    // 扫完MediaStore 才写回来。于是**首帧先显示 "0"、再跳成真实数字**——
    // 用户报"启动时首页会显示读取到的照片、视频数量，这个数在一开始显示为0，
    // 然后才显示为读取到的那个数，观感不好"。
    //
    // 用 `null` 把"还没数完"和"数出来是0"区分开：前者显示 `…`，
    // 后者才是真的 0。（设备里确实一张照片都没有时，"0"才是正确信息。）
    var photoCount by remember { mutableStateOf<Int?>(null) }
    var screenshotCount by remember { mutableStateOf<Int?>(null) }
    var videoCount by remember { mutableStateOf<Int?>(null) }
    var syncNotice by remember { mutableStateOf<String?>(null) }

    // 侧边返回手势：不在「整理」页时先退回整理页，而不是直接退出应用。
    // 这一段必须声明在会话/预览之前 —— Compose 的返回回调是后注册者优先，
    // 会话页和预览层各自注册的处理会自然地盖住这里。
    // ---- 侧边返回手势：必须走 goToTab，不能只改 tab ----
    //
    // 原来这里写的是 `tab = Tab.Review`：底栏读 `tab` ⇒ 选中态立刻变成"首页"，
    // 而 **pager 根本没动** ⇒ 上面的内容还停在这一页。
    // 用户反馈"底部导航栏回到了首页，但上面的部分并没有回到首页"，
    // 症状和原因都正好对上。`goToTab` 里是"`tab` + `scrollToPage`"两件事一起做。
    //
    // 判据也一并改成看 **pager 当前页**：停在「喜欢」那一页时 `tab` 本来就是 Review，
    // 用 `tab` 判断会让返回手势直接失效（应该回到首页，而不是退出应用）。
    BackHandler(enabled = pagerState.currentPage != Tab.Review.ordinal + 1) {
        goToTab(Tab.Review)
    }

    LaunchedEffect(syncNotice) {
        if (syncNotice != null) {
            delay(3200L)
            syncNotice = null
        }
    }

    LaunchedEffect(permissionTick) {
        if (!photoAllowed && !videoAllowed) return@LaunchedEffect
        // 三个计数**并行**扫，不要一个接一个。
        //
        // `idsOf` 每次都是一次 MediaStore 全量查询，几万张照片的设备上
        // 单次就要几百毫秒。串行做的话首页会看到 0 →照片数 → 照片+截屏数 → 全都齐，
        // **数字跳三次**；并行则几乎同一时刻一起变。
        // （`idsOf` 内部有 loadLock + 双检，并发调不同kind 不会互相阻塞——
        //   锁只保护同一个 kind 的重复查询。）
        val (p, s, v) = if (!photoAllowed) {
            Triple(null, null, container.repo.idsOf(MediaKind.VIDEO).size)
        } else if (!videoAllowed) {
            Triple(
                container.repo.idsOf(MediaKind.PHOTO).size,
                container.repo.idsOf(MediaKind.SCREENSHOT).size,
                null,
            )
        } else {
            coroutineScope {
                val pDef = async { container.repo.idsOf(MediaKind.PHOTO).size }
                val sDef = async { container.repo.idsOf(MediaKind.SCREENSHOT).size }
                val vDef = async { container.repo.idsOf(MediaKind.VIDEO).size }
                Triple(pDef.await(), sDef.await(), vDef.await())
            }
        }
        photoCount = p
        screenshotCount = s
        videoCount = v
        // 清掉指向已删除文件的收藏 / 已看条目，否则它们会永久占着"一轮"的名额
        container.sessions.reconcile()
        container.sessions.persist()
        favoriteCount = container.sessions.favoriteCount()
    }

    val buildDeleteRequest: (List<MediaItem>) -> IntentSender? = { targets ->
        runCatching {
            if (container.prefs.deleteToTrash) {
                container.repo.requestTrash(targets)
            } else {
                container.repo.requestDeleteForever(targets)
            }
        }.getOrNull()
    }

    val shareItem: (MediaItem) -> Unit = { item ->
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                // 截屏也是图片，别按视频去分享
                type = if (item.kind.isImage) "image/*" else "video/*"
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "分享到"))
        }
    }

    // ---- 收藏 → 系统相册 的同步 ----
    //
    // 系统不允许应用静默修改相册的「收藏」标记，每次都得弹一次授权框让用户确认。
    // 所以这里绝不可能在下滑收藏的瞬间调用 —— 那会让每个手势都卡一个弹窗。
    // 唯一的做法是攒成一批，在会话结束时（或用户主动点同步时）提交一次。
    var syncAdds by remember { mutableStateOf(emptyList<MediaItem>()) }
    var syncRemoves by remember { mutableStateOf(emptyList<MediaItem>()) }
    var pendingSync by remember { mutableIntStateOf(0) }

    /** 还要推的**剩余批次**（见 [FAVORITE_BATCH]）。 */
    var syncQueue by remember { mutableStateOf(emptyList<MediaItem>()) }
    /** 该弹窗的那一批；非空时由 [addSyncer] 之后的弹出器消费。 */
    var pendingAddBatch by remember { mutableStateOf(emptyList<MediaItem>()) }
    /**
     * 批次序号，**只用来当 `LaunchedEffect` 的键**。
     *
     * ⚠️ 不能拿 `pendingAddBatch` 本身当键：弹出器在 effect 内部会把它清空，
     * 那一下键就变了 ⇒ 协程被取消重启 ⇒ `addSyncer.launch()` 根本来不及执行，
     * 或者执行到一半被打断。表现就是"点了同步什么都没发生"（用户反馈，
     * 实测 shell 能写、Provider 也支持，所以问题一定在我们这一侧）。
     */
    var batchTick by remember { mutableIntStateOf(0) }
    /**
     * 时间格式（相对时间 / 具体日期）。
     *
     * **必须在这里存一份 state**：`prefs.relativeDates` 是 SharedPreferences 读，
     * 不是 Compose 的可观察状态 —— 各页面直接读它的话，设置改完不会触发重组，
     * 表现是"胶囊要切换几下才跟上"（用户反馈）。设置页改动时回调这里更新。
     */
    var relativeDates by remember { mutableStateOf(container.prefs.relativeDates) }

    /** 本轮总条数 / 已确认写入条数，只用于给用户一句进度。 */
    var syncTotal by remember { mutableIntStateOf(0) }
    var syncDone by remember { mutableIntStateOf(0) }

    LaunchedEffect(favoriteCount) {
        pendingSync = container.sessions.pendingSyncCount()
    }

    /** 每次回到前台都重问一次系统（同步状态可能被别的应用改过）。 */
    LaunchedEffect(Unit) {
        pendingSync = container.sessions.pendingSyncCount()
    }


    /** 交一批给弹出器：改内容 + 递增序号。 */
    fun requestAddBatch(batch: List<MediaItem>) {
        if (batch.isEmpty()) return
        pendingAddBatch = batch
        batchTick++
    }

    val removeSyncer = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
        }
        syncRemoves = emptyList()
        scope.launch { pendingSync = container.sessions.pendingSyncCount() }
    }

    val addSyncer = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val adds = syncAdds
        syncAdds = emptyList()

        if (result.resultCode != Activity.RESULT_OK) {
            scope.launch { pendingSync = container.sessions.pendingSyncCount() }
            syncNotice = "已取消，收藏没有写入系统相册"
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            // 系统弹窗点了"允许"不等于真的落库了。这里读回来核对一次 ——
            // 部分厂商的相册应用根本不使用 MediaStore 的 IS_FAVORITE 标记，
            // 只有核对过才能给出诚实的结论，而不是让用户对着一个永远空的收藏夹猜。
            val confirmed = runCatching { container.repo.systemFavoriteKeys(adds) }
                .getOrDefault(emptySet())
            syncDone += confirmed.size

            // ---- 队列里还有下一批 ⇒ 交给弹出器继续 ----
            val next = syncQueue.take(FAVORITE_BATCH)
            syncQueue = syncQueue.drop(FAVORITE_BATCH)
            if (next.isNotEmpty()) {
                requestAddBatch(next)
                return@launch
            }
            syncQueue = emptyList()
            Log.i(TAG, "全部批次结束：写入 $syncDone / $syncTotal")

            // 第二批（取消收藏）接着走
            val removes = syncRemoves
            val sender = if (removes.isNotEmpty()) {
                runCatching { container.repo.requestFavorite(removes, false) }.getOrNull()
            } else {
                null
            }
            if (sender != null) {
                runCatching { removeSyncer.launch(IntentSenderRequest.Builder(sender).build()) }
            } else {
                syncRemoves = emptyList()
            }

            pendingSync = container.sessions.pendingSyncCount()
            syncNotice = when {
                // 还有下一批在排队 ⇒ 先报进度，别让用户以为结束了
                syncQueue.isNotEmpty() ->
                    "已写入 $syncDone 项，继续下一批（共 $syncTotal 项）"

                confirmed.isEmpty() -> {
                    // ---- 如实说，不再偷偷关掉用户的开关 ----
                    // 原来这里会 `autoSyncFavorites = false`，
                    // 也就是**在用户不知情的情况下关掉一个设置** ——
                    // 试失败一次就永久停同步，用户只会看到"收藏再也不同步了"，
                    // 却不知道为什么（用户反馈"收藏夹似乎没有同步到系统"）。
                    // 和 1.3.52 删掉「上滑删除」那条设置是同一个病根：
                    // **替用户做决定 + 不告诉他**。
                    // 现在只报事实：这次没写进去，可能是这台设备的相册
                    // 不支持收藏标记，要不要继续由用户自己在开关里决定。
                    "这台设备的相册没有记录这次收藏标记，本次没能写入系统。" +
                        "仍然可以手动重试，或在设置里关掉自动写入。"
                }

                confirmed.size < adds.size ->
                    "已同步 ${confirmed.size} / ${adds.size} 项，其余没能写入系统"

                else ->
                    // ---- 说清"为什么相册里可能暂时看不到" ----
                    //
                    // 实测（2026-10-16）：取舍收藏 286 条全部写进系统 `is_favorite`，
                    // 逐条对上 0 差异；但**小米相册要重启/等一段时间才显示全**
                    // （用户实测：重启后从 256 涨到 270，在渐进刷新）。
                    // 不说明这一点的话，用户会以为"没同步"，然后反复点。
                    "已写入 ${adds.size} 项。" +
                        "系统相册可能需要重启或等一会儿才会显示出来。"
            }
        }
    }

    /**
     * 批次弹出器：把 [pendingAddBatch] 这一批弹一次系统确认窗。
     *
     * 一次 `MediaStore.createFavoriteRequest` 塞几百个 URI，**系统弹窗会直接弹不出来**
     * —— 实测这台机器上 276 个 URI 的请求一次都没成功过。所以每批 [FAVORITE_BATCH] 个，
     * 一批弹一次窗，`addSyncer` 核对完再交下一批。
     *
     * 必须放在 `addSyncer` **之后**：它要用 `addSyncer.launch`，
     * 而 Kotlin 的局部变量必须先声明后使用。
     */
    LaunchedEffect(batchTick) {
        // 键是 [batchTick]（每次要弹窗时 +1），**不是** `pendingAddBatch` 本身 ——
        // 见 batchTick 的说明。
        if (batchTick == 0) return@LaunchedEffect
        val batch = pendingAddBatch
        if (batch.isEmpty()) return@LaunchedEffect
        Log.i(TAG, "弹窗写入第 $batchTick 批，共 ${batch.size} 项")
        val sender = runCatching { container.repo.requestFavorite(batch, true) }
            .onFailure { Log.w(TAG, "系统拒绝了收藏请求", it) }
            .getOrNull()
        if (sender == null) {
            syncNotice = "系统拒绝了收藏请求（${batch.size} 项）"
            return@LaunchedEffect
        }
        syncAdds = batch
        val launched = runCatching {
            addSyncer.launch(IntentSenderRequest.Builder(sender).build())
        }.onFailure { Log.w(TAG, "无法启动系统确认弹窗", it) }.isSuccess
        if (!launched) {
            syncNotice = "无法启动系统确认弹窗，这台设备可能不支持收藏接口"
            syncAdds = emptyList()
        }
    }

    /**
     * 把收藏写进系统相册。
     *
     * 直接以**系统实际状态**为基准算差额（见 [favoriteSyncDelta]），
     * 没有"已同步"记账 —— 那套记账和事实脱节，
     * 导致收藏夹 276 条却只推 1 条（用户反馈"同步没办法"）。
     */
    fun startFavoriteSync() {
        scope.launch {
            val delta = container.sessions.favoriteSyncDelta()
            if (delta.isEmpty) {
                pendingSync = container.sessions.pendingSyncCount()
                syncNotice = "没有需要同步的收藏"
                return@launch
            }
            syncRemoves = delta.toRemove
            syncTotal = delta.toAdd.size
            syncDone = 0
            syncQueue = delta.toAdd
            Log.i(TAG, "开始同步收藏：待写入 ${delta.toAdd.size} 项（每批 $FAVORITE_BATCH）")
            if (syncQueue.isNotEmpty()) {
                val batch = syncQueue.take(FAVORITE_BATCH)
                syncQueue = syncQueue.drop(FAVORITE_BATCH)
                requestAddBatch(batch)
                return@launch
            }

            if (delta.toRemove.isNotEmpty()) {
                val sender = runCatching { container.repo.requestFavorite(delta.toRemove, false) }
                    .getOrNull()
                if (sender != null) {
                    runCatching { removeSyncer.launch(IntentSenderRequest.Builder(sender).build()) }
                    return@launch
                }
                syncRemoves = emptyList()
            }

            pendingSync = container.sessions.pendingSyncCount()
        }
    }

    /**
     * @param bucket 非空时只从这个文件夹抽（见 `buildSession` 的说明）。
     *   文件夹自带 kind，所以权限检查、SessionScreen 都照原路走，不需要第二套逻辑。
     */
    fun startSession(kind: MediaKind, bucket: String? = null) {
        // 截屏和照片共用图片权限，只有视频是另一条 —— 用 isImage 判断，别再按 PHOTO 判断
        val allowed = if (kind.isImage) photoAllowed else videoAllowed
        if (!allowed) {
            // 具体该申请哪个权限由 permissionsFor 按系统版本决定，
            // 不能在这里写死 READ_MEDIA_IMAGES —— 那是 Android 13 才有的权限
            permissionLauncher.launch(permissionsFor(kind))
            return
        }

        // 照片和视频的左下角都要显示拍摄地点，得单独申请位置权限。
        // 这里**不再判断设置开关** —— 之前加了 `showMediaLocation &&` 这个条件，
        // 只要开关状态和预期不一致就永远不会弹窗，而界面上完全看不出来。
        // 无条件申请，用户拒绝也只是不显示地点那一行，不影响别的功能。
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_MEDIA_LOCATION))
        }
        session = SessionUi.Loading(kind)
        scope.launch {
            val snapshot = container.sessions.buildSession(kind, container.prefs.groupSize, bucket)
            session = if (snapshot.items.isEmpty()) {
                SessionUi.Empty(kind, container.repo.lastQueryFailed)
            } else {
                SessionUi.Running(kind, snapshot.items)
            }
        }
    }

    /**
     * 从「收藏 / 喜欢」页的「回到那天」跳过来：用那一天（±1 天）直接开一组整理。
     *
     * 和 [startSession] 的区别只有一个：**内容不随机抽，而是就用那一天**。
     * 随机抽会毁掉"我刚看的就是那天"这个前提 —— 用户从收藏里捏进来，
     * 想看的就是那天的全貌。
     */
    fun startDaySession(dayStart: Long) {
        if (!photoAllowed) {
            permissionLauncher.launch(permissionsFor(MediaKind.PHOTO))
            return
        }
        session = SessionUi.Loading(MediaKind.PHOTO)
        scope.launch {
            val items = runCatching {
                container.repo.allImagesAroundDay(dayStart)
            }.getOrDefault(emptyList())
            session = if (items.isEmpty()) {
                SessionUi.Empty(MediaKind.PHOTO, container.repo.lastQueryFailed)
            } else {
                SessionUi.Running(MediaKind.PHOTO, items)
            }
        }
    }

    fun conclude(result: SessionResult) {
        container.prefs.recordSession(
            reviewed = result.reviewedCount,
            deleted = result.markedDelete.size,
            favoritedDelta = result.favoriteAdded.size - result.favoriteRemoved.size,
            freedBytes = result.deleteBytes,
            isVideo = result.kind == MediaKind.VIDEO,
        )
        // 落盘异步化：`seen` 几万条时主线程整文件重写是一次可感卡顿
        container.persistAsync()
        // 只摘掉确实被删掉的那几条，而不是丢掉整个媒体库缓存 ——
        // 丢弃整个缓存会让下一次开组又要重扫整张表，在几万张照片的设备上就是好几秒。
        container.repo.dropFromCache(result.markedDelete)
        // 记下"什么时候进的回收站"，回收站页用它算还剩几天。
        // 必须在**删除真正发生之后**记（用户在系统弹窗里点了确认），不是发起请求时。
        if (container.prefs.deleteToTrash && result.markedDelete.isNotEmpty()) {
            container.sessions.markTrashed(result.markedDelete)
        }
        favoriteCount = container.sessions.favoriteCount()
        stats = container.prefs.stats()
        session = SessionUi.None

        // 收藏状态有变化、且开着自动同步，就顺带把系统相册那份也更新掉。
        // 放在最后是有意的：删除的系统弹窗已经在会话页处理完了，这里最多再补一次。
        if (container.prefs.autoSyncFavorites &&
            (result.favoriteAdded.isNotEmpty() || result.favoriteRemoved.isNotEmpty())
        ) {
            startFavoriteSync()
        }

        // 删完之后重新点一遍数量，首页数字才是新的
        scope.launch {
            photoCount = container.repo.idsOf(MediaKind.PHOTO).size
            screenshotCount = container.repo.idsOf(MediaKind.SCREENSHOT).size
            videoCount = container.repo.idsOf(MediaKind.VIDEO).size
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = Surface1) {
                    Tab.entries.forEach { entry ->
                        NavigationBarItem(
                            selected = tab == entry,
                            onClick = { goToTab(entry) },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentKeep,
                                selectedTextColor = AccentKeep,
                                unselectedIconColor = TextTertiary,
                                unselectedTextColor = TextTertiary,
                                indicatorColor = Surface2,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                // ---- 这里**刻意不开** `beyondViewportPageCount` ----
                //
                // 试过设成 1（提前把相邻页组合好），确实能减轻"滑过去那一帧"的组合压力，
                // 但代价更大：启动时**同时**组合三页（喜欢 + 首页 + 收藏），
                // 其中两页都是缩略图网格、各自还要查一次 MediaStore ——
                // 于是后台一直在解码/查询，正要画的那一帧就得和它们抢资源。
                // 症状是"从喜欢页往右滑到首页"比以前更卡（用户反馈 1.3.56 撤回）。
                //
                // 真正的解法是**把那两页本身做轻**（设置页已改成 LazyColumn），
                // 而不是靠多养一页来抢帧。剩下网格页首屏的成本，
                // 需要拿录屏逐帧量才知道值不值得再动，先不猜。
                // 在空白区域左右滑就能切页。内容里的竖向列表不受影响；
                // 横向可滚动的子元素（比如缩略图条）会自己把手势消费掉，不会误触翻页。
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { page ->
                if (page == 0) {
                    FavoritesScreen(
                        container = container,
                        refreshToken = favoriteCount,
                        pendingSync = 0,
                        onFavoritesChanged = {
                            favoriteCount = container.sessions.favoriteCount()
                            scope.launch { pendingSync = container.sessions.pendingSyncCount() }
                        },
                        relativeDates = relativeDates,
                        autoSyncEnabled = false,
                        onRequestSync = {},
                        likeMode = true,
                        resolveLocationLabel = { container.repo.resolveLocationLabel(it) },
                        readExif = { PhotoExifReader.read(context, it) },
                        loadDayItems = { container.repo.allImagesAroundDay(it) },
                        onOpenDaySession = { day ->
                            pagerScope.launch {
                                pagerState.animateScrollToPage(Tab.Review.ordinal + 1)
                            }
                            startDaySession(day)
                        },
                    )
                } else {
                when (Tab.entries[page - 1]) {
                    Tab.Review -> HomeScreen(
                        stats = stats,
                        photoCount = photoCount,
                        screenshotCount = screenshotCount,
                        videoCount = videoCount,
                        favoriteCount = favoriteCount,
                        photoAllowed = photoAllowed,
                        videoAllowed = videoAllowed,
                        doubleTapLikes = container.prefs.doubleTapLikes,
                        swipeUpDeletes = container.prefs.swipeUpDeletes,
                        onStart = { kind -> startSession(kind) },
                        onRequestPermission = { kind ->
                            permissionLauncher.launch(permissionsFor(kind))
                        },
                        loadBuckets = { container.repo.buckets() },
                        onStartBucket = { b -> startSession(b.kind, b.name) },
                    )

                    Tab.Favorites -> FavoritesScreen(
                        container = container,
                        refreshToken = favoriteCount,
                        pendingSync = pendingSync,
                        onFavoritesChanged = {
                            favoriteCount = container.sessions.favoriteCount()
                            scope.launch { pendingSync = container.sessions.pendingSyncCount() }
                        },
                        relativeDates = relativeDates,
                        autoSyncEnabled = container.prefs.autoSyncFavorites,
                        onRequestSync = { startFavoriteSync() },
                        resolveLocationLabel = { container.repo.resolveLocationLabel(it) },
                        readExif = { PhotoExifReader.read(context, it) },
                        loadDayItems = { container.repo.allImagesAroundDay(it) },
                        onOpenDaySession = { day ->
                            pagerScope.launch {
                                pagerState.animateScrollToPage(Tab.Review.ordinal + 1)
                            }
                            startDaySession(day)
                        },
                    )

                    Tab.Trash -> TrashScreen(container = container)

                    Tab.Settings -> SettingsScreen(
                        prefs = container.prefs,
                        stats = stats,
                        permissionTick = permissionTick,
                        onProbeLocation = {
                            container.repo.probeLocationAvailability(MediaKind.PHOTO)
                        },
                        onRequestLocationPermission = {
                            permissionLauncher.launch(
                                arrayOf(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
                            )
                        },
                        relativeDates = relativeDates,
                        onRelativeDatesChange = { relativeDates = it },
                        onSyncFavoritesNow = {
                            // 手动同步走的是和自动同步**同一条链路**（`startFavoriteSync`），
                            // 只是由用户点一下触发 —— 两条路各自实现一遍必然漂移。
                            //
                            // `force = true`：**忽略"已同步"记账，把收藏夹全量重推一遍**。
                            // 记账会失真（导入时就把条目标成了"已同步"），
                            // 而用户点"同步"就是要一个"确定能重新写一遍"的动作。
                            startFavoriteSync()
                            "开始全量写入，请逐批确认系统的弹窗"
                        },
                        pendingFavoriteSync = pendingSync,
                        onImportSystemFavorites = {
                            val keys = container.repo.allSystemFavoriteKeys()
                            val added = container.sessions.importSystemFavorites(keys)
                            if (added > 0) container.persist()
                            when {
                                keys.isEmpty() ->
                                    // 空结果有两种可能：这台设备的相册根本不用这一列标记，
                                    // 或者用户确实一个都没标。都说清楚，别让用户猜。
                                    "系统相册里没有读到任何收藏。这台设备的相册" +
                                        "可能不使用这一列标记，也可能是本来就一个都没标。"
                                added == 0 -> "已经和系统相册一致，没有新增。"
                                else -> "已导入 $added 项到本应用的「收藏」。"
                            }
                        },
                        onStatsReset = {
                            container.prefs.reviewedTotal = 0
                            container.prefs.deletedTotal = 0
                            container.prefs.favoritedTotal = 0
                            container.prefs.freedBytesTotal = 0L
                            container.prefs.roundsCompleted = 0
                            stats = container.prefs.stats()
                        },
                        onOpenAppSettings = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                    )
                }
                }
            }
        }

        when (val current = session) {
            is SessionUi.Loading -> SessionLoadingOverlay(current.kind)
            is SessionUi.Empty -> SessionEmptyCard(
                kind = current.kind,
                queryFailed = current.queryFailed,
                onDismiss = { session = SessionUi.None },
            )

            is SessionUi.Running -> {
                val prefs: Prefs = container.prefs
                if (current.kind.isImage) {
                    PhotoSessionScreen(
                        items = current.items,
                        swipeUpDeletes = prefs.swipeUpDeletes,
                        deleteToTrash = prefs.deleteToTrash,
                        videoMutedByDefault = prefs.videoMuted,
                        isFavorite = { container.sessions.isFavorite(it) },
                        onToggleFavorite = { container.sessions.toggleFavorite(it) },
                        isLiked = { container.sessions.isLiked(it) },
                        onToggleLike = { container.sessions.toggleLike(it) },
                        doubleTapLikes = prefs.doubleTapLikes,
                        relativeDates = prefs.relativeDates,
                        onSeen = { container.sessions.markSeen(listOf(it)) },
                        buildDeleteRequest = buildDeleteRequest,
                        probeMotion = { container.repo.probeMotion(it) },
                        resolveLocation = { container.repo.resolveLocationLabel(it) },
                        readExif = { PhotoExifReader.read(context, it) },
                        resolveMotionOffset = { container.repo.resolveMotionOffset(it) },
                        // 不带 kind：照片和截屏都要，见 allImagesOnDay 的说明。
                        // dayRadius = 1 ⇒ 焦点图那天 + 前后各一天，共三天
                        // （用户要求：只看一天一晃就滑到头了）。
                        loadDayItems = {
                            container.repo.allImagesOnDay(it, dayRadius = 1)
                        },
                        onConclude = { result -> conclude(result) },
                    )
                } else {
                    VideoSessionScreen(
                        items = current.items,
                        deleteToTrash = prefs.deleteToTrash,
                        videoMutedByDefault = prefs.videoMuted,
                        autoPlay = prefs.autoPlayVideo,
                        railOnRight = prefs.railOnRight,
                        doubleTapLikes = prefs.doubleTapLikes,
                        isFavorite = { container.sessions.isFavorite(it) },
                        onToggleFavorite = { container.sessions.toggleFavorite(it) },
                        isLiked = { container.sessions.isLiked(it) },
                        onToggleLike = { container.sessions.toggleLike(it) },
                        onSeen = { container.sessions.markSeen(listOf(it)) },
                        buildDeleteRequest = buildDeleteRequest,
                        resolveLocation = { container.repo.resolveLocationLabel(it) },
                        shareItem = shareItem,
                        onConclude = { result -> conclude(result) },
                    )
                }
            }

            SessionUi.None -> Unit
        }

        // 收藏同步的结果提示。过去这类失败是静默的，用户只能对着空的收藏夹猜原因。
        syncNotice?.let { notice ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 100.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface2)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun SessionLoadingOverlay(kind: MediaKind) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentKeep,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "正在随机抽取这一组${kind.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun SessionEmptyCard(
    kind: MediaKind,
    queryFailed: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface2)
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (queryFailed) "读取失败" else "没有找到${kind.label}",
                style = MaterialTheme.typography.titleLarge,
                color = if (queryFailed) AccentDelete else TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (queryFailed) {
                    "系统媒体库拒绝了这次查询，所以一项都读不出来。\n" +
                        "这通常是权限问题，也可能是这台设备的媒体库实现有差异。\n" +
                        "「整理视频」等其他入口可能仍然正常。"
                } else {
                    "设备上暂时没有可整理的${kind.label}。\n" +
                        "如果内容确实存在，可能是还没授予对应的媒体权限。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentKeep,
                    contentColor = Ink,
                ),
            ) {
                Text("知道了", fontWeight = FontWeight.Medium)
            }
        }
    }
}
