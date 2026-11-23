"""生成发布用的模组图标候选（512×512，另存 400×400 与 256×256）。

三张走同一套配色，方便直接替换：
  银白 (222,228,240)、冷白 (245,248,255)、冰蓝 (140,200,255)、
  深蓝黑底 (10,14,26) → (18,26,46)
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = r"D:\GoldenStar\mod构建\SilverStar\docs\release"
os.makedirs(OUT, exist_ok=True)

S = 4                      # 内部超采样倍数
N = 512                    # 输出边长
W = N * S

SILVER = (222, 228, 240)
WHITE = (245, 248, 255)
ICE = (140, 200, 255)
DARK_TOP = (16, 22, 40)
DARK_BOT = (8, 11, 22)


def base_canvas() -> np.ndarray:
    """深蓝黑的纵向渐变底。"""
    t = np.linspace(0.0, 1.0, W)[:, None]
    top = np.array(DARK_TOP, dtype=np.float32)
    bot = np.array(DARK_BOT, dtype=np.float32)
    img = top * (1 - t)[..., None] + bot * t[..., None]
    img = np.repeat(img, W, axis=1)
    rng = np.random.default_rng(7)
    # 极轻的噪点，别让底看着像纯色塑料
    img += rng.normal(0.0, 1.6, (W, W, 1)).repeat(3, axis=2)
    return np.clip(img, 0, 255)


def stars(px: np.ndarray, count: int, seed: int, bright=210):
    """撒星点：亮核 + 一点外扩。"""
    rng = np.random.default_rng(seed)
    for _ in range(count):
        x = rng.integers(0, W)
        y = rng.integers(0, W)
        b = bright * rng.uniform(0.35, 1.0)
        r = rng.integers(1, 3) * S // 2 + 1
        y0, y1 = max(0, y - r), min(W, y + r + 1)
        x0, x1 = max(0, x - r), min(W, x + r + 1)
        px[y0:y1, x0:x1] = np.maximum(px[y0:y1, x0:x1], b)
    return px


def diamond(draw: ImageDraw.ImageDraw, cx: float, cy: float, half: float,
            outline: int, width: int, fill=None):
    pts = [(cx, cy - half), (cx + half, cy), (cx, cy + half), (cx - half, cy)]
    draw.polygon(pts, fill=fill, outline=outline, width=width)


def four_point(draw: ImageDraw.ImageDraw, cx: float, cy: float, length: float,
               waist: float, fill: int):
    """四芒星：四根尖角 + 细腰，画两遍叠加做旋臂。"""
    pts = [(cx, cy - length), (cx + waist, cy - waist), (cx + length, cy),
           (cx + waist, cy + waist), (cx, cy + length), (cx - waist, cy + waist),
           (cx - length, cy), (cx - waist, cy - waist)]
    draw.polygon(pts, fill=fill)


def ring(draw: ImageDraw.ImageDraw, cx: float, cy: float, rx: float, ry: float,
         width: int, fill: int):
    """压扁的椭圆环（星环）。"""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], outline=fill, width=width)


def save(img: Image.Image, name: str):
    small = img.resize((N, N), Image.LANCZOS)
    small.save(os.path.join(OUT, name + ".png"))
    small.resize((400, 400), Image.LANCZOS).save(os.path.join(OUT, name + "_400.png"))
    small.resize((256, 256), Image.LANCZOS).save(os.path.join(OUT, name + "_256.png"))
    print("saved", name)


def icon_emblem():
    """A：菱形徽记 —— 银框 + 星空底 + 带环行星（就是刀上纹章那块的样子）。"""
    px = base_canvas()
    px = stars(px, 260, 11)
    img = Image.fromarray(px.astype(np.uint8), "RGB")
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    half = W * 0.40
    diamond(d, W / 2, W / 2, half, (226, 232, 244, 255), int(W * 0.022))
    diamond(d, W / 2, W / 2, half * 0.885, (60, 78, 110, 255), int(W * 0.008))

    # 菱形里的夜空
    inner = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    di = ImageDraw.Draw(inner)
    di.polygon([(W / 2, W / 2 - half * 0.86), (W / 2 + half * 0.86, W / 2),
                (W / 2, W / 2 + half * 0.86), (W / 2 - half * 0.86, W / 2)],
               fill=(9, 12, 24, 255))
    px2 = np.array(inner).astype(np.float32)
    px2 = stars(px2, 90, 23, 230)
    inner = Image.fromarray(px2.astype(np.uint8), "RGBA")
    layer = Image.alpha_composite(layer, inner)

    d = ImageDraw.Draw(layer)
    cx, cy = W / 2, W / 2
    pr = W * 0.150
    # 星环（先整圈，行星压住下半，再补回前半圈）
    ring(d, cx, cy, pr * 1.95, pr * 0.52, int(W * 0.012), (215, 222, 234, 235))
    # 行星：上亮下暗的球
    for i in range(int(pr), 0, -1):
        k = i / pr
        shade = 0.55 + 0.45 * math.sqrt(max(0.0, 1 - (1 - k) ** 2))
        col = (int(150 * shade + 20), int(155 * shade + 22), int(166 * shade + 26), 255)
        d.ellipse([cx - i, cy - i, cx + i, cy + i], fill=col)
    # 云带
    for dy, hh, a in ((-0.42, 0.10, 120), (-0.10, 0.14, 150), (0.26, 0.11, 120)):
        y0 = cy + pr * dy
        h = pr * hh
        d.ellipse([cx - pr * 0.94, y0 - h, cx + pr * 0.94, y0 + h], fill=(232, 236, 244, a))
    ring(d, cx, cy, pr * 1.95, pr * 0.52, int(W * 0.012), (215, 222, 234, 235))
    # 只保留下半圈压在行星前面
    front = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    df = ImageDraw.Draw(front)
    df.arc([cx - pr * 1.95, cy - pr * 0.52, cx + pr * 1.95, cy + pr * 0.52],
           20, 160, fill=(228, 233, 243, 255), width=int(W * 0.012))
    layer = Image.alpha_composite(layer, front)

    d = ImageDraw.Draw(layer)
    four_point(d, cx, cy, pr * 1.30, pr * 0.13, (250, 252, 255, 255))

    out = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    save(out, "icon_a_emblem")


def icon_blade():
    """B：斜着的一截星空刀刃 —— 冰蓝刃口 + 银白刀身 + 星域。"""
    px = base_canvas()
    px = stars(px, 200, 31)
    img = Image.fromarray(px.astype(np.uint8), "RGB").convert("RGBA")

    # 刀刃：一块斜置的细长多边形
    blade = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(blade)
    cx, cy = W / 2, W / 2
    L, T = W * 0.86, W * 0.145
    ang = math.radians(-38)
    ca, sa = math.cos(ang), math.sin(ang)

    def rot(x, y):
        return (cx + x * ca - y * sa, cy + x * sa + y * ca)

    quad = [rot(-L / 2, -T / 2), rot(L / 2 - T * 0.9, -T * 0.42),
            rot(L / 2, 0.0), rot(L / 2 - T * 0.9, T * 0.42), rot(-L / 2, T / 2)]
    d.polygon(quad, fill=(120, 170, 220, 255))
    pxb = np.array(blade).astype(np.float32)
    pxb = stars(pxb, 120, 43, 255)
    blade = Image.fromarray(pxb.astype(np.uint8), "RGBA")

    # 刃口：沿刀背那条亮边
    edge = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    de = ImageDraw.Draw(edge)
    de.line([rot(-L / 2, T / 2 * 0.92), rot(L / 2 - T * 0.5, T * 0.10)],
            fill=(206, 228, 255, 255), width=int(W * 0.012))
    de.line([rot(-L / 2, -T / 2 * 0.94), rot(L / 2 - T * 0.6, -T * 0.34)],
            fill=(238, 243, 252, 255), width=int(W * 0.008))

    out = Image.alpha_composite(img, blade)
    out = Image.alpha_composite(out, edge).convert("RGB")
    save(out, "icon_b_blade")


def icon_mark():
    """C：最简 —— 一颗银色四芒星 + 一道星环，纯图形。"""
    px = base_canvas()
    px = stars(px, 300, 53)
    img = Image.fromarray(px.astype(np.uint8), "RGB").convert("RGBA")
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    cx, cy = W / 2, W / 2

    # 外圈细环
    d.ellipse([cx - W * 0.40, cy - W * 0.40, cx + W * 0.40, cy + W * 0.40],
              outline=(120, 150, 190, 160), width=int(W * 0.006))
    ring(d, cx, cy, W * 0.44, W * 0.13, int(W * 0.010), (208, 218, 232, 200))

    # 四芒星：先大后小的两层，尖角更长
    four_point(d, cx, cy, W * 0.32, W * 0.045, (150, 190, 235, 255))
    four_point(d, cx, cy, W * 0.26, W * 0.030, (240, 245, 255, 255))
    d.ellipse([cx - W * 0.035, cy - W * 0.035, cx + W * 0.035, cy + W * 0.035],
              fill=(150, 205, 255, 255))

    glow = layer.filter(ImageFilter.GaussianBlur(W * 0.012))
    out = Image.alpha_composite(img, glow)
    out = Image.alpha_composite(out, layer).convert("RGB")
    save(out, "icon_c_mark")


icon_emblem()
icon_blade()
icon_mark()
