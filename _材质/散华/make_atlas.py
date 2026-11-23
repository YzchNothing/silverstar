import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# 按 remap_uv.py 的图集布局，从零画一张 512x512 的贴图
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
OUT = os.path.join(BASE, "silver_star_atlas.png")

W = H = 512
rng = np.random.default_rng(20261123)

# 与 remap_uv.py 保持一致（v 向上，画图时 y 要翻转）
LAYOUT = {
    "blade":          (0.00, 0.38, 0.44, 1.00),
    "sheath":         (0.00, 0.00, 0.44, 0.36),
    "handle":         (0.46, 0.38, 0.66, 1.00),
    "effect":         (0.68, 0.70, 1.00, 1.00),
    "item":           (0.68, 0.36, 1.00, 0.68),
    "item_back":      (0.46, 0.00, 0.66, 0.36),
}


def rect(name):
    u0, v0, u1, v1 = LAYOUT[name]
    return (int(u0 * W), int((1 - v1) * H), int(u1 * W), int((1 - v0) * H))


img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = np.zeros((H, W, 4), dtype=np.float32)
px[:, :, 3] = 0

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

# ---- 拉丝噪声 ----
noise = rng.normal(0.0, 1.0, (H, W)).astype(np.float32)
streak = np.array(Image.fromarray(((noise * 0.5 + 0.5) * 255).clip(0, 255).astype(np.uint8), "L")
                  .filter(ImageFilter.GaussianBlur(radius=(9, 2)))).astype(np.float32) / 255.0
streak = (streak - 0.5) * 2.0

STOPS = [(0.00, (0.05, 0.07, 0.12)), (0.22, (0.20, 0.25, 0.34)),
         (0.55, (0.60, 0.66, 0.75)), (0.80, (0.85, 0.89, 0.94)),
         (1.00, (1.00, 1.00, 1.00))]


def ramp(t):
    out = np.zeros((len(t), 3), dtype=np.float32)
    for i in range(len(STOPS) - 1):
        t0, c0 = STOPS[i]
        t1, c1 = STOPS[i + 1]
        m = (t >= t0) & (t <= t1)
        k = (t[m] - t0) / max(t1 - t0, 1e-6)
        out[m] = np.array(c0) + (np.array(c1) - np.array(c0)) * k[:, None]
    out[t > 1.0] = STOPS[-1][1]
    return out


def fill(name, color_fn):
    x0, y0, x1, y1 = rect(name)
    sub_y, sub_x = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    # 局部坐标 0..1
    lu = (sub_x - x0) / max(x1 - x0 - 1, 1)
    lv = (sub_y - y0) / max(y1 - y0 - 1, 1)
    col = color_fn(lu, lv, sub_y.astype(int), sub_x.astype(int))
    px[y0:y1, x0:x1, :3] = col * 255.0
    px[y0:y1, x0:x1, 3] = 255


# ---- 刀身：横向（u）是刃口->刀脊，纵向（v）是刀长 ----
def blade_col(lu, lv, y, x):
    # 刃口在 u 小的一侧
    edge = 1.0 - lu
    hamon = 0.5 + 0.28 * np.sin(lv * 22.0 + 0.7) + 0.18 * np.sin(lv * 57.0 + 2.1)
    t = np.clip(0.18 + 0.66 * edge + 0.10 * streak[y, x] + 0.06 * (hamon - 0.5), 0, 1)
    col = ramp(t.ravel()).reshape(lu.shape + (3,))
    # 刃口高光
    glow = np.exp(-((lu - 0.06) ** 2) / 0.0012)
    col = np.clip(col + glow[..., None] * np.array([0.75, 0.82, 0.90]), 0, 1)
    # 刀脊暗线
    spine = np.exp(-((lu - 0.90) ** 2) / 0.0008)
    col = np.clip(col - spine[..., None] * np.array([0.22, 0.22, 0.20]), 0, 1)
    # 星点
    s = (rng.random(lu.shape) > 0.9978).astype(np.float32)
    col = np.clip(col + s[..., None] * np.array([0.55, 0.80, 1.00]), 0, 1)
    return col


# ---- 刀鞘：深蓝黑漆 + 一道斜高光 ----
def sheath_col(lu, lv, y, x):
    sheen = np.exp(-((lu - 0.28 - 0.12 * np.sin(lv * 3.0)) ** 2) / 0.010) * 0.55
    col = np.stack([
        0.035 + sheen * 0.30,
        0.055 + sheen * 0.36,
        0.110 + sheen * 0.48,
    ], axis=-1)
    s = (rng.random(lu.shape) > 0.9985).astype(np.float32)
    col = np.clip(col + s[..., None] * np.array([0.45, 0.70, 1.00]), 0, 1)
    return col


# ---- 刀柄：深藏青 + 菱形缠绳 ----
def handle_col(lu, lv, y, x):
    phase = (lu * 3.0 + lv * 9.0) % 1.0
    rope = np.clip(np.cos(phase * 2 * np.pi) * 0.5 + 0.5, 0, 1) ** 1.4
    edge = np.minimum(lu, 1 - lu) * 2.0
    col = np.stack([
        0.04 + 0.20 * rope,
        0.06 + 0.25 * rope,
        0.12 + 0.34 * rope,
    ], axis=-1)
    col = col * (0.75 + 0.45 * np.clip(edge, 0, 1))[..., None]
    col = col * (0.9 + 0.2 * (streak[y, x] * 0.5 + 0.5))[..., None]
    return np.clip(col, 0, 1)


# ---- effect：暗冷色，不抢戏 ----
def effect_col(lu, lv, y, x):
    t = np.clip(0.10 + 0.25 * (1 - lv), 0, 1)
    col = ramp(t.ravel()).reshape(lu.shape + (3,)) * 0.5
    return np.clip(col, 0, 1)


# ---- 图标：和刀身同款金属 ----
def icon_col(lu, lv, y, x):
    return blade_col(lu, lv, y, x) * 0.98


# ---- 纹章：菱形银框 + 深蓝底 + 四芒星 ----
def item_back_col(lu, lv, y, x):
    col = np.zeros(lu.shape + (3,), dtype=np.float32)
    col[:] = np.array([0.03, 0.05, 0.10])
    cx, cy = 0.5, 0.5
    d = np.abs(lu - cx) + np.abs(lv - cy)
    rim = (d > 0.34) & (d < 0.42)
    col[rim] = np.array([0.72, 0.78, 0.88])
    inner = d <= 0.34
    col[inner] = np.array([0.06, 0.09, 0.17])
    # 四芒星
    dx = np.abs(lu - cx) * 1.55
    dy = np.abs(lv - cy)
    star = (np.minimum(dx, dy) * 0.35 + np.maximum(dx, dy) * 0.0) < 0.16
    star = ((np.abs(lu - cx) * 0.62 + np.abs(lv - cy)) < 0.30) & ((np.abs(lu - cx) + np.abs(lv - cy) * 0.62) < 0.30)
    col[star & inner] = np.array([0.90, 0.95, 1.00])
    core = ((lu - cx) ** 2 + (lv - cy) ** 2) < 0.004
    col[core] = np.array([0.45, 0.80, 1.00])
    s = (rng.random(lu.shape) > 0.9965).astype(np.float32)
    col = np.clip(col + s[..., None] * np.array([0.4, 0.7, 1.0]), 0, 1)
    return col


fill("blade", blade_col)
fill("sheath", sheath_col)
fill("handle", handle_col)
fill("effect", effect_col)
fill("item", icon_col)
fill("item_back", item_back_col)

img = Image.fromarray(np.clip(px, 0, 255).astype(np.uint8), "RGBA").filter(ImageFilter.SMOOTH)
img.save(OUT)
print("atlas ->", OUT, img.size)

flat = Image.new("RGBA", img.size, (14, 16, 26, 255))
Image.alpha_composite(flat, img).convert("RGB").save(os.path.join(BASE, "preview_atlas.png"))
print("preview ->", os.path.join(BASE, "preview_atlas.png"))
