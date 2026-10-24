package com.qushe.app.core

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension

/**
 * 缩略图墙改走**系统已经生成好的缩略图缓存**。
 *
 * ---- 为什么不让 Coil 自己解 ----
 *
 * 视频：Coil 走 `VideoFrameDecoder`，每次都是「打开 MediaMetadataRetriever →
 * seek → 解一整帧 → 再按目标尺寸降采样」。对一格只有一百多 dp 的缩略图来说，
 * 它先按原分辨率把整帧解出来 —— 一个 4K 视频就是几千万像素。单格几十毫秒，
 * 一屏九个格子排队下来，观感就是「滚过去一片空白，停一下才一格格慢慢亮出来」。
 *
 * 图片：`BitmapFactory` 虽然会按 `inSampleSize` 降采样，但仍然要打开原始大文件、
 * 读一部分字节、走一遍缩放。一张 5000×4000 的照片照样是几十毫秒。
 *
 * 而系统相册在扫描时早就为每个 Uri 生成过缩略图，`ContentResolver.loadThumbnail`
 * 直接命中那份缓存，通常 1~2ms 出图，而且是**跨应用共享**的 ——
 * 相册已经付过的解码代价，我们不用再付第二遍。
 *
 * ---- 只接管"明确要一格缩略图"的请求 ----
 *
 * 判定条件是请求上带了 `SYSTEM_THUMBNAIL_KEY`（由 `rememberThumbnailRequest` 打上）。
 * 瀑布流、单图预览那种要看清楚照片的场景不带这个标记，继续走常规解码 ——
 * 系统那份缩略图最长边也就 512，放大到大格子上会糊。
 *
 * 之所以用显式标记、而不是"看请求尺寸大小来决定"：尺寸在测量完成前是
 * Undefined，靠它判断会时灵时不灵。
 *
 * ---- 拿不到就一定要退回原路 ----
 *
 * 老系统、缩略图还没生成、权限被撤销、原图是超长图（系统给的缩略图短边太窄，
 * 放大就糊）……这些情况下 fetch() 返回 null，Coil 会继续往下找
 * `VideoFrameDecoder` / 默认的图片解码器。兜底路径始终还在，
 * 所以这里出任何问题最坏也只是"慢"，不会是"白块"。
 */
class SystemThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = runCatching { loadSystemThumbnail() }.getOrNull() ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        // 缩略图的短边连目标的一半都不到 —— 说明原图是个很长的长条
        // （长截图、全景），系统那份是按 fit 生成的小图，铺到正方格子里
        // 等于放大好几倍，糊得比等一会儿更难看。退回常规解码。
        val req = targetSize()
        val need = minOf(req.width, req.height)
        if (need > 0 && minOf(bitmap.width, bitmap.height) * 2 < need) return null
        return DrawableResult(
            drawable = bitmap.toDrawable(context.resources),
            // 系统给的就是缩略图，本来就是采样过的
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun loadSystemThumbnail(): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(uri, targetSize(), null)
        } else {
            legacyThumbnail(targetSide())
        }
    }

    /**
     * 系统缩略图要的尺寸：**按格子自己的长宽比**，不是正方形。
     *
     * 之前一律要正方形（`Size(side, side)`），系统按"居中裁成方块"生成，
     * 而我们把它铺进一个 4:3 的格子（`ContentScale.Crop`）—— 那是在
     * **已经裁过的方块**上再裁一刀，取景和原图直接 Crop 不一样。
     * 高清图一到，画面内容会整体"挪一下"，看起来就是滑过时闪一下。
     * 传真实比例，取景才和主图完全一致。
     */
    private fun targetSize(): Size {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 0
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 0
        if (w <= 0 || h <= 0) return Size(512, 512)
        val k = minOf(1f, 512f / maxOf(w, h))
        return Size(
            (w * k).toInt().coerceAtLeast(1),
            (h * k).toInt().coerceAtLeast(1),
        )
    }

    /** API 29 以下没有通用的 `loadThumbnail`，退回各自的 Thumbnails 表。 */
    private fun legacyThumbnail(side: Int): Bitmap? {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val resolver = context.contentResolver
        return when (kind) {
            Kind.VIDEO -> legacyVideo(resolver, id, side)
            Kind.IMAGE -> legacyImage(resolver, id, side)
            Kind.NONE -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyVideo(resolver: ContentResolver, id: Long, side: Int): Bitmap? =
        MediaStore.Video.Thumbnails.getThumbnail(
            resolver,
            id,
            if (side <= 192) MediaStore.Video.Thumbnails.MICRO_KIND
            else MediaStore.Video.Thumbnails.MINI_KIND,
            null,
        )

    @Suppress("DEPRECATION")
    private fun legacyImage(resolver: ContentResolver, id: Long, side: Int): Bitmap? =
        MediaStore.Images.Thumbnails.getThumbnail(
            resolver,
            id,
            if (side <= 192) MediaStore.Images.Thumbnails.MICRO_KIND
            else MediaStore.Images.Thumbnails.MINI_KIND,
            null,
        )

    /**
     * 目标边长：取布局给的宽高里较大的一边，夹到 [96, 512]。
     *
     * 上限 512 是刻意的 —— 系统缩略图（MINI_KIND）本身就是 512×384 这一档，
     * 再往上索取不到更多细节，只会让缓存里多一份"其实一模一样"的 Bitmap。
     * 下限 96 保证最密的那档（三列）在 3x 密度的屏上不至于发虚。
     *
     * 尺寸在测量完成前是 Undefined，这时给 512，别退回原分辨率。
     */
    private fun targetSide(): Int {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 0
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 0
        val side = maxOf(w, h)
        return if (side <= 0) 512 else side.coerceIn(96, 512)
    }

    private enum class Kind { VIDEO, IMAGE, NONE }

    private val kind: Kind
        get() {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return Kind.NONE
            if (uri.authority != MediaStore.AUTHORITY) return Kind.NONE
            val seg = uri.pathSegments
            if (seg.size < 3) return Kind.NONE
            return when (seg[1]) {
                "video" -> Kind.VIDEO
                "images" -> Kind.IMAGE
                else -> Kind.NONE
            }
        }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            // 只接"明确要一格缩略图"的请求（见 REQUEST_KEY 的说明）
            if (options.parameters.value<Boolean>(REQUEST_KEY) != true) return null
            if (!isMediaStoreImageOrVideo(data)) return null
            return SystemThumbnailFetcher(context, data, options)
        }
    }

    companion object {
        /**
         * 请求上带这个标记 = "这一格就是缩略图，可以用系统相册那份"。
         *
         * 由 `rememberThumbnailRequest` 打上（收藏页 / 喜欢页 / 回收站的网格、
         * 整理视频页的横向胶片条）。瀑布流、单图预览那种**要看清楚照片**的场景
         * 不打这个标记，继续走常规解码 —— 系统那份缩略图最长边只有 512，
         * 铺到大格子上会糊。
         *
         * 用显式标记、而不是"看请求尺寸大小来决定"，是因为尺寸在测量完成前
         * 是 Undefined，靠它判断会时灵时不灵。
         */
        const val REQUEST_KEY = "qushe_system_thumbnail"

        /**
         * 只认系统 MediaStore 里的图片 / 视频 Uri ——
         * 别去拦文件 Uri 和别的 provider，那些没有系统缩略图可读。
         */
        private fun isMediaStoreImageOrVideo(uri: Uri): Boolean {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
            if (uri.authority != MediaStore.AUTHORITY) return false
            val seg = uri.pathSegments
            if (seg.size < 3) return false
            return seg[1] == "video" || seg[1] == "images"
        }
    }
}
