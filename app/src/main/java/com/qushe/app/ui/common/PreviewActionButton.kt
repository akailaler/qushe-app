package com.qushe.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 全屏预览底部的操作按钮。
 *
 * 回收站（恢复 / 彻底删除）和收藏夹（移出收藏 / 删除）共用同一种外观，
 * 保证"在预览里直接操作"这件事在两个场景里的手感一致。
 *
 * **造型是描边 + 半透明底，不是实心色块。**
 * 实心 accent 底在这个场景里太抢眼：整个屏幕的主体是照片，
 * 底部两块饱和色会持续把视线从画面上拽走。
 * 这里改成"用强调色描边 + 强调色文字"——一样能区分动作性质，
 * 但重量降到和照片同一层级，只在需要时才被注意到。
 */
@Composable
fun PreviewActionButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.Medium,
        )
    }
}
