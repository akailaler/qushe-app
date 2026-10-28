package com.qushe.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「检查更新」的版本号比较。
 *
 * 为什么值得单独测：这段是**纯算术**，错了不会有异常、不会崩，
 * 只会安静地告诉你"已是最新版本" —— 用户永远发现不了。
 * 而且它最容易踩的坑（字典序）看起来是对的。
 */
class VersionCompareTest {

    @Test
    fun `相同版本不算更新`() {
        assertFalse(isNewer("1.4.6", "1.4.6"))
    }

    @Test
    fun `补零后相同也不算更新`() {
        // 1.4 与 1.4.0 是同一版；缺的段当 0
        assertFalse(isNewer("1.4", "1.4.0"))
        assertFalse(isNewer("1.4.0", "1.4"))
    }

    @Test
    fun `补丁号进位能正确比较`() {
        // 字典序下 "1.4.10" < "1.4.9"，这正是这个函数存在的理由
        assertTrue(isNewer("1.4.10", "1.4.9"))
        assertFalse(isNewer("1.4.9", "1.4.10"))
    }

    @Test
    fun `次版本号优先于补丁号`() {
        assertTrue(isNewer("1.5", "1.4.99"))
        assertFalse(isNewer("1.4.99", "1.5"))
    }

    @Test
    fun `主版本号优先于其它段`() {
        assertTrue(isNewer("2.0", "1.99.99"))
        assertFalse(isNewer("1.99.99", "2.0"))
    }

    @Test
    fun `多出一段的版本更新`() {
        assertTrue(isNewer("1.4.5.1", "1.4.5"))
        assertFalse(isNewer("1.4.5", "1.4.5.1"))
    }

    @Test
    fun `解析不了的段当 0 处理不崩溃`() {
        assertFalse(isNewer("abc", "1.4.5"))
        assertTrue(isNewer("1.4.5", "abc"))
    }

    @Test
    fun `空字符串不会抛异常`() {
        assertFalse(isNewer("", ""))
        assertTrue(isNewer("1.0", ""))
    }
}
