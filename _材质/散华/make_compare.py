import os

import numpy as np
from PIL import Image, ImageDraw

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
SRC = os.path.join(BASE, "silver_star_cosmic.png")
ORIG = os.path.join(BASE, "sange_white.png")

# 对照图：原贴图 / 新贴图 / 新贴图叠加分区框
BOXES = [
    ("sheath", 0, 0, 56, 384, (255, 70, 70)),
    ("handle", 69, 179, 106, 375, (70, 220, 90)),
    ("blade", 131, 179, 170, 375, (80, 160, 255)),
    ("emblem", 74, 52, 176, 146, (255, 210, 60)),
    ("mouth", 69, 7, 121, 45, (255, 130, 30)),
]

S = 3
orig = Image.open(ORIG).convert("RGBA")
new = Image.open(SRC).convert("RGBA")
W, H = new.size

flat = Image.new("RGBA", (W, H), (14, 16, 26, 255))
o = Image.alpha_composite(flat, orig)
n = Image.alpha_composite(flat, new)

boxed = n.copy()
d = ImageDraw.Draw(boxed)
for name, x0, y0, x1, y1, col in BOXES:
    d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=col, width=1)
    d.text((x0 + 2, y0 + 1), name, fill=col)

pad = 10
sheet = Image.new("RGB", (W * 3 + pad * 4, H + pad * 2), (14, 16, 26))
for i, im in enumerate([o, n, boxed]):
    sheet.paste(im.convert("RGB"), (pad + i * (W + pad), pad))

sheet = sheet.resize(((sheet.width * S) // 2, (sheet.height * S) // 2), Image.NEAREST)
out = os.path.join(BASE, "compare_cosmic.png")
sheet.save(out)
print("saved", out, sheet.size)
