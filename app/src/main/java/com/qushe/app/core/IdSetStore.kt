package com.qushe.app.core

import java.io.File

/**
 * 一组 Long id 的持久化集合。
 *
 * 用纯文本文件而不是 SharedPreferences：已看集合可能涨到几万条，
 * SharedPreferences 会在启动时把所有条目一次性载入内存并常驻，
 * 这里只需要一个 HashSet，落盘时整文件重写即可（会话结束时才写一次）。
 */
class IdSetStore(private val file: File) {

    private val ids = HashSet<Long>()
    private var dirty = false

    fun load() {
        ids.clear()
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) trimmed.toLongOrNull()?.let { ids.add(it) }
            }
        }
        dirty = false
    }

    fun contains(id: Long): Boolean = ids.contains(id)

    fun add(id: Long) {
        if (ids.add(id)) dirty = true
    }

    fun remove(id: Long) {
        if (ids.remove(id)) dirty = true
    }

    fun clear() {
        if (ids.isNotEmpty()) {
            ids.clear()
            dirty = true
        }
    }

    /** 只保留给定 id，用于清理已经被删掉、永远不会再出现的条目。 */
    fun retainOnly(keep: Set<Long>) {
        if (ids.retainAll(keep)) dirty = true
    }

    fun size(): Int = ids.size

    fun snapshot(): Set<Long> = HashSet(ids)

    fun flush() {
        if (!dirty) return
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ids.joinToString(separator = "\n"))
            dirty = false
        }
    }
}
