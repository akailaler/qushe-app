#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
在**另一台电脑**上把开发环境准备好，一条命令。

    python setup.py

做四件事（都是幂等的，随便重复跑）：

1. 恢复项目记忆：`docs/agent-memory/*.md` → `<工作区>/.workbuddy/memory/`
   —— 这是换机器最关键的一步。没有它，下一次对话等于失忆，
      会重新踩那些已经用实测结论钉死的坑。
2. 找 JDK 17：环境变量 JAVA_HOME → WorkBuddy 托管目录 → 常见安装位置。
3. 找 Android SDK：local.properties 里已有 → 环境变量 → 常见位置；
   找到就写回 local.properties（这个文件不进仓库，每台机器各不相同）。
4. 报告还缺什么、下一条命令该敲什么。

退出码：0 = 环境齐了可以直接 build；1 = 还缺东西（看输出里的 ✗）。
"""

import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))          # qushe-android/
WORKSPACE = os.path.dirname(ROOT)                          # «工作区»（小工具/）
MEM_DST = os.path.join(WORKSPACE, ".workbuddy", "memory")  # 记忆的固定落脚点
MEM_SRC = os.path.join(ROOT, "docs", "agent-memory")       # 仓库里的镜像

HOME = os.path.expanduser("~")
problems = []


def ok(label, detail=""):
    print("  [OK] " + label + (("   " + detail) if detail else ""))


def bad(label, detail=""):
    print("  [!!] " + label + (("   " + detail) if detail else ""))
    problems.append(label)


def info(label):
    print("  [--] " + label)


def is_jdk17(path):
    """确认这个目录真是 JDK 17：有 javac 且版本号对得上。"""
    if not path or not os.path.isdir(path):
        return False
    javac = os.path.join(path, "bin", "javac.exe" if os.name == "nt" else "javac")
    if not os.path.isfile(javac):
        return False
    try:
        r = subprocess.run([javac, "-version"], capture_output=True, text=True,
                           timeout=30)
    except Exception:
        return False
    # ⚠️ `javac -version` 印到 **stdout**，`java -version` 才印到 stderr ——
    # 这里两个都读，省得以后换 JDK 发行版时又踩一次。
    out = (r.stdout or "") + (r.stderr or "")
    return "17." in out


def find_jdk():
    """按可信度从高到低找 JDK 17。"""
    cands = []
    if os.environ.get("JAVA_HOME"):
        cands.append(os.environ["JAVA_HOME"])
    # WorkBuddy 托管的运行时（本机就是这么装的）
    cands.append(os.path.join(HOME, ".workbuddy", "binaries", "jdk17",
                              "jdk-17.0.20.1+1"))
    if os.name == "nt":
        for base in (r"C:\Program Files\Eclipse Adoptium",
                     r"C:\Program Files\Java",
                     r"C:\Program Files\Microsoft"):
            if os.path.isdir(base):
                for name in sorted(os.listdir(base), reverse=True):
                    cands.append(os.path.join(base, name))
    for c in cands:
        if is_jdk17(c):
            return c
    return None


def find_sdk():
    """找 Android SDK：先信 local.properties，再看环境变量和常见位置。"""
    # 1) local.properties 里已经写好的
    lp = os.path.join(ROOT, "local.properties")
    if os.path.isfile(lp):
        for line in open(lp, encoding="utf-8", errors="ignore"):
            if line.strip().startswith("sdk.dir="):
                p = line.split("=", 1)[1].strip().replace("\\\\", "\\")
                if os.path.isdir(p):
                    return p, True
    # 2) 环境变量
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        p = os.environ.get(key)
        if p and os.path.isdir(p):
            return p, False
    # 3) 常见位置
    cands = [os.path.join(HOME, "Android", "Sdk"),
             os.path.join(HOME, "AppData", "Local", "Android", "Sdk")]
    for p in cands:
        if os.path.isdir(p):
            return p, False
    return None, False


def step_memory(force=False):
    print("\n1) 项目记忆")
    if not os.path.isdir(MEM_SRC):
        bad("仓库里没有 docs/agent-memory/", "这台机器之前没提交过记忆？")
        return
    files = [f for f in os.listdir(MEM_SRC) if f.endswith(".md")]
    if not files:
        bad("docs/agent-memory/ 是空的")
        return
    os.makedirs(MEM_DST, exist_ok=True)
    copied, kept = 0, 0
    for f in files:
        src = os.path.join(MEM_SRC, f)
        dst = os.path.join(MEM_DST, f)
        # ⚠️ **默认绝不覆盖已存在的文件**。
        # 这条规则是踩出来的：仓库镜像是"上次提交时"的快照，
        # 如果本机刚写了新记忆还没提交，无脑覆盖就等于**把今天的日志删了**。
        # 只有新机器（目录是空的）才需要复制；本机想强制回滚用 `--force`。
        if os.path.isfile(dst) and not force:
            kept += 1
            continue
        shutil.copy2(src, dst)
        copied += 1
    if copied:
        ok(f"记忆已恢复到 {MEM_DST}", f"共 {len(files)} 个，本次写入 {copied} 个")
    else:
        ok(f"记忆已在本机（未改动）", f"{kept} 个文件，仓库镜像没有覆盖它们")
        info("要从仓库强制覆盖回来：python setup.py --force")


def step_jdk():
    print("\n2) JDK 17")
    jdk = find_jdk()
    if jdk:
        ok("找到 JDK 17", jdk)
        print("\n     构建时先设这个环境变量（Git Bash）：")
        print(f'       export JAVA_HOME="{jdk}"')
        return jdk
    bad("没找到 JDK 17", "装一个 Temurin 17，或设 JAVA_HOME 指向它")
    return None


def step_sdk():
    print("\n3) Android SDK")
    sdk, from_props = find_sdk()
    if not sdk:
        bad("没找到 Android SDK", "需要 platform 35 + build-tools 35.0.0")
        return None
    if from_props:
        ok("local.properties 已指向可用的 SDK", sdk)
    else:
        lp = os.path.join(ROOT, "local.properties")
        with open(lp, "w", encoding="utf-8", newline="\n") as f:
            f.write("# 本文件不进仓库：SDK 路径每台机器都不一样\n")
            f.write("sdk.dir=" + sdk.replace("\\", "/") + "\n")
        ok("已生成 local.properties", sdk)
    # 关键的两个组件
    for sub in ("platforms/android-35", "build-tools/35.0.0"):
        p = os.path.join(sdk, sub.replace("/", os.sep))
        if os.path.isdir(p):
            ok(sub)
        else:
            bad("缺 " + sub, "用 sdkmanager 装")
    return sdk


def step_device():
    print("\n4) 设备（可选）")
    adb = shutil.which("adb")
    if not adb:
        info("这台机器没有 adb —— 只改代码不装机的话不影响")
        return
    try:
        out = subprocess.run([adb, "devices"], capture_output=True, text=True,
                             timeout=20).stdout
    except Exception:
        info("adb devices 执行失败")
        return
    if "\tdevice" in out:
        ok("已连上设备", "可以 verify.py 和装机验证")
    else:
        info("adb 在，但没连设备（verify.py 的部分检查会跳过）")


def main():
    force = "--force" in sys.argv
    print("=" * 52)
    print("取舍 · 换机器环境准备")
    print("=" * 52)
    print(f"\n仓库   {ROOT}")
    print(f"工作区 {WORKSPACE}")

    step_memory(force)
    jdk = step_jdk()
    sdk = step_sdk()
    step_device()

    print("\n" + "=" * 52)
    if problems:
        print(f"[!!] 还差 {len(problems)} 项：")
        for p in problems:
            print("     · " + p)
        print("\n补齐后重跑 `python setup.py`，全 OK 了再构建。")
        return 1
    print("[OK] 环境齐了。下一条命令：\n")
    print('     export JAVA_HOME="%s"' % (jdk or "<你的 JDK 17>"))
    print('     ".workbuddy/binaries/gradle-8.9/bin/gradle" assembleDebug')
    print("\n   （或者直接跑 `python verify.py` 看当前状态）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
