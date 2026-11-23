import os

from PIL import Image, ImageDraw

# 把 10 组 UV 各画一张纯色图，拼成一张对照表
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
MASKS = os.path.join(BASE, "uv_masks")

COLORS = {
    "blade":          (235, 40, 40),
    "blade_damaged":  (255, 140, 20),
    "blade_fragment": (245, 225, 40),
    "handle":         (40, 205, 70),
    "sheath":         (30, 140, 255),
    "effect":         (140, 70, 230),
    "item_blade":     (255, 70, 190),
    "item_bladens":   (0, 210, 190),
    "item_damaged":   (185, 170, 80),
    "item_back":      (245, 245, 245),
}

ORDER = ["blade", "blade_damaged", "blade_fragment", "handle", "sheath",
         "effect", "item_blade", "item_bladens", "item_damaged", "item_back"]

SCALE = 0.42
TW = TH = None
tiles = []
for name in ORDER:
    p = os.path.join(MASKS, name + ".png")
    im = Image.open(p).convert("L")
    if TW is None:
        TW = int(im.width * SCALE)
        TH = int(im.height * SCALE)
    im = im.resize((TW, TH), Image.NEAREST)
    tile = Image.new("RGB", (TW, TH + 18), (14, 16, 26))
    solid = Image.new("RGB", (TW, TH), (10, 12, 20))
    solid.paste(Image.new("RGB", (TW, TH), COLORS[name]), (0, 0), im)
    tile.paste(solid, (0, 18))
    d = ImageDraw.Draw(tile)
    d.text((4, 3), name, fill=COLORS[name])
    tiles.append(tile)

cols = 2
rows = (len(tiles) + cols - 1) // cols
sheet = Image.new("RGB", (cols * (TW + 8) + 8, rows * (TH + 18 + 8) + 8), (14, 16, 26))
for i, t in enumerate(tiles):
    x = 8 + (i % cols) * (TW + 8)
    y = 8 + (i // cols) * (TH + 18 + 8)
    sheet.paste(t, (x, y))

out = os.path.join(BASE, "uv_groups_sheet.png")
sheet.save(out)
print("saved", out, sheet.size)
