package com.qushe.app.ui.common

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.qushe.app.core.SystemThumbnailFetcher

/**
 * 缩略图请求：**走系统缩略图 + 关掉 crossfade + 按布局尺寸降采样**。
 *
 * ---- 为什么要显式构造，而不是直接 `AsyncImage(model = uri)` ----
 *
 * 1. **系统缩略图**（1.3.24 起）：带上 [SystemThumbnailFetcher.REQUEST_KEY] 标记，
 *    交给 `SystemThumbnailFetcher` 去读系统相册**已经生成好的**那份缩略图。
 *    不管是视频帧还是照片，常规解码都要去读原始大文件；
 *    系统那份是早就做好并缓存下来的小图，通常 1~2ms 就出来，
 *    而且是**跨应用共享**的 —— 相册已经付过的代价，我们不用再付第二遍。
 *    取不到时 Fetcher 返回 null，自动落回常规解码，最坏只是慢、不会白块。
 *
 * 2. **crossfade**：ImageLoader 全局开着 `crossfade(true)`，于是每个缩略图
 *    加载完都多一层 `CrossfadeDrawable`（双绘制 + 200ms 动画器逐帧失效）。
 *    缩略图墙一次几十个格子、滚动时还在进出 —— 观感上就是"格子一个个慢慢亮"，
 *    实测是列表滚动卡顿的一大来源。
 *
 * 3. **视频帧的位置**：`videoFrameMillis` 不指定时 Coil 取的是**默认帧**，
 *    有些容器的默认帧在文件尾附近，seek 距离长 ⇒ 更慢。固定取第 0 毫秒
 *    附近的一帧，seek 距离最短，观感上也更接近"相册里那一格的样子"。
 *    （这条只在系统缩略图拿不到、退回归 `VideoFrameDecoder` 时才起作用。）
 *
 * 第 3 条对静态图无效 —— 这些参数只影响视频解码。
 */
@Composable
fun rememberThumbnailRequest(model: Any?): Any? {
    val context = LocalContext.current
    val uri = model as? Uri ?: return model
    return remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .setParameter(SystemThumbnailFetcher.REQUEST_KEY, true)
            .crossfade(false)
            // 视频帧取最前面那一帧，缩短 seek 距离
            .videoFrameMillis(0L)
            .build()
    }
}
