#version 150

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
uniform vec3 bgBase;
uniform vec3 bgAmp;
uniform vec3 bgPhase;
uniform vec3 starBase;
uniform vec3 starJitter;

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

const int CM_MASK_MODE = 2;
const float CM_MASK_GAIN = 2.0;
const float CM_STAR_SENS = 4.0;
const float CM_STAR_FADE = 1.0;

const float CM_MASK_CUTOUT = 0.002;

const int CM_SYM[10] = int[10]
(
    327680, 754308, 1017928, 1544205, 1219538,
    1287446, 208602, 274528, 2765412, 214891
);

const int CM_HDR[46] = int[46]
(
    143155200, 214319129, 285483082, 356647067, 249995540, 252084564,
    285516188, 283550189, 486711861, 486711996, 486712131, 486712266,
    486712401, 254166232, 289678632, 254166411, 287720923, 256263733,
    143582861, 214746790, 285910743, 357074728, 289818529, 325462020,
    289818748, 325339359, 392448343, 463612391, 459426475, 425872211,
    392449007, 392449151, 357051663, 285887880, 285887961, 285888042,
    76328571, 111840906, 147353250, 285486789, 111980310, 147631918,
    285781841, 534777762, 534778018, 534778274
);

const int CM_ANM[37] = int[37]
(
    268435456, 16777266, 0, 2, 3, 0, 1126306064, 286401347,
    0, 1048576, 0, 33554435, 0, 67108864, 0, 0,
    0, 0, 12816, 0, 16777216, 12288, 0, 1126236672,
    841027589, 305279507, 0, 0, 1409286144, 284246, 0, 0,
    0, 589365248, 1, 268435456, 545
);

const int CM_DAT[597] = int[597]
(
    -1342175232, 146782208, 134217904, 720896, 45056, -1157624064, 770047, -1342177265,
    184549376, 184549376, 0, 0, 11, 184549616, 185335803, 61440,
    720896, 0, 184549376, 0, 8, 0, 0, 0,
    176, 983040, -5240832, 524479, 3840, 11534336, 0, 0,
    0, 134217728, 0, 61440, 0, 0, 983040, 0,
    0, 0, 15, 983040, 0, 0, 8, 2296,
    2048, 0, 0, 15, 32768, 9404416, 8388608, 0,
    0, 240, 0, 0, 0, 15, 983040, 0,
    0, 15728640, 0, 0, 128, 2296, 128, 0,
    0, 4096, 131073, 4194817, 134479874, -1778384892, -1793064954, 9625,
    1686, 264196, 33579010, 196609, 2097153, 16777216, 0, 0,
    1, 16, 268435968, 268451840, 1074135104, 1621123072, 2070937600, -1150091264,
    1879049083, 1610612859, 1073766560, 272632064, 268455936, 196608, 2097152, 16777216,
    268435456, 0, 2, 33554480, 33555712, 83914757, 118163200, 147324928,
    -859291648, -939524024, 117440520, 83887883, 33882288, 33556224, 16384, 196608,
    2097152, 33554432, 805306368, 0, 3145732, 1613758560, 6293760, 9490576,
    10326016, -1645374464, -1651507196, -795869184, 6291600, 3170317, 3145872, 1536,
    16384, 196608, 3145728, 67108864, 1342177280, 262144, 117702663, 458912,
    659210, 719360, 1526725200, 184156160, 252313600, -267976694, 263936, 262154,
    112, 1280, 16384, 15, 0, 0, 0, -268435456,
    240, 0, 0, 0, 3840, 128, 9404416, -2147483648,
    0, 0, 0, 251658240, 240, 0, 0, 0,
    15728640, 61440, 0, 0, 0, 2048, 9408256, 134217728,
    0, 0, 15728640, 61440, 0, 0, 0, 0,
    986880, 0, 0, 32768, 150470656, -2147483648, 15728640, 0,
    0, 0, 524288, -134217728, 3848, 2048, 0, 0,
    251658240, 7340032, 10526832, -1600126736, 175112304, 168427520, 251660800, 251658255,
    252641280, 167774720, 2046820362, 1879048192, 0, -1610571776, -252706816, -268435456,
    -268435456, -1610612496, 40960, 1879048192, 1879048192, 5, 1280, 0,
    0, 458752, 7, 2570, 983040, 167772160, 10, 458759,
    0, 0, 83886080, 0, 5, 61440, 0, 0,
    0, 0, 0, 0, 0, 61440, 0, 240,
    0, -268435456, 0, 0, 0, 0, 0, 0,
    0, 0, 15728640, 128, 150470656, 0, 128, 0,
    251658240, 0, 0, 0, 0, 0, 4, 32768,
    -1887174656, 4, 983168, 262144, 0, 128, 150470656, 0,
    128, 0, 0, 0, 0, 0, 0, 61440,
    0, 61440, 0, 570425344, 0, 880, 11075584, 536879104,
    536871407, 704614659, -22609920, 655342, -269549680, -1845493638, 36706815, 50204672,
    512, 154, 471040, 570425344, 0, 285212672, 0, 200704,
    0, 624, -1241513984, 1, 687407104, 0, 2058287888, -2128474111,
    398458606, -268738304, 19011822, -806705648, 1, 11497984, 0, 31504,
    536870912, 7, 19922944, 0, 4352, 0, 1, 139264,
    1610612736, 1, 238080, -452984832, 3375996, -2105088, -486538679, 287424494,
    1324350499, -292282368, 16380, 2123634192, 0, 14608, 83886080, 0,
    544, 1048576, 268435456, 0, 4608, 352321536, 4096, 1394624768,
    -620756970, 21417608, -1074792960, -486539008, 28654, -1343228672, -81649664, 184267,
    -1221421469, 2097154, 90112, 805306368, 0, 4096, 16, 2162688,
    1056768, 2015363136, 679477540, 163510, 1073610592, -82837504, 24303, -537996800,
    -251658238, 589535, 1633478176, 1999765512, 1310723, 513, 18, 65536,
    0, 1, 1192960, 698417154, 539230208, 2807, 469674644, -6291456,
    106222, -1120240, -1325400054, 4823791, 42962944, -1845493694, 18874394, 21281,
    1048576, 0, 458752, -1879048192, 1879048192, 7342848, -1330597888, 0,
    -1761607665, -1681915973, 251658247, -1342177280, 45232, 1879769200, -1879048192, 0,
    1792, 3147520, 185139200, 11534336, -267699968, -268432976, -1191182081, 121307376,
    45056, 592640, 117452800, 7340144, 184586352, 11534480, -268435280, 191889648,
    8061168, 61680, 11534512, -1878327152, 7340144, 3145840, 184549383, 458761,
    -1191182160, 3866864, 805371888, 162590731, 45056, 185139207, 805765120, 61440,
    0, 45296, -1342174213, 0, 524528, 45056, 573368, 176,
    983048, 96, 2048, 45056, 12562432, -1073760160, -1078984600, -1342177280,
    61440, 8, 251658336, 0, -1078984693, 252379136, 2048, -1207959376,
    -1342175041, 134217728, 6291456, 134217728, -1342115840, -1342177280, -1201667905, 6864895,
    49072, 45056, 524288, 6291456, 0, 576, 0, 149,
    2097152, 93651065, 1349844992, 664377098, -1869021184, 52685, -1600847872, 94035967,
    -594542592, -1696595969, -42991099, 1892679679, -66612151, 132120575, -39235552, 164495359,
    -1454374912, 1493893117, 0, -1727406884, -1720582144, 1459986553, 1498415104, 33593088,
    0, 22784, 0, 1056, 0, 2, 536870912, 5570560,
    1073741824, 41374720, 1963065344, 2384, -1459355648, 93653664, -989376512, 1202507228,
    -1073131518, 851967, -627769312, 95289343, -632291328, 1504575487, -1073741312, -1878196225,
    -898359291, 1885130204, 1498415109, 1083837088, 0, 542573968, 1948254208, 262233,
    1342177280, 131077, 0, 8192, 0, 147456, 570425344, 21760,
    83886080, 1360, 122683392, 93651109, 177209344, 1470933722, -867171838, 851965,
    -100641790, 95289343, -100315136, 1524629503, -39518208, 1353711615, -632291323, 11534335,
    -1073733563, 97312767, -898293728, 161525162, 1498415104, 91249152, 0, 5244240,
    0, 2228309, 536870912, 4, 0
);

float cm_rand(vec2 co)
{
    return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 cm_rotate(vec3 v, vec3 axis, float angle)
{
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);

    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

float cm_texel(int symbol, int px, int py, int tick)
{
    int s = CM_SYM[symbol];
    int fb = s & 63;
    int ao = (s >> 6) & 511;
    int cyc = (s >> 15) & 127;

    int ai = ao + int(mod(float(tick), float(cyc)));
    int local = (CM_ANM[ai >> 3] >> ((ai & 7) * 4)) & 15;

    int hd = CM_HDR[fb + local];
    int off = hd & 8191;
    int ox = (hd >> 13) & 15;
    int oy = (hd >> 17) & 15;
    int w = ((hd >> 21) & 15) + 1;
    int h = ((hd >> 25) & 15) + 1;

    int lx = px - ox;
    int ly = py - oy;
    if (lx < 0 || ly < 0 || lx >= w || ly >= h) return 0.0;

    int ni = off + ly * w + lx;
    return float((CM_DAT[ni >> 3] >> ((ni & 7) * 4)) & 15) * (1.0 / 15.0);
}

vec3 cm_background(int ctype, float t)
{
    float pulse = mod(t, 400.0) / 400.0;
    // 底色：三条通道各自按「振幅 × sin(2π·pulse + 相位) + 中点」循环，
    // 参数全部由 StarRender 从刀的 json 里读出来灌进来（见 StarShaders.bind）。
    vec3 wave = sin(vec3(pulse * CM_PI * 2.0) + bgPhase);
    return wave * bgAmp + bgBase;
}

vec3 cm_starColor(int ctype, float rand1, float rand2, float rand3, float rawu, float glyph, int layer, float t)
{
    vec3 c;
    if (ctype == 1)
    {
        float hue = fract((t * 0.02 + rawu * 3.0 + float(layer) * 0.3) * 0.3);
        c = mix(vec3(1.0), 0.5 + 0.5 * cos(2.0 * CM_PI * (hue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0))), 0.6);
    }
    else
    {
        // 星点颜色也是数据：每通道一个中点 + 一个抖动幅度。
        c = vec3(mod(rand1, 29.0) / 29.0, mod(rand2, 35.0) / 35.0,
                 mod(rand1, 17.0) / 17.0) * starJitter + starBase;
    }
    return c;
}

vec3 cosmicSample(vec3 dir, float t, int ctype, float scale)
{
    vec3 col = cm_background(ctype, t);
    float invScale = 1.0 / scale;

    for (int i = 0; i < CM_LAYERS; i++)
    {
        int mult = CM_LAYERS - i;

        int j = i + 7;
        float rand1 = (float(j * j) * 4321.0 + float(j) * 8.0) * 2.0;
        int k = j + 1;
        float rand2 = (float(k * k * k) * 239.0 + float(k) * 37.0) * 3.6;
        float rand3 = /*洛谔谔*/ rand1 * 347.4 + rand2 * 63.4;

        vec3 axis = normalize(vec3(sin(rand1), sin(rand2), cos(rand3)));
        vec3 ray = cm_rotate(dir, axis, mod(rand3, 2.0 * CM_PI));

        float rawu = 0.5 + (atan(ray.z, ray.x) / (2.0 * CM_PI));
        float rawv = 0.5 + (asin(clamp(ray.y, -1.0, 1.0)) / CM_PI);

        float ls = float(mult) * 0.5 + 2.75;
        float u = rawu * ls * scale;
        float v = (rawv + t * 0.0002 * invScale) * ls * 0.6 * scale;

        int tu = int(mod(floor(u * float(CM_TILES)), float(CM_TILES)));
        int tv = int(mod(floor(v * float(CM_TILES)), float(CM_TILES)));
        int position = ((171 * tu) + (489 * tv) + (303 * (i + 31)) + 17209) ^ 10086;
        int symbol = int(mod(float(position), float(CM_OUTOF)));

        if (symbol < 0 || symbol >= CM_SYMBOLS) continue;

        int rotation = int(mod(pow(float(tu), float(tv)) + float(tu) + 3.0 + float(tv * i), 8.0));
        bool flip = false;
        if (rotation >= 4)
        {
            rotation -= 4;
            flip = true;
        }

        float ru = clamp(mod(u, 1.0) * float(CM_TILES) - float(tu), 0.0, 1.0);
        float rv = clamp(mod(v, 1.0) * float(CM_TILES) - float(tv), 0.0, 1.0);
        if (flip) ru = 1.0 - ru;

        float oru = ru, orv = rv;
        if (rotation == 1)
        {
            oru = 1.0 - rv;
            orv = ru;
        }
        else if (rotation == 2)
        {
            oru = 1.0 - ru;
            orv = 1.0 - rv;
        }
        else if (rotation == 3)
        {
            oru = rv;
            orv = 1.0 - ru;
        }

        int px = int(clamp(oru, 0.0, 0.99999) * 16.0);
        int py = int(clamp(orv, 0.0, 0.99999) * 16.0);
        int tick = int(floor(t));

        if (CM_TILE_PHASE) tick += position / 7;

        float g = cm_texel(symbol, px, py, tick);
        float a = g * (0.5 + 1.0 / float(mult)) * (1.0 - smoothstep(0.15, 0.48, abs(rawv - 0.5)));

        col += cm_starColor(ctype, rand1, rand2, rand3, rawu, g, i, t) * a;
    }

    return col;
}

int cosmicMarkerType(vec3 c)
{
    if (c.r < 0.94 || c.b < 0.94 || c.g > 0.60) return -1;
    return int(floor(c.g * 255.0 / 8.0 + 0.5));
}

float cosmicStar(vec3 col, int ctype, float t, float brightness)
{
    vec3 bg = clamp(cm_background(ctype, t) * brightness, 0.0, 1.0);
    return clamp(max(col.r - bg.r, max(col.g - bg.g, col.b - bg.b)) * CM_STAR_SENS, 0.0, 1.0);
}

float cosmicMaskAlpha(float maskAlpha, float star)
{
    if (maskAlpha <= 0.0)  return 0.0;
    if (CM_MASK_MODE == 0) return 1.0;
    if (CM_MASK_MODE == 1) return maskAlpha;

    float starAlpha = star * pow(maskAlpha, CM_STAR_FADE);

    return clamp(max(starAlpha, maskAlpha * CM_MASK_GAIN), 0.0, 1.0);
}

float cosmicAlphaGain(float maskAlpha, float star)
{
    return (maskAlpha > 0.0) ? cosmicMaskAlpha(maskAlpha, star) / maskAlpha : 0.0;
}

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
