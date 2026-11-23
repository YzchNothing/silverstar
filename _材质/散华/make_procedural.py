import os

import numpy as np
from PIL import Image, ImageFilter

# 纯银星贴图：保留散华原贴图的结构（明暗分布 = 刃面/刃口/缠绳/鞘），
# 在上面叠程序化质感：拉丝噪声、刃文波浪、刃口高光、暗部星点、冷色渐变。
SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
OUT = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"

SCALE = 2
W0, H0 = 192, 384
W, H = W0 * SCALE, H0 * SCALE
rng = np.random.default_rng(20261123)

img = Image.open(SRC).convert("RGBA").resize((W, H), Image.LANCZOS)
arr = np.array(img).astype(np.float32)
rgb = arr[:, :, :3]
alpha = arr[:, :, 3]
r, g, b = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
u, v = xx / W, yy / H

# ---- 程序化噪声：横向高频、纵向低频，就是拉丝的方向感 ----
noise = rng.normal(0.0, 1.0, (H, W)).astype(np.float32)
streak = np.array(Image.fromarray(((noise * 0.5 + 0.5) * 255).clip(0, 255).astype(np.uint8), "L")
                  .filter(ImageFilter.GaussianBlur(radius=(7, 1)))).astype(np.float32) / 255.0
streak = (streak - 0.5) * 2.0

# ---- 银白调色板 ----
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
steel_area = (alpha > 8) & (~pink) & (~olive) & (~orange)

# ---- 明暗 -> 冷色金属，再叠拉丝 ----
t = np.clip((lum - 0.26) / 0.66, 0, 1)
t = np.clip(t + streak * 0.07, 0, 1)

# 刃文：只在足够亮的区域（刃面）起作用，沿 v 方向起伏
hamon = 0.5 + 0.5 * np.sin(v * 27.0 + 1.1) * 0.6 + 0.5 * np.sin(v * 61.0)
hamon = np.clip(hamon, 0, 1)
blade_like = steel_area & (lum > 0.45)
t = np.where(blade_like, np.clip(t + (hamon - 0.5) * 0.16, 0, 1), t)

col = rgb.copy()
col[steel_area] = ramp(t[steel_area])

# ---- 刃口高光：最亮的那一小撮推到接近纯白 ----
edge = steel_area & (lum > 0.88)
k = np.clip((lum[edge] - 0.88) / 0.12, 0, 1)[:, None] * 0.85
col[edge] = col[edge] * (1 - k) + np.array([252, 254, 255]) * k

# ---- 暗部星点：深色区域撒稀疏冷光点 ----
star = (rng.random((H, W)) > 0.9955).astype(np.float32)
star = np.array(Image.fromarray((star * 255).astype(np.uint8), "L")
                .filter(ImageFilter.GaussianBlur(0.9))).astype(np.float32) / 255.0
dark = steel_area & (lum < 0.42)
star_rgb = np.array([0.60, 0.85, 1.00], dtype=np.float32)
col[dark] = np.clip(col[dark] + star[dark][:, None] * star_rgb, 0, 1)

# ---- 冷色点缀 ----
col[pink] = np.array([0.58, 0.88, 1.00], dtype=np.float32) * np.clip(lum[pink] * 1.25 + 0.20, 0, 1)[:, None]
col[olive] = np.array([0.05, 0.08, 0.15], dtype=np.float32) * (0.7 + 0.6 * lum[olive])[:, None]
col[orange] = np.array([0.60, 0.86, 1.00], dtype=np.float32) * 0.9

out = np.dstack([np.clip(col, 0, 1) * 255.0, alpha]).astype(np.uint8)
res = Image.fromarray(out, "RGBA").filter(ImageFilter.SMOOTH)

tex = os.path.join(OUT, "silver_star_proc.png")
res.save(tex)
flat = Image.new("RGBA", res.size, (14, 16, 26, 255))
prev = Image.alpha_composite(flat, res).convert("RGB")
prev.resize((prev.width * 2, prev.height * 2), Image.NEAREST).save(os.path.join(OUT, "preview_proc.png"))
print("texture ->", tex, res.size)
