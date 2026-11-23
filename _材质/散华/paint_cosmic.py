import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# 纯银星：星云/银河风格贴图（参考之前给终焉之刃做的那套观感）
# 分区位置沿用散华那张图（只作位置参考）
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
SRC = os.path.join(BASE, "sange_white.png")
OUT = os.path.join(BASE, "silver_star_cosmic.png")

img = Image.open(SRC).convert("RGBA")
w0, h0 = img.size
S = 4
W, H = w0 * S, h0 * S
alpha = np.array(img.resize((W, H), Image.NEAREST))[:, :, 3]
rng = np.random.default_rng(777001)


def smooth_noise(scale, octaves=1):
    """多层平滑随机噪声，0..1"""
    acc = np.zeros((H, W), dtype=np.float32)
    amp = 1.0
    total = 0.0
    s = scale
    for _ in range(octaves):
        small = rng.random((max(2, int(H / s)), max(2, int(W / s)))).astype(np.float32)
        im = Image.fromarray((small * 255).astype(np.uint8), "L").resize((W, H), Image.BICUBIC)
        acc += np.array(im).astype(np.float32) / 255.0 * amp
        total += amp
        amp *= 0.5
        s *= 0.5
    return acc / total


# ---- 星云：两层不同尺度叠起来 ----
n1 = smooth_noise(90, 4)
n2 = smooth_noise(24, 3)
neb = np.clip(n1 * 0.65 + n2 * 0.55, 0, 1)

# 星云配色：深蓝 -> 靛 -> 青白
c_deep = np.array([0.02, 0.03, 0.08])
c_mid = np.array([0.10, 0.16, 0.36])
c_bright = np.array([0.45, 0.62, 0.95])
c_hot = np.array([0.86, 0.93, 1.00])

t = neb[..., None]
neb_col = np.where(t < 0.45, c_deep + (c_mid - c_deep) * (t / 0.45),
                   np.where(t < 0.80, c_mid + (c_bright - c_mid) * ((t - 0.45) / 0.35),
                            c_bright + (c_hot - c_bright) * np.clip((t - 0.80) / 0.20, 0, 1)))

# ---- 星点 ----
stars = np.zeros((H, W), dtype=np.float32)
for _ in range(2600):
    x = rng.integers(0, W)
    y = rng.integers(0, H)
    stars[y, x] = 1.0
stars = np.array(Image.fromarray((stars * 255).astype(np.uint8), "L")
                 .filter(ImageFilter.GaussianBlur(1.6))).astype(np.float32) / 255.0
core = np.array(Image.fromarray((np.array(Image.fromarray((stars * 255).astype(np.uint8), "L"))
                                 ).astype(np.float32) / 255.0 * 255).convert("L") if False else Image.fromarray((stars * 255).astype(np.uint8), "L")) if False else None

# 稍微亮一点的星核（单独撒一层细微点）
tiny = np.zeros((H, W), dtype=np.float32)
for _ in range(900):
    x = rng.integers(0, W)
    y = rng.integers(0, H)
    tiny[y, x] = 1.0
tiny = np.array(Image.fromarray((tiny * 255).astype(np.uint8), "L")
                .filter(ImageFilter.GaussianBlur(0.6))).astype(np.float32) / 255.0

# ---- 刻线：细长的划痕 ----
scr = Image.new("L", (W, H), 0)
sd = ImageDraw.Draw(scr)
for _ in range(38):
    x0 = rng.integers(-W // 4, W)
    y0 = rng.integers(0, H)
    length = rng.integers(W // 6, W // 2)
    ang = rng.normal(0.9, 0.25)
    x1 = int(x0 + np.cos(ang) * length)
    y1 = int(y0 + np.sin(ang) * length)
    sd.line([(x0, y0), (x1, y1)], fill=int(rng.integers(30, 90)), width=1)
scr = np.array(scr.filter(ImageFilter.GaussianBlur(0.5))).astype(np.float32) / 255.0

base = neb_col.copy()
base = base * 0.78                     # 压暗星云，让星点跳出来
base += stars[..., None] * np.array([0.95, 1.00, 1.00])
base += tiny[..., None] * np.array([1.00, 1.00, 1.00])
base += scr[..., None] * np.array([0.40, 0.46, 0.60])
base = np.clip(base, 0, 1)

# ---------- 分区 ----------
out = np.dstack([base * 255.0, alpha.astype(np.float32)])


def sc(rect):
    x0, y0, x1, y1 = rect
    return (int(x0 * S), int(y0 * S), int(x1 * S), int(y1 * S))


# 以下坐标全部来自 content_boxes.py 的实测（192×384 原图坐标）
R_SHEATH = sc((0, 0, 56, 384))
R_SIDE = sc((56, 0, 69, 384))
R_HANDLE = sc((69, 179, 106, 375))
R_BLADE = sc((131, 179, 170, 375))
R_MOUTH = sc((69, 7, 121, 45))
R_BACK = sc((74, 52, 176, 146))
R_DOTS = sc((73, 147, 93, 173))


def apply(rect, col, mask=True):
    x0, y0, x1, y1 = rect
    keep = (alpha[y0:y1, x0:x1] > 8) if mask else np.ones((y1 - y0, x1 - x0), dtype=bool)
    cur = out[y0:y1, x0:x1]
    cur[keep, :3] = col[keep] * 255.0
    cur[keep, 3] = 255
    out[y0:y1, x0:x1] = cur


def region(rect):
    x0, y0, x1, y1 = rect
    h, w = y1 - y0, x1 - x0
    ly, lx = np.mgrid[0:h, 0:w].astype(np.float32)
    return lx / max(w - 1, 1), ly / max(h - 1, 1), slice(y0, y1), slice(x0, x1)


# 刀鞘：星云压暗，加一条银河带
u, v, sy, sx = region(R_SHEATH)
sheath_col = base[sy, sx] * 0.50
sheath_col = np.clip(sheath_col + np.array([0.02, 0.03, 0.07]), 0, 1)
band = np.exp(-(((u - 0.5) - 0.35 * (v - 0.5)) ** 2) / 0.02)
sheath_col = np.clip(sheath_col + band[..., None] * np.array([0.10, 0.14, 0.24]), 0, 1)
ring = (v < 0.012) | (v > 0.988)
sheath_col[ring] = np.array([0.62, 0.70, 0.82])
apply(R_SHEATH, sheath_col)

# 侧面窄条：更暗
u, v, sy, sx = region(R_SIDE)
apply(R_SIDE, np.clip(base[sy, sx] * 0.35, 0, 1))
# 那两个装饰小点（只被 effect 用到）：压成几乎全黑，避免散华的花纹露出来
u, v, sy, sx = region(R_DOTS)
apply(R_DOTS, np.clip(base[sy, sx] * 0.10, 0, 1))

# 刀柄：星云压暗 + 缠绳
u, v, sy, sx = region(R_HANDLE)
p1 = (u * 2.4 + v * 6.5) % 1.0
p2 = (u * 2.4 - v * 6.5) % 1.0
rope = np.clip(np.clip(np.cos(p1 * 2 * np.pi), 0, 1) ** 4 + np.clip(np.cos(p2 * 2 * np.pi), 0, 1) ** 4, 0, 1)
handle_col = base[sy, sx] * 0.45 + rope[..., None] * np.array([0.20, 0.26, 0.40])
handle_col = np.clip(handle_col, 0, 1)
apply(R_HANDLE, handle_col)

# 刀刃：星云 + 金属高光（刃口一侧亮）
u, v, sy, sx = region(R_BLADE)
mb = base[sy, sx]
metal = np.stack([0.34 + 0.60 * (1 - u)] * 3, axis=-1) * np.array([0.92, 0.96, 1.00])
mix = 0.34
blade_col = np.clip(mb * mix + metal * (1 - mix), 0, 1)
hamon = 0.5 + 0.5 * np.sin(v * 26.0 + 1.0)
blade_col = np.clip(blade_col + (hamon - 0.5)[..., None] * 0.08, 0, 1)
hot = np.exp(-((u - 0.05) ** 2) / 0.0010) * 0.55
blade_col = np.clip(blade_col + hot[..., None] * np.array([0.85, 0.90, 1.00]), 0, 0.995)
spine = np.exp(-((u - 0.94) ** 2) / 0.0020) * 0.35
blade_col = np.clip(blade_col - spine[..., None], 0, 1)
apply(R_BLADE, blade_col)

# 鞘口
u, v, sy, sx = region(R_MOUTH)
apply(R_MOUTH, np.clip(base[sy, sx] * 0.5 + 0.06, 0, 1))

res = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")

# 纹章：带星环的行星
d = ImageDraw.Draw(res, "RGBA")
bx0, by0, bx1, by1 = R_BACK
cx, cy = (bx0 + bx1) / 2.0, (by0 + by1) / 2.0
rad = min(bx1 - bx0, by1 - by0) * 0.30

# 背景：整块夜空
d.rounded_rectangle([bx0, by0, bx1, by1], radius=rad * 0.25, fill=(6, 8, 18, 255))

# 星环：先画整圈（会被行星挡住一半）
ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
rd = ImageDraw.Draw(ring)
rb_out = [cx - rad * 1.80, cy - rad * 0.62, cx + rad * 1.80, cy + rad * 0.62]
rb_in = [cx - rad * 1.56, cy - rad * 0.50, cx + rad * 1.56, cy + rad * 0.50]
rd.arc(rb_out, 0, 360, fill=(205, 222, 250, 235), width=max(2, int(rad * 0.07)))
rd.arc(rb_in, 0, 360, fill=(130, 158, 205, 180), width=max(1, int(rad * 0.04)))
ring = ring.filter(ImageFilter.GaussianBlur(0.8))
res = Image.alpha_composite(res, ring)
d = ImageDraw.Draw(res, "RGBA")

# 行星：多层同心圆做明暗，暗面朝右下
for i in range(int(rad), 0, -1):
    k = i / rad
    lit = max(0.0, 1.0 - 1.15 * k)
    col = (
        int(30 + 165 * lit),
        int(48 + 150 * lit),
        int(92 + 118 * lit),
        255,
    )
    d.ellipse([cx - i, cy - i * 0.98, cx + i, cy + i * 0.98], fill=col)

# 云带（只在上半部分，别盖太死）
for dy in (-0.55, -0.25, 0.10):
    hh = rad * 0.07
    d.ellipse([cx - rad * 0.90, cy + dy * rad - hh, cx + rad * 0.90, cy + dy * rad + hh],
              fill=(200, 214, 245, 55))

# 星环前缘：只画下半圈，压在行星前面
rb_out_f = [cx - rad * 1.80, cy - rad * 0.62, cx + rad * 1.80, cy + rad * 0.62]
rb_in_f = [cx - rad * 1.56, cy - rad * 0.50, cx + rad * 1.56, cy + rad * 0.50]
d = ImageDraw.Draw(res, "RGBA")
d.arc(rb_out_f, 8, 172, fill=(214, 230, 255, 245), width=max(2, int(rad * 0.07)))
d.arc(rb_in_f, 8, 172, fill=(140, 168, 215, 200), width=max(1, int(rad * 0.04)))

res = res.filter(ImageFilter.SMOOTH)
res2 = res.resize((w0, h0), Image.LANCZOS)
res2.save(OUT)

big = res2.resize((res2.width * 4, res2.height * 4), Image.NEAREST)
flat = Image.new("RGBA", big.size, (10, 12, 20, 255))
Image.alpha_composite(flat, big).convert("RGB").save(os.path.join(BASE, "preview_cosmic.png"))
print("texture ->", OUT, res2.size)
