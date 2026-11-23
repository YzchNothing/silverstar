package com.goldenstar.silverstar.mixin;

/**
 * 拔刀剑 1.1.23 里被 mixin 盯上的那几个方法"长什么样"（JVM 方法描述符）。
 *
 * <p>Mixin 靠这些字符串精确定位方法和调用点。写成常量集中在这里，是因为以前每个 mixin
 * 各抄一份，同一个描述符最多抄了 3 遍——拔刀剑一旦改签名，漏改的那一份<b>不会报错</b>，
 * 只在日志里留一行 WARN，那一路渲染就悄悄失效了。现在只留一处，改一次全中。</p>
 *
 * <p>注意：注解属性要求编译期常量，所以这里只做字符串拼接，不做运行期拼装。
 * 各 mixin 里的 {@code render(...)} 仍把实体类型写在自己文件里（看得见更直观），
 * 只把后面那截公共尾巴引自 {@link #RENDER_REST}。</p>
 *
 * <p>升级拔刀剑版本后要核对的就三件事：这五个方法还在不在、参数顺序变没变、
 * {@code mods.toml} 里的版本上限要不要跟进。</p>
 */
public final class SSDescriptors {
    private SSDescriptors() {
    }

    /** {@code BladeRenderState} 的类描述符，省得每行都写一遍全名。 */
    public static final String BLADE_RENDER_STATE =
            "Lmods/flammpfeil/slashblade/client/renderer/util/BladeRenderState;";

    /** {@code WavefrontObject} 的类描述符。 */
    public static final String WAVEFRONT_OBJECT =
            "Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;";

    /**
     * 各个渲染器 {@code render(...)} 里"实体参数之后"的那一段尾巴：
     * 两个 float（实体朝向、帧插值）、位姿栈、顶点缓冲、光照值。
     */
    public static final String RENDER_REST =
            "FF"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "I)V";

    /**
     * {@code BladeRenderState.renderOverrided*} 系列里，
     * 从 {@code String group} 开始到方法结尾的那一段。
     */
    private static final String OVERRIDED_REST =
            "Ljava/lang/String;"
            + "Lnet/minecraft/resources/ResourceLocation;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "I)V";

    /** 普通混合那一路的绘制：刀光、风纹用它。 */
    public static final String COLOR_WRITE =
            BLADE_RENDER_STATE + "renderOverridedColorWrite("
            + "Lnet/minecraft/world/item/ItemStack;"
            + WAVEFRONT_OBJECT
            + OVERRIDED_REST;

    /** 自发光那一路的绘制：刀光的后两层、幻影剑、斩击波用它。 */
    public static final String LUMINOUS =
            BLADE_RENDER_STATE + "renderOverridedLuminous("
            + "Lnet/minecraft/world/item/ItemStack;"
            + WAVEFRONT_OBJECT
            + OVERRIDED_REST;

    /** 底层那个 8 参版本，多一个 RenderType getter 和一个 useGlint。 */
    public static final String OVERRIDED =
            BLADE_RENDER_STATE + "renderOverrided("
            + "Lnet/minecraft/world/item/ItemStack;"
            + WAVEFRONT_OBJECT
            + "Ljava/lang/String;"
            + "Lnet/minecraft/resources/ResourceLocation;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "I"
            + "Ljava/util/function/Function;"
            + "Z)V";

    /** 设置当层颜色（int 形式，ARGB）。 */
    public static final String SET_COL_INT = BLADE_RENDER_STATE + "setCol(I)V";

    /** 设置当层颜色（{@code java.awt.Color} 形式）。 */
    public static final String SET_COL_COLOR = BLADE_RENDER_STATE + "setCol(Ljava/awt/Color;)V";
}
