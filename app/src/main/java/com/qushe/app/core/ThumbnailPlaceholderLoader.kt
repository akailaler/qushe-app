package com.qushe.app.core

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers

/**
 * 只干一件事的第二个 ImageLoader：**把系统缩略图垫到格子底下**。
 *
 * ---- 为什么要单开一条队列 ----
 *
 * 瀑布流的每一格其实是**两个请求**：先垫一张系统缩略图（`loadThumbnail`，
 * 通常 1~2ms），再用按显示尺寸解出来的高清图盖上。
 *
 * 两个请求原本都走全局那个 `ImageLoader`。而高清图是常规解码（一张几十毫秒），
 * Coil 内部有解码并行上限 —— 一屏十几格同时进场时，高清图把通道占满，
 * 那批"本该立刻出来"的小缩略图排在后面，于是**垫底图也迟迟不到**，
 * 格子就空着。连续清理过图片之后尤其明显：列表刚变、缓存刚被冲掉，
 * 整屏都是新请求，全挤在一起。
 *
 * 现在垫底图走这里：独立的 dispatcher（4 路，别把 Binder 打爆）、
 * 独立的小内存缓存（不跟高清图抢额度）、不写磁盘（系统那份缩略图本来就在设备上，
 * 再存一份是浪费）。结果是**垫底图永远先到**，高清图晚多少都不影响"看得见"。
 *
 * ---- 为什么不能直接和主 loader 共用一个磁盘目录 ----
 *
 * Coil 的磁盘缓存对同一目录是独占的，两个 ImageLoader 同时打开会报错，
 * 所以这里显式指定了自己的目录。
 */
object ThumbnailPlaceholderLoader {

    /** 一屏二十来格，每张 512 长边约 0.8MB，48MB 够盖住来回滑动。 */
    private const val MAX_MEMORY_BYTES = 48L * 1024 * 1024
    private const val MAX_DISK_BYTES = 16L * 1024 * 1024

    @Volatile
    private var instance: ImageLoader? = null

    // `limitedParallelism` 在当前这版 coroutines 上还挂着 Experimental 标记。
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun get(context: Context): ImageLoader {
        instance?.let { return it }
        val app = context.applicationContext
        return synchronized(this) {
            instance ?: ImageLoader.Builder(app)
                .components {
                    add(SystemThumbnailFetcher.Factory(app))
                }
                .memoryCache {
                    MemoryCache.Builder(app)
                        .maxSizeBytes(MAX_MEMORY_BYTES.toInt())
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(app.cacheDir.resolve("thumb_placeholder"))
                        .maxSizeBytes(MAX_DISK_BYTES)
                        .build()
                }
                .crossfade(false)
                // 4 路足够：每个请求就是一次 loadThumbnail，串太多反而拖慢。
                .dispatcher(Dispatchers.IO.limitedParallelism(4))
                .build()
                .also { instance = it }
        }
    }
}
