package com.qushe.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qushe.app.ui.theme.AccentKeep
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.OverlayBorder
import com.qushe.app.ui.theme.QushePalette
import com.qushe.app.ui.theme.QusheSkin
import com.qushe.app.ui.theme.QusheSkins
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextSecondary
import com.qushe.app.ui.theme.TextTertiary

/**
 * 配色色卡页。
 *
 * 一个皮肤同时有浅色与深色两版，所以每张卡**左右各画一半**：
 * 左边浅色、右边深色。这样一眼能看出"换成它以后白天/夜里各长什么样"，
 * 不用先切深浅再回来对比。
 *
 * 卡片里的色块是**该皮肤自己的颜色**，不是当前主题的颜色 ——
 * 否则所有卡都会长得一样（都用当前主题画）。
 *
 * 用 Dialog 而不是导航目的地：这一页不需要返回栈，也不需要参与底栏切换。
 */
@Composable
fun SkinPickerSheet(
    currentId: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // 自己处理 insets：默认窗口不铺满状态栏/导航栏
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ---- 顶栏 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 18.dp, top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "配色",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            // 说清和「深浅模式」的分工，否则会以为选了皮肤就不用管深浅
                            text = "每套都含浅色和深色；深浅仍由上面的「深浅模式」决定",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(QusheSkins, key = { it.id }) { skin ->
                        SkinCard(
                            skin = skin,
                            selected = skin.id == currentId,
                            onClick = { onPick(skin.id) },
                        )
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "后面还会再加几套。用的是同一套结构，加一套只改一处配色表。",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinCard(skin: QusheSkin, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) AccentKeep else OverlayBorder,
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            MiniSwatch(palette = skin.light, modifier = Modifier.weight(1f))
            MiniSwatch(palette = skin.dark, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = skin.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) AccentKeep else TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AccentKeep,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * 半张色卡的示意：底色 + 一张"图片卡"+ 一条主色 + 一个删除色点 + 一条文字条。
 *
 * 只用这几块，是因为它们正好覆盖了 App 里最显眼的四个颜色角色
 * （页面底、卡片、主色、删除），再加更多元素反而看不出区别。
 */
@Composable
private fun MiniSwatch(palette: QushePalette, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxHeight().background(palette.ink)) {
        // 图片卡
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 9.dp, top = 9.dp)
                .width(38.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.surface1),
        )
        // 文字条
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 9.dp, top = 43.dp)
                .width(28.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(palette.textTertiary),
        )
        // 主色（选中态）
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 9.dp, bottom = 11.dp)
                .width(34.dp)
                .height(7.dp)
                .clip(CircleShape)
                .background(palette.accentKeep),
        )
        // 删除色
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 9.dp, top = 9.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(palette.accentDelete),
        )
    }
}
