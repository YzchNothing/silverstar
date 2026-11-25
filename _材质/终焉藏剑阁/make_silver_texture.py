"""把湮灭之刃的贴图改成银白色渐变（保留原有纹理结构）。

做法：
  1. 取每像素的亮度 L（保留贴图里的星尘、刻线、裂纹）
  2. 把 L 重新映射到一条"银白"色带（暗部偏冷灰、亮部近白）
  3. 纵向加一层很轻的渐变（上白下灰），让它看起来是金属而不是纸
  4. 原图的 alpha 不动

输出三个版本供挑选：
  A 亮银（整体偏白）
  B 冷银（暗部带蓝，像不锈钢）
  C 银白 + 高光（对比拉大，亮部更亮）
"""
import os

import numpy as np
from PIL import Image

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\终焉藏剑阁"
SRC = os.path.join(BASE, "annihilation_blade.png")

img = Image.open(SRC).convert("RGBA")
a = np.asarray(img).astype(np.float32)
rgb = a[..., :3] / 255.0
alpha = a[..., 3]
H, W = alpha.shape

lum = (0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2])
# 提一点对比，让星尘更清楚
lum = np.clip((lum - 0.5) * 1.12 + 0.5, 0.0, 1.0)

# 纵向渐变：上（y=0）亮一点，下（y=H）暗一点
vy = (np.arange(H, dtype=np.float32) / max(1, H - 1))[..., None]
grad = 1.12 - 0.10 * vy            # 0.90 ~ 1.06

RAMPS = {
    "A": ((0.22, 0.23, 0.26), (0.62, 0.63, 0.66), (0.98, 0.99, 1.00)),
    "B": ((0.18, 0.20, 0.26), (0.55, 0.59, 0.65), (0.93, 0.96, 1.00)),
    "C": ((0.16, 0.17, 0.20), (0.68, 0.69, 0.72), (1.00, 1.00, 1.00)),
    "D": ((0.34, 0.36, 0.42), (0.74, 0.77, 0.82), (1.00, 1.00, 1.00)),
}
CONTRAST = {"A": 1.0, "B": 1.0, "C": 1.15, "D": 1.05}

outs = {}
for key, (dark, mid, light) in RAMPS.items():
    l = np.clip((lum - 0.5) * CONTRAST[key] + 0.5, 0.0, 1.0)
    l = l[..., None]
    # 两段线性：0..0.5 暗->中，0.5..1 中->亮
    lo = l * 2.0
    hi = (l - 0.5) * 2.0
    col = np.where(l < 0.5,
                   np.array(dark) + (np.array(mid) - np.array(dark)) * lo,
                   np.array(mid) + (np.array(light) - np.array(mid)) * np.clip(hi, 0, 1))
    col = col * grad[..., None]
    col = np.clip(col, 0.0, 1.0)
    out = np.dstack([col * 255.0, alpha]).astype(np.uint8)
    path = os.path.join(BASE, "annihilation_blade_silver_%s.png" % key)
    Image.fromarray(out, "RGBA").save(path, optimize=True)
    outs[key] = path
    print(key, os.path.getsize(path), "bytes")

# 预览：原图 + 三个版本，黑底
show = [img] + [Image.open(outs[k]) for k in ("A", "B", "C", "D")]
preview = Image.new("RGB", (W * 5 + 40, H), (10, 12, 18))
for k, im in enumerate(show):
    bg = Image.new("RGBA", (W, H), (10, 12, 18, 255))
    preview.paste(Image.alpha_composite(bg, im).convert("RGB"), (k * (W + 10), 0))
preview.resize(((W * 5 + 40) // 2, H // 2), Image.LANCZOS).save(
    os.path.join(BASE, "preview_silver.png"))
print("preview ->", os.path.join(BASE, "preview_silver.png"))
