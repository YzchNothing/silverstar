package com.goldenstar.silverstar.client;

/**
 * 星空会被画到哪几个「槽位」上：刀身、刀光、幻影剑、风纹、斩击波、锁定环、左臂。
 *
 * <p>一个槽位把「怎么染」全部写在一行里：用哪份着色器程序、怎么混、星域缩放多少、要不要跟着刀光
 * 淡出、以及这一层的基础乘色与亮度。以前这些数字散在 {@code StarShaders.update()} 的每一行、
 * {@code StarRenderTypes} 的每个方法、以及 {@code StarOverlay} 每个三目里，同一个槽位要记三处。</p>
 *
 * <p>「染成什么色」不在这里，在 {@link StarRender}（从刀自己的 json 读）：槽位给的是基调，
 * 刀给的是这一套星空的具体颜色，最后在 {@link StarShaders} 里乘起来。</p>
 */
public enum StarSlot {
    /** 刀身本体：实心，普通半透明混合；不跟刀光淡出（刀光淡到 0 时刀上的星空不能一起没）。 */
    BLADE("cosmic", Kind.SOLID, 1.15F, false, 1.0F,
            new float[]{1.0F, 1.0F, 1.0F}, 1.00F),
    /** 原版那道刀光的其中一层（一个实体画四层）。 */
    ARC("cosmic_arc", Kind.ADDITIVE, 1.15F, true, 1.0F,
            new float[]{1.0F, 1.0F, 1.0F}, 0.26F),
    /** 幻影剑：整把剑发光。参数和刀光保持一致（星域缩放、亮度、乘色都不搞特殊），只是程序用星点稀疏的那份。 */
    PHANTOM("cosmic_phantom", Kind.ADDITIVE, 1.15F, true, 1.0F,
            new float[]{1.0F, 1.0F, 1.0F}, 0.26F),
    /** 次元斩外圈的五道风纹。 */
    WIND("cosmic_wind", Kind.ADDITIVE, 1.15F, true, 1.0F,
            new float[]{1.0F, 1.0F, 1.0F}, 0.34F),
    /** 波刀龙胆这类斩击波（EntityDrive）。 */
    WAVE("cosmic_wave", Kind.ADDITIVE, 1.15F, true, 1.0F,
            new float[]{0.88F, 0.97F, 1.0F}, 0.85F),
    /** 锁定目标的那圈光环：不跟刀光淡出，否则挥刀最后一帧圆环会跟着熄一下。 */
    LOCKON("cosmic_lockon", Kind.ADDITIVE, 1.15F, false, 1.0F,
            new float[]{1.0F, 1.0F, 1.0F}, 0.55F),
    /** 左臂的星空壳：自己的几何（四边形），不透明度全交给遮罩贴图的 alpha。 */
    ARM("cosmic_arm", Kind.ARM, 2.4F, false, 1.0F,
            new float[]{0.82F, 0.90F, 1.0F}, 0.95F);

    /** 这批顶点该怎么画：混合方式、图元类型、缓冲区大小都跟着它走。 */
    public enum Kind {
        /** 刀身：三角形图元 + 普通半透明混合（暗的地方要盖住底下，刀看着才是实心的）。 */
        SOLID,
        /** 所有光效层：三角形图元 + 加法混合。 */
        ADDITIVE,
        /** 左臂壳：四边形图元 + 普通半透明混合。 */
        ARM
    }

    private final String program;
    private final Kind kind;
    private final float scale;
    private final boolean fades;
    private final float opacityGain;
    private final float[] defaultTint;
    private final float defaultBrightness;

    StarSlot(String program, Kind kind, float scale, boolean fades, float opacityGain,
             float[] defaultTint, float defaultBrightness) {
        this.program = program;
        this.kind = kind;
        this.scale = scale;
        this.fades = fades;
        this.opacityGain = opacityGain;
        this.defaultTint = defaultTint;
        this.defaultBrightness = defaultBrightness;
    }

    /** 这一层用的着色器程序名（不带命名空间），在 {@code assets/silverstar/shaders/core/} 下。 */
    public String program() {
        return program;
    }

    /** 混合与图元方式。 */
    public Kind kind() {
        return kind;
    }

    /** 星域的外部缩放：u/v 都乘它，越大格子越多、星星越小越密。 */
    public float scale() {
        return scale;
    }

    /** 是否跟着刀光的淡出值走（只有光效层走，刀身 / 锁环 / 左臂恒为 1）。 */
    public boolean fades() {
        return fades;
    }

    /** 把刀光的淡出值换成这一层的实际不透明度（幻影剑额外乘一档，加法混合下它本来偏透）。 */
    public float opacity(float fade) {
        return Math.min(1.0F, fade * opacityGain);
    }

    /** 这一层的基础乘色（刀还可以在 json 里按槽位覆盖）。 */
    public float[] defaultTint() {
        return defaultTint.clone();
    }

    /** 这一层的基础亮度（刀可以在 json 里按槽位覆盖）。 */
    public float defaultBrightness() {
        return defaultBrightness;
    }
}
