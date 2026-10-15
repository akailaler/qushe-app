package com.qushe.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

/** 「回到那天」面板的行标题。用 2025/3/27 而不是「2025年3月27日」—— 列表里更整齐、更短。 */
private val DAY_KEY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d")

// 数字格式化统一钉死 Locale.US：跟随系统区域时，某些地区（如阿拉伯语系）
// 会输出本地数字字形，体积和时长会变得不可读。
private val NUMERIC_LOCALE: Locale = Locale.US

fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val digit = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, SIZE_UNITS.lastIndex)
    if (digit == 0) return "$bytes B"
    val value = bytes / 1024.0.pow(digit.toDouble())
    return String.format(NUMERIC_LOCALE, "%.1f %s", value, SIZE_UNITS[digit])
}

fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val total = millis / 1000L
    val hours = total / 3600L
    val minutes = (total % 3600L) / 60L
    val seconds = total % 60L
    return if (hours > 0L) {
        String.format(NUMERIC_LOCALE, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(NUMERIC_LOCALE, "%d:%02d", minutes, seconds)
    }
}

fun formatDateTime(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return dateTime.format(TIME_FORMATTER)
}

fun formatDate(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return dateTime.format(DATE_FORMATTER)
}

/** 相对时间，用在卡片底部信息栏，比绝对日期更有"回忆感"。 */
fun relativeDayLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(target, today)
    return when {
        days == 0L -> "今天"
        days == 1L -> "昨天"
        days in 2L..6L -> "$days 天前"
        days < 30L -> "${days / 7} 周前"
        days < 365L -> "${days / 30} 个月前"
        else -> "${days / 365} 年前 · ${target.format(DATE_FORMATTER)}"
    }
}

/** 「2020/6/6」—— 具体日期，给「i」胶囊第一行用（设置里可切换成相对时间）。 */
fun exactDateLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DAY_KEY_FORMATTER)
}

/** 「2025/3/27」。判断"是不是同一天"也可以直接比字符串，不必再算零点。 */
fun formatDayKey(millis: Long): String {
    if (millis <= 0L) return "未知日期"
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DAY_KEY_FORMATTER)
}

/**
 * 「回到那天」大标题用的日期：**2023/1/19** 这种写法。
 *
 * 和 [formatDayKey] 刻意分开：那个是内部当键用的（要能排序、能比较），
 * 这个是给人看的 —— 斜杠 + 不带前导零，参照「去留」的写法，
 * 比"2023年1月19日"短一截，也比 ISO 的连字符更像日期。
 */
fun formatDayTitle(millis: Long): String {
    if (millis <= 0L) return "未知日期"
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.year}/${date.monthValue}/${date.dayOfMonth}"
}

/**
 * 某个时刻所属那一天的零点（本地时区）。
 *
 * 「回到那天」的全部口径都建立在这个函数上 —— 直方图分组、取某天的内容、
 * 界面上显示的日期，三处必须用同一套换算，否则会出现"点进去少了一张"
 * 这种按日期跨时区边界漂移的怪事（比如 UTC 存的 3/27 23:00 在东八区是 3/28 07:00）。
 *
 * 先前这段逻辑写在 `MediaRepository` 里（用 java.util.Calendar），
 * 提到这里是为了让 UI 层也能直接算 —— 进日期视图时要知道"当前这张属于哪天"。
 * 两处都走这一个实现，就不会有两套口径。
 */
fun dayStartOf(millis: Long): Long {
    if (millis <= 0L) return 0L
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

/** 「周五」。日期面板的第二段信息，帮助确认"那天是不是周末"。 */
fun weekdayLabel(millis: Long): String {
    if (millis <= 0L) return ""
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .dayOfWeek
        .getDisplayName(java.time.format.TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE)
}
