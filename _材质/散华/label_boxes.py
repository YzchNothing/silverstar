import os

import numpy as np
from PIL import Image

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
MASKS = os.path.join(BASE, "uv_masks")
S = 3  # uv_masks 是 3 倍图

BOXES = [
    ("top-ornament", 7, 0, 47, 48),
    ("sakura-1", 8, 55, 55, 147),
    ("sakura-2", 0, 195, 47, 293),
    ("plum-bottle", 8, 301, 43, 381),
    ("green-square", 69, 7, 120, 44),
    ("emblem-diamond", 74, 52, 175, 145),
    ("dots", 73, 147, 92, 172),
    ("two-bars", 71, 179, 170, 374),
]

groups = {}
for f in sorted(os.listdir(MASKS)):
    if f.endswith(".png"):
        im = Image.open(os.path.join(MASKS, f)).convert("L")
        groups[f[:-4]] = np.array(im.resize((192, 384), Image.NEAREST)) > 127

print("box coverage by group (%% of box area):")
for name, x0, y0, x1, y1 in BOXES:
    area = (x1 - x0 + 1) * (y1 - y0 + 1)
    parts = []
    for g, m in groups.items():
        c = m[y0:y1 + 1, x0:x1 + 1].sum() * 100.0 / area
        if c > 3:
            parts.append("%s %.0f%%" % (g, c))
    print("  %-14s x%3d-%3d y%3d-%3d : %s" % (name, x0, x1, y0, y1, ", ".join(parts) or "-"))

# 下方两根竖条：按列看内容分布，找中间的空隙
img = Image.open(os.path.join(BASE, "sange_white.png")).convert("RGBA")
a = np.array(img).astype(np.int16)
rgb, alpha = a[:, :, :3], a[:, :, 3]
white = (rgb[:, :, 0] >= 250) & (rgb[:, :, 1] >= 250) & (rgb[:, :, 2] >= 250)
content = (alpha > 8) & (~white)
sub = content[179:375, 69:176]
prof = sub.sum(axis=0)
print("\ncolumns x69..175 (y179-374) content per column:")
line = ""
for i, v in enumerate(prof):
    line += ("#" if v > 20 else ("." if v > 0 else " "))
print("  x69" + line)
