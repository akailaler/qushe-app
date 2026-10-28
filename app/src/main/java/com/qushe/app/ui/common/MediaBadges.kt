package com.qushe.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.qushe.app.ui.theme.BadgeAccent
import com.qushe.app.ui.theme.BadgeScrim
import com.qushe.app.ui.theme.BadgeText

/**
 * 贴在**照片上**的小角标：视频时长 / 「截屏」/「长图」。
 *
 * ---- 为什么把它抽出来 ----
 * 同样的东西在收藏页、瀑布流里各写了一遍。不多，但**配色只改对了其中一处**：
 * 瀑布流用的是不跟主题翻的 [BadgeAccent] / [BadgeText]，
 * 收藏页还在用会跟主题翻的 `AccentInfo` —— 浅色模式下那枚角标变成了
 * 深蓝字压在 70% 黑的底上，正是之前用户报过的"看不清"。
 *
 * 抽成一个组件之后，**颜色只有一个出处**，以后不会再出现"改了两处漏一处"。
 *
 * ---- 为什么不跟主题翻 ----
 * 底图是用户的照片，颜色不可控：深底浅字在任何一张照片上都读得出来，
 * 而浅色主题下的"白底深字"压在一张深色照片上会直接消失。
 *
 * @param accent true = 用强调色（视频时长），false = 普通文字色。
 */
@Composable
fun MediaBadge(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BadgeScrim)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) BadgeAccent else BadgeText,
        )
    }
}
