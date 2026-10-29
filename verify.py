#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
取舍 · 回归自检

一条命令看清"主要功能有没有被改坏"。**只读，不改任何东西。**

    python verify.py              只检查
    python verify.py --install    检查前先把最新 APK 装到设备上

退出码：0 = 全通过，1 = 有失败项（可直接接到 CI 或 pre-commit）

---- 为什么要写这个 ----
2026-10-15~16 一天发了 26 个版本，每次改完都要手动点一遍才能确认没坏。
更要紧的是：**核对脚本本身也会写错**，那次我把 `storeKey` 的编码判反，
算出"0 条对上"，据此得出"收藏同步完全没用"的错误结论，白折腾五轮。
所以这个脚本第一步就是**自证编码**，并且用一条真实样本做交叉验证。
"""

import argparse
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.abspath(__file__))
JAVA_SRC = os.path.join(ROOT, "app", "src", "main", "java")
PKG = "com.qushe.app"

PASS, FAIL, SKIP, WARN = "✓", "✗", "–", "△"
failures = []
warnings = []


# ---------------------------------------------------------------------------
# 基础设施
# ---------------------------------------------------------------------------

def adb(*args, timeout=25, with_stderr=False):
    """跑一条 adb 命令，返回 stdout（失败返回空串）。

    `with_stderr=True` 时把 stderr 也拼进来 —— `run-as` 的报错（"package not
    debuggable"）只在 stderr 里，不带就分不清"设备没连"和"装的是 release 包"。
    """
    try:
        r = subprocess.run(
            ["adb", *args],
            capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=timeout,
        )
        out = r.stdout or ""
        if with_stderr:
            out += r.stderr or ""
        return out
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return ""


def read(path):
    if not os.path.exists(path):
        return ""
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def ok(label, detail=""):
    print(f"  {PASS} {label}" + (f"   {detail}" if detail else ""))


def bad(label, detail=""):
    print(f"  {FAIL} {label}" + (f"   {detail}" if detail else ""))
    failures.append(label)


def skip(label, detail=""):
    print(f"  {SKIP} {label}" + (f"   {detail}" if detail else ""))


def warn(label, detail=""):
    """不算失败，但值得知道 —— 结尾会单独列出来。"""
    print(f"  {WARN} {label}" + (f"   {detail}" if detail else ""))
    warnings.append(label)


def section(n, title):
    print(f"\n=== {n}. {title} ===")


# ---------------------------------------------------------------------------
# storeKey 编码 —— 唯一真源，必须和 Kotlin 的 mediaStoreKey() 一致
#
#   Kotlin: fun mediaStoreKey(id: Long, isVideo: Boolean) =
#               id * 2L + if (isVideo) 1L else 0L
#   ⇒ 图片 = 偶数，视频 = 奇数
# ---------------------------------------------------------------------------

def media_store_key(media_id, is_video):
    return media_id * 2 + (1 if is_video else 0)


def decode_key(key):
    """返回 (media_id, is_video)。"""
    return (key // 2, False) if key % 2 == 0 else ((key - 1) // 2, True)


def check_encoding():
    """自证编码 —— 防的就是"核对脚本自己错了"这件事。"""
    section(0, "编码自检（先证明这段逻辑本身是对的）")
    cases = [
        # (id, is_video, 期望 key, 期望奇偶)
        (1, False, 2, "偶数=图片"),
        (1, True, 3, "奇数=视频"),
        (123456, False, 246912, "偶数=图片"),
        (123456, True, 246913, "奇数=视频"),
    ]
    all_ok = True
    for mid, is_video, expect, why in cases:
        got = media_store_key(mid, is_video)
        back = decode_key(got)
        good = got == expect and back == (mid, is_video)
        all_ok &= good
        print(f"  {PASS if good else FAIL} id={mid:<7} {'视频' if is_video else '图片'}"
              f" → key={got:<8} 反解回 {back}   ({why})")
        if not good:
            failures.append("编码自检")
    return all_ok


# ---------------------------------------------------------------------------
# 1. 版本号四处一致
# ---------------------------------------------------------------------------

def check_version():
    section(1, "版本号一致性（BuildConfig 单一来源 + 文件名 + 设备）")

    gradle = read(os.path.join(ROOT, "app", "build.gradle.kts"))
    vn = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
    vc = re.search(r'versionCode\s*=\s*(\d+)', gradle)
    gradle_vn = vn.group(1) if vn else None
    gradle_vc = vc.group(1) if vc else None

    settings = read(os.path.join(JAVA_SRC, "com", "qushe", "app",
                                "ui", "settings", "SettingsScreen.kt"))
    # 1.4.2 起版本号**只有一个真源**：设置页读 BuildConfig.VERSION_NAME。
    # 所以这里反过来查两件事：确实用了 BuildConfig；没有又冒出一个写死的「取舍 x.y.z」。
    uses_build_config = "BuildConfig.VERSION_NAME" in settings
    hardcoded_vn = re.search(r'"取舍\s*([0-9]+\.[0-9]+(?:\.[0-9]+)?)', settings)
    settings_vn = ("BuildConfig" if uses_build_config
                   else (hardcoded_vn.group(1) if hardcoded_vn else None))

    # 最新 APK 文件名。**仓库目录和工作区根都要找** ——
    # 版本化 APK 有时放在仓库里、有时放在工作区根，只认一处就会误报"版本落后"。
    apks = []
    for d in (ROOT, os.path.dirname(ROOT)):
        if not os.path.isdir(d):
            continue
        for f in os.listdir(d):
            if re.fullmatch(r"qushe-v[0-9.]+-debug\.apk", f):
                apks.append(os.path.join(d, f))
    apks.sort(key=lambda p: [int(x) for x in re.findall(r"\d+", os.path.basename(p))])
    apk_vn = None
    if apks:
        m = re.fullmatch(r"qushe-v([0-9.]+)-debug\.apk", os.path.basename(apks[-1]))
        apk_vn = m.group(1) if m else None

    device_vn = ""
    m = re.search(r"versionName=([0-9.]+)", adb("shell", "dumpsys", "package", PKG))
    if m:
        device_vn = m.group(1)

    print(f"     build.gradle.kts  : {gradle_vn} (code {gradle_vc})")
    print(f"     SettingsScreen    : {settings_vn}")
    print(f"     最新 APK          : {apk_vn}  ({os.path.basename(apks[-1]) if apks else '无'})")
    print(f"     设备已安装        : {device_vn or '（未连接/未安装）'}")
    print()

    if not gradle_vn:
        bad("build.gradle.kts 里读不到 versionName")
        return
    if hardcoded_vn:
        bad("设置页又出现了硬编码版本号",
            f'{hardcoded_vn.group(0)}… —— 应改为 取舍 ${{BuildConfig.VERSION_NAME}}')
    elif uses_build_config:
        ok("设置页版本号取自 BuildConfig（没有第二个真源）")
    else:
        bad("设置页读不到版本号", "应当使用 BuildConfig.VERSION_NAME")
    if apk_vn == gradle_vn:
        ok("最新 APK 文件名一致")
    elif apk_vn is None:
        skip("没有 APK，跳过")
    else:
        bad("最新 APK 的文件名版本落后", f"{apk_vn} ≠ {gradle_vn}（还没构建？）")
    if device_vn == gradle_vn:
        ok("设备上装的就是这个版本")
    elif not device_vn:
        skip("设备未连接，跳过装机确认")
    else:
        bad("设备上的版本不是最新", f"{device_vn} ≠ {gradle_vn}（忘了装机？）")

    # APK 是否比源码新 —— 防止"改了代码但装了旧包"
    apk_path = apks[-1] if apks else None
    if apk_path and os.path.exists(apk_path):
        apk_t = os.path.getmtime(apk_path)
        newest = 0.0
        for dirpath, _, files in os.walk(os.path.join(ROOT, "app", "src")):
            for f in files:
                p = os.path.join(dirpath, f)
                newest = max(newest, os.path.getmtime(p))
        if apk_t >= newest:
            ok("APK 不旧于源码")
        else:
            bad("APK 比源码旧", f"差 {int(newest - apk_t)} 秒 —— 需要重新构建")


# ---------------------------------------------------------------------------
# 2. 收藏同步（取舍收藏夹 ↔ 系统 IS_FAVORITE）
# ---------------------------------------------------------------------------

def _query_favorite_ids(collection):
    out = adb("shell",
              f'content query --uri content://media/external/{collection} '
              f'--projection _id --where "is_favorite=1"')
    return {int(x) for x in re.findall(r"_id=(\d+)", out)}


def check_favorites():
    section(2, "收藏同步（取舍收藏夹 ↔ 系统 IS_FAVORITE）")

    raw = adb("shell", "run-as", PKG, "cat", "files/favorites.txt", with_stderr=True)
    if not raw.strip():
        # run-as 只在 debuggable 包上工作。日常装的如果是 release 包（R8 精简版），
        # 这里必然读不到 —— 那是"看不到"，不是"没同步"，必须说清楚。
        if "not debuggable" in raw or "unknown package" in raw:
            skip("装的是 release 包（不可 run-as），核对收藏要换 debug 包")
        else:
            skip("读不到 favorites.txt（设备未连接 / 未安装？）")
        return
    favs = {int(x) for x in raw.split() if x.strip().isdigit()}
    if not favs:
        skip("收藏夹是空的，跳过")
        return

    sys_img = _query_favorite_ids("images/media")
    sys_vid = _query_favorite_ids("video/media")
    sys_keys = {media_store_key(i, False) for i in sys_img} | \
               {media_store_key(i, True) for i in sys_vid}

    print(f"     取舍收藏夹 : {len(favs)} 条")
    print(f"     系统标记   : {len(sys_img)} 图片 + {len(sys_vid)} 视频 = {len(sys_keys)} 条")
    print()

    only_fav = favs - sys_keys          # 该写没写（待同步）
    only_sys = sys_keys - favs          # 系统有、取舍没有
    matched = len(favs & sys_keys)

    if not only_fav and not only_sys:
        ok(f"完全一致（{matched} 条逐条对上）")
    else:
        if only_fav:
            bad(f"{len(only_fav)} 条还没写进系统", "→ 点一次「立即同步收藏到系统相册」")
        if only_sys:
            # 这不是 bug，是**当前设计的结果**：同步只做加法，绝不自动取消
            # （怕误伤别处写入的收藏）。代价就是"取舍里取消收藏，系统标记还在"。
            warn(f"{len(only_sys)} 条系统有、取舍没有",
                 "→ 在取舍里取消收藏后，系统标记不会自动取消（刻意如此，见设置页说明）")

    # 真实样本交叉验证 —— 比纯逻辑自检更强
    sample = next(iter(sorted(favs)), None)
    if sample is not None:
        mid, is_video = decode_key(sample)
        coll = "video/media" if is_video else "images/media"
        out = adb("shell",
                  f'content query --uri content://media/external/{coll} '
                  f'--projection _id --where "_id={mid} AND is_favorite=1"')
        if "Row" in out:
            ok("样本交叉验证", f"key={sample} → id={mid}（{'视频' if is_video else '图片'}）确实是收藏")
        elif out.strip():
            bad("样本交叉验证失败",
                f"key={sample} → id={mid} 在系统里不是收藏（编码错？或该条没同步）")
        else:
            skip("样本交叉验证", f"id={mid} 查不到（文件可能已删）")


# ---------------------------------------------------------------------------
# 3. 回收站记账
# ---------------------------------------------------------------------------

def check_trash():
    section(3, "回收站记账（角标「还剩 N 天」的数据源）")
    raw = adb("shell", "run-as", PKG, "cat", "files/trash_marks.txt")
    lines = [l for l in raw.splitlines() if l.strip()]
    if not lines:
        ok("记账表是空的", "（回收站为空 —— 正常）")
        return

    bad_lines, stamps = [], []
    for l in lines:
        parts = l.split()
        if len(parts) != 2 or not parts[0].isdigit() or not parts[1].isdigit():
            bad_lines.append(l)
            continue
        stamps.append(int(parts[1]))

    if bad_lines:
        bad(f"{len(bad_lines)} 行格式不对", bad_lines[0][:40])
    else:
        ok(f"{len(lines)} 条记账，格式正常")

    if stamps:
        now = time.time() * 1000
        oldest_days = (now - min(stamps)) / 86_400_000
        newest_days = (now - max(stamps)) / 86_400_000
        print(f"     最早一条 {oldest_days:.1f} 天前，最近一条 {newest_days:.1f} 天前")
        if oldest_days > 31:
            skip("有超过 31 天的记账",
                 "系统早已清掉那些文件 → 可以考虑清理（不影响功能）")


# ---------------------------------------------------------------------------
# 4. 构建产物完整性
# ---------------------------------------------------------------------------

def check_duplicate_functions():
    """
    找「同名但实现不同」的顶层函数。

    ---- 为什么值得单独做一项 ----
    这个工程反复栽在同一个坑上：
      · `SegChip` —— 两处各写一个，行为漂移
      · `allFavoriteKeys()` vs `allSystemFavoriteKeys()` —— 我新加了一个，
        把 storeKey 的奇偶写反，导致"收藏同步完全没用"（2026-10-16）
    Kotlin 允许同名不同包，编译器不会有任何提示。所以只能主动查。
    """
    section(4, "重复函数（同名但不同实现 —— 本工程的高发事故）")
    # 只抓**顶层函数**（行首无缩进）。类的成员函数同名是正常的 ——
    # 不同类各自有 `reload()` 完全没问题，抓到它们只会淹没真正的告警。
    decl = re.compile(r"^(?:private |internal |public |@\w+\s+)*(?:suspend )?fun\s+"
                      r"(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)\s*\(", re.M)
    seen = {}
    for dirpath, _, files in os.walk(JAVA_SRC):
        for f in files:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(dirpath, f)
            rel = os.path.relpath(path, ROOT)
            for m in decl.finditer(read(path)):
                seen.setdefault(m.group(1), set()).add(rel)

    # 这些名字天然会重复（重载 / 重写 / 不同模块的入口），不算问题
    # 顶层同名但**语义确实相同**的（例如各模块自己的 cache 清理入口），
    # 留着只是提醒"改一处别忘了另一处"。
    allow = {"clearCache"}
    dupes = {k: v for k, v in seen.items()
             if len(v) > 1 and k not in allow and len(k) > 5}
    if not dupes:
        ok("没有可疑的同名函数")
        return
    for name, files in sorted(dupes.items()):
        warn(f"`{name}()` 出现在 {len(files)} 个文件", "、".join(sorted(files)))


def check_legacy():
    section(5, "历史残留")
    raw = adb("shell", "run-as", PKG, "ls", "files/synced_favorites.txt")
    if "No such file" in raw or not raw.strip():
        ok("没有 synced_favorites.txt")
    else:
        # 1.3.68 起改成就地读系统状态，不再维护"已同步"记账，这个文件已无用途
        warn("synced_favorites.txt 还在", "已无用途（1.3.68 起不再记账），可以删")


def check_skins():
    """
    配色表的两处真源必须对齐：`QusheSkins`（ui 层）与 `Prefs.SKIN_MAX`（core 层）。

    ---- 为什么值得单独做一项 ----
    core 层不能读 ui 层的那张表，所以上限只能另写一个常量。
    这类"同一个数写在两处"的坑在这个工程已经出过好几次（版本号、角标、文件夹列数）。
    一旦忘了改：新皮肤点下去存进去，下次读出来被 `coerceIn` 夹回默认 ——
    表现是"选了没反应"，而且不崩、不报错，最难查。
    """
    section(6, "配色皮肤")
    theme = read(os.path.join(JAVA_SRC, "com/qushe/app/ui/theme/Theme.kt"))
    prefs = read(os.path.join(JAVA_SRC, "com/qushe/app/core/Prefs.kt"))

    # 两种写法都要认：独立的 `QusheSkin(id = 5, name = "…")`，以及列表里内联的
    # `QusheSkin(0, "…", LightPalette, DarkPalette)`。注意 id 与 name 之间
    # 隔着换行和 `name =`，正则不能只认逗号后紧跟引号。
    skins = {}
    for m in re.finditer(
        r'QusheSkin\(\s*(?:id\s*=\s*)?(\d+)\s*,\s*(?:name\s*=\s*)?"([^"]+)"', theme
    ):
        skins[int(m.group(1))] = m.group(2)
    if not skins:
        bad("没解析到任何皮肤", "Theme.kt 里 QusheSkin(...) 的写法变了？")
        return

    if sorted(skins) != list(range(len(skins))):
        bad("皮肤 id 不连续", f"实际 {sorted(skins)}")
    else:
        ok("皮肤 id 连续", f"0..{len(skins) - 1}，共 {len(skins)} 套")

    odd = [(i, n) for i, n in sorted(skins.items()) if len(n) != 4]
    for i, n in odd:
        warn(f"皮肤名不是四个字：{n}", f"第 {i} 套，{len(n)} 字")
    if not odd:
        ok("皮肤名都是四个字")

    m = re.search(r"SKIN_MAX\s*=\s*(\d+)", prefs)
    last = len(skins) - 1
    if not m:
        bad("Prefs 里找不到 SKIN_MAX")
    elif int(m.group(1)) != last:
        bad(
            "SKIN_MAX 与皮肤数量不一致",
            f"SKIN_MAX={m.group(1)}，最后一套 id={last} —— 新皮肤会被夹回默认："
            "表现是「选了没反应」",
        )
    else:
        ok("SKIN_MAX 与最后一套 id 一致", str(last))

    # 预览页是**生成**的（docs/skins.html 由 skins_page.py 从 Theme.kt 渲染），
    # 但生成物会被提交进仓库，所以要确认它没落后于代码。
    page = os.path.join(ROOT, "docs", "skins.html")
    tool = os.path.join(ROOT, "skins_page.py")
    if os.path.exists(page) and os.path.exists(tool):
        r = subprocess.run(
            [sys.executable, tool, "--check"], cwd=ROOT,
            capture_output=True, text=True, encoding="utf-8", errors="replace",
        )
        if r.returncode == 0:
            ok("docs/skins.html 与 Theme.kt 一致")
        else:
            bad("docs/skins.html 落后于 Theme.kt",
                "跑 python skins_page.py 重新生成（改了配色忘了重新渲染）")
    else:
        skip("没有 docs/skins.html，跳过预览页核对")


def check_artifacts():
    section(7, "构建产物")
    apk = os.path.join(ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    if not os.path.exists(apk):
        skip("没有 app-debug.apk")
        return
    size_mb = os.path.getsize(apk) / 1024 / 1024
    if size_mb < 10:
        bad("app-debug.apk 体积异常", f"{size_mb:.1f} MB（正常 30+ MB）")
    else:
        ok("app-debug.apk", f"{size_mb:.1f} MB")

    # release 包：R8 裁剪后的体积，日常装机用这个
    rel = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
    if os.path.exists(rel):
        rmb = os.path.getsize(rel) / 1024 / 1024
        if rmb >= size_mb:
            bad("release 不比 debug 小", f"{rmb:.1f} MB vs {size_mb:.1f} MB —— R8 没生效？")
        else:
            ok("app-release.apk（R8 裁剪后）", f"{rmb:.1f} MB（比 debug 小 {100 - rmb / size_mb * 100:.0f}%）")
    else:
        skip("没有 app-release.apk（assembleRelease 还没跑过）")

    # 版本号要能从 APK 里读出来（aapt 不一定有，直接查字符串兜底）
    vn = re.search(r'versionName\s*=\s*"([^"]+)"',
                  read(os.path.join(ROOT, "app", "build.gradle.kts")))
    if vn:
        expect = vn.group(1)
        with open(apk, "rb") as f:
            blob = f.read()
        if expect.encode() in blob:
            ok("APK 内含正确版本号", expect)
        else:
            bad("APK 里找不到当前版本号", f"可能打包的是旧代码（期望 {expect}）")


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="取舍回归自检（只读）")
    ap.add_argument("--install", action="store_true",
                    help="检查前先把根目录最新的 qushe-v*.apk 装到设备")
    args = ap.parse_args()

    print("取舍 · 回归自检")
    print(f"工程：{ROOT}")

    if adb("devices"):
        devs = [l for l in adb("devices").splitlines()
                if l.strip() and "List of devices" not in l]
        if devs:
            print(f"设备：{devs[0].split()[0]}")
        else:
            print("设备：（未连接，涉及设备的检查会跳过）")

    if args.install:
        apks = [f for f in os.listdir(ROOT)
                if re.fullmatch(r"qushe-v[0-9.]+-debug\.apk", f)]
        apks.sort(key=lambda f: [int(x) for x in re.findall(r"\d+", f)])
        if not apks:
            print("\n没有可安装的 APK")
        else:
            print(f"\n正在安装 {apks[-1]} …")
            out = adb("install", "-r", os.path.join(ROOT, apks[-1]), timeout=120)
            print("  " + (out.strip().splitlines() or ["（无输出）"])[-1])

    check_encoding()
    check_version()
    check_favorites()
    check_trash()
    check_duplicate_functions()
    check_legacy()
    check_skins()
    check_artifacts()

    print("\n" + "=" * 46)
    uniq_warn = list(dict.fromkeys(warnings))
    if uniq_warn:
        print(f"△ {len(uniq_warn)} 项提示（不影响判定）：")
        for w in uniq_warn:
            print(f"   · {w}")
    if failures:
        print(f"\n✗ {len(failures)} 项失败：")
        for f in dict.fromkeys(failures):
            print(f"   · {f}")
        return 1
    print("✓ 全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
