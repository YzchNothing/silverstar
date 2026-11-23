import os

import numpy as np
from PIL import Image

# 散华（sange）原贴图的配色分析
SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
OUT = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"

img = Image.open(SRC).convert("RGBA")
print("size", img.size)
arr = np.array(img).astype(np.float32)
rgb = arr[:, :, :3]
a = arr[:, :, 3]

px = rgb[a > 8].reshape(-1, 3)
print("opaque px", len(px))
q = (px // 32 * 32).astype(np.int32)
uniq, counts = np.unique(q, axis=0, return_counts=True)
order = np.argsort(-counts)
for i in order[:12]:
    c = uniq[i]
    print("  #%02X%02X%02X  %6.2f%%" % (c[0], c[1], c[2], counts[i] * 100.0 / len(px)))

big = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
big.save(os.path.join(OUT, "sange_white_big.png"))
print("saved sange_white_big.png")
