package com.qushe.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 手动「检查更新」。
 *
 * 只查 GitHub 的 latest release，**不做后台轮询、不上报任何东西** ——
 * 这个 App 平时完全不联网（清单里只有这一处请求会用到网络权限）。
 */
sealed interface UpdateResult {
    /** 有更新的版本（[version] 已去掉 v 前缀）。 */
    data class Newer(val version: String, val pageUrl: String) : UpdateResult

    /** 已经是最新。 */
    data object UpToDate : UpdateResult

    /** 查不到：网络、限流、仓库没发过 release。 */
    data class Failed(val reason: String) : UpdateResult
}

private const val LATEST_RELEASE_API =
    "https://api.github.com/repos/akailaler/qushe-app/releases/latest"

/**
 * 查最新版本并与 [current] 比较。
 *
 * 失败一律降级成 [UpdateResult.Failed]，不抛异常 —— 这个动作是用户主动点的，
 * 任何技术细节都不该变成崩溃。
 */
suspend fun checkForUpdate(current: String): UpdateResult = withContext(Dispatchers.IO) {
    val conn = runCatching {
        (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub API 要求带 UA，否则会被拒
            setRequestProperty("User-Agent", "qushe-android")
        }
    }.getOrElse {
        return@withContext UpdateResult.Failed("无法发起请求")
    }

    val body = runCatching {
        try {
            if (conn.responseCode !in 200..299) {
                val hint = when (conn.responseCode) {
                    403, 429 -> "请求太频繁，过一会儿再试"
                    404 -> "仓库还没有发布过版本"
                    else -> "服务器返回 ${conn.responseCode}"
                }
                return@withContext UpdateResult.Failed(hint)
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrElse {
        return@withContext UpdateResult.Failed("网络连接失败，检查一下网络")
    }

    val json = runCatching { JSONObject(body) }.getOrElse {
        return@withContext UpdateResult.Failed("返回内容看不懂")
    }
    val tag = json.optString("tag_name").trim()
    val url = json.optString("html_url").trim()
    if (tag.isEmpty() || url.isEmpty()) {
        return@withContext UpdateResult.Failed("返回内容里没有版本号")
    }

    val latest = tag.removePrefix("v")
    if (isNewer(latest, current)) {
        UpdateResult.Newer(latest, url)
    } else {
        UpdateResult.UpToDate
    }
}

/**
 * [latest] 是否比 [current] 新。按点分段比较，缺的段当 0
 * （`1.4` 与 `1.4.0` 视为相同）。
 *
 * 不用字符串比较：`"1.3.9" > "1.3.10"` 在字典序下是 true，但版本号不是这样。
 */
private fun isNewer(latest: String, current: String): Boolean {
    val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
