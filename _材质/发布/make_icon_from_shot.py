"""把游戏里截的图做成模组图标。

用法：
  1. 把截图存成  D:\\GoldenStar\\mod构建\\SilverStar\\docs\\release\\screenshot.png
  2. 直接跑本脚本（不用改参数）
  3. 产物：
       src\\main\\resources\\icon.png          400×400，会被打进 jar（mods.toml 里 logoFile 指向它）
       docs\\release\\icon_from_shot_512.png   512×512，CurseForge 项目图标
       docs\\release\\icon_from_shot_256.png   256×256，MC百科条目图标

裁切规则：取截图中心的正方形区域（居中裁），所以截图里想要的部分尽量放在画面中间。
想指定裁切框，用 --box x0 y0 x1 y1（像素坐标，左上原点）。
"""
import argparse
import os
import sys

from PIL import Image

SRC = r"D:\GoldenStar\mod构建\SilverStar\docs\release\screenshot.png"
PROJECT = r"D:\GoldenStar\mod构建\SilverStar"
OUT_DIR = os.path.join(PROJECT, "docs", "release")
ICON_IN_JAR = os.path.join(PROJECT, "src", "main", "resources", "icon.png")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=SRC)
    ap.add_argument("--box", nargs=4, type=int, default=None,
                    metavar=("X0", "Y0", "X1", "Y1"))
    args = ap.parse_args()

    if not os.path.exists(args.src):
        print("找不到截图：", args.src)
        print("先把游戏截图存成这个路径，或者用 --src 指定。")
        return 1

    im = Image.open(args.src).convert("RGB")
    print("源图", im.size)

    if args.box:
        x0, y0, x1, y1 = args.box
        if not (0 <= x0 < x1 <= im.width and 0 <= y0 < y1 <= im.height):
            print("裁切框超出图片范围")
            return 1
        crop = im.crop((x0, y0, x1, y1))
    else:
        side = min(im.width, im.height)
        left = (im.width - side) // 2
        top = (im.height - side) // 2
        crop = im.crop((left, top, left + side, top + side))

    print("裁成", crop.size)

    os.makedirs(OUT_DIR, exist_ok=True)
    big = crop.resize((512, 512), Image.LANCZOS)
    big.save(os.path.join(OUT_DIR, "icon_from_shot_512.png"))
    crop.resize((400, 400), Image.LANCZOS).save(ICON_IN_JAR)
    crop.resize((256, 256), Image.LANCZOS).save(os.path.join(OUT_DIR, "icon_from_shot_256.png"))

    print("icon.png(400) ->", ICON_IN_JAR)
    print("512 / 256 ->", OUT_DIR)
    print("接下来跑一次 gradlew build，jar 里才会带图标。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
