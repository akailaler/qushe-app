package com.qushe.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.qushe.app.core.AppContainer
import com.qushe.app.core.LongThumbnailFetcher
import com.qushe.app.core.SystemThumbnailFetcher

class QusheApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /**
     * 注册三类解码器，缺一个就会有一类内容显示不正常：
     *
     * - [VideoFrameDecoder] 让 Coil 能直接对视频 Uri 取首帧。这是视频整理页体验的关键：
     *   缩略图 = 视频第一帧，播放器接管时画面几乎不跳变，不会"先黑一下再出画面"。
     * - [GifDecoder] 让 GIF 真正动起来。不加的话只会停在第一帧。
     * - [ImageDecoderDecoder]（API 28+）负责动态 WebP 和动态 HEIF —— 这两种
     *   GifDecoder 管不了，正是区分两者的意义所在。
     *
     * 另外注册了 [SystemThumbnailFetcher]：对**明确要一格缩略图**的请求
     * （`rememberThumbnailRequest` 打上标记的那些）直接用系统相册已经生成好的
     * 缩略图，图片和视频都走这条 —— 省掉每次读原始大文件、解整帧的开销。
     * 取不到时它返回 null，请求照样落到下面的解码器，行为不变，只是慢一点。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(SystemThumbnailFetcher.Factory(this@QusheApp))
                add(LongThumbnailFetcher.Factory(this@QusheApp))
                add(VideoFrameDecoder.Factory())
                add(GifDecoder.Factory())
                add(ImageDecoderDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
