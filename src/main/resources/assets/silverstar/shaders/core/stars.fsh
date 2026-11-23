#version 150

#define M_PI 3.1415926535897932384626433832795

// One texture unit only: Iris/Oculus rejects a shader that samples more than one texture from
// the same buffer builder. The star sheet and the vanilla silhouette therefore share one image
// (RGB = stars, alpha = shape), and both are read through the same sampler.

const float lightmix = 0.2f;

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

uniform float time;
uniform float yaw;
uniform float pitch;
uniform float externalScale;
uniform float opacity;
/** Overall intensity. Each program json ships its own default, so one shader source serves every layer. */
uniform float brightness;
/** Colour cast, also per program: the phantoms are tinted violet, everything else stays ice blue. */
uniform vec3 tint;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec4 normal;
in vec3 fPos;

out vec4 fragColor;

mat4 rotationMatrix(vec3 axis, float angle)
{
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor)
{
    if (vertexDistance <= fogStart) {
        return inColor;
    }
    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

void main (void)
{
    // vanilla silhouette, straight from the alpha channel of the shared sheet
    float shape = texture(Sampler0, texCoord0).a;

    float oneOverExternalScale = 1.0 / externalScale;

    // the star field is laid out on the view direction, exactly like the cosmic shader, so the
    // stars stay round and anchored to the world instead of smearing along the model's UVs
    vec4 dir = normalize(vec4(-fPos, 0));

    float sb = sin(pitch);
    float cb = cos(pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0));

    float sa = sin(-yaw);
    float ca = cos(-yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0));

    float rawu = 0.5 + (atan(dir.z, dir.x) / (2 * M_PI));
    float rawv = 0.5 + (asin(dir.y) / M_PI);

    // --- drifting nebula: the main body of the effect, slow colour bands rather than points ---
    float flow = rawu + time * 0.00003;
    float band = rawv * 1.7
               + 0.22 * sin(flow * 6.2831 * 1.3 + 1.7)
               + 0.13 * sin(flow * 6.2831 * 2.9 - 0.6);

    vec3 sky = mix(vec3(0.04, 0.07, 0.24), vec3(0.16, 0.09, 0.40), smoothstep(0.15, 0.75, band));
    sky = mix(sky, vec3(0.06, 0.24, 0.44), smoothstep(0.65, 1.25, band));
    sky *= 0.85 + 0.15 * sin(rawv * 9.0 + time * 0.0002);

    vec3 stars = vec3(0.0);
    float lumSum = 0.0;

    // eight layers, unfiltered: the original dense field
    for (int i = 0; i < 8; i++) {
        int mult = 8 - i;

        int j = i + 7;
        float rand1 = (j * j * 4321 + j * 8) * 2.0;
        int k = j + 1;
        float rand2 = (k * k * k * 239 + k * 37) * 3.6;
        float rand3 = rand1 * 347.4 + rand2 * 63.4;

        vec3 axis = normalize(vec3(sin(rand1), sin(rand2), cos(rand3)));
        vec4 ray = dir * rotationMatrix(axis, mod(rand3, 2 * M_PI));

        float ru = 0.5 + (atan(ray.z, ray.x) / (2 * M_PI));
        float rv = 0.5 + (asin(ray.y) / M_PI);

        float scale = mult * 0.5 + 2.75;
        float u = ru * scale * externalScale;
        float v = (rv + time * 0.0002 * oneOverExternalScale) * scale * 0.6 * externalScale;

        float star = texture(Sampler0, fract(vec2(u, v) * 0.37)).r;

        float a = star * (0.5 + (1.0 / mult))
                * (1.0 - smoothstep(0.15, 0.48, abs(rv - 0.5)));

        float r = (mod(rand1, 29.0) / 29.0) * 0.22 + 0.25;
        float g = (mod(rand2, 35.0) / 35.0) * 0.35 + 0.55;
        float b = (mod(rand1, 17.0) / 17.0) * 0.30 + 0.72;

        stars += vec3(r, g, b) * a;
        lumSum += a;
    }

    vec3 col = (sky + stars) * tint;

    vec3 shade = vertexColor.rgb * lightmix + vec3(1.0 - lightmix, 1.0 - lightmix, 1.0 - lightmix);
    col *= shade;

    // brightness drives the blend weight, not the colour: the nebula plus four star layers
    // saturate the rgb long before they saturate the alpha, so scaling the colour alone barely shows
    vec4 outCol = vec4(col, clamp(0.42 + lumSum * 0.9, 0.0, 1.0) * brightness);
    outCol *= shape;
    outCol.a *= opacity;

    outCol = clamp(outCol, 0.0, 1.0);

    fragColor = linear_fog(outCol * ColorModulator, vertexDistance, FogStart, FogEnd, FogColor);
}
