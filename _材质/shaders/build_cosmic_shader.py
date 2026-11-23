import io
import os

src = r"C:\Users\Administrator\AppData\Local\Temp\starpack\assets\minecraft\shaders\include\cosmic.glsl"
dst = r"D:\GoldenStar\mod构建\SilverStar\src\main\resources\assets\silverstar\shaders\core\cosmic.fsh"

text = io.open(src, encoding="utf-8").read()

start = text.index("const int CM_MASK_MODE")
helpers = text[start:]

header = """#version 150

// 无尽贪婪式的球面星域：把星空铺在一个以相机为球心的球面上，逐层堆叠。
//
// 和 stars.fsh 的区别：stars.fsh 是直接从一张星空贴图上采样，星点是"贴在贴图上的"，
// 层与层之间只有缩放差异；这一版是把星点当作 16x16 的字符块来解码——每个块由伪随机数
// 决定用哪个字形、旋几度、要不要翻转、什么时候播到第几帧，所以星点是真正的"点阵图形"，
// 有粗细、有十字星、会闪，跟原版寰宇支配之剑的观感一致。
//
// 字形本身不是贴图，是打包成整数的点阵表（CM_SYM 指向字形编号，CM_HDR 是字形版面，
// CM_ANM 是动画帧序列，CM_DAT 是点阵数据本身，每像素 4 bit）。这样只需要一张 mask 贴图，
// 不会踩到 Iris「一个 buffer 不能有多个纹理单元」的坑。

#define M_PI 3.1415926535897932384626433832795
#define CM_PI 3.1415926535897932384626433832795
#define CM_LAYERS 16
#define CM_TILES 16
#define CM_SYMBOLS 10
#define CM_OUTOF 61
const bool CM_TILE_PHASE = true;

const float lightmix = 0.2f;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;
uniform float externalScale;
uniform float opacity;
uniform float brightness;
uniform vec3 tint;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos;

out vec4 fragColor;

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor)
{
    if (vertexDistance <= fogStart) {
        return inColor;
    }
    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

"""

footer = """
void main(void)
{
    // mask 取自模型自己的贴图：alpha 决定这一块要不要显示星空
    vec4 mask = texture(Sampler0, texCoord0);

    // 相机指向片元的反方向，就是这颗"星空球"上的方向
    vec3 dir = normalize(-fPos);

    // time 传进来的是「游戏刻 × 0.05」，乘 20 还原成刻数，动画相位和原版对齐
    float t = time * 20.0;

    vec3 cosmic = clamp(cosmicSample(dir, t, CM_MASK_MODE, externalScale) * brightness, 0.0, 1.0);
    float star = cosmicStar(cosmic, CM_MASK_MODE, t, brightness);

    vec3 shade = vertexColor.rgb * lightmix + vec3(1.0 - lightmix, 1.0 - lightmix, 1.0 - lightmix);
    cosmic *= shade;

    vec4 outCol = vec4(cosmic * tint, cosmicMaskAlpha(mask.a, star) * opacity);

    outCol = clamp(outCol, 0.0, 1.0);

    fragColor = linear_fog(outCol * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
"""

io.open(dst, "w", encoding="utf-8", newline="\n").write(header + helpers + footer)
print("wrote", dst, os.path.getsize(dst), "bytes")
