from pathlib import Path

SRC = Path(r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\shaders\core\cosmic.fsh")
DST = SRC.with_name("cosmic_sparse.fsh")

text = SRC.read_text(encoding="utf-8")

# 层数决定星星的密度：幻影剑只要很少很少的星，所以砍到 4 层。
old = "#define CM_LAYERS 16"
assert old in text, "CM_LAYERS 定义变了，脚本要跟着改"
text = text.replace(old, "#define CM_LAYERS 4")

DST.write_text(text, encoding="utf-8")
print("written", DST, len(text))
