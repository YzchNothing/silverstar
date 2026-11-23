import os
from collections import defaultdict

# 把散华模型的 UV 重新排布到我们自己的图集上：
# 每个组各自占一块干净的矩形，组内的相对位置不变。
# 这样贴图完全由我们画，不再依赖原作者的图案（樱花、狸猫纹章等）。
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
SRC = os.path.join(BASE, "sange.obj")
OUT = os.path.join(BASE, "silver_star.obj")

# 组 -> 目标矩形 (u0, v0, u1, v1)，v 向上
LAYOUT = {
    "blade":          (0.00, 0.38, 0.44, 1.00),
    "blade_damaged":  (0.00, 0.38, 0.44, 1.00),
    "blade_fragment": (0.00, 0.38, 0.44, 1.00),
    "sheath":         (0.00, 0.00, 0.44, 0.36),
    "handle":         (0.46, 0.38, 0.66, 1.00),
    "effect":         (0.68, 0.70, 1.00, 1.00),
    "item_blade":     (0.68, 0.36, 1.00, 0.68),
    "item_bladens":   (0.68, 0.36, 1.00, 0.68),
    "item_damaged":   (0.68, 0.36, 1.00, 0.68),
    "item_back":      (0.46, 0.00, 0.66, 0.36),
}

verts = []
uvs = []
faces = []          # (group, [tokens])
cur = None
order = []

for line in open(SRC, encoding="utf-8", errors="ignore"):
    s = line.strip()
    if s.startswith("v "):
        verts.append(s[2:])
    elif s.startswith("vt "):
        p = s.split()
        uvs.append((float(p[1]), float(p[2])))
    elif s.startswith("g "):
        cur = s[2:].strip().split()[0]
        order.append(("g", cur))
    elif s.startswith("f "):
        faces.append((cur, s.split()[1:]))
        order.append(("f", len(faces) - 1))
    elif s.startswith(("vn ", "s ", "usemtl ", "mtllib ", "o ")):
        order.append(("raw", s))
    elif s.startswith("#"):
        order.append(("raw", line.rstrip("\n")))

# 每组统计 UV bbox
gb = defaultdict(lambda: [1e9, 1e9, -1e9, -1e9])
for g, toks in faces:
    for tok in toks:
        parts = tok.split("/")
        if len(parts) > 1 and parts[1]:
            u, v = uvs[int(parts[1]) - 1]
            b = gb[g]
            b[0] = min(b[0], u); b[1] = min(b[1], v)
            b[2] = max(b[2], u); b[3] = max(b[3], v)

missing = [g for g in gb if g not in LAYOUT]
if missing:
    raise SystemExit("这些组没有目标矩形: %s" % missing)

# 为每个 (组, 原 vt 索引) 生一个新 vt
new_uvs = []
mapping = {}


def remap(g, idx):
    key = (g, idx)
    if key in mapping:
        return mapping[key]
    u, v = uvs[idx]
    b = gb[g]
    su = 0.0 if b[2] - b[0] < 1e-9 else (u - b[0]) / (b[2] - b[0])
    sv = 0.0 if b[3] - b[1] < 1e-9 else (v - b[1]) / (b[3] - b[1])
    r = LAYOUT[g]
    nu = r[0] + su * (r[2] - r[0])
    nv = r[1] + sv * (r[3] - r[1])
    new_uvs.append((nu, nv))
    mapping[key] = len(new_uvs)
    return len(new_uvs)


# 重建面
new_faces = []
for g, toks in faces:
    out = []
    for tok in toks:
        parts = tok.split("/")
        vi = parts[0]
        if len(parts) > 1 and parts[1]:
            ti = remap(g, int(parts[1]) - 1)
        else:
            ti = ""
        if len(parts) > 2 and parts[2]:
            out.append("%s/%s/%s" % (vi, ti, parts[2]))
        elif ti != "":
            out.append("%s/%s" % (vi, ti))
        else:
            out.append(vi)
    new_faces.append((g, out))

with open(OUT, "w", encoding="utf-8", newline="\n") as f:
    f.write("# SilverStar blade model (UV repacked)\n")
    for v in verts:
        f.write("v %s\n" % v)
    for (u, v) in new_uvs:
        f.write("vt %.6f %.6f\n" % (u, v))
    fi = 0
    for kind, val in order:
        if kind == "g":
            f.write("g %s\n" % val)
        elif kind == "f":
            g, toks = new_faces[val]
            f.write("f %s\n" % " ".join(toks))
        elif kind == "raw":
            if val.startswith("mtllib"):
                continue
            f.write(val + "\n")

print("wrote", OUT, "verts", len(verts), "uvs", len(new_uvs), "faces", len(new_faces))
