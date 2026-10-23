#!/usr/bin/env python3
"""把当前版本发到 GitHub Release —— tag + APK 一起传，访客点开就能下载。

用法：

    gradle assembleRelease
    python release.py

它会做四件事（都可重复执行，已经做过的会跳过）：

1. 从 `app/build.gradle.kts` 读出 versionName（比如 1.3.87）；
2. 本地打 tag `v1.3.87` 并推到远端；
3. 在 GitHub 上创建对应的 Release（已存在就复用）；
4. 把 `qushe-v1.3.87-release.apk` 传上去，并把它的 SHA-256 写进说明。

前提：本机能用 GitHub 凭据（GitHub Desktop / GCM 登录过）。
凭据从 `git credential fill` 取，不读任何明文文件。

---- 为什么不做成 GitHub Actions ----
构建要 Android SDK + JDK17，Actions 上每次跑一遍要几分钟，而本机构建只要 40 秒；
更关键的是**这个工程一直在本地真机上验证**，本来就该发"我验过的这个包"，
而不是"在云上重新编出来的另一个包"。
"""

from __future__ import annotations

import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent
OWNER_REPO = "akailaler/qushe-app"

# 直连，不走系统代理：
#  - 用户在能打开 GitHub Desktop 的网络里，直连一定通；
#  - CI/沙箱里那种"只放行部分域名"的代理反而会把 uploads.github.com 拦掉。
OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def run(args: list[str], **kw) -> subprocess.CompletedProcess:
    # 和 OPENER 同一个理由：绕开只放行部分域名的系统/沙箱代理。
    env = {k: v for k, v in os.environ.items()
           if k.lower() not in ("https_proxy", "http_proxy", "all_proxy")}
    kw.setdefault("env", env)
    return subprocess.run(args, cwd=ROOT, capture_output=True, text=True, **kw)


def version() -> str:
    text = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not m:
        sys.exit("读不到 versionName，检查 app/build.gradle.kts")
    return m.group(1)


def assert_releasable(ver: str) -> None:
    """发布前确认"tag 指向的代码就是这一版"。

    踩过一次：先把版本号改成 1.3.91、构建、跑 release.py，**最后才 commit**。
    tag 于是打在上一个提交上 —— 下载页写着 1.3.91，clone 下来 checkout 却是 1.3.90。
    这类错在网页上完全看不出来，只有翻 tag 才能发现，所以直接拦在这里。
    """
    dirty = run(["git", "status", "--porcelain"]).stdout.strip()
    if dirty:
        sys.exit(
            "工作区有未提交的改动，先 commit 再发版。\n"
            "否则 tag 指向的提交不包含这些改动（下载页的版本号和源码会对不上）。\n\n"
            + dirty[:400]
        )
    head = run(["git", "show", "HEAD:app/build.gradle.kts"]).stdout
    m = re.search(r'versionName\s*=\s*"([^"]+)"', head)
    if not m or m.group(1) != ver:
        got = m.group(1) if m else "?"
        sys.exit(
            f"HEAD 里的 versionName 是 {got}，当前工程是 {ver} —— 先把版本号改动提交。"
        )


def token() -> str:
    """走 GCM 取 GitHub 凭据。拿不到时给出可执行的下一步，而不是一句 'failed'。"""
    p = run(["git", "credential", "fill"],
            input="protocol=https\nhost=github.com\n\n")
    for line in p.stdout.splitlines():
        if line.startswith("password="):
            return line.split("=", 1)[1]
    sys.exit(
        "拿不到 GitHub 凭据。\n"
        "  先确认 `git push` 能成功（装 GitHub Desktop 并登录一次即可），再重跑。"
    )


def api(url: str, tok: str, data: bytes | None = None, ctype: str | None = None):
    headers = {
        "Authorization": f"token {tok}",
        "Accept": "application/vnd.github+json",
    }
    if ctype:
        headers["Content-Type"] = ctype
    req = urllib.request.Request(url, data=data, headers=headers,
                                 method="POST" if data is not None else "GET")
    return json.load(OPENER.open(req, timeout=300))


def ensure_tag(tag: str) -> None:
    if run(["git", "rev-parse", "--verify", "--quiet", f"refs/tags/{tag}"]).returncode != 0:
        p = run(["git", "tag", "-a", tag, "-m", f"取舍 {tag.lstrip('v')}"])
        if p.returncode != 0:
            sys.exit(f"打 tag 失败：{p.stderr.strip()}")
        print(f"  打出 tag {tag}")
    p = run(["git", "push", "origin", tag])
    if p.returncode != 0 and "up-to-date" not in (p.stderr + p.stdout):
        print(f"  ⚠ tag 推送失败（不影响 Release，但 tag 只在本机）：{p.stderr.strip()[:200]}")
    else:
        print(f"  tag {tag} 已在远端")


def find_release(tag: str, tok: str) -> dict | None:
    try:
        return api(f"https://api.github.com/repos/{OWNER_REPO}/releases/tags/{tag}", tok)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def main() -> None:
    ver = version()
    tag = f"v{ver}"
    apk = ROOT / f"qushe-v{ver}-release.apk"

    print(f"取舍 {ver}")
    if not apk.exists():
        # 构建产物不手动复制 —— 版本号变了就自动从 output 目录取最新的那个
        built = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
        if built.exists():
            shutil.copyfile(built, apk)
            print(f"  从构建产物生成 {apk.name}")
        else:
            sys.exit(
                f"找不到 {apk.name}，也没有构建产物。\n"
                f"  先跑：gradle assembleRelease"
            )
    print(f"  APK: {apk.name}（{apk.stat().st_size / 1024 / 1024:.1f} MB）")

    assert_releasable(ver)

    tok = token()
    ensure_tag(tag)

    # APK 的 SHA-256：给想核对"下下来的包是不是发布者传的那个"的人用。
    # 直接对本地这个文件算，而不是读构建日志 —— 传上去的就是这个字节流。
    apk_name = f"qushe-v{ver}.apk"
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()

    rel = find_release(tag, tok)
    if rel is None:
        # 说明只写「装得上、能覆盖」这两件用户真正会问的事。
        # 签名细节（换了密钥、旧包要卸载）是**一次性**信息，写在 README 的安装表里，
        # 不在每个 Release 里重复 —— 否则像 v1.3.87 那句"debug 签名"一样，
        # 签过一次新密钥之后，旧文案会一直留在页面上骗人。
        notes = (
            f"## 安装\n\n"
            f"- Android 11（API 30）及以上，约 {apk.stat().st_size / 1024 / 1024:.1f} MB\n"
            f"- 直接安装即可，同一签名线的版本可以直接覆盖升级\n\n"
            f"### 校验（可选）\n\n"
            f"```\n"
            f"{digest}  {apk_name}\n"
            f"```\n\n"
            f"`sha256sum -c` 或 `Get-FileHash` 对一下，能确认包没被改动。\n\n"
            f"更新记录见 [README](https://github.com/{OWNER_REPO}#八更新日志)。"
        )
        rel = api(
            f"https://api.github.com/repos/{OWNER_REPO}/releases", tok,
            data=json.dumps({
                "tag_name": tag,
                "name": f"取舍 {ver}",
                "body": notes,
                "draft": False,
                "prerelease": False,
            }).encode(),
            ctype="application/json",
        )
        print(f"  创建 Release {tag}（id {rel['id']}）")
    else:
        print(f"  Release {tag} 已存在（id {rel['id']}），复用")

    name = apk_name
    if any(a["name"] == name for a in rel.get("assets", [])):
        print(f"  附件 {name} 已存在，跳过上传")
    else:
        upload = rel["upload_url"].split("{")[0] + f"?name={name}"
        asset = api(upload, tok, data=apk.read_bytes(),
                    ctype="application/vnd.android.package-archive")
        print(f"  上传完成：{asset['browser_download_url']}")

    print(f"\n下载页：https://github.com/{OWNER_REPO}/releases/latest")


if __name__ == "__main__":
    main()
