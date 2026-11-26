import json, os, re, sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                    "assets", "silverstar", "shaders", "core")
BASE = os.path.normpath(BASE)

OLD_BG = ("    vec3 bg = vec3(0.1, sin(pulse * CM_PI * 2.0) * 0.075 + 0.225, "
          "cos(pulse * CM_PI * 2.0) * 0.05 + 0.3);\n")
NEW_BG = ("    // 底色：三条通道各自按「振幅 × sin(2π·pulse + 相位) + 中点」循环，\n"
          "    // 参数全部由 StarRender 从刀的 json 里读出来灌进来（见 StarShaders.bind）。\n"
          "    vec3 wave = sin(vec3(pulse * CM_PI * 2.0) + bgPhase);\n"
          "    return wave * bgAmp + bgBase;\n")

OLD_STAR = ("        c = vec3(mod(rand1, 29.0) / 29.0 * 0.3 + 0.4, "
            "mod(rand2, 35.0) / 35.0 * 0.4 + 0.6, mod(rand1, 17.0) / 17.0 * 0.3 + 0.7);\n")
NEW_STAR = ("        // 星点颜色也是数据：每通道一个中点 + 一个抖动幅度。\n"
            "        c = vec3(mod(rand1, 29.0) / 29.0, mod(rand2, 35.0) / 35.0,\n"
            "                 mod(rand1, 17.0) / 17.0) * starJitter + starBase;\n")

UNIFORMS = ("uniform vec3 bgBase;\n"
            "uniform vec3 bgAmp;\n"
            "uniform vec3 bgPhase;\n"
            "uniform vec3 starBase;\n"
            "uniform vec3 starJitter;\n")

for name in ["cosmic.fsh", "cosmic_sparse.fsh", "cosmic_staronly.fsh", "cosmic_arm.fsh"]:
    path = os.path.join(BASE, name)
    text = open(path, encoding="utf-8").read()
    assert text.count(OLD_BG) == 1, (name, "bg", text.count(OLD_BG))
    assert text.count(OLD_STAR) == 1, (name, "star", text.count(OLD_STAR))
    # 底色原先被 return 用掉，新写法自带 return，所以把旧的 return bg; 去掉
    text = text.replace(OLD_BG, NEW_BG)
    text = text.replace("    return bg;\n}\n", "}\n", 1) if "return wave * bgAmp + bgBase;\n" in text else text
    text = text.replace(OLD_STAR, NEW_STAR)
    assert "uniform vec3 tint;\n" in text
    text = text.replace("uniform vec3 tint;\n", "uniform vec3 tint;\n" + UNIFORMS, 1)
    open(path, "w", encoding="utf-8", newline="\n").write(text)
    print("patched", name)

# ---- 每份 json 补上这五个 uniform 的默认值（纯银星那组数，运行时会被 bind 覆盖）
SILVER = {
    "bgBase": [0.1, 0.225, 0.3],
    "bgAmp": [0.0, 0.075, 0.05],
    "bgPhase": [0.0, 0.0, 1.5707964],
    "starBase": [0.4, 0.6, 0.7],
    "starJitter": [0.3, 0.4, 0.3],
}

for name in sorted(os.listdir(BASE)):
    if not name.endswith(".json"):
        continue
    path = os.path.join(BASE, name)
    data = json.load(open(path, encoding="utf-8"))
    uniforms = data.get("uniforms") or {}
    if not uniforms.get("uniforms"):
        continue
    existing = {u["name"] for u in uniforms["uniforms"]}
    for key, values in SILVER.items():
        if key in existing:
            continue
        uniforms["uniforms"].append({
            "name": key,
            "type": "vec3",
            "count": 3,
            "values": values,
        })
    json.dump(data, open(path, "w", encoding="utf-8", newline="\n"), indent=4)
    print("uniforms ok", name)
