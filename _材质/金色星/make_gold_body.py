from PIL import Image
import numpy as np

src = Image.open(r"src/main/resources/assets/silverstar/model/silver_star_body.png").convert("RGBA")
a = np.asarray(src).astype(np.float32) / 255.0
rgb, alpha = a[..., :3], a[..., 3]

lum = 0.299 * rgb[..., 0] + 0.587 * rgb[..., 1] + 0.114 * rgb[..., 2]
lum = np.clip((lum - 0.5) * 1.10 + 0.5, 0.0, 1.0)

dark  = np.array([0.10, 0.07, 0.03], dtype=np.float32)
mid   = np.array([0.50, 0.36, 0.12], dtype=np.float32)
light = np.array([1.00, 0.92, 0.62], dtype=np.float32)

out = np.zeros_like(rgb)
lo = lum < 0.5
out[lo] = dark + (mid - dark) * (lum[lo: None].reshape(-1, 1) if False else lum[..., None][lo] / 0.5)
hi = ~lo
out[hi] = mid + (light - mid) * ((lum[..., None][hi] - 0.5) / 0.5)

out = np.clip(out, 0.0, 1.0)
res = np.concatenate([out, alpha[..., None]], axis=-1)
img = Image.fromarray((res * 255).round().astype(np.uint8), "RGBA")
img.save(r"src/main/resources/assets/silverstar/model/golden_star_body.png", optimize=True)
img.save(r"_材质/金色星/golden_star_body.png", optimize=True)
print("ok", img.size)
