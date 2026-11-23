import os
from collections import defaultdict

import numpy as np
from PIL import Image, ImageDraw

# 把散华的 UV 三角形按组光栅化，看清楚每组到底占了贴图的哪些像素
OBJ = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange.obj"
OUT = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
W, H = 192, 384

uvs = []
tris = defaultdict(list)     # group -> list of (uv index triples)
cur = "(none)"
buf = []

for line in open(OBJ, encoding="utf-8", errors="ignore"):
    s = line.strip()
    if s.startswith("g "):
        cur = s[2:].strip().split()[0]
        buf = []
    elif s.startswith("vt "):
        p = s.split()
        uvs.append((float(p[1]), float(p[2])))
    elif s.startswith("f "):
        idx = []
        for tok in s.split()[1:]:
            parts = tok.split("/")
            if len(parts) > 1 and parts[1]:
                idx.append(int(parts[1]) - 1)
        # 扇形三角化
        for i in range(1, len(idx) - 1):
            tris[cur].append((idx[0], idx[i], idx[i + 1]))


def px(i):
    u, v = uvs[i]
    return (u * W, (1.0 - v) * H)


masks = {}
for name, faces in tris.items():
    if not faces:
        continue
    im = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(im)
    for a, b, c in faces:
        d.polygon([px(a), px(b), px(c)], fill=255)
    m = np.array(im) > 127
    masks[name] = m
    print("%-18s tris=%-5d px=%-6d %.1f%% of atlas" % (name, len(faces), m.sum(), m.sum() * 100.0 / (W * H)))

print("\n重叠检查（与 blade 的交集 / blade 自身）：")
ref = masks.get("blade")
for name, m in sorted(masks.items()):
    if name == "blade" or ref is None:
        continue
    inter = (m & ref).sum()
    print("  %-18s 与 blade 重叠 %6d px  (占 blade 的 %5.1f%%)" % (name, inter, inter * 100.0 / max(ref.sum(), 1)))

# 落一张总览：每组不同的颜色
colors = [(255, 90, 90), (90, 160, 255), (120, 255, 120), (255, 220, 90),
          (200, 120, 255), (90, 240, 240), (255, 150, 60), (180, 180, 180),
          (255, 120, 200), (140, 255, 200)]
canvas = Image.new("RGB", (W, H), (10, 12, 18))
for i, (name, m) in enumerate(sorted(masks.items())):
    layer = Image.new("RGB", (W, H), colors[i % len(colors)])
    canvas = Image.composite(layer, canvas, Image.fromarray((m * 200).astype(np.uint8), "L"))
canvas.resize((W * 2, H * 2), Image.NEAREST).save(os.path.join(OUT, "uv_groups_sange.png"))
print("\nsaved uv_groups_sange.png")
