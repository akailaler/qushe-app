package com.qushe.app.ui.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.qushe.app.R
import com.qushe.app.ui.theme.TextPrimary

/**
 * 建一个用于翻页 / 列表里的播放器视图。
 *
 * **必须用 TextureView，不能用 Media3 默认的 SurfaceView。**
 * SurfaceView 由系统在独立图层里合成，屏幕位置更新和 Compose 的滚动不同步 ——
 * 上下滑动时视频画面会跟页面脱节、闪烁，这正是"滑动时有闪烁"的根因。
 */
@SuppressLint("InflateParams")
@androidx.annotation.OptIn(UnstableApi::class)
fun createVideoPlayerView(context: Context): PlayerView =
    (LayoutInflater.from(context).inflate(R.layout.view_video_player, null) as PlayerView).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        setShutterBackgroundColor(AndroidColor.TRANSPARENT)
        // 明确指出它不吃触摸。播放器视图经常被放在手势层的上方（屏幕级单视图、
        // 实况播放覆盖层），必须保证点击 / 滑动原样穿到下层，
        // 否则翻页、点击暂停这些手势会静默失效。
        isClickable = false
        isFocusable = false
    }

/**
 * 屏幕级单视图播放器。
 *
 * 这是「上下滑会闪一下」和「离屏页留一块旧帧」那个取舍的**第三条路**：
 *
 *  - 每页各挂一个 → 离屏页的 TextureView 会保留最后一帧（旧画面残留在屏幕上）；
 *  - 视图随页面建 / 毁 → 切页瞬间重绑 Surface，闪一下；
 *  - **全局只有这一份视图、挂在屏幕层** → 既不建 / 毁，也没有离屏副本，
 *    两个问题一起消失。
 *
 * 位置不靠"页内布局"，而是跟着 pager 的当前页偏移走：
 * [PagerState.currentPageOffsetFraction] 为 0 时视图正对当前页；
 * 往下一页拖时为正（当前页被推向布局起点），把视图反向平移相同距离即可与页面同步。
 *
 * 注意读 fraction 的位置：在 `graphicsLayer { }` 的**绘制阶段**读，
 * 滑动时只失效绘制、不触发重组 —— 这是跟手且不掉帧的关键。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ScreenLevelVideoLayer(
    player: ExoPlayer,
    /** 首帧就绪前保持透明，露出下面的缩略图 —— 避免"宽高比未定的一帧被拉伸"。 */
    visible: Boolean,
    pagerState: PagerState,
    /** true = 横向分页（预览页里照片视频混排），false = 竖向分页（整理视频页）。 */
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx -> createVideoPlayerView(ctx) },
        update = { view -> if (view.player !== player) view.player = player },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (visible) 1f else 0f
                val fraction = pagerState.currentPageOffsetFraction
                if (horizontal) {
                    translationX = -fraction * size.width
                } else {
                    translationY = -fraction * size.height
                }
            },
    )
}

/** 从任意 Context 里挖出 Activity —— Compose 拿到的 Context 可能是包装过的。 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 进 / 出全屏：把 Activity 转成横屏并隐藏系统栏（退出时还原）。 */
fun applyFullscreenOrientation(activity: Activity?, on: Boolean) {
    if (activity == null) return
    activity.requestedOrientation = if (on) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    runCatching {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * 「全屏观看」胶囊。造型参照抖音：半透明深底 + 细描边 + 图标与文字。
 *
 * 用图标 + 文字而不是纯图标，是因为全屏这个动作不是每个视频都有
 * （只对横屏视频出现），带文字更容易让人明白点下去会发生什么。
 */
@Composable
fun FullscreenPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0x99000000))
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = FullscreenIcon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "全屏观看",
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
        )
    }
}
