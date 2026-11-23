import numpy as np
from PIL import Image

SRC = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\sange_white.png"
img = Image.open(SRC).convert("RGBA")
a = np.array(img).astype(np.int16)
rgb, alpha = a[:, :, :3], a[:, :, 3]
H, W = alpha.shape

white = (rgb[:, :, 0] >= 250) & (rgb[:, :, 1] >= 250) & (rgb[:, :, 2] >= 250)
content = (alpha > 8) & (~white)

cols = content.sum(axis=0)

runs = []
x = 0
while x < W:
    if cols[x] > 2:
        s = x
        while x < W and cols[x] > 2:
            x += 1
        runs.append((s, x - 1))
    else:
        x += 1
print("col runs:", runs)

for (s, e) in runs:
    sub = content[:, s:e + 1]
    rows = np.where(sub.any(axis=1))[0]
    segs = []
    start = None
    prev = None
    for y in rows:
        if start is None:
            start = y
        elif y != prev + 1:
            segs.append((start, prev))
            start = y
        prev = y
    if start is not None:
        segs.append((start, prev))
    print("\nx %3d-%3d row segments:" % (s, e))
    for (y0, y1) in segs:
        seg = content[y0:y1 + 1, s:e + 1]
        xs = np.where(seg.any(axis=0))[0]
        print("   y %3d-%3d  px %5d  xspan %d-%d" % (y0, y1, seg.sum(), s + xs.min(), s + xs.max()))
