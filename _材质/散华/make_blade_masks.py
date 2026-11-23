import os

import numpy as np
from PIL import Image

# 把刀贴图拆成两张：
#   silver_star_body.png  —— 除了刀刃那条，其它全保留（刀柄、护手、刀鞘、鞘上纹章都从这张出）
#   silver_star_blade.png —— 只有刀刃那条有像素，其它全透明（星空遮罩只认 alpha）
BASE = r"D:\GoldenStar\mod构建\SilverStar"
SRC = os.path.join(BASE, "src", "main", "resources", "assets", "silverstar", "model", "silver_star.png")
BODY = os.path.join(BASE, "src", "main", "resources", "assets", "silverstar", "model", "silver_star_body.png")
BLADE = os.path.join(BASE, "src", "main", "resources", "assets", "silverstar", "model", "silver_star_blade.png")

# 实测的刀刃区域（192x384）
X0, Y0, X1, Y1 = 131, 179, 170, 375

img = Image.open(SRC).convert("RGBA")
W, H = img.size
assert (W, H) == (192, 384), (W, H)

arr = np.array(img)
body = arr.copy()
blade = arr.copy()

body[Y0:Y1, X0:X1, 3] = 0

mask = np.zeros((H, W), dtype=bool)
mask[Y0:Y1, X0:X1] = True
blade[~mask, 3] = 0

Image.fromarray(body, "RGBA").save(BODY)
Image.fromarray(blade, "RGBA").save(BLADE)

print("rect px:", int(mask.sum()))
print("body  ->", BODY, os.path.getsize(BODY))
print("blade ->", BLADE, os.path.getsize(BLADE))
