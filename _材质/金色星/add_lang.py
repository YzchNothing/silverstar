import json, io
for p, name, en in (("src/main/resources/assets/silverstar/lang/zh_cn.json", "金色星", "金色星"),
                    ("src/main/resources/assets/silverstar/lang/en_us.json", "GoldenStar", "GoldenStar")):
    d = json.load(io.open(p, encoding="utf-8-sig"))
    d["item.silverstar.golden_star"] = name
    io.open(p, "w", encoding="utf-8", newline="\n").write(json.dumps(d, indent=2, ensure_ascii=False) + "\n")
    print(p, d["item.silverstar.golden_star"])
