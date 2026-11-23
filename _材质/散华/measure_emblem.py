import numpy as np
from PIL import Image

# 只做测量，不改任何文件：找出纹章那个菱形里"灰色边框"的范围
SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
a = np.array(Image.open(SRC).convert("RGBA")).astype(np.int16)
rgb = a[:, :, :3]
H, W = a.shape[:2]

x0, y0, x1, y1 = 70, 48, 180, 150
sub = rgb[y0:y1, x0:x1]

r, g, b = sub[:, :, 0], sub[:, :, 1], sub[:, :, 2]
grey = (np.abs(r - g) < 12) & (np.abs(g - b) < 12) & (r > 140) & (r < 235)
white = (r >= 245) & (g >= 245) & (b >= 245)
dark = (r < 80) & (g < 80) & (b < 80)

print("region x%d-%d y%d-%d  px=%d" % (x0, x1, y0, y1, sub.shape[0] * sub.shape[1]))
print("  grey(frame) %5d" % grey.sum())
print("  white      %5d" % white.sum())
print("  dark(纹章)  %5d" % dark.sum())

ys, xs = np.where(grey)
print("  frame bbox: x %d-%d  y %d-%d" % (x0 + xs.min(), x0 + xs.max(), y0 + ys.min(), y0 + ys.max()))

ys, xs = np.where(dark)
if len(xs):
    print("  emblem bbox: x %d-%d  y %d-%d" % (x0 + xs.min(), x0 + xs.max(), y0 + ys.min(), y0 + ys.max()))

# 沿着中轴看每行的灰色起始位置，判断边框厚度
cy = (y0 + y1) // 2
row = grey[cy - y0]
idx = np.where(row)[0]
print("  mid row y=%d grey columns: %s" % (cy, (x0 + idx).tolist()[:20]))
