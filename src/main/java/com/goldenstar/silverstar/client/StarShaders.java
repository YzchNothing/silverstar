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
import java.util.HashMap;
import java.util.Map;

/**
 * 星空着色器程序的登记表和参数刷新。
 *
 * <p><b>为什么一个槽位要一份独立程序：</b>uniform（画之前塞给着色器的参数）是跟着程序走的，
 * 而顶点是攒成一批最后统一提交，提交时才按当前 uniform 算。所以刀光淡到 0 的那一帧，
 * 跟它共用程序的圆环也会一起熄。于是「槽位各一份程序」，谁也不会改到别人。</p>
 *
 * <p><b>银和金的差别不再靠两份程序：</b>以前金的是另一套 {@code .fsh}，颜色写死在 json 默认值里，
 * 等于把「染成什么色」也钉死在程序上。现在只有一套程序，五组颜色参数（底色中心 / 振幅 / 相位、
 * 星点基色 / 抖动）全部走 uniform，由 {@link StarRender} 在画之前灌进去。</p>
 *
 * <p>另外两份不属于任何槽位的程序还留在这里：{@code stars_dim}（次元斩的球，亮度压得极低）
 * 和 {@code cosmic_arc_blue}（完美次元斩额外斩出的几刀，走深蓝）。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StarShaders {
    /** 次元斩中间那颗球，亮度压得很低，看起来像一个洞。 */
    private static final ResourceLocation ID_DIM = new ResourceLocation(Silverstar.MODID, "stars_dim");

    /** 完美次元斩额外斩出的那几刀：和风纹同一个深蓝。 */
    private static final ResourceLocation ID_ARC_BLUE =
            new ResourceLocation(Silverstar.MODID, "cosmic_arc_blue");

    /** 程序 id → 已加载的着色器。启动时被 {@link #onRegisterShaders} 填满。 */
    private static final Map<ResourceLocation, ShaderInstance> PROGRAMS = new HashMap<>();

    private StarShaders() {
    }

    /** 按程序 id 取着色器；还没加载好时返回 null（RenderType 的 supplier 允许这样）。 */
    public static ShaderInstance get(ResourceLocation id) {
        return PROGRAMS.get(id);
    }

    /** 某槽位用的那套程序。 */
    public static ShaderInstance get(StarSlot slot) {
        return PROGRAMS.get(new ResourceLocation(Silverstar.MODID, slot.program()));
    }

    /** 次元斩中间那颗球（亮度压得极低）。 */
    public static ShaderInstance dim() {
        return PROGRAMS.get(ID_DIM);
    }

    /** 完美次元斩额外斩出的那几刀用的深蓝 cosmic。 */
    public static ShaderInstance arcBlue() {
        return PROGRAMS.get(ID_ARC_BLUE);
    }

    /**
     * 刷新所有「跟刀光一起淡出」的槽位。
     *
     * @param time    游戏时间，驱动星空缓慢漂移
     * @param opacity 当前这一层的淡出值，实际不透明度由 {@link StarSlot#opacity(float)} 换算
     * @param palette 这一刀的颜色，null 时按纯银星兜底
     */
    public static void update(float time, float opacity, StarRender palette) {
        StarRender colours = palette == null ? StarRender.FALLBACK_SILVER : palette;
        // 球是实心的，星域铺得太开看着像贴纸，收紧一点更像"球里面是星空"
        apply(dim(), time, opacity, 1.9F, StarRender.FALLBACK_SILVER, null);
        apply(arcBlue(), time, opacity, 1.15F, StarRender.FALLBACK_SILVER, null);
        for (StarSlot slot : StarSlot.values()) {
            if (!slot.fades()) {
                continue;
            }
            apply(slot, time, slot.opacity(opacity), colours, null);
        }
    }

    /**
     * 刷新刀身那一份的参数。
     *
     * <p><b>为什么不和特效共用 {@link #update}：</b>特效那一套每帧都在改不透明度（刀光要淡出），
     * 刀的星空不需要淡出——两拨人往同一份程序上写，结果就是刀光淡到 0 的那一帧，
     * 刀身上的星空也被写成 0，看着像"跟着刀光一起消失"。所以刀身不透明度恒为 1，谁也别来改。</p>
     */
    public static void updateBlade(float time, StarRender palette) {
        applySteady(StarSlot.BLADE, time, palette);
    }

    /**
     * 刷新锁定圆环的参数。
     *
     * <p>它也不走 {@link #update}：那个方法的 opacity 是刀光的淡出值，圆环跟着走的话，
     * 挥刀最后一帧会闪一下。单独一份程序 + 固定 1.0，两边彻底不打架。</p>
     */
    public static void updateLockon(float time, StarRender palette) {
        applySteady(StarSlot.LOCKON, time, palette);
    }

    /**
     * 刷新左臂星空的参数。
     *
     * <p>{@code opacity} 恒为 1：手臂上"上淡下浓"是靠遮罩贴图的 alpha 做的，
     * 这里再乘一个系数会把梯度压扁。缩放 2.4 来自 {@link StarSlot#ARM}。</p>
     */
    public static void updateArm(float time, StarRender palette) {
        applySteady(StarSlot.ARM, time, palette);
    }

    /**
     * 给风纹那一摊单独刷参数。
     *
     * <p>原版风纹是循环画的五道，每一道的颜色和透明度都不同，都靠画之前设的那个颜色值区分。
     * 颜色值在 {@code BladeRenderState} 的私有静态字段里读不到，所以由 mixin 拦下设置动作时
     * 顺手存一份分数，这里只把"这一道自己的浓淡"乘进去。</p>
     *
     * <p>要不要让这一道自己的颜色参与，由调色板的 {@code laneWind} 决定：纯银星参与，
     * 金色量把整片星域统一成暖金，风纹就不该再被染成青绿。</p>
     */
    public static void updateWind(float time, float opacity, StarRender palette,
                                  float red, float green, float blue) {
        StarRender colours = palette == null ? StarRender.FALLBACK_SILVER : palette;
        float[] lane = colours.laneWind() ? new float[]{red, green, blue} : null;
        apply(StarSlot.WIND, time, opacity, colours, lane);
    }

    /** 不跟淡出走的那几种（刀身 / 锁环 / 左臂）：按恒定的不透明度 1 刷一遍。 */
    private static void applySteady(StarSlot slot, float time, StarRender palette) {
        apply(slot, time, 1.0F, palette == null ? StarRender.FALLBACK_SILVER : palette, null);
    }

    /**
     * 把「槽位基调 × 刀的颜色」一起塞进这一槽的程序。
     *
     * @param lane 额外乘进 tint 的颜色（风纹用），null 表示不乘
     */
    private static void apply(StarSlot slot, float time, float opacity, StarRender palette, float[] lane) {
        ShaderInstance shader = get(slot);
        apply(shader, time, opacity, slot.scale(), palette, lane);
        if (shader == null) {
            return;
        }
        applyColours(shader, palette);
        set(shader, "brightness", palette.brightness(slot));
        float[] tint = palette.tint(slot);
        if (lane != null) {
            tint[0] *= lane[0];
            tint[1] *= lane[1];
            tint[2] *= lane[2];
        }
        set(shader, "tint", tint[0], tint[1], tint[2]);
    }

    /**
     * 把时间类参数塞进某一份着色器（颜色类由 {@link #apply} 的另一半负责）。
     *
     * <p>{@code yaw} / {@code pitch} 固定填 0：这两个是原版用来让星空跟着玩家视线转的，
     * 我们用不到（刀光本身已经在转了），留着是为了不改着色器接口。</p>
     *
     * <p>{@code palette} 为 null 时只刷时间参数（次元斩的球、深蓝斩击那两份特例用）。</p>
     */
    private static void apply(ShaderInstance shader, float time, float opacity, float scale,
                              StarRender palette, float[] lane) {
        if (shader == null) {
            return;
        }
        set(shader, "time", time);
        set(shader, "yaw", 0.0F);
        set(shader, "pitch", 0.0F);
        set(shader, "externalScale", scale);
        set(shader, "opacity", opacity);
    }

    /** 把一整套星空颜色参数写进某份程序（底色三条曲线 + 星点颜色）。 */
    private static void applyColours(ShaderInstance shader, StarRender palette) {
        if (shader == null || palette == null) {
            return;
        }
        set(shader, "bgBase", palette.bgBase());
        set(shader, "bgAmp", palette.bgAmp());
        set(shader, "bgPhase", palette.bgPhase());
        set(shader, "starBase", palette.starBase());
        set(shader, "starJitter", palette.starJitter());
    }

    /**
     * 给某一份着色器设一个 uniform。
     *
     * <p>各份程序声明的 uniform 不完全一样（{@code cosmic} 那份没有 {@code yaw} / {@code pitch}），
     * 所以要先问一句有没有，没有就跳过——{@code safeGetUniform} 找不到时返回 null。</p>
     */
    private static void set(ShaderInstance shader, String name, float value) {
        if (shader == null) {
            return;
        }
        var uniform = shader.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    /** 一次性设三个分量（比如 {@code tint} 这种 vec3）。 */
    private static void set(ShaderInstance shader, String name, float x, float y, float z) {
        if (shader == null) {
            return;
        }
        var uniform = shader.safeGetUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    /** 一次性设一个 vec3（数组形式）。 */
    private static void set(ShaderInstance shader, String name, float[] values) {
        set(shader, name, values[0], values[1], values[2]);
    }

    /** 游戏启动时由 Forge 调：先按槽位登记全套程序，再补上两份特例。 */
    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        for (StarSlot slot : StarSlot.values()) {
            register(event, idOf(slot));
        }
        register(event, ID_DIM);
        register(event, ID_ARC_BLUE);
    }

    private static ResourceLocation idOf(StarSlot slot) {
        return new ResourceLocation(Silverstar.MODID, slot.program());
    }

    private static void register(RegisterShadersEvent event, ResourceLocation id) {
        try {
            // 顶点格式跟原版刀光一致：BLOCK（位置 + 颜色 + UV + 光照 + 法线），
            // 这样我们的 RenderType 能直接吃原版模型吐出来的顶点。
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.BLOCK),
                    shader -> PROGRAMS.put(id, shader)
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to load shader " + id, e);
        }
    }
}
