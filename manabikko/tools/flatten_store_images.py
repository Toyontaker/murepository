"""store/ の 画像を Google Play の 条件に あわせる。

- フィーチャーグラフィック・スクリーンショット:透過なし(24bit)の PNG に する
- アイコン:512×512 の 32bit PNG のまま
- サイズと 縦横比(長辺は 短辺の 2倍まで)を チェックする

    python3 tools/flatten_store_images.py
"""
import glob
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "store")
ok = True
for path in sorted(glob.glob(os.path.join(ROOT, "**", "*.png"), recursive=True)):
    name = os.path.relpath(path, ROOT)
    im = Image.open(path)
    if name == "icon-512.png":
        if im.size != (512, 512):
            print("NG", name, im.size); ok = False
        continue
    if im.mode != "RGB":
        bg = Image.new("RGB", im.size, (255, 248, 231))
        bg.paste(im, mask=im.split()[-1] if im.mode in ("RGBA", "LA") else None)
        bg.save(path, optimize=True)
        im = bg
    w, h = im.size
    if name.startswith("feature-graphic"):
        good = (w, h) == (1024, 500)
    else:
        good = 320 <= min(w, h) and max(w, h) <= 3840 and max(w, h) <= 2 * min(w, h)
    print("OK" if good else "NG", name, im.size, im.mode)
    ok &= good
sys.exit(0 if ok else 1)
