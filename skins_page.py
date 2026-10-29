# -*- coding: utf-8 -*-
r"""由 Theme.kt 生成 docs/skins.html（静态页，不依赖 JS）。

    python skins_page.py           重新生成预览页
    python skins_page.py --check   只核对磁盘上的预览页是否与代码一致（verify.py 用）

---- 为什么是"生成"而不是手写 ----
10 套 × 2 版 × 10 个色 = 200 个十六进制值。手抄一遍必然有错，
而且以后改一个色就要记得同步两处 —— 这个工程已经栽过好几次"同一个数写两处"。
所以预览页只做一件事：**把代码里的色值渲染出来看**，不持有任何自己的数据。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
THEME = os.path.join(ROOT, "app", "src", "main", "java", "com", "qushe", "app", "ui", "theme", "Theme.kt")
PAGE = os.path.join(ROOT, "docs", "skins.html")

FIELDS = ["bg", "surface", "main", "delete", "info", "purple", "folder",
          "textPrimary", "textSecondary", "textTertiary"]

# 具名调色板（默认那套）的字段名 → 种子色字段名
ALIAS = {"bg": "ink", "surface": "surface1", "main": "accentKeep",
         "delete": "accentDelete", "info": "accentInfo", "purple": "accentPurple",
         "folder": "accentFolder", "textPrimary": "textPrimary",
         "textSecondary": "textSecondary", "textTertiary": "textTertiary"}

# 色卡下方那句"来历"。**只写画面，不写参数** —— 用户选的是感觉。
SOURCE = {
    "素白玄墨": "中性黑白",
    "暖桃初雪": "暖桃 · 一点初雪的白",
    "新茶初雨": "奶绿 · 雨后的新叶",
    "燕麦秋声": "燕麦 · 干草与木",
    "远山如黛": "雾蓝 · 远山的青",
    "烟粉旧梦": "莫兰迪 · 灰玫瑰",
    "睡莲静水": "莫奈 · 睡莲池",
    "赤陶晚照": "塞尚 · 赭石山丘",
    "椰影暖沙": "高更 · 塔希提日光",
    "青苔石阶": "苔庭 · 湿石与青苔",
}


def search(pattern, text):
    """不带反斜杠的 re.search 包装：转义一律走 re.escape，避免 shell 吃掉反斜杠。"""
    return re.search(pattern, text)


def hexes(line):
    m = re.search("0xFF([0-9A-Fa-f]{6})", line)
    return m.group(1).upper() if m else None


def parse():
    src = io.open(THEME, encoding="utf-8").read()
    lines = src.split("\n")

    # ---- 具名调色板 ----
    palette = {}
    var, buf = None, []
    for ln in lines:
        s = ln.split("//")[0]
        m = search(r"val (\w+) = QushePalette\(", s)
        if m:
            var, buf = m.group(1), []
            continue
        if var:
            buf.append(s)
            if s.strip() == ")":
                d = {}
                for line in buf:
                    if "=" not in line:
                        continue
                    k = line.split("=")[0].strip()
                    v = hexes(line)
                    if v:
                        d[k] = v
                palette[var] = d
                var = None

    # ---- QusheSkin 块 ----
    skins, cur, mode, buf = [], None, None, []
    for ln in lines:
        s = ln.split("//")[0]
        if "QusheSkin(" in s:
            cur = {"name": None, "false": None, "true": None, "inline": None}
            m = search(r'QusheSkin\(\s*[0-9]+\s*,\s*"([^"]+)"', s)
            if m:
                cur["name"] = m.group(1)
                cur["inline"] = s
            continue
        if cur is None:
            continue
        if cur["name"] is None:
            m = search(r'name\s*=\s*"([^"]+)"', s)
            if m:
                cur["name"] = m.group(1)
                continue
        if "dark = false" in s or "dark = true" in s:
            mode = "true" if "dark = true" in s else "false"
            buf = []
            continue
        if mode and "Color(0xFF" in s:
            v = hexes(s)
            if v:
                buf.append(v)
            continue
        if mode and s.strip().startswith(")"):
            if len(buf) >= 10:
                cur[mode] = dict(zip(FIELDS, buf[:10]))
            mode, buf = None, []
            if cur.get("false") and cur.get("true") and cur.get("name"):
                skins.append(cur)
                cur = None

    # 默认那套是 LightPalette / DarkPalette
    if "LightPalette" in palette and "DarkPalette" in palette:
        def mapped(name):
            return {f: palette[name][ALIAS[f]] for f in FIELDS if ALIAS[f] in palette[name]}
        skins.insert(0, {"name": "素白玄墨", "false": mapped("LightPalette"),
                         "true": mapped("DarkPalette"), "inline": "0"})
    return skins


CSS = """
  :root { --paper: #f1efea; --ink: #211f1c; --ink-2: #5f5b54; --ink-3: #928d84; --line: #dcd6cd; }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--paper); color: var(--ink);
         font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
         -webkit-font-smoothing: antialiased; }
  .page { max-width: 1080px; margin: 0 auto; padding: 72px 28px 110px; }
  header { border-bottom: 1px solid var(--line); padding-bottom: 30px; margin-bottom: 46px; }
  .kicker { font-size: 11px; letter-spacing: .34em; color: var(--ink-3); margin: 0 0 22px; }
  h1 { font-family: "Songti SC", "Source Han Serif SC", "Noto Serif SC", "SimSun", serif;
       font-weight: 500; font-size: 46px; line-height: 1.18; margin: 0 0 18px; letter-spacing: .06em; }
  .lede { margin: 0; max-width: 47em; font-size: 14px; line-height: 2; color: var(--ink-2); }
  .lede b { font-weight: 500; color: var(--ink); }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 36px 30px; }
  @media (max-width: 860px) { .grid { grid-template-columns: 1fr; } h1 { font-size: 34px; } }
  .skin { border-top: 1px solid var(--line); padding-top: 18px; }
  .skin-head { display: flex; align-items: baseline; gap: 12px; margin: 0 0 2px; }
  .skin-name { font-family: "Songti SC", "Source Han Serif SC", "Noto Serif SC", serif;
               font-size: 21px; letter-spacing: .16em; margin: 0; font-weight: 500; }
  .skin-src { font-size: 11px; color: var(--ink-3); }
  .swatches { display: flex; gap: 3px; margin: 14px 0 16px; }
  .swatches i { display: block; height: 9px; flex: 1; }
  .swatches i.wide { flex: 2.6; }
  .screens { display: flex; gap: 10px; }
  .screen { flex: 1; min-width: 0; border-radius: 13px; overflow: hidden;
            padding: 15px 13px 0; display: flex; flex-direction: column; }
  .s-title { font-size: 15px; letter-spacing: .08em; font-weight: 600; }
  .s-sub { font-size: 8px; margin-top: 3px; }
  .s-cards { margin-top: 13px; display: flex; flex-direction: column; gap: 6px; }
  .s-card { display: flex; align-items: center; gap: 7px; padding: 8px 8px 8px 0; border-radius: 7px; }
  .s-bar { width: 2.5px; align-self: stretch; border-radius: 2px; margin-left: 7px; }
  .s-text { flex: 1; min-width: 0; }
  .s-text b { display: block; font-size: 9.5px; font-weight: 600; }
  .s-text span { display: block; font-size: 7px; margin-top: 2px; opacity: .72; }
  .s-num { font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; padding-right: 4px; }
  .s-stats { margin-top: 13px; display: flex; justify-content: space-between;
             padding: 9px 10px; border-radius: 8px; }
  .s-stats div { text-align: center; }
  .s-stats b { display: block; font-size: 9.5px; font-variant-numeric: tabular-nums; }
  .s-stats span { display: block; font-size: 6px; margin-top: 3px; opacity: .6; }
  .s-nav { margin-top: auto; display: flex; padding: 10px 6px 9px; }
  .s-nav div { flex: 1; text-align: center; font-size: 7px; line-height: 1.4; }
  .s-nav i { display: block; font-size: 10px; font-style: normal; margin-bottom: 2px; }
  footer { margin-top: 62px; border-top: 1px solid var(--line); padding-top: 20px;
           font-size: 11px; color: var(--ink-3); line-height: 1.9; }
  code { font-family: ui-monospace, Consolas, monospace; font-size: 10px; }
"""


def screen(c):
    bg, sf, main, dele, info, purple, folder, tp, ts, tt = ("#" + c[f] for f in FIELDS)
    cards = [
        ("整理照片", "上滑删除 · 左右翻页", "115777", main),
        ("整理截屏", "单独成组 · 一眼废片", "6296", purple),
        ("整理视频", "上下滑切换 · 长按 2×", "8655", info),
    ]
    body = "".join(
        f'<div class="s-card" style="background:{sf}">'
        f'<div class="s-bar" style="background:{accent}"></div>'
        f'<div class="s-text"><b style="color:{tp}">{t}</b>'
        f'<span style="color:{ts}">{sub}</span></div>'
        f'<div class="s-num" style="color:{accent}">{num}</div></div>'
        for t, sub, num, accent in cards
    )
    return (
        f'<div class="screen" style="background:{bg}">'
        f'<div class="s-title" style="color:{tp}">取舍</div>'
        f'<div class="s-sub" style="color:{tt}">不用下决心整理，像刷短视频一样划两下</div>'
        f'<div class="s-cards">{body}</div>'
        f'<div class="s-stats" style="background:{sf}">'
        f'<div><b style="color:{tp}">2862</b><span style="color:{tt}">看过照片</span></div>'
        f'<div><b style="color:{tp}">282</b><span style="color:{tt}">看过视频</span></div>'
        f'<div><b style="color:{dele}">1919</b><span style="color:{tt}">已删除</span></div>'
        f'<div><b style="color:{main}">336</b><span style="color:{tt}">已收藏</span></div>'
        f'</div>'
        f'<div class="s-nav" style="background:{sf}">'
        f'<div style="color:{main}"><i>▣</i>首页</div>'
        f'<div style="color:{tt}"><i>★</i>收藏</div>'
        f'<div style="color:{tt}"><i>▤</i>回收站</div>'
        f'<div style="color:{tt}"><i>⚙</i>设置</div>'
        f'</div></div>'
    )


def build(skins):
    cards = []
    for k in skins:
        l, d = k["false"], k["true"]
        sw = "".join(
            f'<i class="wide" style="background:#{l[FIELDS[i]]}"></i>' if i == 0
            else f'<i style="background:#{l[FIELDS[i]]}"></i>'
            for i in (0, 1, 2, 3, 7)
        )
        cards.append(
            f'<article class="skin">'
            f'<div class="skin-head"><h2 class="skin-name">{k["name"]}</h2>'
            f'<span class="skin-src">{SOURCE.get(k["name"], "")}</span></div>'
            f'<div class="swatches">{sw}</div>'
            f'<div class="screens">{screen(l)}{screen(d)}</div>'
            f'</article>'
        )

    return (
        '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        '<title>取舍 · 十套配色</title>\n<style>' + CSS + '</style>\n</head>\n<body>\n'
        '<div class="page">\n<header>\n'
        '<p class="kicker">取舍 · QUSHE</p>\n<h1>十种光线</h1>\n'
        '<p class="lede">每一套都是<strong>同一张界面</strong>换一层光：底色定温度，'
        '卡片比底色亮一档，主色只出现在选中的字、星形和左侧那道细条上。'
        '<b>右边是深色版</b>，它不是"把浅色反过来"，而是重新配的 —— '
        '深色底永远带一点色相，纯黑会把照片衬得发脏。</p>\n</header>\n'
        '<div class="grid">\n' + "\n".join(cards) + '\n</div>\n'
        '<footer>\n本页由 <code>skins_page.py</code> 从 <code>Theme.kt</code> 生成，'
        '不单独持有色值 · 对比度按 WCAG 2.1 计算值核对'
        '（正文 ≥ 10、次要 ≥ 4.6、主色 ≥ 4.5）\n</footer>\n'
        '</div>\n</body>\n</html>\n'
    )


def main():
    skins = parse()
    if len(skins) < 2:
        print("只解析到 %d 套皮肤，Theme.kt 的写法可能变了" % len(skins))
        return 1
    html = build(skins)

    if "--check" in sys.argv:
        old = io.open(PAGE, encoding="utf-8").read() if os.path.exists(PAGE) else ""
        if old == html:
            print("预览页与 Theme.kt 一致（%d 套）" % len(skins))
            return 0
        print("预览页与 Theme.kt 不一致 —— 跑 python skins_page.py 重新生成")
        return 1

    io.open(PAGE, "w", encoding="utf-8", newline="\n").write(html)
    print("已生成 %s（%d 套）" % (os.path.relpath(PAGE, ROOT), len(skins)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
