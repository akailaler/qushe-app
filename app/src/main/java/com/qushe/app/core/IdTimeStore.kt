package com.qushe.app.core

import java.io.File

/**
 * 「id → 某个时刻」的持久化表。
 *
 * 和 [IdSetStore] 同一套理由（纯文本文件、不给 SharedPreferences 增负担），
 * 只是值从"有没有"变成了"什么时候"。
 *
 * ---- 为什么需要它 ----
 * 系统回收站保留 30 天这件事，**应用侧查不到"删除时刻"**。
 * 唯一看起来相关的列是 `MediaStore.MediaColumns.DATE_EXPIRES`，
 * 但实测在这台机器（小米 / HyperOS）上它给的值**不是到期时刻**
 * （刚删进去的条目算出来是"今天到期"），靠它显示剩余天数只会显示错的。
 *
 * 与其显示一个错的数字，不如自己记账：删除真正发生（用户在系统弹窗里点了确认）
 * 之后记下时刻，回收站页用它算"还剩几天"。
 * **算不出来的条目就不显示角标** —— 缺一个角标比显示一个错的强。
 */
class IdTimeStore(private val file: File) {

    private val times = HashMap<Long, Long>()
    private var dirty = false

    fun load() {
        times.clear()
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEachLine
                // 一行是 "<id> <时刻>"；用空格而不是制表符，避免某些编辑器替换。
                val parts = trimmed.split(' ', limit = 2)
                if (parts.size != 2) return@forEachLine
                val id = parts[0].trim().toLongOrNull() ?: return@forEachLine
                val at = parts[1].trim().toLongOrNull() ?: return@forEachLine
                times[id] = at
            }
        }
        dirty = false
    }

    operator fun get(id: Long): Long? = times[id]

    /** 记下"这个 id 是什么时候被删的"，同一 id 重复记以最后一次为准。 */
    fun put(id: Long, at: Long) {
        if (times[id] != at) {
            times[id] = at
            dirty = true
        }
    }

    fun putAll(ids: Collection<Long>, at: Long) {
        var changed = false
        ids.forEach { id ->
            if (times[id] != at) {
                times[id] = at
                changed = true
            }
        }
        if (changed) dirty = true
    }

    fun remove(id: Long) {
        if (times.remove(id) != null) dirty = true
    }

    /** 恢复出来的条目直接从表里拿掉 —— 不然它会被算成"还在回收站里"。 */
    fun removeAll(ids: Collection<Long>) {
        var changed = false
        ids.forEach { if (times.remove(it) != null) changed = true }
        if (changed) dirty = true
    }

    fun snapshot(): Map<Long, Long> = HashMap(times)

    fun flush() {
        if (!dirty) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(times.entries.joinToString(separator = "\n") { "${it.key} ${it.value}" })
            dirty = false
        }
    }
}