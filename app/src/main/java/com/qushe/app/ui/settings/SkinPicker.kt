package com.qushe.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qushe.app.ui.theme.DarkSkins
import com.qushe.app.ui.theme.Ink
import com.qushe.app.ui.theme.LightSkins
import com.qushe.app.ui.theme.OverlayBorder
import com.qushe.app.ui.theme.QusheSkin
import com.qushe.app.ui.theme.TextPrimary
import com.qushe.app.ui.theme.TextTertiary

/**
 * 配色页：**左边浅色、右边深色，各选一套**。
 *
 * ---- 为什么不再是一个皮肤含两版 ----
 * 白天要的是一张纸，夜里要的是一盏灯 —— 两者的关系不是"压暗"，是"换一套"。
 * 拆开之后，白天可以留白（月石初白），夜里换「紫罗兰夜」，互不牵制。
 * 跟随系统时两套都会用到，所以两列都要选。
 *
 * ---- 每张卡为什么不用当前主题画 ----
 * 用当前主题画的话，五张浅色卡会一模一样（都是此刻那套底色）。
 * 所以卡片内部的底色/文字/色条全部取自**它自己那套**，
 * 整张卡本身就是一张样张：看到什么，选中后就是什么。
 */
@Composable
fun SkinPickerSheet(
    lightId: Int,
    darkId: Int,
    onPickLight: (Int) -> Unit,
    onPickDark: (Int) -> Unit,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
            ) {
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
                            // 只说"怎么用"，不解释机制。
                            text = "左边白天用，右边夜里用，各选一套",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SkinColumn(
                        title = "浅色",
                        hint = "白天",
                        skins = LightSkins,
                        selectedId = lightId,
                        onPick = onPickLight,
                        modifier = Modifier.weight(1f),
                    )
                    // 一条竖线把两栏分开：这一页的核心就是"两件不同的事"，
                    // 光靠标题分不够，视觉上要有一条界线。
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(452.dp)
                            .background(OverlayBorder),
                    )
                    SkinColumn(
                        title = "深色",
                        hint = "夜里",
                        skins = DarkSkins,
                        selectedId = darkId,
                        onPick = onPickDark,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkinColumn(
    title: String,
    hint: String,
    skins: List<QusheSkin>,
    selectedId: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(bottom = 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        skins.forEach { skin ->
            SkinCard(
                skin = skin,
                selected = skin.id == selectedId,
                onClick = { onPick(skin.id) },
            )
        }
    }
}

/**
 * 一张色卡 = 一份样张。
 *
 * 卡里的底色、字色、色条全部来自**它自己那套皮肤**，所以十张卡彼此不同，
 * 也不用先切过去才知道长什么样。
 */
@Composable
private fun SkinCard(skin: QusheSkin, selected: Boolean, onClick: () -> Unit) {
    val p = skin.palette
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(shape)
            .background(p.ink)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) p.accentKeep else OverlayBorder,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                text = skin.name,
                color = p.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.height(7.dp))
            // 三个色块：卡片面、主色、删除色 —— 一眼看出这套的"纸、光、红"。
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .width(22.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.surface1),
                )
                Box(
                    Modifier
                        .width(15.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.accentKeep),
                )
                Box(
                    Modifier
                        .width(7.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(p.accentDelete),
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = p.accentKeep,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(15.dp),
            )
        } else {
            // 未选中也占一个位置，五张卡的右上角才对齐
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(15.dp)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(p.textTertiary.copy(alpha = 0.25f)),
            )
        }
    }
}
