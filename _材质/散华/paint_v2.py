import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

# 纯银星贴图 v2
#   刀鞘 / 刀柄 / 柄头：银色拉丝金属（噪声沿纵向模糊）+ 冷色渐变
#   刀刃：蓝色
#   纹章：保留原图灰色边框，只重画内菱形 —— 夜空 + 带环行星
BASE = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华"
SRC = os.path.join(BASE, "sange_white.png")
OUT = os.path.join(BASE, "silver_star_v2.png")

img = Image.open(SRC).convert("RGBA")
w0, h0 = img.size          # 192 x 384
S = 6                      # 内部 6 倍渲染
W, H = w0 * S, h0 * S
src = np.array(img.resize((W, H), Image.NEAREST))
alpha = src[:, :, 3].astype(np.float32) / 255.0
orig = src[:, :, :3].astype(np.float32) / 255.0
rng = np.random.default_rng(20260112)


def sc(rect):
    x0, y0, x1, y1 = rect
    return (int(x0 * S), int(y0 * S), int(x1 * S), int(y1 * S))


# 实测分区（192x384 原图坐标）
R_SHEATH = sc((0, 0, 56, 384))
R_HANDLE = sc((69, 179, 106, 375))
R_BLADE = sc((131, 179, 170, 375))
R_MOUTH = sc((69, 7, 121, 45))
R_EMBLEM = sc((74, 52, 176, 146))     # 含灰框，整体先还原成原图
CX, CY, R_IN = 128.5 * S, 98.5 * S, 39.5 * S   # 内菱形（曼哈顿距离半径）

out = np.dstack([orig.copy(), alpha])


def blur_y(a, k):
    """只沿纵向做均值模糊（横向不动），用来把条纹拉成拉丝"""
    if k <= 1:
        return a
    pad = k // 2
    ap = np.pad(a, ((pad, pad), (0, 0)), mode="edge")
    cs = np.cumsum(ap, axis=0, dtype=np.float64)
    cs = np.vstack([np.zeros((1, a.shape[1])), cs])
    return ((cs[k:] - cs[:-k]) / k).astype(np.float32)


def brushed(h, w, seed, grain=0.0, stripe=8):
    """纵向拉丝：按列生成条纹 → 只沿纵向糊开；grain>0 再叠金属颗粒"""
    r = np.random.default_rng(seed)
    # 条纹：低横向分辨率插值上来，条宽可控
    cw = max(4, w // stripe)
    col = (r.random(cw).astype(np.float32) * 255).astype(np.uint8)
    col_im = Image.fromarray(np.tile(col, (2, 1)), "L").resize((w, h), Image.BICUBIC)
    streak = np.array(col_im).astype(np.float32) / 255.0
    # 纵向低频起伏：让条纹有明暗变化，不是死板的竖线
    vert = r.random((max(2, h // 60), w)).astype(np.float32)
    vert = np.array(Image.fromarray((vert * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)) / 255.0
    tex = streak * 0.74 + vert * 0.26
    tex = blur_y(tex, max(3, h // 90))
    t = (tex - tex.min()) / max(tex.max() - tex.min(), 1e-6)
    if grain > 0:
        # 金属颗粒：噪声先生成扁的再纵向拉长，呈"被磨出来的竖纹颗粒"
        g = r.random((max(2, h // 12), w)).astype(np.float32)
        g = np.array(Image.fromarray((g * 255).astype(np.uint8), "L")
                     .resize((w, h), Image.BICUBIC)).astype(np.float32) / 255.0
        g = (g - g.mean()) / max(g.std(), 1e-6)
        t = np.clip(t + grain * g * 0.35, 0, 1)
    return t


def cool_gradient(h, w, light, dark, axis="u"):
    lx = np.linspace(0.0, 1.0, w, dtype=np.float32)[None, :]
    ly = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
    t = lx if axis == "u" else ly
    light = np.array(light, dtype=np.float32)
    dark = np.array(dark, dtype=np.float32)
    return light[None, None, :] * (1 - t[..., None]) + dark[None, None, :] * t[..., None]


def put(rect, col):
    x0, y0, x1, y1 = rect
    keep = alpha[y0:y1, x0:x1] > 0.03
    cur = out[y0:y1, x0:x1]
    cur[keep, :3] = col[keep]
    cur[keep, 3] = 1.0
    out[y0:y1, x0:x1] = cur


def lum(col):
    return col.mean(axis=-1, keepdims=True)


# ---------- 刀鞘：银色底 + 中下段横向拉丝 ----------
x0, y0, x1, y1 = R_SHEATH
h, w = y1 - y0, x1 - x0
r_sheath = np.random.default_rng(11)

# 1) 底色：冷色渐变 + 极轻的细腻噪声（不放大条纹，保持干净）
grad = cool_gradient(h, w, (0.91, 0.94, 0.99), (0.50, 0.55, 0.65))
fine = r_sheath.random((max(2, h // 8), max(2, w // 8))).astype(np.float32)
fine = np.array(Image.fromarray((fine * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)) / 255.0
fine = (fine - fine.mean()) / max(fine.std(), 1e-6)
col = np.clip(grad * (1.0 + 0.05 * fine[..., None]), 0, 1)

# 2) 中下段：横向拉丝（每行一条，横向延伸），上下边缘柔和过渡
band_top, band_bot, fade = 0.46, 0.95, 0.03
vnorm = np.linspace(0.0, 1.0, h, dtype=np.float32)[:, None]
band_mask = np.clip((vnorm - band_top) / fade, 0, 1) * np.clip((band_bot - vnorm) / fade, 0, 1)

hr = r_sheath.random((max(4, h // 6), 1)).astype(np.float32)
hstripe = np.array(Image.fromarray((hr * 255).astype(np.uint8), "L").resize((w, h), Image.BICUBIC)) / 255.0
hstripe = blur_y(hstripe, max(3, h // 120))
hstripe = (hstripe - hstripe.min()) / max(hstripe.max() - hstripe.min(), 1e-6)
band_col = np.clip(grad * (0.46 + 0.95 * hstripe[..., None]), 0, 1)
col = col * (1 - band_mask[..., None]) + band_col * band_mask[..., None]

# 3) 竖向柔和高光（像漆面反光）
u = np.linspace(0, 1, w, dtype=np.float32)[None, :]
spec = np.exp(-((u - 0.34) ** 2) / 0.02) * 0.14
col = np.clip(col + spec[..., None], 0, 1)

# 4) 首尾两道银箍
v = np.linspace(0, 1, h, dtype=np.float32)[:, None]
col[((v < 0.012) | (v > 0.988))[:, 0]] = np.array([0.78, 0.84, 0.92])
put(R_SHEATH, col)

# ---------- 刀柄：同款拉丝，压暗一点，加缠绳的明暗 ----------
x0, y0, x1, y1 = R_HANDLE
h, w = y1 - y0, x1 - x0
st = brushed(h, w, 23)
grad = cool_gradient(h, w, (0.78, 0.82, 0.90), (0.42, 0.47, 0.58))
col = grad * (0.55 + 0.72 * st[..., None])
uu = np.linspace(0, 1, w, dtype=np.float32)[None, :]
vv = np.linspace(0, 1, h, dtype=np.float32)[:, None]
p1 = (uu * 1.6 + vv * 5.2) % 1.0
p2 = (uu * 1.6 - vv * 5.2) % 1.0
rope = np.clip(np.cos(p1 * 2 * np.pi), 0, 1) ** 6 + np.clip(np.cos(p2 * 2 * np.pi), 0, 1) ** 6
col = np.clip(col + np.clip(rope, 0, 1)[..., None] * 0.16, 0, 1)
put(R_HANDLE, col)

# ---------- 刀刃：蓝色 ----------
x0, y0, x1, y1 = R_BLADE
h, w = y1 - y0, x1 - x0
st = brushed(h, w, 37)
uu = np.linspace(0, 1, w, dtype=np.float32)[None, :]
vv = np.linspace(0, 1, h, dtype=np.float32)[:, None]
# 横向渐变：刃口（u 小）亮，刀脊（u 大）深
deep = np.array([0.05, 0.14, 0.34], dtype=np.float32)
mid = np.array([0.16, 0.40, 0.74], dtype=np.float32)
hot = np.array([0.72, 0.90, 1.00], dtype=np.float32)
t = np.clip(uu, 0, 1)[..., None]
col = np.where(t < 0.55, mid + (hot - mid) * np.clip((0.55 - t) / 0.55, 0, 1),
               mid + (deep - mid) * np.clip((t - 0.55) / 0.45, 0, 1))
col = col * (0.78 + 0.34 * st[..., None])
# 刃文：波浪分界，靠刃口那侧更亮
hamon = 0.5 + 0.5 * np.sin(vv * 30.0 + 0.8) * 0.5
edge = np.exp(-((uu - (0.10 + 0.05 * hamon)) ** 2) / 0.0016) * 0.5
col = np.clip(col + edge[..., None] * np.array([0.55, 0.75, 0.95]), 0, 1)
put(R_BLADE, col)

# ---------- 柄头：银色 ----------
x0, y0, x1, y1 = R_MOUTH
h, w = y1 - y0, x1 - x0
st = brushed(h, w, 51)
grad = cool_gradient(h, w, (0.72, 0.76, 0.85), (0.34, 0.38, 0.48))
put(R_MOUTH, grad * (0.7 + 0.5 * st[..., None]))

# ---------- 纹章：original 还原，只画内菱形 ----------
ex0, ey0, ex1, ey1 = R_EMBLEM
cur = out[ey0:ey1, ex0:ex1]
cur[:, :, :3] = orig[ey0:ey1, ex0:ex1]          # 灰框 + 白底回到原样
out[ey0:ey1, ex0:ex1] = cur

res = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "RGBA")
d = ImageDraw.Draw(res, "RGBA")

yy, xx = np.mgrid[0:H, 0:W]
inner = (np.abs(xx - CX) + np.abs(yy - CY)) <= R_IN

# 夜空底：上方偏亮，下方更深
sky = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = ImageDraw.Draw(sky)
yy0, yy1 = int(CY - R_IN), int(CY + R_IN)
for i, y in enumerate(range(yy0, yy1 + 1)):
    f = i / max(1, yy1 - yy0)
    c = (int(8 + 10 * (1 - f)), int(12 + 16 * (1 - f)), int(26 + 30 * (1 - f)), 255)
    sd.line([(0, y), (W, y)], fill=c)
sky_arr = np.array(sky)
mask_arr = inner[..., None]
res_arr = np.array(res)
res_arr[:, :, :3] = np.where(mask_arr, sky_arr[:, :, :3], res_arr[:, :, :3])
res_arr[:, :, 3] = np.where(inner, 255, res_arr[:, :, 3])
res = Image.fromarray(res_arr, "RGBA")
d = ImageDraw.Draw(res, "RGBA")

# 内菱形里的星点（银色）
for _ in range(420):
    px = int(rng.integers(CX - R_IN, CX + R_IN))
    py = int(rng.integers(CY - R_IN, CY + R_IN))
    if abs(px - CX) + abs(py - CY) > R_IN - 3 * S:
        continue
    br = int(rng.integers(110, 255))
    r_ = max(1, int(rng.random() * 1.2 * S))
    d.ellipse([px - r_, py - r_, px + r_, py + r_], fill=(br, br, min(255, br + 4), 255))

# 行星 + 星环
pr = 17.0 * S
ring_rx, ring_ry = 27.0 * S, 7.2 * S
tilt = -14.0

# 1) 先把整圈星环画在单独图层
ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
rd = ImageDraw.Draw(ring)
for k, (wdt, a) in enumerate([(2.2, 210), (1.2, 150), (3.4, 90)]):
    rd.ellipse([CX - ring_rx, CY - ring_ry, CX + ring_rx, CY + ring_ry],
               outline=(216, 220, 228, a), width=int(wdt * S / 2))
ring = ring.rotate(tilt, center=(CX, CY), resample=Image.BICUBIC)
res.alpha_composite(ring)

# 2) 行星本体（盖住环的后半）
planet = Image.new("RGBA", (W, H), (0, 0, 0, 0))
pd = ImageDraw.Draw(planet)
pr_i = int(pr)
px0, py0, px1, py1 = CX - pr_i, CY - pr_i, CX + pr_i, CY + pr_i
for i in range(pr_i * 2):
    f = i / max(1, pr_i * 2 - 1)          # 0 顶 -> 1 底
    shade = 0.28 + 0.62 * (1 - abs(f - 0.32) * 1.4)
    shade = max(0.18, min(1.0, shade))
    # 银色星体：三通道接近，只留一点点冷调
    c = (int(152 * shade + 26), int(156 * shade + 27), int(166 * shade + 30), 255)
    yy_ = py0 + i
    half = int((pr_i ** 2 - (yy_ - CY) ** 2) ** 0.5)
    pd.line([(CX - half, yy_), (CX + half, yy_)], fill=c)
# 云带
for band_y, band_w, alpha_b in [(-0.45, 0.13, 90), (-0.05, 0.10, 70), (0.35, 0.16, 80)]:
    yy_ = int(CY + band_y * pr)
    half = int((pr_i ** 2 - (yy_ - CY) ** 2) ** 0.5)
    if half > 0:
        pd.line([(CX - half, yy_), (CX + half, yy_)], fill=(228, 231, 238, alpha_b), width=max(1, int(band_w * pr)))
planet = planet.filter(ImageFilter.GaussianBlur(0.6 * S))
res.alpha_composite(planet)

# 3) 环的前半段再盖一次（0°~180° 下半圈）
front = ring.crop((0, int(CY), W, H))
res.alpha_composite(front, (0, int(CY)))

# 4) 只有纹章那一块里的"内菱形之外"要还原成原图（灰框、白底），别的地方保持新贴图
res_arr = np.array(res)
orig_arr = np.array(img.resize((W, H), Image.NEAREST))
band = np.zeros_like(inner)
band[ey0:ey1, ex0:ex1] = True
restore = (~inner) & band
res_arr[restore, :3] = orig_arr[restore, :3]
res_arr[restore, 3] = orig_arr[restore, 3]
res = Image.fromarray(res_arr, "RGBA")

# 缩回 192x384
final = res.resize((w0, h0), Image.LANCZOS)
final.save(OUT)
print("texture ->", OUT, final.size)

# 预览
flat = Image.new("RGBA", final.size, (13, 15, 24, 255))
prev = Image.alpha_composite(flat, final).convert("RGB")
prev.resize((prev.width * 4, prev.height * 4), Image.NEAREST).save(os.path.join(BASE, "preview_v2.png"))
print("preview -> preview_v2.png")
