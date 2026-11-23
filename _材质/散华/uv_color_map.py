import os
from collections import defaultdict

import numpy as np
from PIL import Image, ImageDraw

# 每组 UV 用不同纯色填出；大块先画（effect 半透明当底），小块叠在上面
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
OBJ = os.path.join(BASE, "sange.obj")
OUTDIR = os.path.join(BASE, "uv_masks")
os.makedirs(OUTDIR, exist_ok=True)

W0, H0 = 192, 384
S = 3
W, H = W0 * S, H0 * S

uvs = []
tris = defaultdict(list)
cur = None
for line in open(OBJ, encoding="utf-8", errors="ignore"):
    s = line.strip()
    if s.startswith("g "):
        cur = s[2:].strip().split()[0]
    elif s.startswith("vt "):
        p = s.split()
        uvs.append((float(p[1]), float(p[2])))
    elif s.startswith("f ") and cur:
        idx = []
        for tok in s.split()[1:]:
            parts = tok.split("/")
            if len(parts) > 1 and parts[1]:
                idx.append(int(parts[1]) - 1)
        for i in range(1, len(idx) - 1):
            tris[cur].append((idx[0], idx[i], idx[i + 1]))

COLORS = {
    "blade":          (235, 40, 40),
    "blade_damaged":  (255, 140, 20),
    "blade_fragment": (245, 225, 40),
    "handle":         (40, 205, 70),
    "sheath":         (30, 140, 255),
    "effect":         (140, 70, 230),
    "item_blade":     (255, 70, 190),
    "item_bladens":   (0, 210, 190),
    "item_damaged":   (185, 170, 80),
    "item_back":      (245, 245, 245),
}

masks = {}
for name in tris:
    im = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(im)
    for a, b, c in tris[name]:
        pts = []
        for i in (a, b, c):
            u, v = uvs[i]
            pts.append((u * W, (1.0 - v) * H))
        d.polygon(pts, fill=255)
    masks[name] = np.array(im) > 127
    im.save(os.path.join(OUTDIR, name + ".png"))

# 面积从大到小，先铺大的
order = sorted(masks, key=lambda n: -masks[n].sum())

canvas = np.zeros((H, W, 3), dtype=np.float32)
canvas[:] = np.array([14, 16, 26], dtype=np.float32)
for name in order:
    m = masks[name]
    col = np.array(COLORS[name], dtype=np.float32)
    alpha = 0.22 if name == "effect" else 1.0
    canvas[m] = canvas[m] * (1 - alpha) + col * alpha

res = Image.fromarray(canvas.astype(np.uint8), "RGB")

legend_h = 24 * len(order) + 16
full = Image.new("RGB", (W, H + legend_h), (14, 16, 26))
full.paste(res, (0, 0))
ld = ImageDraw.Draw(full)
y = H + 8
for name in order:
    ld.rectangle([8, y, 26, y + 13], fill=COLORS[name])
    ld.text((34, y), "%s  (%d%% of atlas)" % (name, round(masks[name].sum() * 100.0 / (W * H))), fill=(228, 234, 244))
    y += 24

full.save(os.path.join(BASE, "uv_groups_color.png"))
print("order (大->小):", order)
print("saved uv_groups_color.png + uv_masks/*.png")
