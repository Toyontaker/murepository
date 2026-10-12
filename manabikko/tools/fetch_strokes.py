"""KanjiVG から ひらがな・カタカナの 書き順データを とってきて
app/src/main/assets/strokes.tsv(1行 = 文字 TAB 線1|線2|…、SVG path、109×109 座標)に まとめる。

KanjiVG © Ulrich Apel, CC BY-SA 3.0  https://kanjivg.tagaini.net/

    python3 tools/fetch_strokes.py
"""
import os
import re
import urllib.request

HIRAGANA = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん"
KATAKANA = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン"
URL = "https://raw.githubusercontent.com/KanjiVG/kanjivg/master/kanji/{:05x}.svg"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "strokes.tsv")

lines = []
for ch in HIRAGANA + KATAKANA:
    svg = urllib.request.urlopen(URL.format(ord(ch))).read().decode("utf-8")
    # id="kvg:03042-s1" の じゅんばんに ならべる
    paths = re.findall(r'<path id="kvg:[0-9a-f]+-s(\d+)"[^>]*\sd="([^"]+)"', svg)
    paths.sort(key=lambda p: int(p[0]))
    assert paths, ch
    lines.append(ch + "\t" + "|".join(d for _, d in paths))
    print(ch, len(paths))

with open(OUT, "w", encoding="utf-8") as f:
    f.write("# KanjiVG (C) Ulrich Apel, CC BY-SA 3.0 - https://kanjivg.tagaini.net/\n")
    f.write("\n".join(lines) + "\n")
