"""把银白贴图 + 刀刃遮罩，派生出测试刀用的三张图，并压成小体积。

输入（都在 _材质\\终焉藏剑阁 或 资源目录里已有）：
  annihilation_blade_silver_D.png   —— 银白版原图（本次重新出的一版）
  annihilation_blade_mask.png       —— 刀刃 UV 遮罩（白=刀刃）

输出（直接覆盖进 src\\main\\resources\\assets\\silverstar\\model\\）：
  test_annihilation_silver.png  银白原图（量化到 64 色）
  test_annihilation_body.png    银白原图，刀刃区 alpha 清零（量化）
  test_annihilation_mask.png    遮罩，原样
"""
import os

import numpy as np
from PIL import Image

BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\终焉藏剑阁"
RES = r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\model"

silver = Image.open(os.path.join(BASE, "annihilation_blade_silver_D.png")).convert("RGBA")
mask = Image.open(os.path.join(BASE, "annihilation_blade_mask.png")).convert("RGBA")

ma = np.array(mask)
blade_zone = ma[..., 3] > 8          # 白色遮罩：有 alpha 的地方就是刀刃

sa = np.array(silver)
sa_body = sa.copy()
sa_body[blade_zone, 3] = 0           # 刀刃那块挖空


def quantised(arr, path):
    im = Image.fromarray(arr, "RGBA")
    q = im.quantize(colors=64, method=Image.FASTOCTREE)
    q.save(path, optimize=True)
    print(os.path.basename(path), os.path.getsize(path), "bytes")


quantised(sa, os.path.join(RES, "test_annihilation_silver.png"))
quantised(sa_body, os.path.join(RES, "test_annihilation_body.png"))
mask.save(os.path.join(RES, "test_annihilation_mask.png"), optimize=True)
print("mask", os.path.getsize(os.path.join(RES, "test_annihilation_mask.png")), "bytes")

# 预览：银白 / body / 遮罩 三联（黑底）
W, H = silver.size
prev = Image.new("RGB", (W * 3 + 20, H), (10, 12, 18))
for i, im in enumerate([silver, Image.fromarray(sa_body, "RGBA"), mask]):
    bg = Image.new("RGBA", (W, H), (10, 12, 18, 255))
    prev.paste(Image.alpha_composite(bg, im).convert("RGB"), (i * (W + 10), 0))
prev.resize((int((W * 3 + 20) * 0.42), int(H * 0.42)), Image.LANCZOS).save(
    os.path.join(BASE, "preview_testblade.png"))
print("preview ->", os.path.join(BASE, "preview_testblade.png"))
