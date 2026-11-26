import json, os

BASE = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main",
                                     "resources", "assets", "silverstar", "shaders", "core"))

SILVER = {
    "bgBase": [0.1, 0.225, 0.3],
    "bgAmp": [0.0, 0.075, 0.05],
    "bgPhase": [0.0, 0.0, 1.5707964],
    "starBase": [0.4, 0.6, 0.7],
    "starJitter": [0.3, 0.4, 0.3],
}

# 原版格式里 vec3 是「type=float, count=3」（type 写的是分量类型，不是 GLSL 类型名）。
# 之前误写成 "vec3" 会让 MC 解析不出分量类型，生成的 Uniform 缓冲区是 null，一 set 就 NPE。
for name in sorted(os.listdir(BASE)):
    if not name.endswith(".json"):
        continue
    path = os.path.join(BASE, name)
    data = json.load(open(path, encoding="utf-8-sig"))
    uniforms = data.get("uniforms")
    if not uniforms:
        print("skip (no uniforms)", name)
        continue
    existing = {u["name"]: u for u in uniforms}
    changed = []
    for key, values in SILVER.items():
        if key in existing:
            entry = existing[key]
            if entry.get("type") != "float" or entry.get("count") != 3:
                entry["type"] = "float"
                entry["count"] = 3
                entry["values"] = values
                changed.append(key + "(fixed)")
            continue
        uniforms.append({"name": key, "type": "float", "count": 3, "values": values})
        changed.append(key)
    if changed:
        json.dump(data, open(path, "w", encoding="utf-8", newline="\n"), indent=4)
    print("ok", name, changed)
