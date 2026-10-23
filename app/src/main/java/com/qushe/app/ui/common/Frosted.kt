package com.qushe.app.ui.common

import android.os.Build
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

/**
 * 毛玻璃的"背后那张图"。
 *
 * ---- 为什么不用系统级的背景模糊 ----
 *
 * Android 只有 12+ 的 `RenderEffect` 能真模糊，而且它作用在**绘制内容**上，
 * 不是"把身后的画面糊掉"。Compose 的 `Modifier.blur()` 同理 —— 只能糊自己这一层。
 * 所以这里的做法是：**在同一张照片上再画一份，把它糊掉**，当作玻璃背后的材质。
 *
 * 反正糊到 26dp 之后只剩色块，取景是否精确已经看不出来 ——
 * 所以按控件在屏幕上的大致位置传 [Alignment]（左边的心用 Start、右边的星用 End），
 * 取到的色块和它背后那块照片基本对得上。
 *
 * ---- 只取一张小图 ----
 *
 * 三个控件共用**同一个** painter（256px 的小图，Coil 内存缓存命中），
 * 不额外解三份。糊成色块之后，256 和 4K 看起来没有区别。
 */
@Composable
fun rememberFrostedBackdrop(uri: Uri?): Painter? {
    if (uri == null) return null
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(256, 256)
            .crossfade(false)
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}

/**
 * 在 [Box] 里铺一层"毛玻璃底"：模糊图 + 半透明色。
 *
 * Android 12 以下没有模糊，只剩半透明色 —— 那就是一块普通的半透明底，
 * 不至于难看，只是没有材质感。
 */
@Composable
fun BoxScope.FrostedBackground(
    painter: Painter?,
    tint: Color,
    alignment: Alignment = Alignment.Center,
    blurRadius: Dp = 26.dp,
) {
    if (painter != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier
                .matchParentSize()
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded),
        )
    }
    Box(Modifier.matchParentSize().background(tint))
}
