import os
from collections import defaultdict

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# 把散华原贴图里"白狐"的标志性图案换掉：
# 1) 右中那块菱形狸猫纹章 -> 换成我们自己的菱形星徽
# 2) 原图的樱花 / 梅花（粉色）-> 冰蓝星点
SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
OBJ = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange.obj"
OUT = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"

SCALE = 2
W, H = 192 * SCALE, 384 * SCALE
rng = np.random.default_rng(20261123)

img = Image.open(SRC).convert("RGBA").resize((W, H), Image.LANCZOS)
arr = np.array(img).astype(np.float32)
rgb = arr[:, :, :3]
alpha = arr[:, :, 3]
r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

# ---- 找到 item_back（菱形纹章）的 UV 遮罩 ----
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

mask_im = Image.new("L", (W, H), 0)
d = ImageDraw.Draw(mask_im)
for a, bb, c in tris["item_back"]:
    pts = []
    for i in (a, bb, c):
        u, v = uvs[i]
        pts.append((u * W, (1.0 - v) * H))
    d.polygon(pts, fill=255)
m_back = np.array(mask_im) > 127

ys, xs = np.where(m_back)
x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
side = max(x1 - x0, y1 - y0)
print("item_back bbox x %d-%d  y %d-%d  side %d" % (x0, x1, y0, y1, side))

# ---- 程序化噪声（拉丝） ----
noise = rng.normal(0.0, 1.0, (H, W)).astype(np.float32)
streak = np.array(Image.fromarray(((noise * 0.5 + 0.5) * 255).clip(0, 255).astype(np.uint8), "L")
                  .filter(ImageFilter.GaussianBlur(radius=(7, 1)))).astype(np.float32) / 255.0
streak = (streak - 0.5) * 2.0

STOPS = [(0.00, (0.04, 0.06, 0.11)), (0.26, (0.16, 0.20, 0.29)),
         (0.58, (0.62, 0.67, 0.75)), (0.82, (0.87, 0.90, 0.95)),
         (1.00, (1.00, 1.00, 1.00))]


def ramp(t):
    out = np.zeros((len(t), 3), dtype=np.float32)
    for i in range(len(STOPS) - 1):
        t0, c0 = STOPS[i]
        t1, c1 = STOPS[i + 1]
        m = (t >= t0) & (t <= t1)
        k = (t[m] - t0) / max(t1 - t0, 1e-6)
        out[m] = np.array(c0) + (np.array(c1) - np.array(c0)) * k[:, None]
    out[t > 1.0] = STOPS[-1][1]
    return out


pink = (alpha > 8) & (r > b + 18) & (r > g + 14)
olive = (alpha > 8) & (g > b + 25) & (r > b + 20) & (g > 90) & (~pink)
orange = (alpha > 8) & (r > 150) & (g < 130) & (b < 90) & (~pink)
steel_area = (alpha > 8) & (~pink) & (~olive) & (~orange) & (~m_back)

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
v = yy / H

t = np.clip((lum - 0.26) / 0.66, 0, 1)
t = np.clip(t + streak * 0.07, 0, 1)
hamon = np.clip(0.5 + 0.3 * np.sin(v * 27.0 + 1.1) + 0.25 * np.sin(v * 61.0), 0, 1)
blade_like = steel_area & (lum > 0.45)
t = np.where(blade_like, np.clip(t + (hamon - 0.5) * 0.16, 0, 1), t)

col = rgb.copy()
col[steel_area] = ramp(t[steel_area])

edge = steel_area & (lum > 0.88)
k = np.clip((lum[edge] - 0.88) / 0.12, 0, 1)[:, None] * 0.85
col[edge] = col[edge] * (1 - k) + np.array([252, 254, 255]) * k

star = (rng.random((H, W)) > 0.9955).astype(np.float32)
star = np.array(Image.fromarray((star * 255).astype(np.uint8), "L")
                .filter(ImageFilter.GaussianBlur(0.9))).astype(np.float32) / 255.0
dark = steel_area & (lum < 0.42)
col[dark] = np.clip(col[dark] + star[dark][:, None] * np.array([0.60, 0.85, 1.00]), 0, 1)

# ---- 樱花 / 梅花 -> 冰蓝星屑（保留原形状的细枝，但换成星光色并压暗枝条） ----
col[pink] = np.array([0.62, 0.90, 1.00], dtype=np.float32) * np.clip(lum[pink] * 1.1 + 0.25, 0, 1)[:, None] * 0.95
col[olive] = np.array([0.05, 0.08, 0.15], dtype=np.float32) * (0.7 + 0.6 * lum[olive])[:, None]
col[orange] = np.array([0.60, 0.86, 1.00], dtype=np.float32) * 0.9

# ---- 重画纹章：菱形银边框 + 深蓝底 + 四芒星 ----
emblem = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ed = ImageDraw.Draw(emblem)
pts = [(cx, y0), (x1, cy), (cx, y1), (x0, cy)]
ed.polygon(pts, fill=(198, 208, 224, 255))                       # 外框
inner = [(cx, y0 + side * 0.085), (x1 - side * 0.085, cy),
         (cx, y1 - side * 0.085), (x0 + side * 0.085, cy)]
ed.polygon(inner, fill=(10, 16, 32, 255))                        # 内底

# 四芒星
sp = side * 0.30
star_poly = [(cx, cy - sp), (cx + sp * 0.22, cy - sp * 0.22), (cx + sp, cy),
             (cx + sp * 0.22, cy + sp * 0.22), (cx, cy + sp),
             (cx - sp * 0.22, cy + sp * 0.22), (cx - sp, cy),
             (cx - sp * 0.22, cy - sp * 0.22)]
ed.polygon(star_poly, fill=(236, 246, 255, 255))
ed.ellipse([cx - side * 0.045, cy - side * 0.045, cx + side * 0.045, cy + side * 0.045],
           fill=(120, 200, 255, 255))

em = np.array(emblem).astype(np.float32)
inside = m_back & (em[:, :, 3] > 0)
col[inside] = em[inside][:, :3] / 255.0

out = np.dstack([np.clip(col, 0, 1) * 255.0, alpha]).astype(np.uint8)
res = Image.fromarray(out, "RGBA").filter(ImageFilter.SMOOTH)

tex = os.path.join(OUT, "silver_star_emblem.png")
res.save(tex)
flat = Image.new("RGBA", res.size, (14, 16, 26, 255))
prev = Image.alpha_composite(flat, res).convert("RGB")
prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST).save(os.path.join(OUT, "preview_emblem.png"))
print("texture ->", tex, res.size)
