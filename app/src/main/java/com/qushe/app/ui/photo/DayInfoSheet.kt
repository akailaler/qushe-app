package com.qushe.app.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qushe.app.core.MediaItem
import com.qushe.app.core.formatDateTime
import com.qushe.app.ui.theme.Surface2
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * `i` 展开的详细信息。
 *
 * 和底部 `InfoPill` **不重复** —— 那儿已经给了日期、类型、尺寸、大小，
 * 这里只补"想深挖时才看"的那些：文件名、文件夹、精确到分秒的拍摄时间、
 * 格式、媒体库 ID、实况偏移、是否长图。
 */
@Composable
internal fun DayInfoSheet(item: MediaItem) {
    val rows = buildList {
        add("文件名" to item.name)
        if (item.bucket.isNotBlank()) add("文件夹" to item.bucket)
        add("拍摄时间" to formatDateTime(item.dateTakenMillis))
        if (item.mimeType.isNotBlank()) add("格式" to item.mimeType)
        add("媒体库 ID" to item.id.toString())
        item.motionOffsetFromEnd?.let { off ->
            add("实况视频" to if (off < 0) "有（偏移待读）" else "有（尾 -${off}B）")
        }
        add("长图" to if (item.isLongImage) "是" else "否")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2.copy(alpha = 0.94f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "详细信息",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            fontWeight = FontWeight.Medium,
        )
        rows.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = k,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    text = v,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
