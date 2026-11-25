"""生成"左臂星空"用的竖向渐变遮罩。

贴图是 16x256，竖着铺在小臂上：v=0 在上（第 1 段与第 2 段的交界），v=1 在下（手腕）。

为什么 alpha 不能直接写想要的透明度：
cosmic.fsh 的 CM_MASK_MODE == 2 会算 max(星星强度*mask.a, mask.a*2)，
也就是 mask.a 到 0.5 就已经饱和成 1 了。所以这里存的是"目标透明度 / 2"。

四段（每段占 1/4，但第 1 段是干净皮肤，所以贴图只覆盖第 2~4 段）：
    第 2 段 0% → 45%
    第 3 段 45% → 70%
    第 4 段 70% → 80%
"""
import os

import numpy as np
from PIL import Image

W, H = 16, 256
BASE = r"D:\GoldenStar\mod构建\SilverStar"
out = os.path.join(BASE, r"src\main\resources\assets\silverstar\textures\misc\arm_mask.png")
os.makedirs(os.path.dirname(out), exist_ok=True)

v = (np.arange(H, dtype=np.float32) + 0.5) / H      # 0 上 → 1 下
# 三段分段线性
a = np.where(
    v < 1.0 / 3.0,
    0.45 * (v / (1.0 / 3.0)),
    np.where(
        v < 2.0 / 3.0,
        0.45 + (0.70 - 0.45) * ((v - 1.0 / 3.0) / (1.0 / 3.0)),
        0.70 + (0.80 - 0.70) * ((v - 2.0 / 3.0) / (1.0 / 3.0)),
    ),
)
# 最上面留一点点完全的 0，让交界处彻底看不见
a = np.where(v < 0.02, 0.0, a)

alpha = np.clip(a * 0.5, 0.0, 1.0)                   # 除以 2，见上面的说明
rgb = np.full((H, W, 3), 255, dtype=np.uint8)
a8 = np.repeat((alpha[:, None] * 255.0).astype(np.uint8), W, axis=1)
img = np.dstack([rgb, a8])
Image.fromarray(img.astype(np.uint8), "RGBA").save(out, optimize=True)

print("mask ->", out, os.path.getsize(out), "bytes")
print("alpha 上/中/下 = %.2f / %.2f / %.2f" % (a[2], a[H // 2], a[-1]))
