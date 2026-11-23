import os
from collections import defaultdict

import numpy as np

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
        verts.append([float(p[1]), float(p[2]), float(p[3])])
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

V = np.array(verts)
UV = np.array([[u * TEX_W, v * TEX_H] for u, v in uvs])   # 像素坐标，v 已翻成图片方向

along, across = [], []       # 每单位模型长度对应多少像素
for f in tris["blade"]:
    for k in range(3):
        (v0, t0), (v1, t1) = f[k], f[(k + 1) % 3]
        if t0 < 0 or t1 < 0:
            continue
        d3 = V[v1] - V[v0]
        d3len = np.linalg.norm(d3)
        d2 = UV[t1] - UV[t0]
        d2len = np.linalg.norm(d2)
        if d3len < 1e-3:
            continue
        density = d2len / d3len
        # 沿刀身的方向（|dX| 占绝对优势）算"纵向"，其余算"横向"
        if abs(d3[0]) > 2.0 * max(abs(d3[1]), abs(d3[2])):
            along.append(density)
        else:
            across.append(density)

a = np.median(along) if along else float("nan")
c = np.median(across) if across else float("nan")
print("edges: along=%d across=%d" % (len(along), len(across)))
print("沿刀身方向 密度中位数: %.4f px/单位" % a)
print("横跨刀身方向 密度中位数: %.4f px/单位" % c)
print("拉伸比 (横向密度 / 纵向密度) = %.3f" % (c / a))
print("也就是说: 贴图横跨刀身时每单位铺 %.3f 倍像素——横向被拉伸 %.3f 倍" % (c / a, c / a))
print("换成长宽比: 贴图想要不变形，长度方向的像素数应为宽度方向的 %.2f 倍" % (a / c))
