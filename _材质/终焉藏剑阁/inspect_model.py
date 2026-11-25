"""看看终焉藏剑阁·湮灭之刃的模型：各组用到的 UV 范围、模型尺寸。"""
import os
from PIL import Image

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\终焉藏剑阁"
OBJ = os.path.join(BASE, "annihilation_blade.obj")
PNG = os.path.join(BASE, "annihilation_blade.png")

img = Image.open(PNG)
W, H = img.size
print("texture", W, "x", H, "mode", img.mode)

verts, uvs = [], []
group = None
data = {}  # group -> {"uv":[], "v":[]}

for line in open(OBJ, encoding="utf-8", errors="ignore"):
    line = line.strip()
    if line.startswith("v "):
        p = line.split()
        verts.append((float(p[1]), float(p[2]), float(p[3])))
    elif line.startswith("vt "):
        p = line.split()
        uvs.append((float(p[1]), float(p[2])))
    elif line.startswith("g "):
        group = line[2:].strip()
        data.setdefault(group, {"uv": [], "v": []})
    elif line.startswith("f "):
        if group is None:
            continue
        for tok in line.split()[1:]:
            parts = tok.split("/")
            if len(parts) >= 2 and parts[1]:
                uvs_i = int(parts[1]) - 1
                data[group]["uv"].append(uvs[uvs_i])
            v_i = int(parts[0]) - 1
            data[group]["v"].append(verts[v_i])

print("groups:", len(data))
print("%-22s %6s %8s %8s  %s" % ("group", "faces", "u px", "v px", "uv bbox"))
for g, d in sorted(data.items()):
    if not d["uv"]:
        continue
    us = [p[0] for p in d["uv"]]
    vs = [p[1] for p in d["uv"]]
    x0, x1 = min(us) * W, max(us) * W
    y0, y1 = (1 - max(vs)) * H, (1 - min(vs)) * H
    print("%-22s %6d  %5.0f-%5.0f  y %4.0f-%4.0f" % (g, len(d["uv"]) // 3, x0, x1, y0, y1))

# 整体模型尺寸（用来判断它有多少格）
xs = [v[0] for v in verts]
ys = [v[1] for v in verts]
zs = [v[2] for v in verts]
print("\nper-group vertex bbox:")
for g, d in sorted(data.items()):
    if not d["v"]:
        continue
    gx = [p[0] for p in d["v"]]
    gy = [p[1] for p in d["v"]]
    gz = [p[2] for p in d["v"]]
    print("%-22s x %7.1f..%7.1f  y %7.1f..%7.1f  z %7.1f..%7.1f" % (
        g, min(gx), max(gx), min(gy), max(gy), min(gz), max(gz)))

print("\nmodel bbox: x %.1f..%.1f  y %.1f..%.1f  z %.1f..%.1f" % (
    min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
print("size: %.1f x %.1f x %.1f" % (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)))
