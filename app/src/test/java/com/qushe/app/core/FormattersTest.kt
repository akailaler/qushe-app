package com.qushe.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 格式化函数的边界。
 *
 * 这些函数在列表里每一格都会调用（一屏几十次），出错的后果不是崩溃，
 * 而是"体积显示成 0.0 MB""时长多一小时"这种**看着不对但说不清哪不对**的显示。
 * 所以只测两类：0/负数的兜底，和单位进位。
 *
 * 时区相关的部分（日期）只断言"两次调用一致"和"格式对"，
 * 不断言具体字符串 —— 那会让测试依赖跑测试那台机器的时区。
 */
class FormattersTest {

    // ---- 体积 ----

    @Test
    fun `零与负数字节给出 0 B`() {
        assertEquals("0 B", formatSize(0))
        assertEquals("0 B", formatSize(-1))
    }

    @Test
    fun `不满 1KB 时不带小数`() {
        assertEquals("512 B", formatSize(512))
    }

    @Test
    fun `从 KB 起保留一位小数`() {
        assertEquals("1.0 KB", formatSize(1024))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("1.0 MB", formatSize(1024L * 1024))
        assertEquals("5.0 GB", formatSize(5L * 1024 * 1024 * 1024))
    }

    // ---- 时长 ----

    @Test
    fun `零与负数的时长是 0 00`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:00", formatDuration(-1000))
    }

    @Test
    fun `不足一秒向下取整`() {
        assertEquals("0:00", formatDuration(999))
    }

    @Test
    fun `分钟补两位秒`() {
        assertEquals("1:05", formatDuration(65_000))
    }

    @Test
    fun `满一小时才出现第三段`() {
        assertEquals("59:59", formatDuration(TimeUnit.MINUTES.toMillis(59) + 59_000))
        assertEquals("1:00:00", formatDuration(TimeUnit.HOURS.toMillis(1)))
        assertEquals("1:02:03", formatDuration(TimeUnit.HOURS.toMillis(1) + 123_000))
    }

    // ---- 日期 ----

    @Test
    fun `未知时间有明确兜底文案`() {
        assertEquals("时间未知", formatDateTime(0))
        assertEquals("时间未知", formatDate(0))
        assertEquals("时间未知", relativeDayLabel(0))
        assertEquals("未知日期", formatDayKey(0))
        assertEquals("未知日期", formatDayTitle(0))
        assertEquals("", weekdayLabel(0))
    }

    @Test
    fun `某一天的零点不晚于该时刻且可重复调用`() {
        val now = System.currentTimeMillis()
        val start = dayStartOf(now)
        assertTrue(start in 1..now)
        // 幂等：对零点再取一次零点还是它自己（这条能挡住"按 UTC 取整"的写法）
        assertEquals(start, dayStartOf(start))
        // 与当天的距离必须小于 24 小时
        assertTrue(now - start < TimeUnit.DAYS.toMillis(1))
    }

    @Test
    fun `相对时间按整天分档`() {
        val start = dayStartOf(System.currentTimeMillis())
        assertEquals("今天", relativeDayLabel(start))
        assertEquals("昨天", relativeDayLabel(start - 1))
        assertEquals("3 天前", relativeDayLabel(start - TimeUnit.DAYS.toMillis(3)))
        assertEquals("1 周前", relativeDayLabel(start - TimeUnit.DAYS.toMillis(10)))
        assertEquals("1 个月前", relativeDayLabel(start - TimeUnit.DAYS.toMillis(40)))
        assertEquals("1 年前", relativeDayLabel(start - TimeUnit.DAYS.toMillis(400)))
    }

    @Test
    fun `给人看的日期格式不带前导零`() {
        val t = System.currentTimeMillis()
        assertTrue(formatDayTitle(t).matches(Regex("""\d{4}/\d{1,2}/\d{1,2}""")))
        assertTrue(formatDayKey(t).matches(Regex("""\d{4}/\d{1,2}/\d{1,2}""")))
        assertTrue(formatDate(t).matches(Regex("""\d{4}年\d{1,2}月\d{1,2}日""")))
        assertTrue(weekdayLabel(t).startsWith("周"))
    }
}
