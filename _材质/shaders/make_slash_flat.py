"""生成单层刀光用的贴图。

形状直接沿用拔刀剑那张 slash.png 的 alpha（就是那道弧形），颜色我们自己给一个纯色。
这样第一版"单层刀光"不需要任何自定义着色器，用原版半透明 RenderType 就能画，
开光影时也一样能显示。
"""

import io
import zipfile

import numpy as np
from PIL import Image

JAR = r"D:\GoldenStar\mod构建\SilverStar\libs\slashblade-1.20.1-1.1.23.jar"
OUT = r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\textures\misc\slash_flat.png"
PREVIEW = r"D:\GoldenStar\mod构建\SilverStar\_材质\slash_flat_preview.png"

z = zipfile.ZipFile(JAR)
src = Image.open(io.BytesIO(z.read("assets/slashblade/model/util/slash.png"))).convert("RGBA")
print("source", src.size)

alpha = np.array(src)[:, :, 3].astype(np.float32)
peak = float(alpha.max()) or 1.0
alpha = np.clip(alpha / peak, 0.0, 1.0)

h, w = alpha.shape
yy, xx = np.mgrid[0:h, 0:w]

# 纯冰蓝，稍微按高度做一点点渐变，免得整片死平
t = (yy / max(1, h - 1))[:, :, None]
base = np.array([0.62, 0.84, 1.00], dtype=np.float32)
top = np.array([0.86, 0.96, 1.00], dtype=np.float32)
rgb = base * (1.0 - t) + top * t

out = np.zeros((h, w, 4), dtype=np.uint8)
out[:, :, :3] = np.clip(rgb * 255.0, 0, 255).astype(np.uint8)
out[:, :, 3] = (alpha * 255.0).astype(np.uint8)

img = Image.fromarray(out, "RGBA")
img.save(OUT)
print("saved", OUT, img.size, "alpha peak", peak)

# 预览：贴在深色底上看形状
bg = Image.new("RGBA", img.size, (18, 18, 26, 255))
Image.alpha_composite(bg, img).convert("RGB").save(PREVIEW)
print("preview", PREVIEW)
