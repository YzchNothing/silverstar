from PIL import Image
import numpy as np

def gold(src, dst):
    im = Image.open(src).convert("RGBA")
    a = np.asarray(im).astype(np.float32) / 255.0
    rgb, alpha = a[..., :3], a[..., 3]
    lum = np.clip((0.299*rgb[...,0] + 0.587*rgb[...,1] + 0.114*rgb[...,2] - 0.5)*1.05 + 0.5, 0.0, 1.0)
    dark  = np.array([0.045, 0.035, 0.017], np.float32)
    mid   = np.array([0.24, 0.17, 0.065], np.float32)
    light = np.array([0.62, 0.47, 0.19], np.float32)
    l = lum[..., None]
    out = np.where(l < 0.5, dark + (mid-dark)*(l/0.5), mid + (light-mid)*((l-0.5)/0.5))
    res = np.concatenate([np.clip(out,0,1), alpha[..., None]], axis=-1)
    Image.fromarray((res*255).round().astype(np.uint8), "RGBA").save(dst, optimize=True)
    print(dst)

b = "src/main/resources/assets/silverstar/model/"
gold(b+"silver_star.png", b+"golden_star.png")
gold(b+"silver_star_body.png", b+"golden_star_body.png")

