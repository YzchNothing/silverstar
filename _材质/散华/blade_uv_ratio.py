import os
from collections import defaultdict

import numpy as np
from PIL import Image

# 量一下散华 blade 组的 UV 矩形和它在模型上的实际尺寸，算出贴图被拉伸的倍数
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
OBJ = os.path.join(BASE, "sange.obj")
TEX_W, TEX_H = 192, 384

verts, uvs = [], []
tris = defaultdict(list)
cur = None
for line in open(OBJ, encoding="utf-8", errors="ignore"):
    s = line.strip()
    if s.startswith("v "):
        p = s.split()
        verts.append((float(p[1]), float(p[2]), float(p[3])))
    elif s.startswith("g "):
        cur = s[2:].strip().split()[0]
    elif s.startswith("vt "):
        p = s.split()
        uvs.append((float(p[1]), float(p[2])))
    elif s.startswith("f ") and cur:
        vi, ti = [], []
        for tok in s.split()[1:]:
            parts = tok.split("/")
            vi.append(int(parts[0]) - 1)
            ti.append(int(parts[1]) - 1 if len(parts) > 1 and parts[1] else -1)
        for i in range(1, len(vi) - 1):
            tris[cur].append(((vi[0], ti[0]), (vi[i], ti[i]), (vi[i + 1], ti[i + 1])))


def stats(name):
    us = [uvs[t][0] for f in tris[name] for _, t in f if t >= 0]
    vs = [uvs[t][1] for f in tris[name] for _, t in f if t >= 0]
    pts = [verts[v] for f in tris[name] for v, _ in f]
    arr = np.array(pts)
    lo, hi = arr.min(axis=0), arr.max(axis=0)
    return (min(us), max(us), min(vs), max(vs)), (lo, hi)


(umin, umax, vmin, vmax), (lo, hi) = stats("blade")
px_w = (umax - umin) * TEX_W
px_h = (vmax - vmin) * TEX_H
print("blade UV: u %.4f..%.4f  v %.4f..%.4f" % (umin, umax, vmin, vmax))
print("  换算成像素: x %.1f..%.1f  y %.1f..%.1f  -> %.1f x %.1f px"
      % (umin * TEX_W, umax * TEX_W, (1 - vmax) * TEX_H, (1 - vmin) * TEX_H, px_w, px_h))
ext = hi - lo
print("blade 3D bbox: X %.2f..%.2f (%.2f)  Y %.2f..%.2f (%.2f)  Z %.2f..%.2f (%.2f)"
      % (lo[0], hi[0], ext[0], lo[1], hi[1], ext[1], lo[2], hi[2], ext[2]))

# 刀刃：长度沿 X，宽度沿 Z（刀身是薄片，Y 是厚）
length, width = ext[0], ext[2]
print("长度(X)=%.2f  宽度(Z)=%.2f  厚度(Y)=%.2f" % (length, width, ext[1]))
print("贴图密度: 纵向 %.3f px/单位  横向 %.3f px/单位"
      % (px_h / length, px_w / max(width, 1e-6)))
print("拉伸倍数（纵向/横向）= %.2f  ->  贴图在刀刃上被纵向拉长 %.2f 倍（或横向压扁）"
      % ((px_h / length) / (px_w / max(width, 1e-6)), (px_h / length) / (px_w / max(width, 1e-6))))
print("若要不变形，这段贴图的高度应约为宽度的 %.2f 倍" % (px_w * length / width / px_w))
