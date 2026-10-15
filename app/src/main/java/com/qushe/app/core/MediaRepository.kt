package com.qushe.app.core

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * MediaStore 访问层。
 *
 * 删除走的是系统提供的两个受控入口，而不是直接写文件：
 *  - [requestTrash]        -> 移入系统回收站，30 天内可恢复
 *  - [requestDeleteForever] -> 彻底删除
 * 两者都会弹一次系统授权对话框，所以调用方必须「批量」使用，
 * 绝不能每滑一条就调一次，否则会被弹窗淹没。
 */
class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * 每个分类只缓存 **ID 列表**，不缓存完整条目。
     *
     * 这是开组速度的关键。把整张表读进来（几万行 × 10 列）光填充 CursorWindow
     * 就要一两秒 —— 照片比视频慢就是这个原因：表更大，而且图片那条路径
     * 每行还要在 Kotlin 里拼字符串做截屏判定。
     *
     * 现在改成：
     *  1. 只查一列 `_ID`（几万行 × 1 列，比 10 列快一个数量级）；
     *  2. 截屏 / 照片的区分交给 SQL 的 `LIKE` 去做，不在 Kotlin 里逐行匹配；
     *  3. 抽样之后只对选中的那十几条再取完整信息。
     * 于是「开组」的代价与媒体库有多大彻底解耦。
     */
    private val idIndex = HashMap<MediaKind, List<Long>>()
    private val idIndexLoadedAt = HashMap<MediaKind, Long>()

    /** 同一时刻只允许一个协程去填缓存，避免并发触发重复的全量查询。 */
    private val loadLock = Mutex()

    /** 实况照片的探测结果缓存，避免同一张图反复读文件头。 */
    private val motionProbe = HashMap<Long, MotionPhoto.Resolution>()

    /** 拍摄地点的缓存。地名反查有成本，同一条不必算两次。 */
    private val locationCache = HashMap<Long, String?>()

    /**
     * 上一次媒体查询是否彻底失败（完整投影和最小投影都没成功）。
     *
     * 界面必须据此区分「相册里确实没有内容」和「内容读不出来」——
     * 这两件事对用户的含义完全不同。曾经因为把查询异常静默吞成空列表，
     * 表现成了"照片 0 张"，排查花了很久。
     */
    var lastQueryFailed: Boolean = false
        private set

    /**
     * 廉价探测：这张是不是实况照片、元数据里有没有现成的视频偏移量。
     *
     * 每张照片切到前台时都会调用，所以最多只读一次 128KB 文件头，不做全文件扫描。
     * 结果按条目缓存，来回翻看不会重复读。
     */
    suspend fun probeMotion(item: MediaItem): MotionPhoto.Resolution =
        withContext(Dispatchers.IO) {
            val key = item.storeKey()
            motionProbe[key]?.let { return@withContext it }
            val result = MotionPhoto.probe(
                context = context,
                uri = item.uri,
                knownFromColumn = item.motionOffsetFromEnd,
            )
            motionProbe[key] = result
            result
        }

    /**
     * 真正要播放时才做的深度解析。
     *
     * 如果元数据给不出偏移量，就会扫全文件找 MP4 的 `ftyp` 签名 ——
     * 这一步要读完整个文件（可能几十 MB），所以只允许在用户明确长按播放时触发。
     */
    suspend fun resolveMotionOffset(item: MediaItem): Long? = withContext(Dispatchers.IO) {
        val probe = probeMotion(item)

        // 元数据给的偏移**必须先验证**再用。
        // 新容器格式里的 Item:Length 是项目长度，不等于"距文件尾的字节数"，
        // 直接拿它切出来的文件是坏的 —— 表现出来就是"长按播不出来"。
        // 验证不过就落到下面那条不依赖任何厂商字段的签名扫描。
        val fromMetadata = probe.offsetFromEnd
        if (fromMetadata != null && fromMetadata > 0L &&
            fromMetadata < item.sizeBytes &&
            MotionPhoto.looksLikeMp4At(context, item.uri, item.sizeBytes - fromMetadata)
        ) {
            return@withContext fromMetadata
        }

        val scanned = MotionPhoto.findByMp4Signature(context, item.uri, item.sizeBytes)
        if (scanned != null) {
            motionProbe[item.storeKey()] = MotionPhoto.Resolution(true, scanned)
        }
        scanned
    }

    /**
     * 取某个分类的全部 ID。
     *
     * 只投影 `_ID` 一列 —— 这是「开组快要等两秒」的解法核心。
     * 结果按分类缓存，删除时走 [dropFromCache] 增量摘除，另有 TTL 兜底。
     */
    /**
     * 「回到那天」用：某一天的起始毫秒（本地时区）。
     *
     * 委托到 [dayStartOf] 顶层函数 —— UI 层进日期视图时也要算同一个东西，
     * 两处各写一份 Calendar 很容易在时区边界上分叉。
     */
    fun dayStartOf(millis: Long): Long = com.qushe.app.core.dayStartOf(millis)

    /**
     * 某一天里"有内容"的日期与条数，按日期从新到旧。
     *
     * **当前没有调用方，刻意留着。** 「回到那天」原来用它喂一个日期选择面板，
     * 那个面板已被"Yes 手指出现在哪天、就跳到哪天"的整屏日期视图取代。
     * 保留是因为 [itemsOnDay] 的口径说明引用它，而且"哪些日子有内容"
     * 对将来做日期刻度尺 / 日历视图仍是现成的原料。
     */
    suspend fun dayHistogram(kind: MediaKind): List<Pair<Long, Int>> = withContext(Dispatchers.IO) {
        val counts = HashMap<Long, Int>()
        // 复用同一个 Calendar。十几万行逐行 new 一个的话，光分配的开销就可感 ——
        // 这个函数只在打开日期面板时跑，但也不该白白慢一倍。
        val cal = Calendar.getInstance()
        runCatching {
            resolver.query(
                collectionOf(kind),
                arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED),
                selectionOf(kind),
                argsOf(kind),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    // DATE_TAKEN 是拍摄时间，绝大多数照片都有；
                    // 没有的（比如部分下载图）退回 DATE_ADDED，那个是秒
                    val taken = if (c.isNull(0)) 0L else c.getLong(0)
                    val millis = if (taken > 0L) taken else c.getLong(1) * 1000L
                    cal.timeInMillis = millis
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    val day = cal.timeInMillis
                    counts[day] = (counts[day] ?: 0) + 1
                }
            }
        }.onFailure { Log.e(TAG, "读取日期分布失败", it) }
        counts.entries.sortedByDescending { it.key }.map { it.key to it.value }
    }

    /**
     * 取某一天的全部内容，按时间从新到旧。
     *
     * 和 [dayHistogram] 的口径**必须完全一致**，否则会出现"面板说有 128 项、
     * 点进去只剩一半"的矛盾。一致点有两个：
     *  1. 照片 / 截屏的分流（截屏判定的 LIKE 条件）要跟着 kind 走；
     *  2. `DATE_TAKEN` 为空时按 `DATE_ADDED`（秒）退回 —— 这个回退在 SQL 里
     *     写不干净（要 COALESCE + 单位换算，部分 ROM 的 Provider 不接受），
     *     所以先用"两个区间 OR"取超集，再在 Kotlin 里按同一套
     *     [MediaItem.dateTakenMillis] 规则精确过滤。
     */
    /**
     * 某一天（及前后各 [dayRadius] 天）的全部**图片**内容（照片 + 截屏）。
     *
     * ---- 为什么单独有这个口径 ----
     * 「回到那天」要还原的是"那一天我拍了什么"，而照片和截屏在
     * MediaStore 里是靠路径区分的（`selectionOf` 一个 `NOT 截屏路径`、
     * 一个 `截屏路径`）—— 它们是同一天记忆的两半，不该因为用户
     * 从哪个入口进来就只看一半。
     *
     * 用户报："整理截屏时的「回到那天」不能只有截屏，也要包括其他的照片"；
     * 同理整理照片时也该看到那天的截屏。
     *
     * 视频不并入：视频走的是抖音式整理页，卡片堆叠这一套装不下它。
     */
    suspend fun allImagesOnDay(dayStart: Long, dayRadius: Int = 0): List<MediaItem> =
        withContext(Dispatchers.IO) {
            // ---- [dayRadius]：往前/往后各带几天 ----
            //
            // 「回到那天」传的是 1 ⇒ 焦点图那天 + 前后各一天，共三天。
            // 只看一天的话一晃就滑到头了（一天往往就十几张），
            // 而"那天"的记忆本来就是连着的 —— 头天晚上拍的、第二天早上拍的，
            // 在人的印象里属于同一件事。
            val from = dayStart - dayRadius * 86_400_000L
            val dayEnd = dayStart + (dayRadius + 1) * 86_400_000L
            // 不带 kind 的 selection ⇒ 只要"是图片"（collectionOf 已经限定 Images 表）
            val dateSel = "((${MediaStore.MediaColumns.DATE_TAKEN} >= ? AND " +
                "${MediaStore.MediaColumns.DATE_TAKEN} < ?) OR " +
                "(${MediaStore.MediaColumns.DATE_ADDED} >= ? AND " +
                "${MediaStore.MediaColumns.DATE_ADDED} < ?))"
            val args = arrayOf(
                from.toString(),
                dayEnd.toString(),
                (from / 1000L).toString(),
                (dayEnd / 1000L).toString(),
            )

            runCatching {
                // defaultKind 传 PHOTO：`parseItems` 本来就**逐条**用
                // `looksLikeScreenshot` 判截屏，只有 defaultKind == VIDEO 才强制。
                // 所以这里拿到的是照片与截屏混在一起、各自 kind 正确的结果。
                runSelectionQuery(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    defaultKind = MediaKind.PHOTO,
                    selection = dateSel,
                    selectionArgs = args,
                    minimal = false,
                )
            }.onFailure { Log.e(TAG, "读取某天的图片失败", it) }.getOrDefault(emptyList())
                .filter {
                    val d = dayStartOf(it.dateTakenMillis)
                    d >= from && d < dayEnd
                }
        }

    /**
     * 取焦点图那一天 ± [minRadius] 天的图片，**并且保证前后都至少有一天有内容**。
     *
     * ---- 为什么不能直接用 [allImagesOnDay] ----
     * 它按固定半径取，前一天恰好一张都没有的情况很常见（周一拍的、周日歇手）。
     * 于是瀑布流往左一滑就到头，"前后各一天"这个卖点等于名存实亡，
     * 而且断点正好出现在焦点图旁边，看着像 bug（用户反馈"有点突兀"）。
     *
     * 所以这里逐轮往外扩：某一侧为空就把半径翻倍再查一次，
     * 直到两侧都有内容、或者到 [maxRadius]、或者再查也查不到更多为止。
     * 多出来的查询只在"某一侧为空"时才发生，正常情况下一次就够。
     */
    suspend fun allImagesAroundDay(
        dayStart: Long,
        minRadius: Int = 1,
        maxRadius: Int = 7,
    ): List<MediaItem> {
        var radius = minRadius.coerceAtLeast(0)
        var result = allImagesOnDay(dayStart, radius)
        val anchorDay = dayStartOf(dayStart)
        repeat(4) {
            val hasEarlier = result.any { dayStartOf(it.dateTakenMillis) < anchorDay }
            val hasLater = result.any { dayStartOf(it.dateTakenMillis) > anchorDay }
            if (hasEarlier && hasLater) return result
            if (radius >= maxRadius) return result
            val next = (radius * 2 + 1).coerceAtMost(maxRadius)
            if (next <= radius) return result
            radius = next
            val wider = allImagesOnDay(dayStart, radius)
            // 一张都没多 ⇒ 再往外也没有了，别白查
            if (wider.size <= result.size) return result
            result = wider
        }
        return result
    }

    suspend fun itemsOnDay(kind: MediaKind, dayStart: Long): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val dayEnd = dayStart + 86_400_000L
            val base = selectionOf(kind)
            val dateSel = "((${MediaStore.MediaColumns.DATE_TAKEN} >= ? AND " +
                "${MediaStore.MediaColumns.DATE_TAKEN} < ?) OR " +
                "(${MediaStore.MediaColumns.DATE_ADDED} >= ? AND " +
                "${MediaStore.MediaColumns.DATE_ADDED} < ?))"
            val selection = if (base == null) dateSel else "$dateSel AND $base"
            val args = arrayOf(
                dayStart.toString(),
                dayEnd.toString(),
                // DATE_ADDED 的单位是秒
                (dayStart / 1000L).toString(),
                (dayEnd / 1000L).toString(),
            ) + (argsOf(kind) ?: emptyArray())

            runCatching {
                runSelectionQuery(collectionOf(kind), kind, selection, args, minimal = false)
            }.onFailure { Log.e(TAG, "读取某天的内容失败", it) }.getOrDefault(emptyList())
                // 再用"有效的拍摄时间"过一遍 —— 与 dayHistogram 统计用的是同一份规则
                .filter { dayStartOf(it.dateTakenMillis) == dayStart }
        }

    private fun collectionOf(kind: MediaKind): Uri = if (kind == MediaKind.VIDEO) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun selectionOf(kind: MediaKind): String? = when (kind) {
        MediaKind.VIDEO -> null
        MediaKind.SCREENSHOT -> SCREENSHOT_SELECTION
        MediaKind.PHOTO -> "NOT $SCREENSHOT_SELECTION"
    }

    private fun argsOf(kind: MediaKind): Array<String>? =
        if (kind == MediaKind.VIDEO) null else SCREENSHOT_ARGS

    suspend fun idsOf(kind: MediaKind, forceReload: Boolean = false): List<Long> =
        withContext(Dispatchers.IO) {
            idIndex[kind]?.let { cached ->
                if (!forceReload && !isStale(idIndexLoadedAt[kind] ?: 0L)) return@withContext cached
            }

            // 加锁 + 双检：应用启动时的预热和用户点开组可能同时发生，
            // 没有这层保护会并发跑两次全量查询，白白多等一倍。
            loadLock.withLock {
                idIndex[kind]?.let { cached ->
                    if (!forceReload && !isStale(idIndexLoadedAt[kind] ?: 0L)) {
                        return@withContext cached
                    }
                }
                lastQueryFailed = false
                val ids = queryIds(kind)
                idIndex[kind] = ids
                idIndexLoadedAt[kind] = SystemClock.elapsedRealtime()
                ids
            }
        }

    private fun queryIds(kind: MediaKind): List<Long> = when (kind) {
        MediaKind.VIDEO ->
            rawIds(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)

        MediaKind.SCREENSHOT -> rawIds(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            SCREENSHOT_SELECTION,
            SCREENSHOT_ARGS,
        )

        MediaKind.PHOTO -> rawIds(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "NOT $SCREENSHOT_SELECTION",
            SCREENSHOT_ARGS,
        )
    }

    private fun rawIds(
        collection: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): List<Long> {
        val ids = ArrayList<Long>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                selectionArgs,
                null,
            )?.use { c ->
                while (c.moveToNext()) ids += c.getLong(0)
            }
        }.onFailure {
            Log.e(TAG, "读取 ID 列表失败", it)
            lastQueryFailed = true
        }
        return ids
    }

    /**
     * 从某个分类里随机抽样。
     *
     * 直接在 ID 列表上洗牌后按序取 —— 列表里只是 Long，几万个也只有几百 KB，
     * 几毫秒就能洗完；比"把完整条目读进来再随机"便宜得多。
     *
     * 返回的第二个值表示「没看过的已经抽干了」：如果遍历完整个 ID 列表
     * 都没凑够数量，说明符合条件的确实不多了。
     */
    suspend fun sampleItems(
        kind: MediaKind,
        wanted: Int,
        exclude: Set<Long> = emptySet(),
    ): Pair<List<MediaItem>, Boolean> {
        val ids = idsOf(kind)
        if (ids.isEmpty()) return emptyList<MediaItem>() to false

        val picked = ArrayList<Long>(wanted)
        for (id in ids.shuffled()) {
            if (picked.size >= wanted) break
            if (storeKeyOf(kind, id) in exclude) continue
            picked += id
        }
        val exhausted = picked.size < wanted
        if (picked.isEmpty()) return emptyList<MediaItem>() to exhausted

        // 只对这十几条取完整信息 —— 顺带把 in-memory 顺序还原成抽样顺序
        val byKey = byKeys(picked.map { storeKeyOf(kind, it) }.toSet()).associateBy { it.storeKey() }
        return picked.mapNotNull { byKey[storeKeyOf(kind, it)] } to exhausted
    }

    private fun storeKeyOf(kind: MediaKind, id: Long): Long =
        id * 2L + if (kind == MediaKind.VIDEO) 1L else 0L


    /**
     * 缓存是否过期。
     *
     * 有了 TTL，就不必为了「怕数据旧了」而在每次开组时重扫整张表 ——
     * 那正是之前照片开组要等好几秒的原因。删除走 [dropFromCache] 增量摘除，
     * 时间维度则交给这个窗口兜底。
     */
    private fun isStale(loadedAt: Long): Boolean =
        loadedAt == 0L || SystemClock.elapsedRealtime() - loadedAt > CACHE_TTL_MS

    /**
     * 增量地把已经不存在的条目摘掉。
     *
     * 相比 [invalidate] 直接丢弃整个缓存，这里只移除确实被删掉的那几条 ——
     * 下一次开组立刻命中缓存，不必重扫媒体库。
     */
    fun dropFromCache(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val goneImageIds = items.filter { it.kind.isImage }.map { it.id }.toHashSet()
        val goneVideoIds = items.filter { it.kind == MediaKind.VIDEO }.map { it.id }.toHashSet()

        // 照片和截屏共用同一张 images 表，两个 ID 列表都要摘
        if (goneImageIds.isNotEmpty()) {
            idIndex[MediaKind.PHOTO]?.let { cached ->
                idIndex[MediaKind.PHOTO] = cached.filterNot { it in goneImageIds }
            }
            idIndex[MediaKind.SCREENSHOT]?.let { cached ->
                idIndex[MediaKind.SCREENSHOT] = cached.filterNot { it in goneImageIds }
            }
        }
        if (goneVideoIds.isNotEmpty()) {
            idIndex[MediaKind.VIDEO]?.let { cached ->
                idIndex[MediaKind.VIDEO] = cached.filterNot { it in goneVideoIds }
            }
        }

        val goneKeys = items.map { it.storeKey() }.toHashSet()
        motionProbe.keys.removeAll(goneKeys)
        locationCache.keys.removeAll(goneKeys)
    }

    /**
     * 按 [storeKey] 精确取内容。
     *
     * 收藏夹只关心"我收藏的那几百条"，过去是把整个媒体库扫出来再过滤 ——
     * 在几万条视频的设备上要等好几秒，页面看上去就像空的。
     * 这里改成 `_ID IN (...)` 精确定位，代价只和要取的数量相关，与库的大小无关。
     *
     * 回收站清理、收藏同步也都走这里，都不再需要全量扫描。
     */
    suspend fun byKeys(keys: Set<Long>): List<MediaItem> = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext emptyList()

        // storeKey 的最低位编码了"是不是视频"，这里拆回去
        val imageIds = keys.filter { it % 2L == 0L }.map { it / 2L }
        val videoIds = keys.filter { it % 2L == 1L }.map { (it - 1L) / 2L }

        buildList {
            addAll(queryByIds(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaKind.PHOTO, imageIds))
            addAll(queryByIds(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaKind.VIDEO, videoIds))
        }
    }

    private fun queryByIds(
        collection: Uri,
        defaultKind: MediaKind,
        ids: List<Long>,
    ): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val result = ArrayList<MediaItem>()

        // 分块：SQLite 的 IN 子句参数个数有上限（旧版本只有 999）
        ids.chunked(ID_QUERY_CHUNK).forEach { chunk ->
            val selection = "${MediaStore.MediaColumns._ID} IN " +
                chunk.joinToString(prefix = "(", postfix = ")", separator = ",") { "?" }
            val args = chunk.map { it.toString() }.toTypedArray()

            result += try {
                runSelectionQuery(collection, defaultKind, selection, args, minimal = false)
            } catch (t: Throwable) {
                Log.w(TAG, "按 ID 查询失败，退回最小投影重试", t)
                try {
                    runSelectionQuery(collection, defaultKind, selection, args, minimal = true)
                } catch (t2: Throwable) {
                    Log.e(TAG, "按 ID 查询彻底失败", t2)
                    emptyList()
                }
            }
        }
        return result
    }

    /**
     * 核对系统是否真的把这些项标记成了收藏，返回确认成功的那些。
     *
     * **系统弹窗点了"允许"不等于真的落库了** —— 部分厂商的相册应用完全不使用
     * MediaStore 的 IS_FAVORITE 标记（各家对"收藏"的实现不一样）。
     * 所以写入之后必须读回来核对，界面才能给出诚实的结论，
     * 而不是让用户对着一个永远空的收藏夹猜。
     *
     * 只投影 _ID 和 IS_FAVORITE 两个普通列，不涉及任何受权限约束的敏感列。
     */
    suspend fun systemFavoriteKeys(items: List<MediaItem>): Set<Long> =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext emptySet()
            buildSet {
                addAll(
                    confirmedFavoriteKeys(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        items.filter { it.kind.isImage },
                    )
                )
                addAll(
                    confirmedFavoriteKeys(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        items.filter { it.kind == MediaKind.VIDEO },
                    )
                )
            }
        }

    /**
     * 读出**整个媒体库**里被系统标记为收藏的那些（[MediaStore.MediaColumns.IS_FAVORITE] = 1）。
     *
     * 和 [systemFavoriteKeys] 的区别：那个是"我刚写进去的这些核对一下"，
     * 按 ID 分批精确查；这个是反向导入——把系统相册里已有的收藏全部捞回来。
     *
     * 只投影 `_ID` 一列（普通列，不涉及受权限约束的敏感字段）。
     * 照片与视频各查一次，因为它们在 MediaStore 里是两个 collection；
     * 返回的是 [storeKey]（`id * 2 + 是否视频`），和应用内那套集合对得上。
     */
    suspend fun allSystemFavoriteKeys(): Set<Long> = withContext(Dispatchers.IO) {
        buildSet {
            addAll(favoriteIdsIn(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false))
            addAll(favoriteIdsIn(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true))
        }
    }

    private fun favoriteIdsIn(collection: Uri, video: Boolean): Set<Long> {
        val out = HashSet<Long>()
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.IS_FAVORITE} = 1",
                null,
                null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    out += if (video) id * 2L + 1L else id * 2L
                }
            }
        }.onFailure { Log.w(TAG, "读取系统收藏失败", it) }
        return out
    }

    private fun confirmedFavoriteKeys(collection: Uri, items: List<MediaItem>): Set<Long> {
        if (items.isEmpty()) return emptySet()
        val byId = items.associateBy { it.id }
        val confirmed = HashSet<Long>()

        items.map { it.id }.chunked(ID_QUERY_CHUNK).forEach { chunk ->
            val selection = "${MediaStore.MediaColumns._ID} IN " +
                chunk.joinToString(prefix = "(", postfix = ")", separator = ",") { "?" } +
                " AND ${MediaStore.MediaColumns.IS_FAVORITE} = 1"
            val args = chunk.map { it.toString() }.toTypedArray()

            runCatching {
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    selection,
                    args,
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        byId[c.getLong(0)]?.let { confirmed += it.storeKey() }
                    }
                }
            }.onFailure { Log.w(TAG, "核验系统收藏状态失败", it) }
        }
        return confirmed
    }

    /**
     * 位置读取自检。
     *
     * 回答"到底卡在哪一步"。这个问题光看界面分辨不出来 ——
     * 没授权、列读不出来、内容本身没有位置信息，三种情况的界面表现**完全一样**
     * （都是不显示地点那一行）。所以把结论做成一句能直接看的话。
     */
    suspend fun probeLocationAvailability(kind: MediaKind, sample: Int = 60): LocationProbe =
        withContext(Dispatchers.IO) {
            val granted = hasLocationPermission(context)
            // 必须**随机**抽，不能 take(n) —— 那只会在库里取到最旧的 n 张，
            // 而最旧的往往是截图和下载图，全都带不动位置信息，结论就假了。
            val ids = idsOf(kind).shuffled().take(sample)
            if (ids.isEmpty()) {
                return@withContext LocationProbe(
                    hasPermission = granted,
                    scanned = 0,
                    withPoint = 0,
                    readFailed = false,
                    columnMissing = false,
                )
            }

            val items = byKeys(ids.map { storeKeyOf(kind, it) }.toSet())
            var withPoint = 0
            var readFailed = false
            var columnMissing = false
            // 注意用 for 而不是 forEach —— readPoint 是挂起函数，非挂起 lambda 里调不了
            for (item in items) {
                if (GeoLocation.readPoint(context, item) != null) withPoint++
                if (GeoLocation.lastReadFailed) readFailed = true
                if (GeoLocation.lastColumnMissing) columnMissing = true
            }
            LocationProbe(granted, items.size, withPoint, readFailed, columnMissing)
        }

    /** 位置自检的结论。 */
    data class LocationProbe(
        val hasPermission: Boolean,
        val scanned: Int,
        val withPoint: Int,
        val readFailed: Boolean,
        /** 系统压根没返回经纬度这两列 —— 和"返回了空值"是两回事，必须分开报。 */
        val columnMissing: Boolean,
    )

    /** 系统回收站里的内容。部分机型/三方 ROM 不支持，失败时返回空列表而不是崩溃。 */
    suspend fun trashed(): List<MediaItem> = withContext(Dispatchers.IO) {
        runCatching {
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            }
            // DATE_EXPIRES = 系统回收站的到期时刻（保留 30 天就是它 + 30 天）。
            // 只在这里多投影一列 —— 别的地方不需要，也不该为它承担厂商 Provider 的风险。
            val extra = arrayOf(MediaStore.MediaColumns.DATE_EXPIRES)
            queryCollection(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaKind.PHOTO, args, extra,
            ) + queryCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaKind.VIDEO, args, extra,
            )
        }.getOrDefault(emptyList())
    }

    fun invalidate() {
        idIndex.clear()
        idIndexLoadedAt.clear()
        motionProbe.clear()
        locationCache.clear()
        GeoLocation.clearCache()
    }

    /**
     * 解析某一条的拍摄地点（地名）。
     *
     * 需要 `ACCESS_MEDIA_LOCATION` 权限才拿得到经纬度；没授权、或这条本来就没有
     * 位置信息（下载来的视频基本都没有）时返回 null，界面直接不显示这一行。
     */
    suspend fun resolveLocationLabel(item: MediaItem): String? = withContext(Dispatchers.IO) {
        val key = item.storeKey()
        if (locationCache.containsKey(key)) return@withContext locationCache[key]

        val point = GeoLocation.readPoint(context, item)
        val label = point?.let { GeoLocation.describe(context, it) }
        locationCache[key] = label
        label
    }

    // ---- 删除入口：全部返回 IntentSender，由界面层用 launcher 触发 ----
    // 注意 MediaStore.createTrashRequest / createDeleteRequest / createFavoriteRequest
    // 返回的都是 PendingIntent，必须再取 .intentSender 才能交给
    // ActivityResultContracts.StartIntentSenderForResult。

    /** 移入回收站（可恢复）。 */
    fun requestTrash(items: List<MediaItem>): IntentSender =
        MediaStore.createTrashRequest(resolver, items.take(MAX_DELETE_BATCH).map { it.uri }, true)
            .intentSender

    /** 从回收站恢复。 */
    fun requestRestore(items: List<MediaItem>): IntentSender =
        MediaStore.createTrashRequest(resolver, items.take(MAX_DELETE_BATCH).map { it.uri }, false)
            .intentSender

    /** 彻底删除，不可恢复。 */
    fun requestDeleteForever(items: List<MediaItem>): IntentSender =
        MediaStore.createDeleteRequest(resolver, items.take(MAX_DELETE_BATCH).map { it.uri })
            .intentSender

    /**
     * 写入系统相册的「收藏」（MediaStore 的 IS_FAVORITE 列）。
     *
     * 这一列应用不能直接改 —— 非文件所有者没有写权限。唯一的正规入口就是这个
     * createFavoriteRequest，它会弹一次系统授权框，用户在框里确认后才真正落库。
     * 因为是「一次请求弹一次框」，绝对不能在下滑收藏的瞬间调用，必须攒起来批量提交。
     *
     * API 30 起提供，与本工程 minSdk 一致，无需版本判断。
     */
    fun requestFavorite(items: List<MediaItem>, favorite: Boolean): IntentSender =
        MediaStore.createFavoriteRequest(
            resolver,
            items.take(MAX_DELETE_BATCH).map { it.uri },
            favorite,
        ).intentSender

    // ---- 内部查询 ----

    private fun queryCollection(
        collection: Uri,
        defaultKind: MediaKind,
        queryArgs: Bundle?,
        /** 只在这一次查询里额外要的列（见 [trashed] 里的 DATE_EXPIRES）。 */
        extraProjection: Array<String> = emptyArray(),
    ): List<MediaItem> {
        // 两级投影。第一级失败时必须能退到最小投影，否则任何一列在某个厂商的
        // MediaProvider 上不可用，整个相册就会显示成"空的"—— 而用户完全不知道为什么。
        return try {
            val result = runQuery(collection, defaultKind, queryArgs, minimal = false, extraProjection)
            lastQueryFailed = false
            result
        } catch (t: Throwable) {
            Log.w(TAG, "完整投影查询失败，退回最小投影重试：$collection", t)
            try {
                val result = runQuery(collection, defaultKind, queryArgs, minimal = true)
                lastQueryFailed = false
                result
            } catch (t2: Throwable) {
                Log.e(TAG, "查询彻底失败：$collection", t2)
                lastQueryFailed = true
                emptyList()
            }
        }
    }

    private fun runQuery(
        collection: Uri,
        defaultKind: MediaKind,
        queryArgs: Bundle?,
        minimal: Boolean,
        extraProjection: Array<String> = emptyArray(),
    ): List<MediaItem> {
        val projection = if (minimal) {
            MINIMAL_PROJECTION
        } else {
            projectionFor(defaultKind) + extraProjection
        }
        val cursor = if (queryArgs == null) {
            resolver.query(collection, projection, null, null, SORT_ORDER)
        } else {
            resolver.query(collection, projection, queryArgs, null)
        }
        return parseItems(cursor, collection, defaultKind)
    }

    private fun runSelectionQuery(
        collection: Uri,
        defaultKind: MediaKind,
        selection: String,
        selectionArgs: Array<String>,
        minimal: Boolean,
    ): List<MediaItem> {
        val projection = if (minimal) MINIMAL_PROJECTION else projectionFor(defaultKind)
        val cursor = resolver.query(collection, projection, selection, selectionArgs, SORT_ORDER)
        return parseItems(cursor, collection, defaultKind)
    }

    private fun parseItems(
        cursor: Cursor?,
        collection: Uri,
        defaultKind: MediaKind,
    ): List<MediaItem> {
        val items = ArrayList<MediaItem>()

        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
            if (idCol < 0) return items

            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val addedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val takenCol = c.getColumnIndex(dateTakenColumn(defaultKind))
            val durationCol = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val bucketCol = c.getColumnIndex(bucketColumn(defaultKind))
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val relativeCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            // 只有回收站那次查询会投影它；别处查不到这一列时 getColumnIndex 返回 -1，
            // 下面的 `takeIf { it >= 0 }` 就把它当成"没有到期时间"。
            val expiresCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val addedSeconds = if (addedCol >= 0) c.getLong(addedCol) else 0L

                var taken = if (takenCol >= 0 && !c.isNull(takenCol)) c.getLong(takenCol) else 0L
                if (taken <= 0L) taken = addedSeconds * 1000L

                val name = if (nameCol >= 0) c.getString(nameCol).orEmpty() else ""
                val bucket = if (bucketCol >= 0) c.getString(bucketCol).orEmpty() else ""
                val relative = if (relativeCol >= 0) c.getString(relativeCol).orEmpty() else ""
                val mime = if (mimeCol >= 0) c.getString(mimeCol).orEmpty() else ""

                val kind = when {
                    defaultKind == MediaKind.VIDEO -> MediaKind.VIDEO
                    looksLikeScreenshot(bucket, relative, name) -> MediaKind.SCREENSHOT
                    else -> MediaKind.PHOTO
                }

                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    kind = kind,
                    name = name,
                    sizeBytes = if (sizeCol >= 0) c.getLong(sizeCol) else 0L,
                    dateTakenMillis = taken,
                    durationMillis =
                        if (kind == MediaKind.VIDEO && durationCol >= 0 && !c.isNull(durationCol)) {
                            c.getLong(durationCol)
                        } else {
                            0L
                        },
                    bucket = bucket,
                    dateExpiresMillis = expiresCol.takeIf { it >= 0 && !c.isNull(it) }
                        ?.let { c.getLong(it) },
                    width = if (widthCol >= 0) c.getInt(widthCol) else 0,
                    height = if (heightCol >= 0) c.getInt(heightCol) else 0,
                    mimeType = mime,
                    // 实况照片不在这里判断 —— 见 projectionFor 的说明，
                    // 真正的探测由 MotionPhoto.probe() 读文件头完成
                    motionOffsetFromEnd = null,
                )
            }
        }
        return items
    }

    private fun projectionFor(kind: MediaKind): Array<String> {
        val base = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            dateTakenColumn(kind),
            bucketColumn(kind),
        )
        if (kind == MediaKind.VIDEO) {
            base += MediaStore.Video.Media.DURATION
        }
        // 注意：这里**绝对不能**加入 MediaStore.MediaColumns.XMP。
        // 那一列用于位置隐私脱敏，受 ACCESS_MEDIA_LOCATION 约束，
        // 部分厂商的 MediaProvider（含小米）只要在投影里看到它就直接抛异常，
        // 结果是一张照片都读不出来 —— 而异常被上层吞掉后表现为
        // "照片 0 张、视频正常"，极难排查。实况照片需要的 XMP 改由
        // MotionPhoto.probe() 直接读文件头获取，不需要任何额外权限。
        return base.toTypedArray()
    }

    private fun dateTakenColumn(kind: MediaKind): String = if (kind == MediaKind.VIDEO) {
        MediaStore.Video.Media.DATE_TAKEN
    } else {
        MediaStore.Images.Media.DATE_TAKEN
    }

    private fun bucketColumn(kind: MediaKind): String = if (kind == MediaKind.VIDEO) {
        MediaStore.Video.Media.BUCKET_DISPLAY_NAME
    } else {
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    }

    companion object {
        private const val TAG = "MediaRepository"

        /** 系统限制：单次删除请求最多 2000 个 Uri。界面层需要据此提示用户分批处理。 */
        const val MAX_DELETE_BATCH = 2000

        /** `_ID IN (...)` 的分块大小。SQLite 旧版本对 IN 的参数个数上限只有 999。 */
        private const val ID_QUERY_CHUNK = 500

        /**
         * 媒体库缓存的存活时间。
         *
         * 删除走增量摘除，所以这个窗口只需要覆盖「应用运行期间用户在别处新增了内容」
         * 这一种情况。定得太短会让开组重新变慢，太长则新内容迟迟不出现。
         */
        private const val CACHE_TTL_MS = 5 * 60 * 1000L

        /**
         * 兜底用的最小投影。
         *
         * 只留最基本、任何 Android 版本与任何厂商实现都一定提供的几列。
         * 完整投影里只要有一列在某个 MediaProvider 上不可用，整条查询就会失败；
         * 退到这一组至少能保证内容正常显示出来（代价是时间、相册名、尺寸等退化为默认值）。
         */
        private val MINIMAL_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
        )

        /**
         * 截屏判定线索。
         *
         * 各家系统把截屏放在同名目录下（`Pictures/Screenshots` 或中文「截图」），
         * 所以按 bucket / 相对路径 / 文件名三个维度一起匹配，命中任一即算。
         * 这是启发式判断，不是系统提供的权威标记 —— Android 并没有「这是截屏」这种元数据。
         */
        private val SCREENSHOT_HINTS = listOf(
            "screenshot", "screen_shot", "screen-shot", "screencapture",
            "截屏", "截图", "屏幕截图",
        )

        /**
         * 截屏判定下推到 SQLite。
         *
         * 在几万行的表上，让数据库用 `LIKE` 去筛比在 Kotlin 里逐行拼字符串匹配快得多，
         * 而且这样查询只需要投影 `_ID` 一列 —— 这正是「开组秒开」的前提。
         *
         * SQLite 的 `LIKE` 对 ASCII 天然不区分大小写（对应原来的 ignoreCase），
         * 对中文则是精确字节匹配，语义与之前一致。
         */
        private val SCREENSHOT_SELECTION: String = SCREENSHOT_HINTS
            .flatMap {
                listOf(
                    "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} LIKE ?",
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                )
            }
            .joinToString(separator = " OR ", prefix = "(", postfix = ")")

        private val SCREENSHOT_ARGS: Array<String> = SCREENSHOT_HINTS
            .flatMap { listOf("%$it%", "%$it%", "%$it%") }
            .toTypedArray()

        /**
         * Kotlin 侧的同一套截屏判定规则。
         *
         * 只在「按 ID 取内容」时用（最多几百条），不在开组的热路径上 ——
         * 那条路径已经交给上面的 SQL 选择条件了。
         * 两处规则必须保持一致，否则会出现「ID 列表说它是截屏、取回来却是照片」的矛盾。
         */
        private fun looksLikeScreenshot(
            bucket: String,
            relativePath: String,
            name: String,
        ): Boolean {
            val haystack = "$bucket $relativePath $name"
            return SCREENSHOT_HINTS.any { haystack.contains(it, ignoreCase = true) }
        }

        /**
         * 排序用**拍摄时间**，不是 [MediaStore.MediaColumns.DATE_ADDED]。
         *
         * DATE_ADDED 是"文件被扫描进 MediaStore 的时间"——
         * 老照片被复制进手机、微信里收下来的图，那个时间都跟拍摄时间对不上。
         * 用它排序的后果是**同一天的图顺序和拍摄时间错乱**，
         * 于是「回到那天」里左右滑时，标题上的日期会在两天之间来回跳
         * （用户报："标题日期一直在来回变"）。
         *
         * **升序**：时间轴从左到右递增 —— 「回到那天」里往左滑是更早、
         * 往右滑是更晚，和读日期的方向一致（用户要求："左边的更小，右边的更大"）。
         *
         * 后面挂 DATE_ADDED 只是当 tie-breaker：同一秒拍的多张要有稳定顺序，
         * 否则列表顺序可能每次查询都不一样。
         * 没有 DATE_TAKEN 的条目（少数）在 SQLite 里 NULL 排最后，
         * 它们的 `dateTakenMillis` 在 parseItems 里本来就用 DATE_ADDED 兜底。
         */
        private val SORT_ORDER =
            "${MediaStore.MediaColumns.DATE_TAKEN} ASC, " +
                "${MediaStore.MediaColumns.DATE_ADDED} ASC"
    }
}
