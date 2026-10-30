package com.qushe.app.core

import android.content.Context

/**
 * 设置项 + 累计统计。
 *
 * 统计刻意只记「已经整理了多少」而不是「还剩多少没整理」：
 * 前者是 done list，看着有成就感；后者是 todo list，会带来心理压力。
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("qushe_prefs", Context.MODE_PRIVATE)

    /** 每组数量。太小没有仪式感，太大一次做不完。 */
    var groupSize: Int
        get() = sp.getInt(KEY_GROUP_SIZE, DEFAULT_GROUP_SIZE)
        set(value) = sp.edit().putInt(KEY_GROUP_SIZE, value.coerceIn(10, 200)).apply()

    /**
     * 外观：跟随系统 / 浅色 / 深色（[THEME_SYSTEM] / [THEME_LIGHT] / [THEME_DARK]）。
     *
     * 存 Int 而不是 Boolean：以后要加"随日出日落"之类不用再改存储格式。
     * **默认深色** —— 这是这个 App 原本的样子，浅色是选项，不是默认。
     */
    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, THEME_DARK).coerceIn(0, 2)
        set(value) = sp.edit().putInt(KEY_THEME_MODE, value.coerceIn(0, 2)).apply()

    /**
     * 浅色皮肤（见 `ui/theme/Theme.kt` 的 `LightSkins`）。
     *
     * 和 [darkSkinId] 是**两件事**：白天用哪套、夜里用哪套，互不影响。
     * 跟随系统时两套都会用到，所以两个都得存。
     *
     * 上限 [SKIN_MAX] 必须与两张表的最后一个 id 一致（`verify.py` 会核对）；
     * 取到不存在的 id 时 `lightSkinOf` 会回落到第一套，所以调小上限只会少几套。
     */
    var lightSkinId: Int
        get() = sp.getInt(KEY_LIGHT_SKIN, 0).coerceIn(0, SKIN_MAX)
        set(value) = sp.edit().putInt(KEY_LIGHT_SKIN, value.coerceIn(0, SKIN_MAX)).apply()

    /** 深色皮肤（见 `DarkSkins`）。 */
    var darkSkinId: Int
        get() = sp.getInt(KEY_DARK_SKIN, 0).coerceIn(0, SKIN_MAX)
        set(value) = sp.edit().putInt(KEY_DARK_SKIN, value.coerceIn(0, SKIN_MAX)).apply()

    /**
     * 缩略图网格每行放几个（2 或 3）。
     *
     * 三个页面共用这一份：整理文件夹、收藏 / 喜欢、回收站。
     * 两列图大、一眼认出；三列一屏看得更多（默认三列）。
     * 夹在 2~3 之间是为了容错：存量值被改坏时不会出现 12 列那种灾难。
     */
    var folderColumns: Int
        get() = sp.getInt(KEY_FOLDER_COLUMNS, DEFAULT_FOLDER_COLUMNS).coerceIn(2, 3)
        set(value) = sp.edit().putInt(KEY_FOLDER_COLUMNS, value.coerceIn(2, 3)).apply()

    /**
     * 上滑 = 删除。
     *
     * ---- 为什么钉死成 true（1.3.52）----
     * 原来这里是个可选的开关（「上滑删除 / 上滑收藏」）。1.3.48 把那条设置删了 ——
     * 因为整理页的收藏早就搬到左下角的星形按钮，"上滑收藏"那条路是死的。
     * **但当时只删了 UI，没处理已经存下来的值**：把开关关过的设备从此读到的
     * 永远是 false，而界面上再也改不回来 ⇒ 「回到那天」的瀑布流上滑变成了收藏，
     * 而且格子不会消失（用户反馈"怎么是收藏、而且不消失"）。
     *
     * 所以这里直接返回常量：没有开关，也就没有"关过就回不来"的残留状态。
     * 存量的 SharedPreferences 值不再被读取。
     */
    val swipeUpDeletes: Boolean
        get() = true

    /** 看过的条目在一轮之内不再重复出现。 */
    var onlyUnseen: Boolean
        get() = sp.getBoolean(KEY_ONLY_UNSEEN, true)
        set(value) = sp.edit().putBoolean(KEY_ONLY_UNSEEN, value).apply()


    /**
     * 删除去向。true = 系统回收站（30 天内可恢复，安全）；
     * false = 彻底删除。默认走回收站，本地文件删掉是不可逆的。
     */
    var deleteToTrash: Boolean
        get() = sp.getBoolean(KEY_DELETE_TO_TRASH, true)
        set(value) = sp.edit().putBoolean(KEY_DELETE_TO_TRASH, value).apply()

    /**
     * 「i」胶囊上要不要显示**相对时间**（3 天前）。
     *
     * 和 [absoluteDates] 是两个**独立**开关，四种组合都合法：
     * 只相对 / 只具体 / 都显示 / 都不显示。
     * 早先是一个二选一的布尔，但"都显示"和"都不显示"都用表达不出来（用户要求）。
     */
    var relativeDates: Boolean
        get() = sp.getBoolean(KEY_RELATIVE_DATES, true)
        set(value) = sp.edit().putBoolean(KEY_RELATIVE_DATES, value).apply()

    /**
     * 「i」胶囊上要不要显示**具体日期**（2020/6/6）。
     * 默认关闭 —— 和 1.3.73 之前的行为保持一致（那时只有相对时间）。
     */
    var absoluteDates: Boolean
        get() = sp.getBoolean(KEY_ABSOLUTE_DATES, false)
        set(value) = sp.edit().putBoolean(KEY_ABSOLUTE_DATES, value).apply()

    /** 视频默认静音，避免在地铁上点开就外放。 */
    var videoMuted: Boolean
        get() = sp.getBoolean(KEY_VIDEO_MUTED, true)
        set(value) = sp.edit().putBoolean(KEY_VIDEO_MUTED, value).apply()

    /** 一进视频整理就自动开始播放。 */
    var autoPlayVideo: Boolean
        get() = sp.getBoolean(KEY_AUTO_PLAY, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_PLAY, value).apply()

    /**
     * 视频整理页双击的含义。
     *
     * true = 双击「喜欢」（默认）——只记在本应用里，不碰系统相册，点错了也没有副作用；
     * false = 双击「收藏」——会写进系统相册的收藏夹。
     *
     * 之所以默认给"喜欢"：双击是个高频且无提示的手势，落在一个"删不掉、不同步、
     * 一秒就能撤销"的动作上最安全。想让它直接同步进系统相册的人可以在这里切过来。
     */
    var doubleTapLikes: Boolean
        get() = sp.getBoolean(KEY_DOUBLE_TAP_LIKES, true)
        set(value) = sp.edit().putBoolean(KEY_DOUBLE_TAP_LIKES, value).apply()

    /**
     * 整理结束后把新收藏同步到系统相册。
     *
     * 默认开启。代价是每轮结束会多弹一次系统确认框（系统不允许应用静默改收藏，
     * 必须用户点确认）—— 这是「打通」唯一的实现路径，嫌烦可以关掉，
     * 之后手动到收藏页一次性同步。
     */
    var autoSyncFavorites: Boolean
        get() = sp.getBoolean(KEY_AUTO_SYNC_FAVORITES, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_SYNC_FAVORITES, value).apply()

    /**
     * 视频整理页的操作栏放在右边（默认）。
     *
     * 关掉就放到左边 —— 惯用左手，或者用左手滑视频的人会更顺手。
     * 收藏 / 删除 / 分享 / 静音 会整组一起换边。
     */
    var railOnRight: Boolean
        get() = sp.getBoolean(KEY_RAIL_ON_RIGHT, true)
        set(value) = sp.edit().putBoolean(KEY_RAIL_ON_RIGHT, value).apply()

    /**
     * 显示拍摄地点。
     *
     * 照片显示在底部信息胶囊的第三行，视频显示在底部信息区。
     *
     * 需要 `ACCESS_MEDIA_LOCATION` 权限才能拿到经纬度 —— Android 会对媒体里的位置
     * 做隐私脱敏，不授权就永远是空值。下载来的内容通常本来也没有位置信息，
     * 那种情况下这一行不会显示。
     */
    var showMediaLocation: Boolean
        // 存储键沿用旧名字，这样升级上来的用户原有设置不会丢
        get() = sp.getBoolean(KEY_SHOW_VIDEO_LOCATION, true)
        set(value) = sp.edit().putBoolean(KEY_SHOW_VIDEO_LOCATION, value).apply()

    /**
     * 收藏页的「纯观看模式」。
     *
     * 打开后只浏览不动内容：预览里不再出现「移出收藏 / 删除」，长按也不会移除。
     * 想翻翻自己攒下来的东西、又不想误删时用。
     */
    var favoritesViewOnly: Boolean
        get() = sp.getBoolean(KEY_FAVORITES_VIEW_ONLY, false)
        set(value) = sp.edit().putBoolean(KEY_FAVORITES_VIEW_ONLY, value).apply()

    var hapticsEnabled: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) = sp.edit().putBoolean(KEY_HAPTICS, value).apply()

    // ---- done list ----

    var reviewedTotal: Int
        get() = sp.getInt(KEY_REVIEWED_TOTAL, 0)
        set(value) = sp.edit().putInt(KEY_REVIEWED_TOTAL, value).apply()

    /**
     * 照片 / 视频**分开**累计的回顾数。
     *
     * 之前只有一个总数，于是首页那个计数看不出"我到底刷了多少照片、多少视频" ——
     * 而这两个是完全不同的习惯（照片是随手刷、视频是专门看），混在一起没有意义。
     */
    var reviewedPhotoTotal: Int
        get() = sp.getInt(KEY_REVIEWED_PHOTO_TOTAL, 0)
        set(value) = sp.edit().putInt(KEY_REVIEWED_PHOTO_TOTAL, value).apply()

    var reviewedVideoTotal: Int
        get() = sp.getInt(KEY_REVIEWED_VIDEO_TOTAL, 0)
        set(value) = sp.edit().putInt(KEY_REVIEWED_VIDEO_TOTAL, value).apply()

    var deletedTotal: Int
        get() = sp.getInt(KEY_DELETED_TOTAL, 0)
        set(value) = sp.edit().putInt(KEY_DELETED_TOTAL, value).apply()

    var favoritedTotal: Int
        get() = sp.getInt(KEY_FAVORITED_TOTAL, 0)
        set(value) = sp.edit().putInt(KEY_FAVORITED_TOTAL, value).apply()

    var freedBytesTotal: Long
        get() = sp.getLong(KEY_FREED_BYTES, 0L)
        set(value) = sp.edit().putLong(KEY_FREED_BYTES, value).apply()

    var roundsCompleted: Int
        get() = sp.getInt(KEY_ROUNDS, 0)
        set(value) = sp.edit().putInt(KEY_ROUNDS, value).apply()

    fun stats(): Stats = Stats(
        reviewed = reviewedTotal,
        reviewedPhotos = reviewedPhotoTotal,
        reviewedVideos = reviewedVideoTotal,
        deleted = deletedTotal,
        favorited = favoritedTotal,
        freedBytes = freedBytesTotal,
        rounds = roundsCompleted,
    )

    fun recordSession(
        reviewed: Int,
        deleted: Int,
        favoritedDelta: Int,
        freedBytes: Long,
        isVideo: Boolean = false,
    ) {
        reviewedTotal += reviewed
        // 分开记：截屏归到照片那一类（它们就是图片，且整理入口和照片是同一个）
        if (isVideo) reviewedVideoTotal += reviewed else reviewedPhotoTotal += reviewed
        deletedTotal += deleted
        favoritedTotal = (favoritedTotal + favoritedDelta).coerceAtLeast(0)
        freedBytesTotal += freedBytes
    }

    companion object {
        const val DEFAULT_GROUP_SIZE = 30

        /** 「整理文件夹」默认三列。 */
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        /** 外观选项，顺序即设置页胶囊的顺序。 */
        val THEME_CHOICES = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)

        const val DEFAULT_FOLDER_COLUMNS = 3

        /** 可选列数。和 [GROUP_SIZE_CHOICES] 一样放在这里，界面层只负责画。 */
        val FOLDER_COLUMNS_CHOICES = listOf(2, 3)

        /** 可供选择的每组数量。放在这里而不是界面层，是因为容器启动时要拿它做一次归一。 */
        val GROUP_SIZE_CHOICES = listOf(30, 50, 100)
        const val GROUP_SIZE_OPTIONS = 10

        private const val KEY_GROUP_SIZE = "group_size"
        private const val KEY_FOLDER_COLUMNS = "folder_columns"
        private const val KEY_THEME_MODE = "theme_mode"
        /** 1.6.0 及以前只有一列皮肤，已废弃 —— **不再读取**，读回来也不认识。 */
        private const val KEY_SKIN_ID = "skin_id"
        private const val KEY_LIGHT_SKIN = "light_skin_id"
        private const val KEY_DARK_SKIN = "dark_skin_id"

        /**
         * 皮肤 id 上限，必须等于 `LightSkins` / `DarkSkins` 里最后一个 id。
         *
         * 放在 core 层不能直接读 ui 层的那两张表，所以两处都得写 —— 由 `verify.py` 核对，
         * 加皮肤时忘了改这里会直接报错，而不是"新皮肤选完又跳回默认"。
         */
        private const val SKIN_MAX = 7
        private const val KEY_SWIPE_UP_DELETES = "swipe_up_deletes"
        private const val KEY_ONLY_UNSEEN = "only_unseen"
        private const val KEY_DELETE_TO_TRASH = "delete_to_trash"
        private const val KEY_VIDEO_MUTED = "video_muted"
        private const val KEY_AUTO_PLAY = "auto_play"
        private const val KEY_DOUBLE_TAP_LIKES = "double_tap_likes"
        private const val KEY_AUTO_SYNC_FAVORITES = "auto_sync_favorites"
        private const val KEY_RAIL_ON_RIGHT = "rail_on_right"
        private const val KEY_SHOW_VIDEO_LOCATION = "show_video_location"
        private const val KEY_FAVORITES_VIEW_ONLY = "favorites_view_only"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_REVIEWED_TOTAL = "reviewed_total"
        private const val KEY_REVIEWED_PHOTO_TOTAL = "reviewed_photo_total"
        private const val KEY_REVIEWED_VIDEO_TOTAL = "reviewed_video_total"
        private const val KEY_DELETED_TOTAL = "deleted_total"
        private const val KEY_FAVORITED_TOTAL = "favorited_total"
        private const val KEY_FREED_BYTES = "freed_bytes"
        private const val KEY_ROUNDS = "rounds"
        private const val KEY_RELATIVE_DATES = "relative_dates"
        private const val KEY_ABSOLUTE_DATES = "absolute_dates"
    }
}
