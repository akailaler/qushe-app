package com.qushe.app.core

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把未捕获异常的堆栈写到应用私有目录，供用户在设置页一键导出。
 *
 * ---- 为什么需要它 ----
 * 用户报"闪退"时，没有堆栈就只能猜。而让他去开 USB 调试、装 adb、
 * 抓 `logcat -b crash`，门槛比"把闪退说清楚"高得多。
 * 这里在崩溃现场把堆栈落盘，下次打开设置页就能直接分享出来。
 *
 * 注意：被系统 LMK 杀掉（内存不足）**不会**走这里 —— 那种情况没有 Java 堆栈。
 * 如果设置页里没有"导出崩溃日志"，基本可以判断不是 Java 崩溃。
 */
object CrashLog {

    private const val DIR = "crash"
    private const val FILE = "last.txt"

    /** 在 Application.onCreate 里挂一次。必须把异常继续交给系统，不能吞。 */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** 上次崩溃的完整文本；没有就返回 null。 */
    fun read(context: Context): String? {
        val f = file(context)
        return if (f.isFile) runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(File(context.filesDir, DIR), FILE)

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        // 直接流式写盘，不在内存里拼整段字符串 ——
        // OOM 崩溃时任何大分配都可能再次失败，那就一条日志都留不下。
        runCatching {
            PrintWriter(File(dir, FILE).bufferedWriter()).use { w ->
                w.println("取舍 $version")
                w.println("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                w.println("设备：${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}")
                w.println("线程：${thread.name}")
                w.println()
                error.printStackTrace(w)
                w.flush()
            }
        }
    }
}
