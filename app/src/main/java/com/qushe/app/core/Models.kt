package com.qushe.app.core

import android.net.Uri

/**
 * 整理对象的分类。
 *
 * 刻意不提供「混合」模式：混在一起时，大脑要在几种不同的浏览任务之间来回切换，
 * 会产生一种隐约的不适感 —— 一次只看一种类型，整个浏览过程才顺滑。
 */
enum class MediaKind {
    PHOTO,
    SCREENSHOT,
    VIDEO;

    /** 照片和截屏共用同一套整理界面（卡片堆叠），只有视频走抖音式。 */
    val isImage: Boolean get() = this != VIDEO

    val label: String
        get() = when (this) {
            PHOTO -> "照片"
            SCREENSHOT -> "截屏"
            VIDEO -> "视频"
        }
}

/**
 * 一条可整理的媒体。
 *
 * 只持有 MediaStore 的 Uri，不持有 Bitmap 或播放器实例 —— 列表里可能躺着几万条，
 * 任何形式的预加载都会炸内存。
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val kind: MediaKind,
    val name: String,
    val sizeBytes: Long,
    val dateTakenMillis: Long,
    /**
     * **加入媒体库的时间**（`DATE_ADDED`）。
     *
     * 和 [dateTakenMillis] 是两回事：前者是拍摄时间（EXIF 或文件名解析），
     * 后者是文件被放进媒体库的时间 —— 截图、下载、微信保存的文件两者可能差好几年。
     * 详情面板里两者都要标清楚，别混成一个"日期"。
     */
    val dateAddedMillis: Long = 0L,
    val durationMillis: Long,
    val bucket: String,
    val width: Int,
    val height: Int,
    val mimeType: String = "",
    /**
     * 实况照片（Motion Photo）的嵌入视频「距文件尾部多少字节」。
     * - `null` 表示不是实况照片（或还没探测）
     * - `-1` 表示确认是实况照片，但偏移量得读文件头才知道
     * - `>= 0` 表示偏移量已知，可以直接抽取
     *
     * 注意：**查询阶段不会填充这个字段**。它依赖 `MediaStore.MediaColumns.XMP`，
     * 而那一列受 ACCESS_MEDIA_LOCATION 约束、部分厂商的 Provider 会直接抛异常
     * （加进投影会导致整个相册读不出来）。实况判定统一由 [MotionPhoto.probe]
     * 读文件头完成，这个字段只作为"已知信息"的传递通道保留。
     */
    val motionOffsetFromEnd: Long? = null,
) {
    val isMotionPhoto: Boolean get() = motionOffsetFromEnd != null

    /** 动图：GIF 或 WebP。这类文件在查看时会自动播放（靠 coil-gif）。 */
    val isAnimated: Boolean
        get() = mimeType.equals("image/gif", ignoreCase = true) ||
            mimeType.equals("image/webp", ignoreCase = true)

    /** 长图：高度远大于宽度的图，基本就是截屏，需要放大才看得清细节。 */
    val isLongImage: Boolean
        get() = height > 0 && width > 0 && height >= width * 2
}

/**
 * 持久化用的复合键。
 *
 * 关键：MediaStore 里图片和视频是两张独立的表，各自的 `_id` 从 1 开始自增，
 * 所以"图片 100"和"视频 100"会同时存在。这里把「是不是视频」编进最低位来消歧。
 *
 * 截屏与照片共用同一张 images 表，id 在表内唯一，所以两者映射到同一个键空间不会撞车 ——
 * 新增截屏分类**不需要**做收藏/已看的数据迁移。
 */
/**
 * storeKey 编码的**唯一来源**。
 *
 * 图片 = id*2（偶数），视频 = id*2+1（奇数）。
 *
 * ---- 为什么必须只有这一个函数 ----
 * 这套编码被两处用：应用内的集合（`favorites` / `likes` / `seen`）
 * 和 `MediaRepository.allSystemFavoriteKeys()`（反过来从系统 MediaStore 的 `_id` 编码）。
 * 1.3.68 我给后者另写了一遍，结果把图片/视频的奇偶写反了 ——
 * 两边永远不重合 ⇒ 差额算出来永远是"全部待同步"、且写进去也永远对不上，
 * 表现就是"收藏同步完全没用"（用户反馈，实测确认）。
 * **凡是有两处以上要算同一个 key 的，就必须抽成唯一函数。**
 */
fun mediaStoreKey(id: Long, isVideo: Boolean): Long =
    id * 2L + if (isVideo) 1L else 0L

fun MediaItem.storeKey(): Long = mediaStoreKey(id, kind == MediaKind.VIDEO)

/** 一次滑动的判定结果。 */
enum class SwipeDecision { NONE, DELETE, FAVORITE, NEXT, PREV }

/** 上滑 / 下滑是一对互斥的动作，知道一个就知道另一个。 */
fun SwipeDecision.opposite(): SwipeDecision = when (this) {
    SwipeDecision.DELETE -> SwipeDecision.FAVORITE
    SwipeDecision.FAVORITE -> SwipeDecision.DELETE
    else -> SwipeDecision.NONE
}

/** 一轮整理结束后回传给外层的结果。 */
data class SessionResult(
    val kind: MediaKind,
    val reviewedCount: Int,
    val markedDelete: List<MediaItem>,
    val favoriteAdded: List<MediaItem>,
    val favoriteRemoved: List<MediaItem>,
) {
    val deleteBytes: Long get() = markedDelete.sumOf { it.sizeBytes }
}

/** 累计成果。刻意只记「已整理多少」，不记「还剩多少」。 */
data class Stats(
    val reviewed: Int,
    /** 照片这一类累计回顾了多少（与 [reviewedVideos] 分开统计）。 */
    val reviewedPhotos: Int,
    /** 视频这一类累计回顾了多少。 */
    val reviewedVideos: Int,
    val deleted: Int,
    val favorited: Int,
    val freedBytes: Long,
    val rounds: Int,
)
