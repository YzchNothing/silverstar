"""把湮灭之刃每个 UV 区域按"组"上色，看哪块是谁的。"""
import os
from PIL import Image, ImageDraw

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\终焉藏剑阁"
OBJ = os.path.join(BASE, "annihilation_blade.obj")
SRC = os.path.join(BASE, "annihilation_blade.png")

img = Image.open(SRC).convert("RGBA")
W, H = img.size

COLORS = {
    "blade": (255, 60, 60),
    "blade_damaged": (255, 140, 0),
    "blade_fragment": (255, 220, 0),
    "blade_luminous": (0, 255, 120),
    "handle": (60, 140, 255),
    "sheath": (200, 60, 255),
    "effect": (0, 240, 240),
    "item_blade": (150, 150, 150),
    "item_bladens": (110, 110, 110),
    "item_damaged": (180, 180, 180),
    "item_back": (210, 210, 210),
    "item_blade_luminous": (240, 240, 120),
    "item_bladens_luminous1": (200, 200, 80),
}

verts, uvs, faces = [], [], []
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

overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
draw = ImageDraw.Draw(overlay)
stats = {}
for g, idx in faces:
    col = COLORS.get(g, (255, 255, 255))
    xs = [verts[vi][0] for vi, ti in idx]
    area = 0.0
    pts = [(uvs[ti][0] * W, (1.0 - uvs[ti][1]) * H) for vi, ti in idx if ti >= 0]
    if len(pts) >= 3:
        draw.polygon(pts, fill=col + (120,))
    st = stats.setdefault(g, {"faces": 0, "xmin": 1e9, "xmax": -1e9})
    st["faces"] += 1
    st["xmin"] = min(st["xmin"], min(xs))
    st["xmax"] = max(st["xmax"], max(xs))

out = Image.alpha_composite(img, overlay).convert("RGB")
out.save(os.path.join(BASE, "uv_groups_overlay.png"))
print("%-24s %5s %10s %10s" % ("group", "faces", "xmin", "xmax"))
for g, st in sorted(stats.items()):
    print("%-24s %5d %10.1f %10.1f" % (g, st["faces"], st["xmin"], st["xmax"]))
