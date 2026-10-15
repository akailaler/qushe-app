package com.qushe.app.core

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 极简手写依赖容器。
 *
 * 这个体量的应用不值得引入 Hilt/Koin —— 一个 Application 级别的单例就够了，
 * 而且少一层注解处理器，构建也更快。
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val prefs = Prefs(appContext)
    val repo = MediaRepository(appContext)

    val favorites = IdSetStore(File(appContext.filesDir, "favorites.txt")).also { it.load() }
    val seen = IdSetStore(File(appContext.filesDir, "seen.txt")).also { it.load() }

    /** 「喜欢」。和收藏分开存 —— 喜欢不会同步到系统相册。 */
    val likes = IdSetStore(File(appContext.filesDir, "likes.txt")).also { it.load() }

    /** 已经和系统相册对齐过收藏状态的条目，用来算「还差哪些没同步」。 */
    val syncedFavorites = IdSetStore(File(appContext.filesDir, "synced_favorites.txt"))
        .also { it.load() }

    val sessions = SessionRepository(repo, prefs, seen, favorites, likes, syncedFavorites)

    init {
        // 旧版本存过 10 / 15 / 20 这些已经下线的档位。不归一的话，
        // 设置页会一项都不选中，而实际组大小又还是那个旧值 —— 用户完全看不出是怎么回事。
        if (prefs.groupSize !in Prefs.GROUP_SIZE_CHOICES) {
            prefs.groupSize = Prefs.DEFAULT_GROUP_SIZE
        }
    }

    /** 进程被杀之前把内存里的集合刷回磁盘。 */
    fun persist() {
        sessions.persist()
    }

    // ---- 异步落盘 ----
    //
    // `IdSetStore.flush()` 是**整文件重写**：`seen` 会涨到媒体库量级
    // （几万条 ≈ 260KB 文本），主线程做"拼 260KB 字符串 + 写 flash"
    // 约 10-30ms。`MainActivity.onStop` 每次退到后台都要付一次 ——
    // 而那正好发生在应用切换动画期间，是最容易看见卡顿的时刻。
    //
    // 丢一个独立作用域（不跟任何 UI 生命周期走，退到后台也要能写完）。
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 落盘移到 IO 线程。软数据（已看进度）可接受极小的丢失窗口。 */
    fun persistAsync() {
        ioScope.launch { sessions.persist() }
    }
}
