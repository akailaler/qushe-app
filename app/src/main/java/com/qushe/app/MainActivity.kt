package com.qushe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.qushe.app.ui.AppRoot
import com.qushe.app.ui.theme.QusheTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
