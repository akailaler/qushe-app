package com.qushe.app.core

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 从**原始文件**里读出来的照片元数据。
 *
 * 为什么不只用 MediaStore：它的 `LATITUDE` / `LONGITUDE` 是受权限约束的敏感列，
 * 而且不同 ROM 的行为不一致 —— 有的把值返回成空，有的干脆一开始就不返回这两列。
 * 直读文件里的 EXIF 是另一条互不依赖的路，两条都试过才算尽力。
 *
 * 顺带也把设备、光圈、快门、ISO、焦距读出来，供详情面板展示。
 */
data class PhotoExif(
    val make: String? = null,
    val model: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** "Apple iPhone 15 Pro Max" —— 厂商已经出现在型号里就不重复拼。 */
    val deviceLabel: String?
        get() = when {
            model.isNullOrBlank() -> make?.takeIf { it.isNotBlank() }
            make.isNullOrBlank() -> model
            model.startsWith(make, ignoreCase = true) -> model
            else -> "$make $model"
        }
}

object PhotoExifReader {

    private const val TAG = "PhotoExif"

    private val cache = HashMap<Long, PhotoExif?>()

    fun contains(key: Long): Boolean = cache.containsKey(key)

    fun cached(key: Long): PhotoExif? = cache[key]

    fun clearCache() = cache.clear()

    suspend fun read(context: Context, item: MediaItem): PhotoExif? = withContext(Dispatchers.IO) {
        val key = item.storeKey()
        if (cache.containsKey(key)) return@withContext cache[key]

        val result = runCatching { parse(context, item.uri) }
            .onFailure { Log.w(TAG, "读取 EXIF 失败", it) }
            .getOrNull()
        cache[key] = result
        result
    }

    private fun parse(context: Context, uri: Uri): PhotoExif? {
        // setRequireOriginal 要求拿到**原始文件**而不是系统给的降级副本，EXIF 才完整。
        // 没有 ACCESS_MEDIA_LOCATION 时它不会抛错，只是位置标签仍为 null ——
        // 那种情况由 GeoLocation 的另一条路兜。
        val target = runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        val stream = context.contentResolver.openInputStream(target) ?: return null

        return stream.use { input ->
            val exif = ExifInterface(input)

            // 用无参的 getLatLong()：它返回 DoubleArray，精度比老的 FloatArray 版本好，
            // 老的那个已被平台标记弃用。
            val latLong = runCatching { exif.getLatLong() }.getOrNull()
            val lat = latLong?.getOrNull(0)
            val lng = latLong?.getOrNull(1)
            // (0,0) 是"没有记录"的常见占位，不是真的几内亚湾
            val validGps = lat != null && lng != null && !(lat == 0.0 && lng == 0.0)

            PhotoExif(
                make = exif.text(ExifInterface.TAG_MAKE),
                model = exif.text(ExifInterface.TAG_MODEL),
                fNumber = exif.rational(ExifInterface.TAG_F_NUMBER)?.let { "f/${trimZeros(it)}" },
                exposureTime = exif.exposureLabel(),
                iso = exif.text(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { "ISO $it" },
                focalLength = exif.rational(ExifInterface.TAG_FOCAL_LENGTH)
                    ?.let { "${trimZeros(it)} mm" },
                latitude = if (validGps) lat else null,
                longitude = if (validGps) lng else null,
            )
        }
    }

    private fun ExifInterface.text(tag: String): String? =
        runCatching { getAttribute(tag) }.getOrNull()?.trim()?.ifBlank { null }

    private fun ExifInterface.rational(tag: String): Double? =
        text(tag)?.let { parseRational(it) }

    /**
     * 快门时间的显示形式。
     *
     * EXIF 里存的是秒数（分数形式），但摄影习惯是：短于 1 秒写成 `1/100 s`，
     * 长于等于 1 秒才写成 `2 s`。直接显示"0.01 s"没人看得懂。
     */
    private fun ExifInterface.exposureLabel(): String? {
        val seconds = rational(ExifInterface.TAG_EXPOSURE_TIME) ?: return null
        return if (seconds > 0.0 && seconds < 1.0) {
            "1/${(1.0 / seconds).roundToInt()} s"
        } else {
            "${trimZeros(seconds)} s"
        }
    }

    /** EXIF 的有理数可能是 `1/100` 这种分数，也可能是 `30`。两种都要吃。 */
    private fun parseRational(raw: String): Double? {
        val s = raw.trim()
        val slash = s.indexOf('/')
        if (slash <= 0) return s.toDoubleOrNull()
        val numerator = s.substring(0, slash).trim().toDoubleOrNull() ?: return null
        val denominator = s.substring(slash + 1).trim().toDoubleOrNull() ?: return null
        if (denominator == 0.0) return null
        return numerator / denominator
    }

    private fun trimZeros(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
