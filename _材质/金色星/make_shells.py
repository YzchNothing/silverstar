from PIL import Image, ImageDraw
import numpy as np

SRC = "src/main/resources/assets/silverstar/model/silver_star.png"
OUT = "_材质/金色星/"
im = Image.open(SRC).convert("RGBA")
a = np.asarray(im).astype(np.float32) / 255.0
rgb, alpha = a[..., :3], a[..., 3]
lum = np.clip((0.299*rgb[...,0] + 0.587*rgb[...,1] + 0.114*rgb[...,2] - 0.5)*1.05 + 0.5, 0.0, 1.0)
h, w = lum.shape
yy = np.linspace(0, 1, h)[:, None] * np.ones((1, w))
grain = (np.random.default_rng(7).random((h, w)) - 0.5) * 0.022

GOLD = np.array([0.62, 0.47, 0.19], np.float32)

def paint(dark, mid, light, seam=True):
    d = np.array(dark, np.float32); m = np.array(mid, np.float32); l = np.array(light, np.float32)
    x = lum[..., None]
    base = np.where(x < 0.5, d + (m - d) * (x / 0.5), m + (l - m) * ((x - 0.5) / 0.5))
    # 纵向微渐变：越靠下越沉，模拟握持处的磨损
    base = base * (1.0 - 0.16 * yy[..., None])
    if seam:
        # 原来贴图里最亮的边缘 → 抽出来当“金线”，这是绘制出来的细节，不是整体换色
        band = np.clip((lum - 0.72) / 0.18, 0, 1)[..., None]
        base = base * (1 - band) + GOLD * band
    base = np.clip(base + grain[..., None], 0, 1)
    return np.concatenate([base, alpha[..., None]], axis=-1)

cands = {
    "A_teal":  ((0.030, 0.105, 0.115), (0.075, 0.300, 0.320), (0.150, 0.560, 0.580)),
    "B_black": ((0.020, 0.020, 0.026), (0.075, 0.075, 0.090), (0.230, 0.230, 0.250)),
    "C_silver":((0.060, 0.068, 0.080), (0.260, 0.285, 0.320), (0.620, 0.650, 0.690)),
    "D_purple":((0.055, 0.022, 0.090), (0.230, 0.105, 0.330), (0.420, 0.250, 0.560)),
}
tiles = []
for name, ramp in cands.items():
    arr = paint(*ramp)
    img = Image.fromarray((arr*255).round().astype(np.uint8), "RGBA")
    img.save(OUT + name + ".png", optimize=True)
    tiles.append((name, img))
    print(name, "ok")

# 拼对照图：四张并排，下面写名字
scale = 2
tw, th = w*scale, h*scale
canvas = Image.new("RGBA", (tw*4 + 5*10, th + 40), (24, 24, 26, 255))
d = ImageDraw.Draw(canvas)
for i, (name, img) in enumerate(tiles):
    canvas.paste(img.resize((tw, th), Image.NEAREST), (10 + i*(tw+10), 10), img.resize((tw, th), Image.NEAREST))
    d.text((10 + i*(tw+10) + 4, th + 16), name, fill=(230, 230, 230, 255))
canvas.save(OUT + "preview_shells.png")
print("preview", canvas.size)
