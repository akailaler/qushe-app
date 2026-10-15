package com.qushe.app.core

/**
 * 抽组 + 收藏的业务层。
 *
 * 抽组逻辑是整个 App 的核心体验：随机顺序本身就是产品价值所在。
 * 按时间顺序删照片之所以枯燥，是因为下一张可以猜到 —— 划掉一张旅游照，
 * 下一张八成还是同一个景点，感觉像在流水线上打螺丝。随机带来的是未知感。
 */
class SessionRepository(
    private val repo: MediaRepository,
    private val prefs: Prefs,
    private val seen: IdSetStore,
    private val favorites: IdSetStore,
    /** 「喜欢」。和收藏**刻意分开**：喜欢只存在应用里，不往系统相册写。 */
    private val likes: IdSetStore,
    private val syncedFavorites: IdSetStore,
    /** 「什么时候被删进回收站」，用来算剩余天数。见 [IdTimeStore]。 */
    private val trashMarks: IdTimeStore,
) {

    /**
     * 抽一组。
     *
     * 默认只抽「没看过的」，看完一轮自动开新一轮 —— 这样你能真的刷完相册里
     * 所有回忆，而不是反复看到同一批。剩余不足一组时就把剩下的全给你，
     * 不做补齐（补齐意味着重复，而重复正是要消灭的东西）。
     */
    suspend fun buildSession(kind: MediaKind, size: Int): SessionSnapshot {
        // 关键：**只抽样，不把整张表读进来**。
        // 过去这里是 repo.all(kind)，几万张照片的设备上光填充 CursorWindow 就要一两秒；
        // 现在只需要一个 ID 列表 + 那十几条的完整信息，代价与库的大小无关。
        if (!prefs.onlyUnseen) {
            val (items, _) = repo.sampleItems(kind, size)
            return SessionSnapshot(items, roundJustCompleted = false, total = 0)
        }

        // 「只抽没看过的」需要拿 seen 做过滤，把 seen 交给抽样器，
        // 由它在遍历 ID 列表时顺手跳过 —— 同时也能顺带判断"是不是抽干了"。
        val seenKeys = seen.snapshot()
        val (items, exhausted) = repo.sampleItems(kind, size, exclude = seenKeys)

        if (!exhausted) {
            return SessionSnapshot(items, roundJustCompleted = false, total = seenKeys.size + items.size)
        }

        // 没凑够数量，说明没看过的基本抽干了 —— 开启新一轮
        seen.clear()
        prefs.roundsCompleted += 1
        val (fresh, _) = repo.sampleItems(kind, size)
        return SessionSnapshot(fresh, roundJustCompleted = true, total = 0)
    }

    fun markSeen(items: List<MediaItem>) {
        items.forEach { seen.add(it.storeKey()) }
    }

    fun isFavorite(item: MediaItem): Boolean = favorites.contains(item.storeKey())

    /** 收藏 / 取消收藏，返回操作后是否处于已收藏状态。 */
    fun toggleFavorite(item: MediaItem): Boolean {
        val key = item.storeKey()
        return if (favorites.contains(key)) {
            favorites.remove(key)
            false
        } else {
            favorites.add(key)
            true
        }
    }

    fun favoriteCount(): Int = favorites.size()

    // ---- 喜欢 ----
    //
    // 和收藏是**两个独立的概念**：
    //  · 收藏（星形）—— 会写进系统相册的收藏夹，在相册里也看得到；
    //  · 喜欢（心形）—— 只存在本应用里，绝不动系统相册。
    // 分开的原因是"想留个印象"和"要让系统相册也知道"是两种不同程度的意图，
    // 混在一起会让本机相册被塞进一堆用户并不想标记的东西。

    fun isLiked(item: MediaItem): Boolean = likes.contains(item.storeKey())

    /** 喜欢 / 取消喜欢，返回操作后是否处于已喜欢状态。 */
    fun toggleLike(item: MediaItem): Boolean {
        val key = item.storeKey()
        return if (likes.contains(key)) {
            likes.remove(key)
            false
        } else {
            likes.add(key)
            true
        }
    }

    fun likeCount(): Int = likes.size()

    /**
     * 取出收藏夹里还能在设备上找到的内容，按拍摄时间倒序。
     *
     * 用 byKeys 精确取，不扫全库 —— 否则在几万条视频的设备上打开收藏要等好几秒，
     * 页面看起来就像"空的"。
     */
    private suspend fun itemsFrom(store: IdSetStore): List<MediaItem> {
        val keys = store.snapshot()
        if (keys.isEmpty()) return emptyList()
        return repo.byKeys(keys).sortedByDescending { it.dateTakenMillis }
    }

    suspend fun favoriteItems(): List<MediaItem> = itemsFrom(favorites)

    /** 「喜欢」的内容。和收藏是两套独立的数据，互不影响。 */
    suspend fun likedItems(): List<MediaItem> = itemsFrom(likes)

    /**
     * 清理已经被删掉的条目。
     *
     * 收藏夹里指向已删除文件的项必须摘掉；已看集合里指向已删除文件的项也一样 ——
     * 否则它们会永远占着一轮的名额，让「刷完一轮」变得不可能。
     */
    /**
     * 清理已经被删掉的条目。
     *
     * 走 [MediaRepository.byKeys] 精确核对，而不是全量扫描媒体库 ——
     * 后者在几万条视频的设备上要花好几秒，而这个函数每次启动都会跑。
     */
    suspend fun reconcile() {
        val living = favorites.snapshot() + likes.snapshot() + seen.snapshot() +
            syncedFavorites.snapshot()
        if (living.isEmpty()) return

        val alive = repo.byKeys(living).map { it.storeKey() }.toHashSet()
        favorites.retainOnly(alive)
        likes.retainOnly(alive)
        seen.retainOnly(alive)
        // 内容都没了，系统里那份收藏自然也谈不上同步状态
        syncedFavorites.retainOnly(alive)
    }

    // ---- 与系统相册的收藏同步 ----

    /**
     * 还差多少项没和系统对齐。
     *
     * 纯集合运算，不查 MediaStore，所以可以放心在组合里调用。
     * 差值包含两个方向：应用里收藏了但系统没有的，以及系统里收藏了但应用里已经取消的。
     */
    /**
     * 把系统相册里的收藏**导入**到本应用的收藏夹，返回新增了几项。
     *
     * ---- 只加、不删 ----
     * 系统里没有的，不代表用户不想留 —— 可能是在别的应用里取消过、
     * 或者换了台没同步过的机器。所以只做并集，反向删除一律不做。
     *
     * 导入进来的同时标记成"已同步"：它们本来就在系统里标着，
     * 不然"待同步"会一直提示用户把这些再写一遍系统（等于白弹一次窗）。
     */
    fun importSystemFavorites(keys: Set<Long>): Int {
        if (keys.isEmpty()) return 0
        val current = favorites.snapshot()
        var added = 0
        keys.forEach { k ->
            if (!current.contains(k)) {
                favorites.add(k)
                added++
            }
            syncedFavorites.add(k)
        }
        return added
    }

    fun pendingSyncCount(): Int {
        val favs = favorites.snapshot()
        val synced = syncedFavorites.snapshot()
        val toAdd = favs.count { it !in synced }
        val toRemove = synced.count { it !in favs }
        return toAdd + toRemove
    }

    /**
     * 算出这次同步要提交的两批内容。
     *
     * 注意「取消收藏」也要同步：用户在应用里取消之后，系统相册里那条还留着收藏标记，
     * 两边就对不上了。所以取消同样进队列，由调用方发起一次 favorite=false 的请求。
     */
    /**
     * @param force true = **忽略"已同步"记账，把收藏夹里所有条目都重新推一遍**。
     *
     * 记账会失真：`syncedFavorites` 里的条目可能从来没真正写进过系统
     * （导入时就被标成"已同步"、或者当年写进去了后来又被清掉）。
     * 用户说"同步没办法"时，能给他一个**确定会把全部条目重推一遍**的入口，
     * 比纠结记账准不准有用得多。
     */
    suspend fun favoriteSyncDelta(force: Boolean = false): FavoriteSyncDelta {
        val favs = favorites.snapshot()
        val synced = syncedFavorites.snapshot()
        val toAddKeys = if (force) favs else favs - synced
        val toRemoveKeys = if (force) emptySet() else synced - favs

        if (toAddKeys.isEmpty() && toRemoveKeys.isEmpty()) return FavoriteSyncDelta()

        // 只取需要同步的那几项，不扫全库
        val all = repo.byKeys(toAddKeys + toRemoveKeys)
        return FavoriteSyncDelta(
            toAdd = all.filter { it.storeKey() in toAddKeys },
            toRemove = all.filter { it.storeKey() in toRemoveKeys },
        )
    }

    /** 用户在系统弹窗里确认之后调用：这些项的收藏状态已经和系统一致了。 */
    fun markSynced(items: List<MediaItem>) {
        items.forEach { syncedFavorites.add(it.storeKey()) }
    }

    /** 用户确认取消后调用：这些项在两边都不再是收藏。 */
    fun markUnsynced(items: List<MediaItem>) {
        items.forEach { syncedFavorites.remove(it.storeKey()) }
    }

    /**
     * 记下这些项是**什么时候**被删进系统回收站的。
     *
     * 必须在删除**真正发生之后**调用（用户在系统弹窗里点了确认），
     * 不是在发起请求时 —— 用户可能取消。
     *
     * @param at 时刻；默认当前时间。
     */
    fun markTrashed(items: List<MediaItem>, at: Long = System.currentTimeMillis()) {
        trashMarks.putAll(items.map { it.storeKey() }, at)
    }

    /** 从回收站恢复出来的条目要把记账去掉，否则它会被算成"还在回收站里"。 */
    fun unmarkTrashed(items: List<MediaItem>) {
        trashMarks.removeAll(items.map { it.storeKey() })
    }

    /** 某个条目被删进回收站的时刻；没有记账（例如不是从取舍删的）时为 null。 */
    fun trashedAt(item: MediaItem): Long? = trashMarks[item.storeKey()]

    /** 整个记账表的快照，回收站页一次拿全。 */
    fun trashMarksSnapshot(): Map<Long, Long> = trashMarks.snapshot()

    fun persist() {
        favorites.flush()
        likes.flush()
        seen.flush()
        syncedFavorites.flush()
        trashMarks.flush()
    }
}

/** 一次收藏同步要提交的两批内容。 */
data class FavoriteSyncDelta(
    val toAdd: List<MediaItem> = emptyList(),
    val toRemove: List<MediaItem> = emptyList(),
) {
    val isEmpty: Boolean get() = toAdd.isEmpty() && toRemove.isEmpty()
    val total: Int get() = toAdd.size + toRemove.size
}

data class SessionSnapshot(
    val items: List<MediaItem>,
    val roundJustCompleted: Boolean,
    val total: Int,
)
