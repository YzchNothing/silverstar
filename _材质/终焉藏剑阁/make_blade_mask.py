"""给湮灭之刃生成「刀刃专用」的两张贴图。

它和散华不一样：散华的刀刃在贴图上是一整块矩形，切一刀就行；
这把刀的 UV 是散开的，只能按三角形逐个描出来。

产出两张：
  annihilation_body.png  原贴图，但刀刃那些三角形覆盖的像素 alpha 清零（第一遍画刀柄、护手）
  annihilation_blade.png 只有刀刃那些三角形的 alpha（第二遍当星空的形状遮罩）
"""
import os
from PIL import Image, ImageDraw, ImageFilter

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\终焉藏剑阁"
OBJ = os.path.join(BASE, "annihilation_blade.obj")
SRC = os.path.join(BASE, "annihilation_blade.png")

img = Image.open(SRC).convert("RGBA")
W, H = img.size

verts, uvs = [], []
faces = []  # (group, [(vi, ti), ...])
group = None
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
    elif line.startswith("f "):
        idx = []
        for tok in line.split()[1:]:
            parts = tok.split("/")
            vi = int(parts[0]) - 1
            ti = int(parts[1]) - 1 if len(parts) > 1 and parts[1] else -1
            idx.append((vi, ti))
        faces.append((group, idx))

# 握柄的 x 范围用来当分界：比它更靠刀尖的都算"刃"
handle_x = [verts[vi][0] for g, idx in faces if g == "handle" for vi, ti in idx]
BOUND = min(handle_x)
print("handle x min = %.2f  -> 刀刃 = x <= %.2f" % (BOUND, BOUND))

blade_faces = []
for g, idx in faces:
    if g not in ("blade", "blade_damaged", "blade_fragment"):
        continue
    xs = [verts[vi][0] for vi, ti in idx]
    if sum(xs) / len(xs) <= BOUND + 0.01:   # 用面的中心判，刃根那一圈的面才会有跨界的
        blade_faces.append(idx)
print("blade faces picked: %d" % len(blade_faces))

mask = Image.new("L", (W, H), 0)
draw = ImageDraw.Draw(mask)
for idx in blade_faces:
    pts = [(uvs[ti][0] * W, (1.0 - uvs[ti][1]) * H) for vi, ti in idx if ti >= 0]
    if len(pts) >= 3:
        draw.polygon(pts, fill=255)

# 边缘稍微外扩一点，免得星星遮罩比模型小一圈出现硬边
mask = mask.filter(ImageFilter.MaxFilter(3))

# 1) 遮罩贴图：白底 + alpha
alpha = mask.point(lambda p: 255 if p > 127 else 0)
blade_img = Image.new("RGBA", (W, H), (255, 255, 255, 0))
blade_img.putalpha(alpha)
blade_img.save(os.path.join(BASE, "annihilation_blade_mask.png"))

# 2) 身体贴图：把遮罩覆盖的地方挖掉
body_pixels = img.copy()
body_alpha = body_pixels.getchannel("A")
body_alpha = Image.composite(Image.new("L", (W, H), 0), body_alpha, alpha)
body_pixels.putalpha(body_alpha)
body_pixels.save(os.path.join(BASE, "annihilation_body.png"))

print("mask px = %d (%.1f%%)" % (
    sum(1 for p in alpha.getdata() if p > 0),
    100.0 * sum(1 for p in alpha.getdata() if p > 0) / (W * H)))

# 预览：原图 / 遮罩 / 挖空
preview = Image.new("RGB", (W * 3 + 20, H), (10, 12, 20))
for k, im in enumerate([img, blade_img, body_pixels]):
    bg = Image.new("RGBA", (W, H), (10, 12, 20, 255))
    preview.paste(Image.alpha_composite(bg, im).convert("RGB"), (k * (W + 10), 0))
preview.save(os.path.join(BASE, "preview_masks.png"))
for k, name in enumerate(["annihilation_blade_mask.png", "annihilation_body.png"]):
    print(name, os.path.getsize(os.path.join(BASE, name)), "bytes")
