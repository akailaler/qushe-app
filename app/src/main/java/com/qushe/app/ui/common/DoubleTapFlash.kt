package com.qushe.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.common.StarFilledIcon

/** 双击之后要显示的那一下：[liked] 是"喜欢"还是"收藏"，[on] 是操作后的状态。 */
@Immutable
data class DoubleTapFlash(val liked: Boolean, val on: Boolean)

/**
 * 双击的反馈动画：**只有一颗图标**，从 0.72 弹到 1.0，同时渐显。
 *
 * 整理视频页和整理照片页共用它 —— 两个页面的这个动作语义完全一样，
 * 各写一份迟早会漂移（用户明确要求"图片页要和视频页的动画一样"）。
 *
 * 刻意不带文字：画面中央压一行"喜欢 · 已添加"太吵，
 * 图标本身的形状变化（空心 → 实心）已经把状态说清楚了。
 */
@Composable
fun DoubleTapFlashOverlay(flash: DoubleTapFlash, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0.72f) }
    LaunchedEffect(flash) {
        anim.snapTo(0.72f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
    Column(
        modifier = modifier.graphicsLayer {
            scaleX = anim.value
            scaleY = anim.value
            alpha = (anim.value * 1.35f - 0.2f).coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (flash.liked) {
                if (flash.on) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
            } else {
                if (flash.on) StarFilledIcon else StarBorderIcon
            },
            contentDescription = null,
            tint = if (flash.on) AccentKeep else TextPrimary,
            modifier = Modifier.size(92.dp),
        )
    }
}
