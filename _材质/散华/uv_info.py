import os
from collections import defaultdict

import numpy as np
from PIL import Image, ImageDraw

# 散华（sange）模型的组与 UV 分区
OBJ = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange.obj"
OUT = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
W, H = 192, 384

verts = []
uvs = []
groups = defaultdict(list)
cur = "(none)"
faces = defaultdict(int)

for line in open(OBJ, encoding="utf-8", errors="ignore"):
    s = line.strip()
    if s.startswith("g "):
        cur = s[2:].strip().split()[0]
    elif s.startswith("v "):
        p = s.split()
        verts.append((float(p[1]), float(p[2]), float(p[3])))
    elif s.startswith("vt "):
        p = s.split()
        uvs.append((float(p[1]), float(p[2])))
    elif s.startswith("f "):
        faces[cur] += 1
        for tok in s.split()[1:]:
            parts = tok.split("/")
            if len(parts) > 1 and parts[1]:
                groups[cur].append(int(parts[1]) - 1)

print("groups:", len(groups), "uvs:", len(uvs))
canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
draw = ImageDraw.Draw(canvas)

for name in sorted(groups):
    idx = groups[name]
    if not idx:
        continue
    us = [uvs[i][0] for i in idx]
    vs = [uvs[i][1] for i in idx]
    x0, x1 = min(us) * W, max(us) * W
    y0, y1 = (1 - max(vs)) * H, (1 - min(vs)) * H
    draw.rectangle([x0, y0, x1, y1], outline=(120, 200, 255))
    draw.text((x0 + 2, y0 + 1), name, fill=(160, 230, 255))
    print("%-20s faces=%-5d u %.3f-%.3f  v %.3f-%.3f" % (name, faces[name], min(us), max(us), min(vs), max(vs)))

canvas.resize((W * 2, H * 2), Image.NEAREST).save(os.path.join(OUT, "uv_map.png"))
print("saved uv_map.png")
