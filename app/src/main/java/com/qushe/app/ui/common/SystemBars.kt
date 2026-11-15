package com.qushe.app.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 沉浸态：把**系统状态栏和导航栏一起**藏掉。
 *
 * ---- 为什么要做 ----
 *
 * 之前"沉浸"只是把 App 自己的控件（顶栏、底栏、操作提示）淡出，
 * 系统那条状态栏还杵在最上面 —— 看一张竖构图的长图时，画面顶上永远缺一条，
 * 这不是沉浸，只是"我们自己的 UI 让开了"。
 *
 * ---- 为什么用 owner 集合，而不是一个 Boolean ----
 *
 * 三个地方都会要求沉浸（整理照片页的第三态、瀑布流放大、视频横屏全屏），
 * 它们**可能叠着**：整理页沉浸着捏合进"回到那天"、放大页又要求沉浸。
 * 如果各自直接 `hide()` / `show()`，上层退出时会把下层的沉浸一起取消 ——
 * 表现是"退了一层，状态栏莫名其妙冒出来"。
 *
 * 所以记的是"**现在有几个人要求隐藏**"：任何一方退出只把自己那条申请撤掉，
 * 只有集合空了才真的把栏还回来。
 */
@Composable
fun ImmersiveSystemBars(owner: String, hidden: Boolean) {
    val context = LocalContext.current
    val controller = rememberSystemBarsController(context)
    DisposableEffect(controller, owner, hidden) {
        if (controller == null) return@DisposableEffect onDispose { }
        ImmersiveRequests.update(owner, hidden, controller)
        onDispose {
            // 离开这一屏必须把自己那条申请撤掉，否则整个 App 会一直没状态栏。
            ImmersiveRequests.release(owner, controller)
        }
    }
}

@Composable
private fun rememberSystemBarsController(context: Context): WindowInsetsControllerCompat? =
    remember(context) {
        // `findActivity` 用 `VideoSurface.kt` 里那个（同包，早就有了，别再造一份）。
        val window = context.findActivity()?.window ?: return@remember null
        // 用 decorView 取控制器：这是唯一一处能同时控制状态栏和导航栏的入口。
        WindowCompat.getInsetsController(window, window.decorView)
    }


/** 见 [ImmersiveSystemBars] 的说明：记"有几个人要隐藏"，而不是一个布尔。 */
private object ImmersiveRequests {

    private val owners = HashSet<String>()

    fun update(owner: String, hidden: Boolean, controller: WindowInsetsControllerCompat) {
        if (hidden) owners.add(owner) else owners.remove(owner)
        apply(controller)
    }

    fun release(owner: String, controller: WindowInsetsControllerCompat) {
        owners.remove(owner)
        apply(controller)
    }

    private fun apply(controller: WindowInsetsControllerCompat) {
        if (owners.isEmpty()) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            // 从边缘轻扫可以临时唤出栏（半透明、几秒后自动消失）——
            // 全屏看不起状态栏时，这是唯一"看一眼时间"的办法。
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
