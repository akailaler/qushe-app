package com.qushe.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 超长图的一块"区域解码"结果。
 *
 * 之所以要这个东西：828×30656 这类长截图整张解出来是 **25MP ≈ 101MB**。
 * Coil 的 `size()` 只决定 `inSampleSize`（2 的幂），长图按宽度算采样率后
 * 仍然会解出整张 —— 轻则缩略图变成一根 6px 宽的细条，重则 OOM 闪退。
 *
 * 正解是 `BitmapRegionDecoder`：只解我们要看的那一块，
 * 解码量从"整张 25MP"降到"一屏 1~3MP"。
 */
class RegionTile(
    val bitmap: ImageBitmap,
    /** 原图尺寸（区域坐标以它为准）。 */
    val srcWidth: Int,
    val srcHeight: Int,
    /** 这块区域在原图里的位置与大小（像素）。 */
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * 按**归一化矩形**解一块区域（0..1，相对原图）。
 *
 * 调用方只需要表达"我要看中间这一段"，不必先知道原图像素尺寸 ——
 * 尺寸由解码器自己给出，正好解决 MediaStore 宽高缺失的情况。
 *
 * @param maxPixels 这块区域允许的解码像素上限。超了按 2 的幂降采样；
 *   一屏的量级是 828×1840 ≈ 1.5MP，留 4MP 已经绰绰有余。
 * @return null = 解不出来（文件坏、权限没了、EXIF 旋转过的图不走这条路）。
 */
@Suppress("DEPRECATION") // newInstance(FileDescriptor) 是 minSdk 30 上唯一的入口
suspend fun decodeRegionTile(
    context: Context,
    uri: Uri,
    leftFraction: Float,
    topFraction: Float,
    rightFraction: Float,
    bottomFraction: Float,
    maxPixels: Int = 4_000_000,
): RegionTile? = withContext(Dispatchers.IO) {
    // 旋转过的图不碰：BitmapRegionDecoder 不应用 EXIF 旋转，
    // 解出来的方向会和底层那张 Fit 图对不上。超长图基本都是截图，正常方向。
    if (!isImageUpright(context, uri)) return@withContext null

    val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }
        .getOrNull() ?: return@withContext null
    try {
        val decoder = runCatching {
            BitmapRegionDecoder.newInstance(pfd.fileDescriptor, false)
        }.getOrNull() ?: return@withContext null
        try {
            val sw = decoder.width
            val sh = decoder.height
            if (sw <= 0 || sh <= 0) return@withContext null

            val l = (leftFraction.coerceIn(0f, 1f) * sw).toInt().coerceIn(0, sw - 1)
            val t = (topFraction.coerceIn(0f, 1f) * sh).toInt().coerceIn(0, sh - 1)
            val r = (rightFraction.coerceIn(0f, 1f) * sw).toInt().coerceIn(l + 1, sw)
            val b = (bottomFraction.coerceIn(0f, 1f) * sh).toInt().coerceIn(t + 1, sh)
            val rect = Rect(l, t, r, b)

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(r - l, b - t, maxPixels)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = runCatching { decoder.decodeRegion(rect, options) }.getOrNull()
                ?: return@withContext null
            RegionTile(
                bitmap = bmp.asImageBitmap(),
                srcWidth = sw,
                srcHeight = sh,
                left = l,
                top = t,
                width = r - l,
                height = b - t,
            )
        } finally {
            // recycle() 是 API 31 才有的；minSdk 30 上不能直接调。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) decoder.recycle()
        }
    } finally {
        runCatching { pfd.close() }
    }
}

/** 选一个 2 的幂采样率，让 `w × h` 落到 [maxPixels] 以内。 */
internal fun sampleSize(w: Int, h: Int, maxPixels: Int): Int {
    if (w <= 0 || h <= 0) return 1
    var sample = 1
    while ((w.toLong() / sample) * (h.toLong() / sample) > maxPixels) {
        sample *= 2
        if (sample >= 32) break
    }
    return sample
}

/**
 * EXIF 方向是否"不需要旋转"。
 *
 * 只判 90° / 270° 与翻转 —— 它们会改变宽高或镜像，区域坐标就不再是
 * 原图坐标。方向正常的图（截图、绝大多数照片）直接放行。
 *
 * 结果按 Uri 缓存：每次区域解码都读一遍 EXIF 是白付的 IO。
 */
private val uprightCache = HashMap<Uri, Boolean>()

internal fun isImageUpright(context: Context, uri: Uri): Boolean {
    synchronized(uprightCache) { uprightCache[uri]?.let { return it } }
    val ok = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            // 读 EXIF 失败不代表图不能解 —— 解不出来会由 decodeRegion 自己返回 null。
            // 只有"明确读到了旋转"才把区域解码关掉。
            runCatching {
                val orientation = ExifInterface(pfd.fileDescriptor).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
                orientation == ExifInterface.ORIENTATION_NORMAL ||
                    orientation == ExifInterface.ORIENTATION_UNDEFINED
            }.getOrDefault(true)
        } ?: false
    }.getOrDefault(false)
    synchronized(uprightCache) { uprightCache[uri] = ok }
    return ok
}
