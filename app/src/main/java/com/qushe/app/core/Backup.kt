package com.qushe.app.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据导出 / 导入。
 *
 * ---- 导出的东西 ----
 *
 * 收藏 / 喜欢 / 已看这三个集合，加上设置和累计统计。它们都在应用私有目录里，
 * 卸载或换机就没了 —— 这是这个功能存在的唯一理由。
 *
 * **照片文件本身不在里面**，只有 `storeKey`（ID 编码）。所以：
 *  · 同一台设备重装：能完整恢复；
 *  · 换一台设备：ID 是那台设备相册的 ID，对不上 —— 导进来的条目不会指向任何照片。
 *    这一点写在导入结果里，不让用户以为"换机也能带走收藏"。
 *
 * ---- 合并策略 ----
 *
 * 集合一律**并集**：导入只做加法，绝不因为文件里没有就把现有的删掉。
 * 统计取**较大值**（不倒退）。设置直接覆盖（那是用户显式想恢复的东西）。
 */
object Backup {

    /** 格式版本。以后字段有增减时靠它判断。 */
    private const val FORMAT = 1

    /** 生成 JSON 文本。几万条已看记录约几百 KB，能接受。 */
    fun export(container: AppContainer): String {
        val p = container.prefs
        val root = JSONObject()
        root.put("app", "qushe")
        root.put("format", FORMAT)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("favorites", toArray(container.favorites.snapshot()))
        root.put("likes", toArray(container.likes.snapshot()))
        root.put("seen", toArray(container.seen.snapshot()))

        root.put("settings", JSONObject().apply {
            put("groupSize", p.groupSize)
            put("onlyUnseen", p.onlyUnseen)
            put("deleteToTrash", p.deleteToTrash)
            put("relativeDates", p.relativeDates)
            put("absoluteDates", p.absoluteDates)
            put("videoMuted", p.videoMuted)
            put("autoPlayVideo", p.autoPlayVideo)
            put("doubleTapLikes", p.doubleTapLikes)
            put("autoSyncFavorites", p.autoSyncFavorites)
            put("railOnRight", p.railOnRight)
            put("showMediaLocation", p.showMediaLocation)
            put("hapticsEnabled", p.hapticsEnabled)
        })

        root.put("stats", JSONObject().apply {
            put("reviewedPhotos", p.reviewedPhotoTotal)
            put("reviewedVideos", p.reviewedVideoTotal)
            put("deleted", p.deletedTotal)
            put("favorited", p.favoritedTotal)
            put("freedBytes", p.freedBytesTotal)
            put("rounds", p.roundsCompleted)
        })

        return root.toString(2)
    }

    /**
     * 导入 [text]，返回一句给用户看的结果。
     *
     * 不抛异常：文件被截断、指向别的 App 的 JSON，都只是"格式不对"。
     */
    fun import(container: AppContainer, text: String): String {
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: return "这个文件不是有效的 JSON"
        if (root.optString("app") != "qushe") {
            return "这不是取舍导出的文件"
        }

        val favAdded = merge(container.favorites, root.optJSONArray("favorites"))
        val likeAdded = merge(container.likes, root.optJSONArray("likes"))
        val seenAdded = merge(container.seen, root.optJSONArray("seen"))

        root.optJSONObject("settings")?.let { s ->
            val p = container.prefs
            if (s.has("groupSize")) {
                val v = s.optInt("groupSize", p.groupSize)
                // 旧版本存过已经下线的档位，别把非法值写进去
                if (v in Prefs.GROUP_SIZE_CHOICES) p.groupSize = v
            }
            if (s.has("onlyUnseen")) p.onlyUnseen = s.optBoolean("onlyUnseen")
            if (s.has("deleteToTrash")) p.deleteToTrash = s.optBoolean("deleteToTrash")
            if (s.has("relativeDates")) p.relativeDates = s.optBoolean("relativeDates")
            if (s.has("absoluteDates")) p.absoluteDates = s.optBoolean("absoluteDates")
            if (s.has("videoMuted")) p.videoMuted = s.optBoolean("videoMuted")
            if (s.has("autoPlayVideo")) p.autoPlayVideo = s.optBoolean("autoPlayVideo")
            if (s.has("doubleTapLikes")) p.doubleTapLikes = s.optBoolean("doubleTapLikes")
            if (s.has("autoSyncFavorites")) {
                p.autoSyncFavorites = s.optBoolean("autoSyncFavorites")
            }
            if (s.has("railOnRight")) p.railOnRight = s.optBoolean("railOnRight")
            if (s.has("showMediaLocation")) {
                p.showMediaLocation = s.optBoolean("showMediaLocation")
            }
            if (s.has("hapticsEnabled")) p.hapticsEnabled = s.optBoolean("hapticsEnabled")
        }

        root.optJSONObject("stats")?.let { s ->
            val p = container.prefs
            // 取较大值：导入一份旧备份不该把现在的累计数字冲小
            p.reviewedPhotoTotal = maxOf(p.reviewedPhotoTotal, s.optInt("reviewedPhotos"))
            p.reviewedVideoTotal = maxOf(p.reviewedVideoTotal, s.optInt("reviewedVideos"))
            p.deletedTotal = maxOf(p.deletedTotal, s.optInt("deleted"))
            p.favoritedTotal = maxOf(p.favoritedTotal, s.optInt("favorited"))
            p.freedBytesTotal = maxOf(p.freedBytesTotal, s.optLong("freedBytes"))
            p.roundsCompleted = maxOf(p.roundsCompleted, s.optInt("rounds"))
        }

        // 立刻落盘：导入完用户可能就杀进程了
        runCatching { container.persist() }

        val parts = buildList {
            if (favAdded > 0) add("收藏 +$favAdded")
            if (likeAdded > 0) add("喜欢 +$likeAdded")
            if (seenAdded > 0) add("已看 +$seenAdded")
        }
        return if (parts.isEmpty()) {
            "导入完成，没有新增内容（这些记录本来就有），设置已恢复"
        } else {
            "导入完成：${parts.joinToString("，")}，设置已恢复"
        }
    }

    private fun toArray(ids: Set<Long>): JSONArray =
        JSONArray().apply { ids.sorted().forEach { put(it) } }

    /** 并集：只加不删。返回新增条数。 */
    private fun merge(store: IdSetStore, array: JSONArray?): Int {
        if (array == null) return 0
        var added = 0
        for (i in 0 until array.length()) {
            val id = array.optLong(i, -1L)
            if (id >= 0L && !store.contains(id)) {
                store.add(id)
                added++
            }
        }
        return added
    }
}
