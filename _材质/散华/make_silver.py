import os
import sys

import numpy as np
from PIL import Image, ImageFilter

# 散华（sange）原贴图 -> 纯银星的银白配色
SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"

VARIANT = sys.argv[1] if len(sys.argv) > 1 else "A"
SCALE = 2

img = Image.open(SRC).convert("RGBA")
img = img.resize((img.width * SCALE, img.height * SCALE), Image.LANCZOS)
arr = np.array(img).astype(np.float32)
rgb = arr[:, :, :3]
alpha = arr[:, :, 3]
r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

if VARIANT == "A":      # 银白冷调：暗部深蓝黑，亮部近白
    STOPS = [(0.00, (0.04, 0.06, 0.11)), (0.26, (0.16, 0.20, 0.29)),
             (0.58, (0.62, 0.67, 0.75)), (0.82, (0.87, 0.90, 0.95)),
             (1.00, (1.00, 1.00, 1.00))]
    ACCENT = np.array([0.58, 0.88, 1.00], dtype=np.float32)
else:                   # 深蓝夜：整体压暗一档，偏藏青
    STOPS = [(0.00, (0.03, 0.05, 0.10)), (0.26, (0.12, 0.16, 0.26)),
             (0.58, (0.42, 0.48, 0.60)), (0.82, (0.72, 0.77, 0.86)),
             (1.00, (0.93, 0.96, 1.00))]
    ACCENT = np.array([0.72, 0.92, 1.00], dtype=np.float32)


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


new = rgb.copy()

pink = (alpha > 8) & (r > b + 18) & (r > g + 14)
new[pink] = ACCENT * np.clip(lum[pink] * 1.25 + 0.18, 0, 1)[:, None]

olive = (alpha > 8) & (g > b + 25) & (r > b + 20) & (g > 90) & (~pink)
new[olive] = np.array([0.05, 0.08, 0.15], dtype=np.float32) * (0.7 + 0.6 * lum[olive])[:, None]

orange = (alpha > 8) & (r > 150) & (g < 130) & (b < 90) & (~pink)
new[orange] = ACCENT * 0.85

rest = (alpha > 8) & (~pink) & (~olive) & (~orange)
t = np.clip((lum[rest] - 0.26) / 0.66, 0, 1)
new[rest] = ramp(t)

out = np.dstack([np.clip(new, 0, 1) * 255.0, alpha]).astype(np.uint8)
res = Image.fromarray(out, "RGBA")

# 刃口：已经很亮的部分再推一档，做出开刃的高光
a = np.array(res).astype(np.float32)
bl = (a[:, :, 3] > 8) & (lum > 0.86)
k = np.clip((lum[bl] - 0.86) / 0.14, 0, 1)[:, None] * 0.7
a[:, :, :3][bl] = a[:, :, :3][bl] * (1 - k) + np.array([255, 255, 255]) * k
res = Image.fromarray(a.astype(np.uint8), "RGBA").filter(ImageFilter.SMOOTH)

tex = os.path.join(BASE, "silver_star_%s.png" % VARIANT)
res.save(tex)
flat = Image.new("RGBA", res.size, (14, 16, 26, 255))
prev = Image.alpha_composite(flat, res).convert("RGB")
prev = prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST)
prev.save(os.path.join(BASE, "preview_%s.png" % VARIANT))
print("variant", VARIANT, "->", tex, res.size)
