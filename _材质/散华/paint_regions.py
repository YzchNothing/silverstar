import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# 纯银星贴图：分区位置沿用"白狐/散华"那张图的分区关系（只是位置参考），
# 画面内容全部自己画——银白金属刀刃、深蓝黑刀鞘带星轨、缠绳刀柄、四芒星纹章。
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
SRC = os.path.join(BASE, "sange_white.png")
OUT = os.path.join(BASE, "silver_star_paint.png")

img = Image.open(SRC).convert("RGBA")
w0, h0 = img.size
S = 2
arr = np.array(img.resize((w0 * S, h0 * S), Image.NEAREST)).astype(np.float32)
H, W = arr.shape[:2]
alpha = arr[:, :, 3]
rng = np.random.default_rng(90210)


def sc(rect):
    """把 192x384 下的坐标换算到当前尺寸"""
    x0, y0, x1, y1 = rect
    return (int(x0 * S), int(y0 * S), int(x1 * S), int(y1 * S))


R_SHEATH = sc((0, 0, 53, 384))
R_SIDE = sc((53, 0, 79, 300))
R_BOTTOM = sc((53, 300, 79, 384))
R_HANDLE = sc((79, 172, 130, 384))
R_BLADE = sc((143, 170, 192, 384))
R_MOUTH = sc((66, 2, 124, 50))
R_BACK = sc((78, 52, 178, 178))

# 拉丝：横向高频、纵向低频
noise = rng.normal(0.0, 1.0, (H, W)).astype(np.float32)
streak = np.array(Image.fromarray(((noise * 0.5 + 0.5) * 255).clip(0, 255).astype(np.uint8), "L")
                  .filter(ImageFilter.GaussianBlur(radius=(0.6, 6)))).astype(np.float32) / 255.0
streak = (streak - 0.5) * 2.0

out = arr.copy()


def patch(rect, fn):
    x0, y0, x1, y1 = rect
    h, w = y1 - y0, x1 - x0
    ly, lx = np.mgrid[0:h, 0:w].astype(np.float32)
    u = lx / max(w - 1, 1)
    v = ly / max(h - 1, 1)
    sy = np.clip(ly + y0, 0, H - 1).astype(int)
    sx = np.clip(lx + x0, 0, W - 1).astype(int)
    col = fn(u, v, sy, sx)
    keep = alpha[y0:y1, x0:x1] > 8
    cur = out[y0:y1, x0:x1]
    cur[keep, :3] = col[keep] * 255.0
    cur[keep, 3] = 255
    out[y0:y1, x0:x1] = cur


# ---------- 刀刃：银白，刃口一道细高光，刃文把它们分开 ----------
def blade(u, v, sy, sx):
    # 刃口朝 u 小的一侧
    hamon_line = 0.30 + 0.055 * np.sin(v * 30.0 + 1.2) + 0.035 * np.sin(v * 71.0 + 0.4)
    edge_side = u < hamon_line

    t = np.where(edge_side, 0.93 - 0.10 * (u / np.maximum(hamon_line, 1e-3)),
                 0.72 - 0.42 * np.clip((u - hamon_line) / (1 - hamon_line), 0, 1))
    t = np.clip(t + streak[sy, sx] * 0.035, 0, 1)

    # 冷调银白
    col = np.stack([t * 0.96 + 0.03, t * 0.975 + 0.035, t * 1.00 + 0.05], axis=-1)

    # 刃口最外缘的细高光（不到纯白，避免塑料感）
    hot = np.exp(-((u - 0.035) ** 2) / 0.00035) * 0.55
    col = col + hot[..., None] * np.array([0.55, 0.60, 0.68])

    # 刃文线本身：一条略暗的分界
    line = np.exp(-((u - hamon_line) ** 2) / 0.00025) * 0.22
    col = col - line[..., None]

    # 刀脊
    spine = np.exp(-((u - 0.96) ** 2) / 0.0012) * 0.30
    col = col - spine[..., None]

    # 切先（v 大的一端）微微提亮
    tip = np.clip((v - 0.88) / 0.12, 0, 1) * 0.12
    col = col + tip[..., None]

    # 极稀疏的星屑，只在暗侧
    star = (rng.random(u.shape) > 0.9990).astype(np.float32) * (~edge_side)
    col = col + star[..., None] * np.array([0.35, 0.55, 0.85])

    return np.clip(col, 0, 0.985)


# ---------- 刀鞘：深蓝黑漆 + 一条斜向星轨 + 首尾银箍 ----------
def sheath(u, v, sy, sx):
    sheen = np.exp(-((u - 0.34 - 0.05 * np.sin(v * 4.0)) ** 2) / 0.020) * 0.30
    col = np.stack([
        0.020 + sheen * 0.16,
        0.030 + sheen * 0.21,
        0.062 + sheen * 0.32,
    ], axis=-1)

    # 斜向星轨：一条细线 + 沿线的星点
    trail = u - (0.62 - 0.42 * v)
    on_trail = np.exp(-(trail ** 2) / 0.00035)
    col = col + on_trail[..., None] * np.array([0.05, 0.07, 0.12])
    dots = (rng.random(u.shape) > 0.9940).astype(np.float32) * on_trail
    col = col + dots[..., None] * np.array([0.55, 0.78, 1.00])

    # 零星散布
    scat = (rng.random(u.shape) > 0.9982).astype(np.float32)
    col = col + scat[..., None] * np.array([0.30, 0.48, 0.75])

    # 首尾银箍
    ring = (v < 0.018) | (v > 0.982)
    col[ring] = np.array([0.55, 0.62, 0.74])
    ring2 = ((v > 0.026) & (v < 0.032)) | ((v > 0.968) & (v < 0.974))
    col[ring2] = np.array([0.30, 0.35, 0.45])

    return np.clip(col, 0, 1)


def side(u, v, sy, sx):
    col = np.zeros(u.shape + (3,), dtype=np.float32)
    col[:] = np.array([0.016, 0.022, 0.048])
    # 一条极淡的反光，让侧面不是死黑
    col += np.exp(-((u - 0.35) ** 2) / 0.05)[..., None] * np.array([0.02, 0.03, 0.05])
    return col


def bottom(u, v, sy, sx):
    col = np.zeros(u.shape + (3,), dtype=np.float32)
    col[:] = np.array([0.024, 0.032, 0.062])
    ring = np.abs(v - 0.30) < 0.045
    col[ring] = np.array([0.50, 0.57, 0.70])
    return col


# ---------- 刀柄：深藏青 + 交叉缠绳 + 绳脊高光 ----------
def handle(u, v, sy, sx):
    p1 = (u * 2.4 + v * 6.5) % 1.0
    p2 = (u * 2.4 - v * 6.5) % 1.0
    c1 = np.clip(np.cos(p1 * 2 * np.pi), 0, 1)
    c2 = np.clip(np.cos(p2 * 2 * np.pi), 0, 1)
    rope = np.clip(c1 ** 4 + c2 ** 4, 0, 1)
    ridge = np.clip((c1 ** 12 + c2 ** 12), 0, 1)

    col = np.stack([
        0.040 + 0.26 * rope,
        0.052 + 0.31 * rope,
        0.100 + 0.40 * rope,
    ], axis=-1)
    col = col + ridge[..., None] * np.array([0.16, 0.19, 0.24])

    edge = np.clip(np.minimum(u, 1 - u) * 5.0, 0, 1)
    col *= (0.68 + 0.46 * edge)[..., None]
    col += (streak[sy, sx] * 0.02)[..., None]

    # 绳结缝里的暗线
    gap = np.clip((rope * 6.0) - 5.0, 0, 1) * 0.25
    col = col * (1 - gap[..., None])

    return np.clip(col, 0, 1)


def mouth(u, v, sy, sx):
    k = np.exp(-((u - 0.5) ** 2) / 0.16)
    return np.stack([
        0.075 + 0.13 * k,
        0.095 + 0.15 * k,
        0.150 + 0.19 * k,
    ], axis=-1)


patch(R_SHEATH, sheath)
patch(R_SIDE, side)
patch(R_BOTTOM, bottom)
patch(R_HANDLE, handle)
patch(R_BLADE, blade)
patch(R_MOUTH, mouth)

res2 = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")

# ---------- 纹章：菱形银框 + 四芒星 ----------
bx0, by0, bx1, by1 = R_BACK
tmp = np.array(res2)
region = tmp[by0:by1, bx0:bx1]
m = region[:, :, 3] > 8
region[m, :3] = np.array([9, 13, 26], dtype=np.uint8)
region[m, 3] = 255
tmp[by0:by1, bx0:bx1] = region
res2 = Image.fromarray(tmp, "RGBA")

d = ImageDraw.Draw(res2)
cx, cy = (bx0 + bx1) / 2.0, (by0 + by1) / 2.0
half = min(bx1 - bx0, by1 - by0) / 2.0

# 外框（双层）
d.polygon([(cx, cy - half), (cx + half, cy), (cx, cy + half), (cx - half, cy)],
          fill=(64, 72, 88, 255))
inr = half * 0.86
d.polygon([(cx, cy - inr), (cx + inr, cy), (cx, cy + inr), (cx - inr, cy)],
          fill=(214, 226, 240, 255))
inr2 = half * 0.76
d.polygon([(cx, cy - inr2), (cx + inr2, cy), (cx, cy + inr2), (cx - inr2, cy)],
          fill=(8, 12, 24, 255))

# 四芒星（细长）
sp = half * 0.60
wsp = 0.12
d.polygon([(cx, cy - sp), (cx + sp * wsp, cy - sp * 0.10), (cx + sp, cy),
           (cx + sp * wsp, cy + sp * 0.10), (cx, cy + sp),
           (cx - sp * wsp, cy + sp * 0.10), (cx - sp, cy),
           (cx - sp * wsp, cy - sp * 0.10)], fill=(232, 243, 255, 255))
d.ellipse([cx - half * 0.10, cy - half * 0.10, cx + half * 0.10, cy + half * 0.10],
          fill=(98, 192, 255, 255))

res2 = res2.filter(ImageFilter.SMOOTH)
res2 = res2.resize((w0, h0), Image.LANCZOS)
res2.save(OUT)

big = res2.resize((res2.width * 4, res2.height * 4), Image.NEAREST)
flat = Image.new("RGBA", big.size, (14, 16, 26, 255))
Image.alpha_composite(flat, big).convert("RGB").save(os.path.join(BASE, "preview_paint.png"))
print("texture ->", OUT, res2.size)
