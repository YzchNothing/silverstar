package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/**
 * 加载星空着色器，一共四份。
 *
 * <p>四份的 {@code .vsh} / {@code .fsh} 源码是同一套，区别只在各自的 {@code .json} 里：默认亮度不同、
 * 默认色调不同。所以刀光、次元斩的球、风纹、幻影剑可以用同一个算法画出四种观感。</p>
 *
 * <p><b>为什么必须分成四个程序，而不是一个程序中途改参数：</b>着色器的 uniform（可以理解为“画之前塞给
 * 着色器的参数”）是跟着程序走的，而同一批顶点是在最后统一提交的，提交时才真正按 uniform 去算。
 * 一个程序没法在一批里既画暗的球又画亮的刀光——后设的值会把前面的覆盖掉。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StarShaders {
    /** 次元斩中间那颗球，亮度压得很低，看起来像一个洞。 */
    private static final ResourceLocation ID_DIM = new ResourceLocation(Silverstar.MODID, "stars_dim");
    /**
     * 刀身专用的那一份：不是从贴图里采样星点，而是把星点当成 16x16 的点阵字形来画，
     * 观感跟原版「寰宇支配之剑」一致。具体原理写在 {@code cosmic.fsh} 的头部注释里。
     */
    private static final ResourceLocation ID_COSMIC = new ResourceLocation(Silverstar.MODID, "cosmic");
    /**
     * 刀光专用的 cosmic：和刀身同一套算法，但亮度压到约四分之一。
     *
     * <p>因为原版一道刀光要画四层，四层叠同一片星空，不压低就是四倍亮度，糊成一片白。</p>
     */
    private static final ResourceLocation ID_COSMIC_ARC = new ResourceLocation(Silverstar.MODID, "cosmic_arc");
    /** 完美次元斩额外斩出的那几刀：和风纹同一个深蓝。 */
    private static final ResourceLocation ID_COSMIC_LOCKON =
            new ResourceLocation(Silverstar.MODID, "cosmic_lockon");

    private static final ResourceLocation ID_COSMIC_ARC_BLUE =
            new ResourceLocation(Silverstar.MODID, "cosmic_arc_blue");
    /** 幻影剑：星点稀疏版（层数从 16 砍到 4），深紫。 */
    private static final ResourceLocation ID_COSMIC_PHANTOM =
            new ResourceLocation(Silverstar.MODID, "cosmic_phantom");
    /**
     * 次元斩外圈那五道风纹：和刀光同一套 cosmic，但每一道的颜色、透明度都不一样，
     * 所以单独一份程序，好在每次绘制前把自己那一道的色调塞进去。
     */
    private static final ResourceLocation ID_COSMIC_WIND =
            new ResourceLocation(Silverstar.MODID, "cosmic_wind");
    /**
     * 金色星专属的那一份风纹：和 {@link #ID_COSMIC_WIND} 同一套参数，
     * 只是片元换成金色那份（{@code silverstar:cosmic_gold}）。
     *
     * <p>不共用的原因和刀光一样：{@code tint} / 亮度是程序级状态，共用会把纯银星的青色风纹也染成金色。</p>
     */
    private static final ResourceLocation ID_COSMIC_WIND_GOLD =
            new ResourceLocation(Silverstar.MODID, "cosmic_wind_gold");
    /**
     * 波刀龙胆这类“斩击波”（{@code EntityDrive}）用的 cosmic。
     *
     * <p>它只有一层，不像刀光要叠四层，所以亮度比刀光那份（0.26）再低一点，
     * 免得一大片飞过去糊成白墙。</p>
     */
    private static final ResourceLocation ID_COSMIC_WAVE =
            new ResourceLocation(Silverstar.MODID, "cosmic_wave");
    /**
     * 左臂星空用的那一份：和刀身同一套算法，但透明度只由遮罩贴图的 alpha 决定，
     * 所以单独开一份程序，免得别人改 {@code opacity} 时把手臂一起改掉。
     */
    private static final ResourceLocation ID_COSMIC_ARM =
            new ResourceLocation(Silverstar.MODID, "cosmic_arm");

    /** 左臂星空壳的金色版（片元是 cosmic_gold，其余和青色那份一样）。 */
    private static final ResourceLocation ID_COSMIC_ARM_GOLD =
            new ResourceLocation(Silverstar.MODID, "cosmic_arm_gold");
    /**
     * 「金色星」刀身专用的那一份：同一套星空算法，只把 {@code tint} 换成金色、亮度抬一点。
     * 单独开一份，是因为 tint 是程序级的默认值 —— 和刀身共用的话，两把刀会一起变金。
     */
    private static final ResourceLocation ID_COSMIC_GOLD =
            new ResourceLocation(Silverstar.MODID, "cosmic_gold");
    /** 「金色星」的刀光：同一套金色 fragment，亮度按刀光压低（刀光要叠四层）。 */
    private static final ResourceLocation ID_COSMIC_ARC_GOLD =
            new ResourceLocation(Silverstar.MODID, "cosmic_arc_gold");

    private static ShaderInstance dim;
    private static ShaderInstance cosmic;
    private static ShaderInstance cosmicArc;
    private static ShaderInstance cosmicArcBlue;
    private static ShaderInstance cosmicLockon;
    private static ShaderInstance cosmicPhantom;
    private static ShaderInstance cosmicWind;
    private static ShaderInstance cosmicWindGold;
    private static ShaderInstance cosmicWave;
    private static ShaderInstance cosmicArm;
    private static ShaderInstance cosmicArmGold;
    private static ShaderInstance cosmicGold;
    private static ShaderInstance cosmicArcGold;

    /** 幻影剑的不透明度增益：加法混合下它偏透，乘一下才像实体剑。 */
    private static final float PHANTOM_OPACITY_GAIN = 1.8F;

    private StarShaders() {
    }

    /** 次元斩中间那颗球（亮度压得极低）。 */
    public static ShaderInstance dim() {
        return dim;
    }

    public static ShaderInstance cosmic() {
        return cosmic;
    }

    /** 刀光那层用的 cosmic（亮度已压低，见 {@link #ID_COSMIC_ARC}）。 */
    public static ShaderInstance cosmicArc() {
        return cosmicArc;
    }

    /** 锁定目标的圆环专用：单独一份程序，不和刀光抢 uniform。 */
    public static ShaderInstance cosmicLockon() {
        return cosmicLockon;
    }

    /** 完美次元斩额外斩击用的 cosmic：和风纹同一个深蓝。 */
    public static ShaderInstance cosmicArcBlue() {
        return cosmicArcBlue;
    }

    /** 幻影剑用的 cosmic：星点稀疏、深紫。 */
    public static ShaderInstance cosmicPhantom() {
        return cosmicPhantom;
    }

    /** 次元斩风纹用的 cosmic。 */
    public static ShaderInstance cosmicWind() {
        return cosmicWind;
    }

    /** 金色星那边的风纹。 */
    public static ShaderInstance cosmicWindGold() {
        return cosmicWindGold;
    }

    /** 斩击波（波刀龙胆这类 drive 招式）用的 cosmic。 */
    public static ShaderInstance cosmicWave() {
        return cosmicWave;
    }

    /** 左臂星空用的 cosmic。 */
    public static ShaderInstance cosmicArm() {
        return cosmicArm;
    }

    public static ShaderInstance cosmicArmGold() {
        return cosmicArmGold;
    }

    /** 「金色星」刀身用的 cosmic（金色调）。 */
    public static ShaderInstance cosmicGold() {
        return cosmicGold;
    }

    /**
     * 刷新会动的参数。每次画之前调一次，等这一批顶点真正提交时值就在那儿了。
     *
     * @param time    游戏时间，驱动星空缓慢漂移
     * @param opacity 当前这一层的不透明度，由淡入淡出算出来
     */
    public static void update(float time, float opacity) {
        // 球是实心的，星域铺得太开看着像贴纸，收紧一点更像“球里面是星空”
        apply(dim, time, opacity, 1.9F);
        // cosmic 那份是刀身专用的，不走这里——见 updateBlade()。
        apply(cosmicArc, time, opacity, 1.15F);
        apply(cosmicArcBlue, time, opacity, 1.15F);
        // 幻影剑的星域放大一点：星点稀疏之后，铺得开才像“剑上挂着几颗星”
        // 不透明度额外给一档增益：加法混合下它本来偏透，乘一下看着才像实体剑，
        // 淡入也在一帧内到位，不会有一段“很淡”的过渡。
        apply(cosmicPhantom, time, Math.min(1.0F, opacity * PHANTOM_OPACITY_GAIN), 1.45F);
        apply(cosmicWind, time, opacity, 1.15F);
        apply(cosmicWave, time, opacity, 1.15F);
        // 「金色星」的刀光。和 arc 分开一份，理由同刀身：程序级 tint 是共享状态，
        // 共用的话纯银星的刀光也会被染成金色。
        apply(cosmicArcGold, time, opacity, 1.15F);
    }

    /**
     * 刷新刀身那一份的动画参数。
     *
     * <p><b>为什么不和特效共用 {@link #update}：</b>uniform 是每份程序各存一份，但会一直留到下一次有人写它。
     * 特效那一套每帧都在改不透明度（刀光要淡出），刀的星空不需要淡出——两拨人往同一份程序上写，
     * 结果就是刀光淡到 0 的那一帧，刀身上的星空也被写成 0，看着像“跟着刀光一起消失”。
     * 所以刀身单独一份参数，不透明度恒为 1，谁也别来改。</p>
     */
    public static void updateBlade(float time) {
        apply(cosmic, time, 1.0F, 1.15F);
    }

    /**
     * 刷新「金色星」刀身那一份的参数。
     *
     * <p>和 {@link #updateBlade} 完全一样，只是换了一份程序 —— 金色写在
     * {@code cosmic_gold.json} 的 {@code tint} 默认值里，不需要每帧设。
     * 两把刀各用各的程序，谁也不会把谁的色调改掉。</p>
     */
    public static void updateBladeGold(float time) {
        apply(cosmicGold, time, 1.0F, 1.15F);
    }

    /**
     * 刷新左臂那一份的参数。
     *
     * <p>{@code opacity} 恒为 1：手臂上"上淡下浓"是靠遮罩贴图的 alpha 做的，
     * 这里再乘一个系数会把梯度压扁。</p>
     */
    /**
     * 给锁定圆环刷参数。
     *
     * <p>它<b>不走</b> {@code update(time, opacity)}：那个方法的 opacity 是刀光的淡出值，
     * 而圆环和刀光一旦共用程序，刀光淡到 0 的那一帧圆环就跟着消失（再下一帧又回来，看着像闪）。
     * 单独一份程序 + 固定 1.0，两边彻底不打架。</p>
     */
    public static void updateLockon(float time) {
        apply(cosmicLockon, time, 1.0F, 1.15F);
    }

    /**
     * 给左臂星空刷参数。
     *
     * <p>最后一个数是星域的"缩放"：u/v 都乘它，放大等于格子变多 → 星星更小更密。
     * 手臂只有 20×40 像素，用刀身那个 1.10 会让星点看着像纸屑，所以这里调到 2.4。</p>
     */
    public static void updateArm(float time) {
        apply(cosmicArm, time, 1.0F, 2.4F);
    }

    /** 金色版的左臂星空：外部缩放和青色那份保持一致，只换了颜色。 */
    public static void updateArmGold(float time) {
        apply(cosmicArmGold, time, 1.0F, 2.4F);
    }

    /**
     * 给风纹那边的 cosmic 单独刷参数，顺便把这一道的色调塞进去。
     *
     * <p>原版风纹是循环画的五道，每一道的颜色和透明度都不同，都靠画之前设的那个颜色值区分。
     * 颜色值我们读不到（在 {@code BladeRenderState} 的私有静态字段里），所以在 mixin 拦下设置动作时
     * 顺手存一份，这里再用 tint 还原。</p>
     *
     * @param opacity 已经乘过这一道自身 alpha 的不透明度
     */
    public static void updateWind(float time, float opacity, float red, float green, float blue) {
        apply(cosmicWind, time, opacity, 1.15F);
        if (cosmicWind != null) {
            set(cosmicWind, "tint", red, green, blue);
        }
    }

    /** 金色星的风纹：参数和纯银星那份完全一样，只是换了一份金色片元。 */
    public static void updateWindGold(float time, float opacity) {
        apply(cosmicWindGold, time, opacity, 1.15F);
    }

    /**
     * 把参数塞进某一份着色器。
     *
     * <p>{@code yaw} / {@code pitch} 固定填 0：这两个是原版用来让星空跟着玩家视线转的，
     * 我们用不到（刀光本身已经在转了），留着是为了不改着色器接口。</p>
     *
     * <p>亮度 {@code brightness} 和色调 {@code tint} 不在这里设——它们写在各份 {@code .json} 的默认值里，
     * 一份程序一套，设一次就够。</p>
     */
    private static void apply(ShaderInstance shader, float time, float opacity, float scale) {
        if (shader == null) {
            return;
        }
        set(shader, "time", time);
        set(shader, "yaw", 0.0F);
        set(shader, "pitch", 0.0F);
        set(shader, "externalScale", scale);
        set(shader, "opacity", opacity);
    }

    /**
     * 给某一份着色器设一个 uniform。
     *
     * <p>各份程序声明的 uniform 不完全一样（{@code cosmic} 那份没有 {@code yaw} / {@code pitch}），
     * 所以要先问一句有没有，没有就跳过——{@code safeGetUniform} 找不到时返回 null。</p>
     */
    private static void set(ShaderInstance shader, String name, float value) {
        var uniform = shader.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    /** 一次性设三个分量（比如 {@code tint} 这种 vec3）。 */
    private static void set(ShaderInstance shader, String name, float x, float y, float z) {
        var uniform = shader.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    /** 游戏启动时由 Forge 调，把四份着色器登记进去。 */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        register(event, ID_DIM, shader -> dim = shader);
        register(event, ID_COSMIC, shader -> cosmic = shader);
        register(event, ID_COSMIC_ARC, shader -> cosmicArc = shader);
        register(event, ID_COSMIC_ARC_BLUE, shader -> cosmicArcBlue = shader);
        register(event, ID_COSMIC_LOCKON, shader -> cosmicLockon = shader);
        register(event, ID_COSMIC_PHANTOM, shader -> cosmicPhantom = shader);
        register(event, ID_COSMIC_WIND, shader -> cosmicWind = shader);
        register(event, ID_COSMIC_WAVE, shader -> cosmicWave = shader);
        register(event, ID_COSMIC_ARM, shader -> cosmicArm = shader);
        register(event, ID_COSMIC_ARM_GOLD, shader -> cosmicArmGold = shader);
        register(event, ID_COSMIC_GOLD, shader -> cosmicGold = shader);
        register(event, ID_COSMIC_ARC_GOLD, shader -> cosmicArcGold = shader);
        register(event, ID_COSMIC_WIND_GOLD, shader -> cosmicWindGold = shader);
    }

    private static void register(RegisterShadersEvent event, ResourceLocation id,
                                 java.util.function.Consumer<ShaderInstance> setter) {
        try {
            // 顶点格式跟原版刀光一致：BLOCK（位置 + 颜色 + UV + 光照 + 法线），
            // 这样我们的 RenderType 能直接吃原版模型吐出来的顶点。
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.BLOCK),
                    setter
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader " + id, e);
        }
    }
}
