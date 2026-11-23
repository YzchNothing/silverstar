import io
import sys

p = r"D:\GoldenStar\mod构建\SilverStar\_材质\散华\paint_regions.py"
data = open(p, "rb").read()
try:
    text = data.decode("gbk")
    fixed = text.replace("bx0, by0, bx1, by1 = 86, 56, 170, 174",
                         "bx0, by0, bx1, by1 = 78, 52, 178, 178")
    open(p, "wb").write(fixed.encode("utf-8"))
    print("recovered as gbk -> utf-8")
except UnicodeDecodeError as e:
    print("gbk failed:", e)
    sys.exit(1)
