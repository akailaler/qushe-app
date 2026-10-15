package com.qushe.app.ui.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qushe.app.core.MediaItem
import com.qushe.app.core.PhotoExif
import com.qushe.app.core.formatSize
import com.qushe.app.core.relativeDayLabel
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Surface1
import com.qushe.app.ui.theme.Surface3
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary
import com.qushe.app.ui.common.RoundActionButton
import com.qushe.app.ui.common.StarBorderIcon
import java.util.Calendar

/**
 * 喜欢按钮（心形）。
 *
 * 只记在本应用里，**绝不写进系统相册**。
 * 想让相册也知道的那一档用右边的星形（[StarButton]）。
 * 理由不是好不好看，而是"留个印象"和"要让系统相册也知道"是两种不同强度的意图，
 * 混在一起会把本机相册塞满用户并不想标记的东西。
 */
@Composable
fun FavoriteButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 规格（44dp 圆、深色半透明底、22dp 图标）统一由 [RoundActionButton] 定义 ——
    // 收藏页 / 喜欢页的图片预览也用同一份，两边不会走偏。
    RoundActionButton(
        icon = if (selected) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        label = if (selected) "取消喜欢" else "喜欢",
        modifier = modifier,
        tint = if (selected) AccentKeep else TextPrimary,
        onClick = onClick,
    )
}

/**
 * 收藏按钮（星形）。
 *
 * 和左边的「喜欢」心形是**一对，但不是一回事**：
 *  · 收藏（这个星形）—— 会同步进系统相册的收藏夹，在相册里也看得到；
 *  · 喜欢（那边的红心）—— 只记在本应用里，绝不动系统相册。
 * 形状刻意做得不一样：两个动作的后果不同，长得一样会让人以为说的是同一件事。
 */
@Composable
fun StarButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RoundActionButton(
        icon = if (selected) Icons.Filled.Star else StarBorderIcon,
        label = if (selected) "取消收藏" else "收藏",
        modifier = modifier,
        tint = if (selected) AccentKeep else TextPrimary,
        onClick = onClick,
    )
}

/**
 * 日期 + 地点的小胶囊，右侧带 ⓘ。
 *
 * 点它展开完整信息。把细节收在这里而不是常驻在画面上，
 * 是为了让看照片这件事本身不被打扰 —— 想看的时候才展开。
 */
@Composable
fun InfoPill(
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 顶层单例：这个胶囊在详情面板里常驻，且会被反复重组 ——
    // 组合期现算 `RoundedCornerShape` 每次都要 new 一个（值相同，
    // 所以不会导致裁剪路径重建，但对象分配是白付的 GC 压力）。
    Row(
        modifier = modifier
            .clip(InfoPillShape)
            .background(Color(0xB3000000))
            .border(1.dp, Color(0x26FFFFFF), InfoPillShape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 230.dp),
        ) {
            Text(
                text = primary,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
            )
            if (secondary.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = "详细信息",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// 详情面板
// ---------------------------------------------------------------------------

/**
 * 完整信息面板。布局参照「去留」：日期在最上，然后是相片信息、拍摄位置。
 *
 * 没做地图缩略图 —— 那需要一个地图 SDK（国内还要单独的地图服务），
 * 为一个信息面板引入不划算。地名 + 坐标已经够定位了。
 */
@Composable
fun PhotoDetailContent(
    item: MediaItem,
    exif: PhotoExif?,
    locationName: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = relativeDayLabel(item.dateTakenMillis),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = fullDateLabel(item.dateTakenMillis),
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )

        // ---- 相片信息 ----
        Spacer(Modifier.height(20.dp))
        SectionLabel("相片信息")

        exif?.deviceLabel?.let { device ->
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("设备", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Text(
                    text = device,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        val chips = listOfNotNull(
            exif?.fNumber?.let { it to "光圈" },
            exif?.exposureTime?.let { it to "快门" },
            exif?.iso?.let { it to "感光度" },
            exif?.focalLength?.let { it to "焦距" },
        )
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (value, label) ->
                    ExifChip(value = value, label = label, modifier = Modifier.weight(1f))
                }
            }
        }

        // ---- 拍摄时间 / 添加时间 ----
        // 刻意分成两行：截图、下载、聊天记录存下来的文件，拍摄时间和入库时间
        // 可能差好几年，混成一个"日期"会让人以为图是那会儿拍的。
        if (item.dateTakenMillis > 0L) {
            Spacer(Modifier.height(10.dp))
            SectionLabel("时间")
            Spacer(Modifier.height(4.dp))
            DetailRow("拍摄时间", fullDateLabel(item.dateTakenMillis))
        }
        if (item.dateAddedMillis > 0L &&
            (item.dateTakenMillis <= 0L || item.dateAddedMillis != item.dateTakenMillis)
        ) {
            DetailRow("添加时间", fullDateLabel(item.dateAddedMillis))
        }

        Spacer(Modifier.height(10.dp))
        DetailRow("文件名", item.name)
        if (item.width > 0 && item.height > 0) {
            DetailRow("分辨率", "${item.width} × ${item.height}")
        }
        DetailRow("文件大小", formatSize(item.sizeBytes))
        if (item.bucket.isNotBlank()) {
            DetailRow("所在文件夹", item.bucket)
        }

        // ---- 拍摄位置 ----
        val lat = exif?.latitude
        val lng = exif?.longitude
        if (!locationName.isNullOrBlank() || lat != null) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("拍摄位置")
            Spacer(Modifier.height(8.dp))
            Text(
                text = locationName ?: "坐标已记录，但没能解析出地名",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            if (lat != null && lng != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "%.5f, %.5f".format(lat, lng),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** EXIF 小方块：上面是值，下面是含义。 */
@Composable
private fun ExifChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(ExifChipShape)
            .background(Surface1)
            .border(1.dp, Surface3.copy(alpha = 0.5f), ExifChipShape)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
        )
    }
}

// ---------------------------------------------------------------------------
// 日期格式化
// ---------------------------------------------------------------------------

private val WEEK_DAYS = arrayOf("日", "一", "二", "三", "四", "五", "六")

/** "2024年8月18日 星期日 16:43" —— 相对时间用 core 里的 relativeDayLabel，这里只补绝对时间。 */
fun fullDateLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "%d年%d月%d日 星期%s %02d:%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        WEEK_DAYS[(cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)],
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
    )
}

/** 信息胶囊的圆角 —— 提到顶层，避免每次重组都新建实例。 */
private val InfoPillShape = RoundedCornerShape(percent = 50)

/** Exif 小标签的圆角 —— 同上。 */
private val ExifChipShape = RoundedCornerShape(10.dp)
