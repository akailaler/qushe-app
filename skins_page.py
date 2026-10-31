# -*- coding: utf-8 -*-
r"""由 Theme.kt 生成 docs/skins.html（静态页，不依赖 JS）。

    python skins_page.py           重新生成预览页
    python skins_page.py --check   只核对磁盘上的预览页是否与代码一致（verify.py 用）

---- 为什么是"生成"而不是手写 ----
八套 × 两版 × 十个色 = 160 个十六进制值。手抄一遍必然有错，而且改一个色就要
记得同步两处 —— 这个工程已经栽过好几次"同一个数写两处"。
预览页只做一件事：**把代码里的色值渲染出来看**，不持有任何自己的数据。

---- 一张卡为什么两个屏 ----
每套配色自带浅色和深色，是同一种调子的两种光。并排放，一眼看到"白天 / 夜里"。
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

# 名字下面那句：**只写画面，不写参数**。
SOURCE = {
    "空山新雨": "雨后松针的绿，纸是湿的",
    "雪夜访戴": "雪光映着的蓝，冷而干净",
    "落霞孤鹜": "晚照的橘褐，暖得发闷",
    "月下叩门": "月色压在门上，紫灰",
    "松风煮茗": "茶烟里的金褐",
    "寒江独钓": "江雾的青，几乎无色",
    "疏影暗香": "梅影的白里透一点粉",
    "富春山居": "旧绢上的橄榄与土黄",
}


def colors_in(block, marker):
    i = block.index(marker)
    tail = block[i + len(marker):]
    # 到这一版结束为止：后面再出现 "skinPalette(" 就是另一版了
    j = tail.find("skinPalette(")
    seg = tail if j < 0 else tail[:j]
    out = {}
    for line in seg.split("\n"):
        m = re.search(r"Color\(0xFF([0-9A-Fa-f]{6})\)", line)
        if not m:
            continue
        key = line.split("=")[0].strip()
        if key in FIELDS:
            out[key] = m.group(1).upper()
    return out


def parse():
    src = io.open(THEME, encoding="utf-8").read()
    start = src.index("val QusheSkins: List<QusheSkin>")
    end = src.index("\n)", start)
    seg = src[start:end]
    out = []
    for block in seg.split("QusheSkin(")[1:]:
        m = re.search(r'name\s*=\s*"([^"]+)"', block)
        if not m:
            continue
        light = colors_in(block, "light = skinPalette(")
        dark = colors_in(block, "dark = skinPalette(")
        if len(light) == len(FIELDS) and len(dark) == len(FIELDS):
            out.append({"name": m.group(1), "light": light, "dark": dark})
    return out


CSS = """
  :root { --paper: #f1efea; --ink: #211f1c; --ink-2: #5f5b54; --ink-3: #928d84; --line: #dcd6cd; }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--paper); color: var(--ink);
         font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
         -webkit-font-smoothing: antialiased; }
  .page { max-width: 1080px; margin: 0 auto; padding: 70px 28px 110px; }
  header { border-bottom: 1px solid var(--line); padding-bottom: 30px; margin-bottom: 42px; }
  .kicker { font-size: 11px; letter-spacing: .34em; color: var(--ink-3); margin: 0 0 22px; }
  h1 { font-family: "Songti SC", "Source Han Serif SC", "Noto Serif SC", "SimSun", serif;
       font-weight: 500; font-size: 46px; line-height: 1.18; margin: 0 0 18px; letter-spacing: .06em; }
  .lede { margin: 0; max-width: 47em; font-size: 14px; line-height: 2; color: var(--ink-2); }
  .lede b { font-weight: 500; color: var(--ink); }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 36px 30px; }
  @media (max-width: 860px) { .grid { grid-template-columns: 1fr; } h1 { font-size: 34px; } }
  .skin { border-top: 1px solid var(--line); padding-top: 16px; }
  .skin-head { display: flex; align-items: baseline; gap: 10px; margin: 0 0 10px; }
  .skin-name { font-family: "Songti SC", "Source Han Serif SC", serif; font-size: 18px;
               letter-spacing: .16em; margin: 0; font-weight: 500; }
  .skin-src { font-size: 10.5px; color: var(--ink-3); }
  .screens { display: flex; gap: 10px; }
  .screen { flex: 1; min-width: 0; border-radius: 12px; overflow: hidden;
            padding: 15px 13px 0; display: flex; flex-direction: column; }
  .s-title { font-size: 15px; letter-spacing: .08em; font-weight: 600; }
  .s-sub { font-size: 8px; margin-top: 3px; }
  .s-cards { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
  .s-card { display: flex; align-items: center; gap: 7px; padding: 8px 8px 8px 0; border-radius: 7px; }
  .s-bar { width: 2.5px; align-self: stretch; border-radius: 2px; margin-left: 7px; }
  .s-text { flex: 1; min-width: 0; }
  .s-text b { display: block; font-size: 9.5px; font-weight: 600; }
  .s-text span { display: block; font-size: 7px; margin-top: 2px; opacity: .72; }
  .s-num { font-size: 12px; font-weight: 600; font-variant-numeric: tabular-nums; padding-right: 4px; }
  .s-stats { margin-top: 12px; display: flex; justify-content: space-between;
             padding: 9px 10px; border-radius: 8px; }
  .s-stats div { text-align: center; }
  .s-stats b { display: block; font-size: 9.5px; font-variant-numeric: tabular-nums; }
  .s-stats span { display: block; font-size: 6px; margin-top: 3px; opacity: .6; }
  .s-nav { margin-top: auto; display: flex; padding: 10px 6px 9px; }
  .s-nav div { flex: 1; text-align: center; font-size: 7px; line-height: 1.4; }
  .s-nav i { display: block; font-size: 10px; font-style: normal; margin-bottom: 2px; }
  footer { margin-top: 58px; border-top: 1px solid var(--line); padding-top: 20px;
           font-size: 11px; color: var(--ink-3); line-height: 1.9; }
  code { font-family: ui-monospace, Consolas, monospace; font-size: 10px; }
"""


def screen(c):
    bg, sf, main, dele, info, purple, folder, tp, ts, tt = (c[f] for f in FIELDS)
    cards = [
        ("整理照片", "上滑删除 · 左右翻页", "115777", "#" + main),
        ("整理截屏", "单独成组 · 一眼废片", "6296", "#" + purple),
        ("整理视频", "上下滑切换 · 长按 2×", "8655", "#" + info),
    ]
    body = "".join(
        f'<div class="s-card" style="background:#{sf}">'
        f'<div class="s-bar" style="background:{accent}"></div>'
        f'<div class="s-text"><b style="color:#{tp}">{t}</b>'
        f'<span style="color:#{ts}">{sub}</span></div>'
        f'<div class="s-num" style="color:{accent}">{num}</div></div>'
        for t, sub, num, accent in cards
    )
    return (
        f'<div class="screen" style="background:#{bg}">'
        f'<div class="s-title" style="color:#{tp}">取舍</div>'
        f'<div class="s-sub" style="color:#{tt}">不用下决心整理，像刷短视频一样划两下</div>'
        f'<div class="s-cards">{body}</div>'
        f'<div class="s-stats" style="background:#{sf}">'
        f'<div><b style="color:#{tp}">2862</b><span style="color:#{tt}">看过照片</span></div>'
        f'<div><b style="color:#{tp}">282</b><span style="color:#{tt}">看过视频</span></div>'
        f'<div><b style="color:#{dele}">1919</b><span style="color:#{tt}">已删除</span></div>'
        f'<div><b style="color:#{main}">336</b><span style="color:#{tt}">已收藏</span></div>'
        f'</div>'
        f'<div class="s-nav" style="background:#{sf}">'
        f'<div style="color:#{main}"><i>▣</i>首页</div>'
        f'<div style="color:#{tt}"><i>★</i>收藏</div>'
        f'<div style="color:#{tt}"><i>▤</i>回收站</div>'
        f'<div style="color:#{tt}"><i>⚙</i>设置</div>'
        f'</div></div>'
    )


def build(skins):
    items = "".join(
        f'<article class="skin">'
        f'<div class="skin-head"><h3 class="skin-name">{k["name"]}</h3>'
        f'<span class="skin-src">{SOURCE.get(k["name"], "")}</span></div>'
        f'<div class="screens">{screen(k["light"])}{screen(k["dark"])}</div>'
        f'</article>'
        for k in skins
    )
    return (
        '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        '<title>取舍 · 八套配色</title>\n<style>' + CSS + '</style>\n</head>\n<body>\n'
        '<div class="page">\n<header>\n'
        '<p class="kicker">取舍 · QUSHE</p>\n<h1>八种光</h1>\n'
        '<p class="lede">每套都是<b>白天一张纸、夜里一盏灯</b>：左边浅色、右边深色，'
        '同一种调子的两种照法。挑一次，深浅模式自动用对应的那一版。</p>\n'
        '</header>\n<div class="grid">\n' + items + '\n</div>\n'
        '<footer>\n本页由 <code>skins_page.py</code> 从 <code>Theme.kt</code> 生成，'
        '不单独持有色值 · 对比度按 WCAG 2.1 计算值核对'
        '（正文 ≥ 10、次要 ≥ 4.6、主色 ≥ 4.5）\n</footer>\n'
        '</div>\n</body>\n</html>\n'
    )


def main():
    skins = parse()
    if len(skins) < 2:
        print("只解析到 %d 套配色，Theme.kt 的写法可能变了" % len(skins))
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
