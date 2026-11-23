import os

import numpy as np
from PIL import Image

# 回到「按长宽比裁」之前那版：直接取一块 78x392 缩成 39x196。
# 星点更清楚，代价是贴到刀刃上会被横向拉开 3.44 倍（拔刀剑这块 UV 本身的比例限制）。
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
MODEL = r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\model"
SHOT = r"C:\Users\Administrator\.dsh\attachments\v1\objects\8f\8fe785e935ece611cc8086fb4ce4ab48378e72d38ebbc721509406b7849d6626"

X0, Y0, X1, Y1 = 131, 179, 170, 375
BW, BH = X1 - X0, Y1 - Y0              # 39 x 196

src = Image.open(SHOT).convert("RGB")
cw, ch = BW * 2, BH * 2                 # 78 x 392
cx, cy = 168, 404
crop = src.crop((cx, cy, cx + cw, cy + ch)).resize((BW, BH), Image.LANCZOS)
print("crop", (cw, ch), "at", (cx, cy), "->", (BW, BH))

base = Image.open(os.path.join(MODEL, "silver_star.png")).convert("RGBA")
arr = np.array(base)
arr[Y0:Y1, X0:X1, :3] = np.array(crop)
arr[Y0:Y1, X0:X1, 3] = 255
full = Image.fromarray(arr, "RGBA")
full.save(os.path.join(MODEL, "silver_star.png"))

body = np.array(full).copy()
body[Y0:Y1, X0:X1, 3] = 0
Image.fromarray(body, "RGBA").save(os.path.join(MODEL, "silver_star_body.png"))

blade = np.array(full).copy()
blade[:, :, 3] = 0
blade[Y0:Y1, X0:X1, 3] = 255
Image.fromarray(blade, "RGBA").save(os.path.join(MODEL, "silver_star_blade.png"))

band = full.crop((X0, Y0, X1, Y1))
band.resize((BW * 6, BH * 3), Image.NEAREST).save(os.path.join(BASE, "zoom_blade.png"))

prev = Image.new("RGBA", full.size, (12, 14, 22, 255))
prev.alpha_composite(full)
prev.convert("RGB").resize((full.width * 2, full.height * 2), Image.NEAREST).save(
    os.path.join(BASE, "preview_baked.png"))
print("done: 回到 78x392 -> 39x196 那版")
