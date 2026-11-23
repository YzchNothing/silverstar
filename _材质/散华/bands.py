import numpy as np
from PIL import Image

SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
img = Image.open(SRC).convert("RGBA")
arr = np.array(img)
a = arr[:, :, 3]
H, W = a.shape
print("texture", W, "x", H)

opaque = a > 8
cols = opaque.sum(axis=0)
# 找出连续的"有像素"列区间
bands = []
x = 0
while x < W:
    if cols[x] > 3:
        s = x
        while x < W and cols[x] > 3:
            x += 1
        bands.append((s, x - 1))
    else:
        x += 1

print("列区间（含像素的连续段）:")
for (s, e) in bands:
    sub = opaque[:, s:e + 1]
    rows = np.where(sub.any(axis=1))[0]
    print("  x %3d-%3d (宽%2d)  y %3d-%3d  px %d" % (s, e, e - s + 1, rows.min(), rows.max(), sub.sum()))
