package com.qushe.app.core

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 实况照片（Motion Photo / 动态照片）。
 *
 * Android **完全没有官方 API**：MediaStore 里既没有 IS_MOTION_PHOTO 这样的列，
 * 也没有任何相关类（已核对 android-35 的 api-versions.xml 与 android.jar）。
 * 所以只能自己解析。共有三代格式，必须都覆盖：
 *
 *  1. **旧标准（MicroVideo V1 / GCamera）**：XMP 里 `GCamera:MicroVideoOffset`
 *     直接给出"视频起点距文件尾多少字节"。Google 早期机型、旧版 MIUI 用这个。
 *  2. **新标准（MotionPhoto Container）**：视频长度记在 `Container:Directory` 下
 *     那个 `Item:Semantic="MotionPhoto"` 的 `Item:Length` 上。
 *     三星、Pixel 很早就切了，**小米从 HyperOS 3 起也切到新标准**。
 *  3. **无元数据兜底**：嵌入的视频一定是 MP4，MP4 的第一个盒子是
 *     `[4字节盒子长度][f t y p][主品牌]`。所以直接在文件里搜 `ftyp`，
 *     位置减 4 就是视频起点。这个方法不依赖任何厂商元数据。
 *
 * 三种手段按"便宜到昂贵"的顺序尝试，任何一步成功就停。
 */
object MotionPhoto {

    /**
     * 读文件头做嗅探时最多读多少字节。
     *
     * XMP 位于 JPEG 的 APP1 段，规范上 EXIF 段最长 64KB，所以 XMP 一定在前 128KB 内。
     */
    private const val HEAD_SNIFF_BYTES = 128 * 1024

    private const val UNKNOWN_OFFSET = -1L

    /**
     * 尾部探测窗口。
     *
     * 实况片段一定是追加在文件末尾的，所以视频自己的 `ftyp` 盒子必然落在
     * 「文件尾往前 TAIL_PROBE_BYTES」这个窗口内（除非单段视频本身就超过这个大小）。
     * 只看尾部就不必为每张照片读整个文件 —— 那是让它能作为"每次切换都跑"的廉价探测的关键。
     */
    private const val TAIL_PROBE_BYTES = 4L * 1024 * 1024

    /** 扫描 MP4 签名时的分块大小与跨块重叠量。 */
    private const val SCAN_CHUNK = 1 shl 18
    private const val SCAN_OVERLAP = 64

    // ---- XMP 解析 ----

    /** 旧标准：MicroVideoOffset 是元素 <Camera:MicroVideoOffset>123</...> 或属性形式。 */
    private val MICRO_VIDEO_OFFSET = Regex("""MicroVideoOffset['">=\s]*(\d+)""")

    /**
     * 新标准：Item:Length 可能是属性 `Item:Length="123"`，
     * 也可能是元素 `<Item:Length>123</Item:Length>`，这一个正则两种都能吃。
     */
    private val ITEM_LENGTH = Regex("""Item:Length\s*[=>]\s*["']?\s*(\d+)""", RegexOption.IGNORE_CASE)

    /** Item:Semantic 的取值，属性与元素形式同样都用 `[=>]` 兜住。 */
    private val ITEM_SEMANTIC = Regex("""Item:Semantic\s*[=>]\s*["']?\s*([A-Za-z]+)""", RegexOption.IGNORE_CASE)

    private const val ITEM_MARKER = "Container:Item"

    /**
     * 从 XMP 文本判断并解析偏移量。
     *
     * 返回 null 表示不是实况照片（或判断不了）；`-1` 表示确认是实况照片但偏移量未知；
     * `> 0` 表示"视频起点距文件尾多少字节"，可直接使用。
     */
    fun detectInXmp(xmp: String?): Long? {
        if (xmp.isNullOrBlank()) return null

        // 1. 旧标准：直接给了偏移量
        MICRO_VIDEO_OFFSET.find(xmp)?.let { match ->
            match.groupValues.getOrNull(1)?.toLongOrNull()?.let { offset ->
                if (offset > 0L) return offset
            }
        }

        // 2. 新标准：先找到语义是视频的那个 Item，再取它的 Length
        containerVideoLength(xmp)?.let { return it }

        // 3. 认得出来是实况，但这次拿不到偏移量
        val marked = xmp.contains("MotionPhoto", ignoreCase = true) ||
            xmp.contains("MicroVideo", ignoreCase = true) ||
            xmp.contains("GCamera", ignoreCase = true) ||
            xmp.contains("Container:Directory", ignoreCase = true)
        return if (marked) UNKNOWN_OFFSET else null
    }

    /**
     * 在新标准的 Container 目录里找出视频项的长度。
     *
     * 做法是把 XMP 按 `Container:Item` 切成一段段，只在"语义是视频"的那一段里读 Length。
     * 不能简单地全局搜第一个 Length —— 主图项也有 Length（图片自身大小），
     * 取错了会算出一个完全错误的偏移。
     */
    private fun containerVideoLength(xmp: String): Long? {
        var cursor = xmp.indexOf(ITEM_MARKER, 0, ignoreCase = true)
        while (cursor >= 0) {
            val next = xmp.indexOf(ITEM_MARKER, cursor + ITEM_MARKER.length, ignoreCase = true)
            // 属性写法和元素写法都在这一段里；没有下一个 Item 时留够窗口给子元素
            val segmentEnd = if (next > 0) next else minOf(xmp.length, cursor + 512)
            val segment = xmp.substring(cursor, segmentEnd)

            val semantic = ITEM_SEMANTIC.find(segment)?.groupValues?.getOrNull(1)
            val isVideoItem = semantic != null &&
                (semantic.equals("MotionPhoto", ignoreCase = true) ||
                    semantic.equals("Video", ignoreCase = true))

            if (isVideoItem) {
                ITEM_LENGTH.find(segment)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
                    if (it > 0L) return it
                }
            }
            cursor = next
        }
        return null
    }

    // ---- 文件读取 ----

    /** 一次探测的结果。 */
    data class Resolution(val isMotionPhoto: Boolean, val offsetFromEnd: Long?)

    /**
     * 廉价探测：判断是不是实况照片、以及元数据里有没有现成的偏移量。
     *
     * 不做全文件扫描 —— 它会在每张照片切到前台时被调用，必须足够便宜。
     * 真正的全文件扫描由调用方在用户明确长按播放时追加。
     *
     * 顺序刻意从便宜到昂贵：
     *  1. MediaStore 的 XMP 列已经算出来的值 —— 零 I/O
     *  2. 读一次文件头自己解析 XMP —— 覆盖"列是空的"的情况
     *  3. 读一次文件**尾部**找 MP4 签名 —— 覆盖"厂商根本不写 XMP"的情况
     */
    fun probe(
        context: Context,
        uri: Uri,
        knownFromColumn: Long?,
    ): Resolution {
        if (knownFromColumn != null && knownFromColumn > 0L) {
            return Resolution(isMotionPhoto = true, offsetFromEnd = knownFromColumn)
        }

        // 1. 读一次文件头，自己解析 XMP
        val fromHead = detectInXmp(sniffHeader(context, uri))
        if (fromHead != null && fromHead > 0L) {
            return Resolution(isMotionPhoto = true, offsetFromEnd = fromHead)
        }

        // 2. 头里没有（或只有标记没有偏移），退到**只看尾部**的签名扫描。
        //    实况片段一定是追加在文件末尾的，所以视频的 ftyp 盒子必然落在尾部窗口内。
        //    这样既不必读整个文件，又能覆盖"元数据完全不可用"的机型。
        val fileSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: 0L

        val tailOffset = if (fileSize > 0L) {
            findByMp4Signature(
                context = context,
                uri = uri,
                fileSize = fileSize,
                fromOffset = (fileSize - TAIL_PROBE_BYTES).coerceAtLeast(0L),
            )
        } else {
            null
        }

        // 列里标了 -1（认得出来但没偏移）同样算实况照片
        val isMotion = knownFromColumn != null || fromHead != null || tailOffset != null
        return Resolution(isMotionPhoto = isMotion, offsetFromEnd = tailOffset)
    }

    fun sniffHeader(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(HEAD_SNIFF_BYTES)
            var filled = 0
            while (filled < buffer.size) {
                val read = input.read(buffer, filled, buffer.size - filled)
                if (read <= 0) break
                filled += read
            }
            // 用 ISO-8859-1 解码：只做字节级搜索，不能让解码失败把内容吃掉
            String(buffer, 0, filled, Charsets.ISO_8859_1)
        }
    }.getOrNull()

    /** MP4 主品牌白名单。ftyp 盒子里写着这些，基本可确认后面跟着的是视频。 */
    private val MP4_BRANDS = listOf(
        "isom", "iso2", "iso4", "iso5", "iso6", "mp41", "mp42",
        "3gp4", "3gp5", "3gp6", "avc1", "dash", "M4V ", "M4A ", "MSNV", "qt  ",
    )

    /**
     * 在文件里搜索 MP4 的 `ftyp` 盒子，反推出视频起点。
     *
     * 这是不依赖任何厂商元数据的兜底手段，对小米 / 三星 / Pixel 一视同仁。
     * 为了不被 JPEG 压缩数据里偶然出现的 "ftyp" 骗到，加了三重校验：
     * 盒子长度合理、主品牌在白名单里、以及视频必须落在文件后半段
     * （实况照片的视频一定是追加在尾部的）。
     * 如果正好能读到紧邻的 JPEG 结束标记 FFD9，那就直接确认。
     *
     * [fromOffset] 用于只在尾部窗口里找（见 [probe]）；传 0 就是全文扫描。
     */
    fun findByMp4Signature(
        context: Context,
        uri: Uri,
        fileSize: Long,
        fromOffset: Long = 0L,
    ): Long? {
        if (fileSize <= 0L) return null
        val start = fromOffset.coerceIn(0L, fileSize)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 跳过开头，只读窗口内的部分
                var skipped = 0L
                while (skipped < start) {
                    val step = input.skip(start - skipped)
                    if (step <= 0L) break
                    skipped += step
                }

                val buffer = ByteArray(SCAN_CHUNK)
                var consumed = skipped
                var tail = ByteArray(0)

                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break

                    // 拼上上一块的尾巴，避免签名正好跨在分块边界上被漏掉
                    val window = tail + buffer.copyOf(read)
                    val windowBase = consumed - tail.size

                    var searchFrom = 0
                    while (true) {
                        val at = indexOfFtyp(window, searchFrom) ?: break
                        val boxStart = windowBase + at - 4L
                        if (isPlausibleMp4Start(window, at, boxStart, fileSize)) {
                            val offsetFromEnd = fileSize - boxStart
                            return@use if (offsetFromEnd > 0L) offsetFromEnd else null
                        }
                        searchFrom = at + 1
                    }

                    consumed += read
                    val keep = minOf(window.size, SCAN_OVERLAP)
                    tail = window.copyOfRange(window.size - keep, window.size)
                }
                null
            }
        }.getOrNull()
    }

    private fun indexOfFtyp(buffer: ByteArray, from: Int): Int? {
        val f = 'f'.code.toByte()
        val t = 't'.code.toByte()
        val y = 'y'.code.toByte()
        val p = 'p'.code.toByte()
        var i = from
        val limit = buffer.size - 4
        while (i <= limit) {
            if (buffer[i] == f && buffer[i + 1] == t && buffer[i + 2] == y && buffer[i + 3] == p) {
                return i
            }
            i++
        }
        return null
    }

    private fun isPlausibleMp4Start(
        window: ByteArray,
        ftypIndex: Int,
        boxStart: Long,
        fileSize: Long,
    ): Boolean {
        if (boxStart < 0L || boxStart >= fileSize - 8L) return false

        // ftyp 盒子的长度写在它前面 4 个字节，大端序
        val sizeIndex = ftypIndex - 4
        if (sizeIndex < 0) return false
        val boxSize = ((window[sizeIndex].toInt() and 0xFF) shl 24) or
            ((window[sizeIndex + 1].toInt() and 0xFF) shl 16) or
            ((window[sizeIndex + 2].toInt() and 0xFF) shl 8) or
            (window[sizeIndex + 3].toInt() and 0xFF)
        // ftyp 一般 16~32 字节，这里放宽到 1KB
        if (boxSize !in 16..1024) return false

        // ftyp 之后 4 个字节是主品牌
        val brandIndex = ftypIndex + 4
        if (brandIndex + 4 > window.size) return false
        val brand = String(window, brandIndex, 4, Charsets.ISO_8859_1)
        if (MP4_BRANDS.none { it == brand }) return false

        // 最强证据：视频之前正好是 JPEG 的结束标记 FFD9
        val eoiIndex = sizeIndex - 2
        if (eoiIndex >= 0 &&
            window[eoiIndex] == 0xFF.toByte() &&
            window[eoiIndex + 1] == 0xD9.toByte()
        ) {
            return true
        }

        // 中间可能有厂商填充，拿不到紧邻的 FFD9 也接受，
        // 但要求视频落在文件后半段 —— 实况视频一定追加在尾部，前半段的多半是误报
        return boxStart > fileSize / 2L
    }

    // ---- 抽取 ----

    /**
     * 把追加在文件尾部的那段 MP4 抽到缓存目录。
     *
     * 用流式复制而不是一次性读进内存：实况片段通常几 MB，但可能几十 MB，
     * 一次性 ByteArray 在低端机上会直接 OOM。
     */
    fun extractVideo(
        context: Context,
        uri: Uri,
        cacheName: String,
        offsetFromEnd: Long,
        fileSize: Long,
    ): File? {
        if (offsetFromEnd <= 0L || fileSize <= 0L) return null
        val videoStart = fileSize - offsetFromEnd
        if (videoStart <= 0L || videoStart >= fileSize) return null

        val expectedLength = fileSize - videoStart
        val target = File(File(context.cacheDir, "motion"), "$cacheName.mp4")

        // 抽过一次就复用，避免每次长按都重新拷几十 MB
        if (target.exists() && target.length() == expectedLength) return target

        return runCatching {
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                var skipped = 0L
                while (skipped < videoStart) {
                    // InputStream.skip 允许少跳，必须循环补足
                    val step = input.skip(videoStart - skipped)
                    if (step <= 0L) break
                    skipped += step
                }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.exists() && target.length() > 0L) target else null
        }.getOrNull()
    }

    /**
     * 检查文件在 [at] 这个位置是否正好是一个 MP4 盒子的起点。
     *
     * 用来**验证元数据给出的偏移量**，不能无条件相信厂商写的值：
     * 新容器格式里的 `Item:Length` 是那个项目的长度，并不等于"距文件尾的字节数"
     * （尾部可能还有 padding，或者 XMP 排在视频之后）——
     * 照它切出来的文件根本没法播，这正是"长按播不出来"的可能原因之一。
     * 验证不过就退回全文扫描签名，那条路不依赖任何厂商字段。
     */
    fun looksLikeMp4At(context: Context, uri: Uri, at: Long): Boolean {
        if (at < 0L) return false
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                var skipped = 0L
                while (skipped < at) {
                    val step = input.skip(at - skipped)
                    if (step <= 0L) break
                    skipped += step
                }
                val head = ByteArray(12)
                var filled = 0
                while (filled < head.size) {
                    val read = input.read(head, filled, head.size - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled < head.size) return@use false

                // 盒子大小（大端）+ 类型 + 主品牌
                val boxSize = ((head[0].toInt() and 0xFF) shl 24) or
                    ((head[1].toInt() and 0xFF) shl 16) or
                    ((head[2].toInt() and 0xFF) shl 8) or
                    (head[3].toInt() and 0xFF)
                val type = String(head, 4, 4, Charsets.ISO_8859_1)
                val brand = String(head, 8, 4, Charsets.ISO_8859_1)
                boxSize in 16..1024 && type == "ftyp" && MP4_BRANDS.any { it == brand }
            }
        }.getOrNull() == true
    }

    /** 清掉抽取缓存。缓存目录本来就归系统管，这里只用于主动回收。 */
    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, "motion").deleteRecursively() }
    }
}
