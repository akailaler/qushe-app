package com.qushe.app.core

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 超长竖图的缩略图：**只解原图垂直居中的一块 4:3 区域**。
 *
 * ---- 为什么不交给 Coil 的默认解码 ----
 *
 * 828×30656 的截图请求一个 292×1080 的格子，`BitmapFactory` 的 `inSampleSize`
 * 是 2 的幂：按宽度最多只能降到 2（414 ≥ 292），于是**每格要解 414×15328 ≈ 25MB**。
 * 滑过几格就 OOM（用户报"拖底部进度条经常闪退"）；即使解出来，
 * 整张缩到格子里也只是一根看不清的细条（用户报"像一个细长条"）。
 *
 * 这里改成 `BitmapRegionDecoder`：宽取满、高取 `宽 × 3/4`、垂直居中，
 * 只解 828×621 ≈ 2MB —— 内存降到 1/12，画面还正好铺满 4:3 的格子。
 *
 * 解码量固定、与图有多长无关，越长的图收益越大。
 */
class LongThumbnailFetcher(
    private val context: Context,
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    @Suppress("DEPRECATION") // newInstance(FileDescriptor) 是 minSdk 30 上唯一的入口
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        if (!isImageUpright(context, uri)) return@withContext null

        val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }
            .getOrNull() ?: return@withContext null
        try {
            val decoder = runCatching {
                BitmapRegionDecoder.newInstance(pfd.fileDescriptor, false)
            }.getOrNull() ?: return@withContext null
            try {
                val w = decoder.width
                val h = decoder.height
                if (w <= 0 || h <= 0) return@withContext null
                // 不够长就不是这条路该管的（调用方只给超长图打标记，
                // 这里再守一道，避免参数意外泄漏到普通图）。
                if (h.toLong() <= w.toLong() * ULTRA_LONG_RATIO) return@withContext null
                // 宽取满、高 = 宽 × 3/4 ⇒ 区域正好 4:3，垂直居中。
                val regionH = minOf(h, (w.toLong() * 3L / 4L).toInt().coerceAtLeast(1))
                val top = (h - regionH) / 2
                val rect = Rect(0, top, w, top + regionH)

                val targetW = (options.size.width as? Dimension.Pixels)?.px ?: 0
                val targetH = (options.size.height as? Dimension.Pixels)?.px ?: 0
                val bmpOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeForTarget(w, regionH, targetW, targetH)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = runCatching { decoder.decodeRegion(rect, bmpOptions) }.getOrNull()
                    ?: return@withContext null
                DrawableResult(
                    drawable = bmp.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) decoder.recycle()
            }
        } finally {
            runCatching { pfd.close() }
        }
    }

    /** 区域解码后再按目标尺寸降采样（2 的幂），避免大图白解。 */
    private fun sampleSizeForTarget(w: Int, h: Int, targetW: Int, targetH: Int): Int {
        if (targetW <= 0 || targetH <= 0) return 1
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) {
            sample *= 2
        }
        return sample
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (options.parameters.value<Boolean>(REQUEST_KEY) != true) return null
            if (!isMediaStoreImage(data)) return null
            return LongThumbnailFetcher(context, data, options)
        }

        private fun isMediaStoreImage(uri: Uri): Boolean {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
            if (uri.authority != MediaStore.AUTHORITY) return false
            val seg = uri.pathSegments
            return seg.size >= 3 && seg[1] == "images"
        }
    }

    companion object {
        /**
         * 请求上带这个标记 = "这是超长竖图，只解居中的 4:3 一块"。
         *
         * 由 `DayCell` 给 `isUltraLong` 的格子打上；普通图不带。
         */
        const val REQUEST_KEY = "qushe_long_region_thumb"

        /** 高宽比超过它才算"超长图"（用户定的阈值）。 */
        const val ULTRA_LONG_RATIO = 3
    }
}
