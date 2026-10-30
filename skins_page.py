# -*- coding: utf-8 -*-
r"""由 Theme.kt 生成 docs/skins.html（静态页，不依赖 JS）。

    python skins_page.py           重新生成预览页
    python skins_page.py --check   只核对磁盘上的预览页是否与代码一致（verify.py 用）

---- 为什么是"生成"而不是手写 ----
十套 × 十个色 = 100 个十六进制值。手抄一遍必然有错，而且以后改一个色就要
记得同步两处 —— 这个工程已经栽过好几次"同一个数写两处"。
所以预览页只做一件事：**把代码里的色值渲染出来看**，不持有任何自己的数据。

---- 1.7.0 起是"左浅右深" ----
浅色五套、深色五套各选各的，所以页面也照 App 里配色页的样子排：
左边一列浅色（白天），右边一列深色（夜里）。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
THEME = os.path.join(ROOT, "app", "src", "main", "java", "com", "qushe", "app", "ui", "theme", "Theme.kt")
PAGE = os.path.join(ROOT, "docs", "skins.html")

# 种子色的字段名（与 skinPalette 的参数名一致）
FIELDS = ["bg", "surface", "main", "delete", "info", "purple", "folder",
          "textPrimary", "textSecondary", "textTertiary"]

# 卡下方那句"来历"：**只写画面，不写参数** —— 用户选的是感觉。
SOURCE = {
    "西山晴雪": "冷白底，一抹灰蓝",
    "苏堤春晓": "浅柔粉，堤上一线嫩青",
    "雷峰夕照": "暖橘棕，塔影压着豆沙红",
    "月泉晓彻": "浅金，天将亮未亮那点蓝",
    "琼岛春阴": "浅芽绿，花还没开",
    "曲院风荷": "柔绿配淡玫，水面上的风",
    "云栖竹径": "灰调青碧，竹林里没太阳",
    "平湖秋月": "冷灰蓝，月光落在水面上",
    "断桥残雪": "冷黑底，雪还没化",
    "双峰插云": "雾蓝灰，两座山看不见顶",
    "三潭印月": "深蓝，三个月亮在潭里",
    "洞庭秋月": "深青灰，一点暗紫",
    "九溪烟树": "深绿灰，水汽浮在林间",
    "柳浪闻莺": "柳黄，夜里也亮",
    "花港观鱼": "珊瑚红，灯下的鱼影",
    "南屏晚钟": "暖金，钟声落下来的颜色",
}


def parse_list(lines, start_marker):
    """从 `val XxxSkins: List<QusheSkin> = listOf(` 开始，逐行收集每套皮肤。"""
    start = next(i for i, ln in enumerate(lines) if start_marker in ln)
    out, cur = [], None
    for ln in lines[start + 1:]:
        s = ln.split("//")[0]
        if "QusheSkin(" in s:
            cur = {"name": None, "colors": {}}
            continue
        if cur is None:
            if s.strip() == ")":
                break
            continue
        m = re.search(r'name\s*=\s*"([^"]+)"', s)
        if m:
            cur["name"] = m.group(1)
            continue
        m = re.search(r"Color\(0xFF([0-9A-Fa-f]{6})\)", s)
        if m:
            key = s.split("=")[0].strip()
            if key in FIELDS:
                cur["colors"][key] = m.group(1).upper()
        if s.strip() == ")," and cur["name"]:
            if len(cur["colors"]) >= len(FIELDS):
                out.append(cur)
            cur = None
    return out


def parse():
    src = io.open(THEME, encoding="utf-8").read()
    # 只去掉整行注释，不碰带引号的行（名字里可能出现 //）
    lines = []
    for ln in src.split("\n"):
        if ln.strip().startswith("//"):
            lines.append("")
        else:
            lines.append(ln)
    return (parse_list(lines, "val LightSkins: List<QusheSkin>"),
            parse_list(lines, "val DarkSkins: List<QusheSkin>"))


CSS = """
  :root { --paper: #f1efea; --ink: #211f1c; --ink-2: #5f5b54; --ink-3: #928d84; --line: #dcd6cd; }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--paper); color: var(--ink);
         font-family: "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
         -webkit-font-smoothing: antialiased; }
  .page { max-width: 1060px; margin: 0 auto; padding: 70px 28px 110px; }
  header { border-bottom: 1px solid var(--line); padding-bottom: 30px; margin-bottom: 40px; }
  .kicker { font-size: 11px; letter-spacing: .34em; color: var(--ink-3); margin: 0 0 22px; }
  h1 { font-family: "Songti SC", "Source Han Serif SC", "Noto Serif SC", "SimSun", serif;
       font-weight: 500; font-size: 46px; line-height: 1.18; margin: 0 0 18px; letter-spacing: .06em; }
  .lede { margin: 0; max-width: 47em; font-size: 14px; line-height: 2; color: var(--ink-2); }
  .lede b { font-weight: 500; color: var(--ink); }
  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 34px; }
  @media (max-width: 820px) { .cols { grid-template-columns: 1fr; } h1 { font-size: 34px; } }
  .col-head { display: flex; align-items: baseline; gap: 10px; border-top: 1px solid var(--line);
              padding-top: 14px; margin-bottom: 18px; }
  .col-head h2 { font-family: "Songti SC", "Source Han Serif SC", serif; font-size: 19px;
                 letter-spacing: .14em; margin: 0; font-weight: 500; }
  .col-head span { font-size: 11px; color: var(--ink-3); }
  .skin { margin-bottom: 20px; }
  .skin-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 8px; }
  .skin-name { font-family: "Songti SC", "Source Han Serif SC", serif; font-size: 15px;
               letter-spacing: .14em; margin: 0; font-weight: 500; }
  .skin-src { font-size: 10.5px; color: var(--ink-3); }
  .screen { border-radius: 12px; overflow: hidden; padding: 16px 14px 0;
            display: flex; flex-direction: column; }
  .s-title { font-size: 16px; letter-spacing: .08em; font-weight: 600; }
  .s-sub { font-size: 8.5px; margin-top: 3px; }
  .s-cards { margin-top: 14px; display: flex; flex-direction: column; gap: 7px; }
  .s-card { display: flex; align-items: center; gap: 8px; padding: 9px 9px 9px 0; border-radius: 8px; }
  .s-bar { width: 3px; align-self: stretch; border-radius: 2px; margin-left: 8px; }
  .s-text { flex: 1; min-width: 0; }
  .s-text b { display: block; font-size: 10.5px; font-weight: 600; }
  .s-text span { display: block; font-size: 7.5px; margin-top: 2px; opacity: .72; }
  .s-num { font-size: 13px; font-weight: 600; font-variant-numeric: tabular-nums; padding-right: 5px; }
  .s-stats { margin-top: 14px; display: flex; justify-content: space-between;
             padding: 10px; border-radius: 8px; }
  .s-stats div { text-align: center; }
  .s-stats b { display: block; font-size: 10.5px; font-variant-numeric: tabular-nums; }
  .s-stats span { display: block; font-size: 6.5px; margin-top: 3px; opacity: .6; }
  .s-nav { margin-top: auto; display: flex; padding: 11px 6px 10px; }
  .s-nav div { flex: 1; text-align: center; font-size: 7.5px; line-height: 1.4; }
  .s-nav i { display: block; font-size: 11px; font-style: normal; margin-bottom: 2px; }
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


def column(title, hint, skins):
    items = "".join(
        f'<article class="skin">'
        f'<div class="skin-head"><h3 class="skin-name">{k["name"]}</h3>'
        f'<span class="skin-src">{SOURCE.get(k["name"], "")}</span></div>'
        f'{screen(k["colors"])}'
        f'</article>'
        for k in skins
    )
    return (f'<section><div class="col-head"><h2>{title}</h2><span>{hint}</span></div>'
            f'{items}</section>')


def build(light, dark):
    return (
        '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n<meta charset="utf-8">\n'
        '<meta name="viewport" content="width=device-width, initial-scale=1">\n'
        '<title>取舍 · 十种光</title>\n<style>' + CSS + '</style>\n</head>\n<body>\n'
        '<div class="page">\n<header>\n'
        '<p class="kicker">取舍 · QUSHE</p>\n<h1>十种光</h1>\n'
        '<p class="lede">浅色五套、深色五套，<b>各选各的</b>：白天用的是一张纸，'
        '夜里用的是暗室里的一束 —— 所以深色不是把浅色压暗，是另外配的五套。'
        '名字借了奔驰氛围灯那一路（一个地名配一个色），读起来像一个有光的房间。</p>\n'
        '</header>\n<div class="cols">\n'
        + column("浅色", "白天", light) + "\n" + column("深色", "夜里", dark) +
        '\n</div>\n<footer>\n本页由 <code>skins_page.py</code> 从 <code>Theme.kt</code> 生成，'
        '不单独持有色值 · 对比度按 WCAG 2.1 计算值核对'
        '（正文 ≥ 10、次要 ≥ 4.6、主色 ≥ 4.5）\n</footer>\n'
        '</div>\n</body>\n</html>\n'
    )


def main():
    light, dark = parse()
    if len(light) < 2 or len(dark) < 2:
        print("解析到 浅色 %d 套 / 深色 %d 套，Theme.kt 的写法可能变了" % (len(light), len(dark)))
        return 1
    html = build(light, dark)

    if "--check" in sys.argv:
        old = io.open(PAGE, encoding="utf-8").read() if os.path.exists(PAGE) else ""
        if old == html:
            print("预览页与 Theme.kt 一致（浅 %d / 深 %d）" % (len(light), len(dark)))
            return 0
        print("预览页与 Theme.kt 不一致 —— 跑 python skins_page.py 重新生成")
        return 1

    io.open(PAGE, "w", encoding="utf-8", newline="\n").write(html)
    print("已生成 %s（浅 %d 套 / 深 %d 套）" % (os.path.relpath(PAGE, ROOT), len(light), len(dark)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
