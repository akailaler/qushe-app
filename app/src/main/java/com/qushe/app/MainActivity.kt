package com.qushe.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qushe.app.ui.AppRoot
import com.qushe.app.ui.theme.QusheTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // ---- 系统栏固定按"深色"处理 ----
        //
        // 这个 App 的底永远是 Ink（近黑），所以系统栏不该跟随系统浅色模式。
        // `enableEdgeToEdge()` 不带参数时用的是 `SystemBarStyle.auto(...)`：
        // 它按**系统当前是浅色还是深色**挑底色 —— 浅色模式下导航栏会给一层
        // 白色 90% 的半透明底（`lightScrim`）。
        //
        // 在用手势导航、或者系统本身是深色的机器上，这一层被内容盖住，看不出来；
        // 但三键导航 + 系统浅色（比如 MIUI 12.5 的红米 K20 Pro）时，
        // Android 10 不允许导航栏真透明，系统就退化成那条**白边**。
        //
        // 显式传 `dark()`：底色透明、图标用浅色，不再看系统配色。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // ---- 关掉系统的"对比度蒙层" ----
        //
        // Android 10 起，三键导航下如果导航栏是透明的，系统会自己补一层
        // 半透明底来保证按钮看得清（`isNavigationBarContrastEnforced`）。
        // 那层也是浅色 —— 和上面那条白边是同一件事的两个来源。
        // 我们的底够暗、图标够亮，不需要它。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val container = (application as QusheApp).container

        setContent {
            QusheTheme {
                AppRoot(container = container)
            }
        }
    }

    /** 已看 / 收藏集合只在会话结束时落盘，退到后台时补一次，避免被系统直接杀掉丢数据。 */
    override fun onStop() {
        super.onStop()
        runCatching { (application as QusheApp).container.persistAsync() }
    }
}
