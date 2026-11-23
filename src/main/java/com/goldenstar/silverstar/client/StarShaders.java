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
     * 波刀龙胆这类“斩击波”（{@code EntityDrive}）用的 cosmic。
     *
     * <p>它只有一层，不像刀光要叠四层，所以亮度比刀光那份（0.26）再低一点，
     * 免得一大片飞过去糊成白墙。</p>
     */
    private static final ResourceLocation ID_COSMIC_WAVE =
            new ResourceLocation(Silverstar.MODID, "cosmic_wave");

    private static ShaderInstance dim;
    private static ShaderInstance cosmic;
    private static ShaderInstance cosmicArc;
    private static ShaderInstance cosmicArcBlue;
    private static ShaderInstance cosmicPhantom;
    private static ShaderInstance cosmicWind;
    private static ShaderInstance cosmicWave;

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

    /** 斩击波（波刀龙胆这类 drive 招式）用的 cosmic。 */
    public static ShaderInstance cosmicWave() {
        return cosmicWave;
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
        register(event, ID_COSMIC_PHANTOM, shader -> cosmicPhantom = shader);
        register(event, ID_COSMIC_WIND, shader -> cosmicWind = shader);
        register(event, ID_COSMIC_WAVE, shader -> cosmicWave = shader);
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
