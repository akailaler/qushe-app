# -*- coding: utf-8 -*-
r"""核对 docs/skins.html 的色值是否与 Theme.kt 完全一致。

逐行扫，不用带反斜杠的正则（Git Bash 的 heredoc 会把 `\s` 之类的转义吃掉）。
"""
import io
import re

FIELDS = ["bg", "surface", "main", "delete", "info", "purple", "folder",
          "textPrimary", "textSecondary", "textTertiary"]

SIX = re.compile("[0-9A-Fa-f]{6}")


def hexes(text):
    out = []
    for m in re.finditer(r"0xFF([0-9A-Fa-f]{6})", text):
        out.append(m.group(1).upper())
    return out


def named(text):
    """从 QushePalette(...) 块里取 `key = Color(0xFF......)`。"""
    d = {}
    for line in text.split("\n"):
        line = line.split("//")[0]
        if "=" not in line:
            continue
        key = line.split("=")[0].strip()
        v = re.search("0xFF([0-9A-Fa-f]{6})", line)
        if v:
            d[key] = v.group(1).upper()
    return d


def parse_theme():
    src = io.open("app/src/main/java/com/qushe/app/ui/theme/Theme.kt", encoding="utf-8").read()
    lines = src.split("\n")

    skins = {}          # name -> {"false": {...}, "true": {...}}
    palette = {}        # 常量名 -> 具名色 dict

    # ---- 一、LightPalette / DarkPalette 这类具名调色板 ----
    pending_var = None
    buf = []
    for ln in lines:
        s = ln.split("//")[0]
        m = re.search(r"val (\w+) = QushePalette\(", s)
        if m:
            pending_var = m.group(1)
            buf = []
            continue
        if pending_var:
            buf.append(s)
            if s.strip() == ")":
                palette[pending_var] = named("\n".join(buf))
                pending_var = None

    # ---- 二、QusheSkin 块 ----
    cur = None
    mode = None
    buf = []
    for ln in lines:
        s = ln.split("//")[0]
        if "QusheSkin(" in s:
            cur = {"name": None, "false": None, "true": None, "inline": None}
            inline = re.search(r'QusheSkin\(\s*[0-9]+\s*,\s*"([^"]+)"', s)
            if inline:
                cur["name"] = inline.group(1)
                cur["inline"] = s
            continue
        if cur is None:
            continue
        if cur["name"] is None:
            nm = re.search(r'name\s*=\s*"([^"]+)"', s)
            if nm:
                cur["name"] = nm.group(1)
                continue
        if "dark = false" in s or "dark = true" in s:
            mode = "true" if "dark = true" in s else "false"
            buf = []
            continue
        if mode and "Color(0xFF" in s:
            buf.extend(hexes(s))
            continue
        if mode and s.strip().startswith(")"):
            if len(buf) >= 10:
                cur[mode] = dict(zip(FIELDS, buf[:10]))
            mode = None
            buf = []
            # 两版都收齐就落库。不能等 ")" 单独一行 —— skinPalette 的收尾是 "),"，
            # 而 QusheSkin 的收尾前面一定已经有两版了。
            if cur.get("false") and cur.get("true") and cur.get("name"):
                skins[cur["name"]] = {"false": cur["false"], "true": cur["true"]}
                cur = None
    return skins, palette


def parse_html():
    src = io.open("docs/skins.html", encoding="utf-8").read()
    start = src.index("const SKINS = [")
    end = src.index("\n];", start)
    seg = src[start:end]
    out = {}
    for chunk in seg.split("{ n:")[1:]:
        nm = chunk.split('"')[1]
        li = chunk.index("l: [")
        di = chunk.index("d: [")
        l = chunk[li:chunk.index("]", li) + 1]
        d = chunk[di:chunk.index("]", di) + 1]
        out[nm] = {"false": dict(zip(FIELDS, SIX.findall(l))),
                   "true": dict(zip(FIELDS, SIX.findall(d)))}
    return out


def main():
    skins, palette = parse_theme()
    html = parse_html()
    print("Theme.kt 解析到 %d 套；具名调色板 %s" % (len(skins), sorted(palette)))
    print("skins.html 解析到 %d 套" % len(html))

    # 默认那套是 LightPalette / DarkPalette 的拷贝。
    # 这两个是具名调色板（ink / surface1 / accentKeep…），要先映射成种子色的字段名。
    ALIAS = {"bg": "ink", "surface": "surface1", "main": "accentKeep",
             "delete": "accentDelete", "info": "accentInfo", "purple": "accentPurple",
             "folder": "accentFolder", "textPrimary": "textPrimary",
             "textSecondary": "textSecondary", "textTertiary": "textTertiary"}
    if "LightPalette" in palette and "DarkPalette" in palette:
        def mapped(src):
            return {f: src[ALIAS[f]] for f in FIELDS if ALIAS[f] in src}

        skins["素白玄墨"] = {"false": mapped(palette["LightPalette"]),
                            "true": mapped(palette["DarkPalette"])}
        print("默认套取自 LightPalette / DarkPalette")

    bad = 0
    for name in sorted(set(list(skins) + list(html))):
        if name not in skins:
            print("  Theme 缺：", name)
            bad += 1
            continue
        if name not in html:
            print("  HTML 缺：", name)
            bad += 1
            continue
        for mode, label in (("false", "浅"), ("true", "深")):
            a, b = skins[name][mode], html[name][mode]
            diffs = [(k, a.get(k), b.get(k)) for k in FIELDS
                     if (a.get(k) or "").upper() != (b.get(k) or "").upper()]
            if diffs:
                bad += 1
                print("  不一致 %s（%s）：" % (name, label), diffs)

    print("\n不一致：%d 处" % bad)
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
