"""从 cosmic.fsh 派生出“只有星点、没有底色”的版本。

cosmicSample() 返回的是一整片天空——包括底色渐变和星点。刀光那种细弧看不出底色，
但斩击波是一大片模型，底色会把它糊成一块紫色塑料板，所以这一份只保留真正亮起来的星。

做法：按字节读原文件，保留 void main 之前的全部内容（点阵表和 helper 函数），
再把 main 换成只输出星点的版本。用字节切分是为了不动前面那些中文注释的原始编码。
"""

SRC = r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\shaders\core\cosmic.fsh"
DST = r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\shaders\core\cosmic_staronly.fsh"

MAIN = b"""
void main(void)
{
    vec4 mask = texture(Sampler0, texCoord0);

    vec3 dir = normalize(-fPos);
    float t = time * 20.0;

    vec3 cosmic = clamp(cosmicSample(dir, t, CM_MASK_MODE, externalScale) * brightness, 0.0, 1.0);
    float star = cosmicStar(cosmic, CM_MASK_MODE, t, brightness);

    // star only: the background layer is much dimmer than the stars, the star factor kills it
    vec3 col = cosmic * star;

    vec3 shade = vertexColor.rgb * 0.2 + vec3(0.8, 0.8, 0.8);
    col *= shade * tint;

    float a = clamp(star * 1.6, 0.0, 1.0) * mask.a * opacity;
    fragColor = linear_fog(vec4(col, a) * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
"""

raw = open(SRC, "rb").read()
marker = b"void main(void)"
idx = raw.find(marker)
if idx < 0:
    raise SystemExit("void main(void) not found")

out = raw[:idx] + MAIN
open(DST, "wb").write(out)
print("wrote", DST, len(out), "bytes")
