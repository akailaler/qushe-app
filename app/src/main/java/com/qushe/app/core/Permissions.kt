package com.qushe.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 13 起媒体权限被拆成图片 / 视频两条，各自独立授权。
 * 用户完全可能只给了照片权限 —— 所以两个整理入口的可用性要分开判断，
 * 而不是简单地"要么全能用，要么全不能用"。
 *
 * 截屏属于图片（同一张 images 表），所以和照片共用 READ_MEDIA_IMAGES。
 */
val REQUIRED_MEDIA_PERMISSIONS: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

fun hasMediaPermission(context: Context): Boolean =
    REQUIRED_MEDIA_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * READ_MEDIA_IMAGES / READ_MEDIA_VIDEO 是 Android 13 才引入的常量，
 * 直接引用会被 Lint 判为 InlinedApi。但这两个字符串常量在编译期就已内联，
 * 运行时并不会去访问不存在的字段，而且本函数的三个调用点都在
 * `SDK_INT >= TIRAMISU` 的分支里。所以这里显式抑制。
 */
@Suppress("InlinedApi")
private fun permissionOf(kind: MediaKind): String = if (kind == MediaKind.VIDEO) {
    Manifest.permission.READ_MEDIA_VIDEO
} else {
    // PHOTO 与 SCREENSHOT 都是图片
    Manifest.permission.READ_MEDIA_IMAGES
}

/** 单独判断某一类是否已授权，用于在首页把不可用的入口置灰。 */
fun hasPermissionFor(context: Context, kind: MediaKind): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return hasMediaPermission(context)
    }
    return ContextCompat.checkSelfPermission(context, permissionOf(kind)) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * 读取媒体位置信息的权限。
 *
 * Android 会对媒体里的位置做隐私脱敏，没有这个权限时 `LATITUDE` / `LONGITUDE`
 * 只会是空值。它不是媒体读取权限的一部分，要单独申请。
 */
fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 单独为某一类申请权限时要用的权限数组。
 *
 * 注意必须区分版本：`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` 是 Android 13 才引入的，
 * 在 Android 11~12 上申请它们不会有任何反应（系统不认识这两个权限），
 * 那两个版本要申请的是 `READ_EXTERNAL_STORAGE`。
 */
fun permissionsFor(kind: MediaKind): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(permissionOf(kind))
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
