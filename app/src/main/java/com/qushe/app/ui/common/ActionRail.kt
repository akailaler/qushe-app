package com.qushe.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.OverlayBorder
import com.qushe.app.ui.theme.OverlaySoft
import com.qushe.app.ui.theme.QusheColors

/**
 * 侧边栏上的一枚动作：图标 + 文字，选中时轻微放大。
 *
 * 从视频整理页抽出来共用 —— 收藏页 / 喜欢页 / 回收站的编辑模式
 * 用同一套，视觉才一致（用户要求"和整理视频页面的一样"）。
 */
@Composable
fun RailAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    emphasized: Boolean = false,
    /** 水平镜像。用于把逆时针的图标翻成顺时针。 */
    mirror: Boolean = false,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (emphasized) 1.12f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "railScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer { if (mirror) scaleX = -scaleX },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/**
 * 「整理照片页」那套圆形动作按钮：深色半透明底 + 一圈淡边，图标居中。
 *
 * 喜欢（心）、收藏（星）、删除（回收站）三颗共用同一个规格 ——
 * 它们是一组，长得一样才像一组。
 *
 * 抽出来还有一个更实际的理由：`FavoriteButton` / `StarButton`
 * （在 `ui/photo/PhotoInfoUi.kt`）和收藏页图片预览里的这一排
 * 现在用的是**同一份定义**，以后调规格不会两边走偏。
 */
@Composable
fun RoundActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    /**
     * 非空 = 走"毛玻璃"：背后垫一层模糊的照片（见 [rememberFrostedBackdrop]）。
     * 收藏/喜欢页的预览用它；整理页、瀑布流那些不需要（背景不是单张照片）。
     */
    backdrop: Painter? = null,
    /** 取哪一块照片当玻璃底色 —— 左边的心用 Start、右边的星用 End。 */
    backdropAlignment: Alignment = Alignment.Center,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // ---- 毛玻璃只在浅色主题下开 ----
        //
        // 深色主题下本来就是"深色半透明压一层"，再糊一层看不出差别，
        // 反而多一次绘制。用户要求：深色回归原样。
        if (backdrop != null && !QusheColors.palette.isDark) {
            FrostedBackground(
                painter = backdrop,
                tint = OverlaySoft,
                alignment = backdropAlignment,
            )
        } else {
            Box(Modifier.matchParentSize().background(OverlaySoft))
        }
        Box(Modifier.matchParentSize().border(1.dp, OverlayBorder, CircleShape))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 竖排的一列动作，和视频整理页的侧栏同款。
 *
 * 只给**视频条目**用 —— 图片条目走 [RoundActionButton] 那一套
 * （和整理照片页一致，用户要求）。
 *
 * @param onRight true = 贴屏幕右下（设置里的"操作栏放在右侧"）。
 */
@Composable
fun ActionRail(
    onRight: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(
            start = if (onRight) 0.dp else 8.dp,
            end = if (onRight) 8.dp else 0.dp,
            bottom = 128.dp,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        actions()
    }
}
