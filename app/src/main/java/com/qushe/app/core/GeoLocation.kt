package com.qushe.app.core

import android.content.Context
import android.location.Geocoder
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** 经纬度。 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 拍摄地点。
 *
 * **`LATITUDE` / `LONGITUDE` 是受 `ACCESS_MEDIA_LOCATION` 约束的敏感列** ——
 * 性质和之前踩过坑的 `XMP` 完全一样：Android 会对媒体里的位置做隐私脱敏，
 * 没授权就只能读到空值，部分厂商的 Provider 甚至会在投影里出现它们时直接抛异常。
 * 所以这里严格做到三件事：
 *  1. 绝不放进主查询投影，只在需要看某一条时单独查它的 Uri；
 *  2. 全程 `runCatching` 兜底，拿不到就当作"没有位置信息"，绝不影响浏览；
 *  3. 结果按条目 / 按坐标缓存 —— 一次旅行里几十段视频往往在同一个地方，
 *     没必要反复查。
 */
object GeoLocation {

    private const val TAG = "GeoLocation"

    private val pointCache = HashMap<Long, GeoPoint?>()
    private val nameCache = HashMap<String, String?>()

    /**
     * 上一次 [readPoint] 是否**因为异常而失败**，而不是"确实没有值"。
     *
     * 这两种情况在界面上都表现为"不显示地点那一行"，但原因完全不同：
     * 前者是权限或设备实现的问题，后者是内容本身就没有。
     * 设置页的自检靠它把两者区分开。
     */
    var lastReadFailed: Boolean = false
        private set

    /**
     * 系统**根本没有返回** `latitude` / `longitude` 这两列（而不是返回了空值）。
     *
     * 这两件事必须分开：[lastReadFailed] 是抛异常，这里是列名压根没出现在结果里。
     * 之前的自检分辨不出它们，于是"系统不返回这两列"会被误报成"照片没有位置信息"。
     */
    var lastColumnMissing: Boolean = false
        private set

    /**
     * 读取某一条媒体的经纬度。返回 null 表示没有位置信息、或两条路都没走通。
     *
     * **两条独立的路径**，顺序从便宜到昂贵：
     *  1. MediaStore 的经纬度列 —— 一次轻量查询，但受 `ACCESS_MEDIA_LOCATION` 约束，
     *     而且部分 ROM 干脆不返回这两列；
     *  2. 直读原始文件的 EXIF —— 不依赖 MediaStore 有没有填这两个列，是唯一的退路。
     */
    suspend fun readPoint(context: Context, item: MediaItem): GeoPoint? {
        pointCache[item.storeKey()]?.let { return it }

        lastReadFailed = false
        lastColumnMissing = false

        val point = readFromColumns(context, item) ?: readFromExif(context, item)

        pointCache[item.storeKey()] = point
        return point
    }

    private fun readFromColumns(context: Context, item: MediaItem): GeoPoint? = runCatching {
        val columns = arrayOf(latColumn(item), lngColumn(item))
        context.contentResolver.query(item.uri, columns, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val latIdx = c.getColumnIndex(latColumn(item))
            val lngIdx = c.getColumnIndex(lngColumn(item))
            if (latIdx < 0 || lngIdx < 0) {
                lastColumnMissing = true
                return@use null
            }
            if (c.isNull(latIdx) || c.isNull(lngIdx)) return@use null
            val lat = c.getDouble(latIdx)
            val lng = c.getDouble(lngIdx)
            // (0,0) 是"没有记录位置"时最常见的占位值，不是真的几内亚湾
            if (lat == 0.0 && lng == 0.0) null else GeoPoint(lat, lng)
        }
    }.onFailure {
        Log.w(TAG, "读取经纬度列失败", it)
        lastReadFailed = true
    }.getOrNull()

    /** 直读原始文件的 EXIF 拿 GPS。视频不走这条 —— ExifInterface 对视频的支持不可靠。 */
    private suspend fun readFromExif(context: Context, item: MediaItem): GeoPoint? {
        if (item.kind == MediaKind.VIDEO) return null
        val exif = PhotoExifReader.read(context, item) ?: return null
        val lat = exif.latitude ?: return null
        val lng = exif.longitude ?: return null
        return GeoPoint(lat, lng)
    }

    /**
     * 经纬度反查地名，取「省 市 区」三段并去重 —— 比完整地址好读得多。
     *
     * Geocoder 依赖设备自带的地理编码服务，服务不可用或没有网络时会返回 null，
     * 这时界面就不显示这一行。
     */
    @Suppress("DEPRECATION")
    suspend fun describe(context: Context, point: GeoPoint): String? = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "%.3f,%.3f", point.latitude, point.longitude)
        if (nameCache.containsKey(key)) return@withContext nameCache[key]

        val name = runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(point.latitude, point.longitude, 1)
                ?.firstOrNull()
                ?.let { addr ->
                    listOfNotNull(
                        addr.adminArea,                      // 省
                        addr.locality ?: addr.subAdminArea,  // 市
                        addr.subLocality,                    // 区 / 县
                    )
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                        .joinToString(" ")
                        .ifBlank { null }
                }
        }.onFailure { Log.w(TAG, "反查地名失败", it) }.getOrNull()

        nameCache[key] = name
        name
    }

    fun clearCache() {
        pointCache.clear()
        nameCache.clear()
        PhotoExifReader.clearCache()
    }

    // 平台把这两个列标成了废弃（官方建议改用 ExifInterface 读原始文件），
    // 但 ExifInterface 对**视频**的 GPS 支持并不可靠，而这里主要就是给视频用的。
    // 这两列在 MediaStore 里依然有数据、依然可读，所以保留并显式抑制警告。
    @Suppress("DEPRECATION")
    private fun latColumn(item: MediaItem): String = if (item.kind == MediaKind.VIDEO) {
        MediaStore.Video.VideoColumns.LATITUDE
    } else {
        MediaStore.Images.ImageColumns.LATITUDE
    }

    @Suppress("DEPRECATION")
    private fun lngColumn(item: MediaItem): String = if (item.kind == MediaKind.VIDEO) {
        MediaStore.Video.VideoColumns.LONGITUDE
    } else {
        MediaStore.Images.ImageColumns.LONGITUDE
    }
}
