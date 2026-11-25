package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 星空层的 {@link RenderType}（渲染类型）。
 *
 * <p>RenderType 可以理解成“这一批三角形该怎么画”的一整套开关：用哪个着色器、贴哪张图、要不要混合、
 * 要不要测深度、写不写颜色……原版把它切成一个个小碎片（StateShard），拼起来复用。</p>
 *
 * <p>这里所有碎片都是自己 new 的，没用原版现成的常量——那些常量原来是 {@code protected static final}，
 * 外部拿不到，要拿就得加 Access Transformer 改访问权限，不如自己造一份干净。</p>
 *
 * <p>每一层（亮刀光 / 暗球 / 风纹 / 幻影剑）都自带一份着色程序，程序 json 里写死了自己的亮度和色调，
 * 所以暗球和亮刀光可以同时在一批里，不会互相抢参数。</p>
 */
public final class StarRenderTypes {
    /** 只有星星、A 通道全满，给次元斩球用（形状由模型几何决定）。 */
    public static final ResourceLocation STARS_PLAIN =
            new ResourceLocation(Silverstar.MODID, "textures/misc/stars_plain.png");

    /** 每种（程序, 贴图）组合只造一个 RenderType，造过就缓存。 */
    private static final Map<String, RenderType> CACHE = new HashMap<>();

    /** 次元斩球：几乎是个洞，亮度压到很低。 */
    public static RenderType dim(ResourceLocation sheet) {
        return get("d", sheet, StarShaders::dim);
    }

    /**
     * 刀身：无尽贪婪那种“整块星域”的观感。
     *
     * <p>它跟上面四层不是一回事——那四层是光效，靠加法混合叠在夜色上；刀身本体是实心的，
     * 必须用普通半透明混合（src*alpha + dst*(1-alpha)），否则星域暗的地方会变成透明的，
     * 看上去像刀是镂空的。</p>
     */
    public static RenderType cosmic(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("c@" + sheet, key -> createCosmic(sheet));
    }

    /** 「金色星」刀身：和 {@link #cosmic} 同一套状态，只是换一份金色调的着色器程序。 */
    public static RenderType cosmicGold(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cg@" + sheet, key -> createCosmicGold(sheet));
    }

    /**
     * 刀光那一层用的 cosmic。
     *
     * <p>和刀身那份的区别有两个：亮度在程序 json 里压到约 1/4（一道刀光要叠四层），
     * 混合换成加法（刀光是光，不是实体）。</p>
     *
     * <p>这里传进来的 {@code sheet} 是原版刀光自己的贴图，它的 alpha 就是那道弧的形状；
     * 星点则按空间方向算，四层各自 UV 怎么挪都落在同一片星空上，所以不会出现四份重影。</p>
     */
    public static RenderType cosmicArc(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("ca@" + sheet, key -> createCosmicArc("cosmic_arc",
                StarShaders::cosmicArc, sheet));
    }

    /**
     * 锁定目标的圆环：单独一份着色器程序。
     *
     * <p>和刀光分开是必须的——共用程序时，刀光淡出到 0 的那一帧圆环会跟着熄一下。</p>
     */
    public static RenderType cosmicLockon(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cl@" + sheet, key -> createCosmicArc("cosmic_lockon",
                StarShaders::cosmicLockon, sheet));
    }

    /** 「金色星」的刀光：金色 fragment，其余与 {@link #cosmicArc} 完全一致。 */
    public static RenderType cosmicArcGold(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cag@" + sheet, key -> createCosmicArc("cosmic_arc_gold",
                StarShaders::cosmicGold, sheet));
    }

    /** 完美次元斩额外斩出的那几刀：和 wind 同一个深蓝，其余与刀光一致。 */
    public static RenderType cosmicArcBlue(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cab@" + sheet, key -> createCosmicArc("cosmic_arc_blue",
                StarShaders::cosmicArcBlue, sheet));
    }

    /** 幻影剑：星点稀疏版 cosmic，深紫。 */
    public static RenderType cosmicPhantom(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cp@" + sheet, key -> createCosmicArc("cosmic_phantom",
                StarShaders::cosmicPhantom, sheet));
    }

    /** 次元斩外圈的风纹：跟刀光同一套 cosmic，色调由 {@code StarShaders.updateWind} 每次单独塞。 */
    public static RenderType cosmicWind(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cw@" + sheet, key -> createCosmicArc("cosmic_wind",
                StarShaders::cosmicWind, sheet));
    }

    /** 金色星的风纹：参数一样，片元是金色那份。单独缓存，别和纯银星那份混。 */
    public static RenderType cosmicWindGold(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cwg@" + sheet, key -> createCosmicArc("cosmic_wind_gold",
                StarShaders::cosmicWindGold, sheet));
    }

    /** 斩击波（波刀龙胆这类 drive 招式）：跟刀光同一套 cosmic，只有一层。 */
    public static RenderType cosmicWave(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("cwv@" + sheet, key -> createCosmicArc("cosmic_wave",
                StarShaders::cosmicWave, sheet));
    }

    /**
     * 左臂星空：半透明叠加在小臂外面的一层壳。
     *
     * <p>和刀身那份（{@link #cosmic}）的区别：那边是三角形（模型来自 .obj），这边是我们自己拼的四边形；
     * 透明度完全交给遮罩贴图的 alpha 决定，程序里 {@code opacity} 恒为 1。</p>
     */
    public static RenderType cosmicArm(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("car@" + sheet, key -> createCosmicArm(sheet));
    }

    /** 左臂星空壳的金色版：同一套几何，只换着色器程序。 */
    public static RenderType cosmicArmGold(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("carg@" + sheet, key -> createCosmicArm("silverstar_stars_arm_gold", sheet, StarShaders::cosmicArmGold));
    }

    private static RenderType createCosmicArm(ResourceLocation sheet) {
        return createCosmicArm("silverstar_stars_arm", sheet, StarShaders::cosmicArm);
    }

    private static RenderType createCosmicArm(String name, ResourceLocation sheet,
                                              java.util.function.Supplier<net.minecraft.client.renderer.ShaderInstance> shader) {
        return RenderType.create(
                name,
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                2048,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(sheet, false, false))
                        .setTransparencyState(TRANSLUCENT)
                        .setDepthTestState(LEQUAL_DEPTH)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createCosmicArc(String tag, Supplier<ShaderInstance> shader, ResourceLocation sheet) {
        return RenderType.create(
                "silverstar_stars_" + tag,
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.TRIANGLES,
                2097152,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(sheet, false, false))
                        .setTransparencyState(ADDITIVE)
                        .setDepthTestState(LEQUAL_DEPTH)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType createCosmic(ResourceLocation sheet) {
        return createCosmic("silverstar_stars_cosmic", sheet, StarShaders::cosmic);
    }

    private static RenderType createCosmicGold(ResourceLocation sheet) {
        return createCosmic("silverstar_stars_cosmic_gold", sheet, StarShaders::cosmicGold);
    }

    private static RenderType createCosmic(String name, ResourceLocation sheet,
                                           Supplier<ShaderInstance> shader) {
        return RenderType.create(
                name,
                DefaultVertexFormat.BLOCK,
                // 必须是三角形：模型来自 .obj，每个面就是三个顶点。
                // 写成 QUADS 的话，每 4 个顶点才凑一个图元，模型交上来的三角形会被强行拼接，
                // 于是画面里两个模型之间会拖出一条线（顶点数据全错位了）。
                // 拔刀剑自己的 slashblade_blend 用的也是 TRIANGLES。
                VertexFormat.Mode.TRIANGLES,
                2097152,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(sheet, false, false))
                        .setTransparencyState(TRANSLUCENT)
                        .setDepthTestState(LEQUAL_DEPTH)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    private static RenderType get(String tag, ResourceLocation sheet, Supplier<ShaderInstance> shader) {
        return CACHE.computeIfAbsent(tag + "@" + sheet, key -> create(tag, sheet, shader));
    }

    private static RenderType create(String tag, ResourceLocation sheet, Supplier<ShaderInstance> shader) {
        RenderStateShard.ShaderStateShard shaderState = new RenderStateShard.ShaderStateShard(shader);
        RenderStateShard.TextureStateShard texture =
                new RenderStateShard.TextureStateShard(sheet, false, false);
        return RenderType.create(
                "silverstar_stars_" + tag,
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                // 缓冲区大小：2097152 字节，够一层刀光用，写大了只是白占内存
                2097152,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(shaderState)
                        .setTextureState(texture)
                        .setTransparencyState(ADDITIVE)
                        .setDepthTestState(LEQUAL_DEPTH)
                        .setLightmapState(LIGHTMAP)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }

    /**
     * 加法混合：颜色直接往画面上“加”，亮的更亮，黑的地方等于没画。
     *
     * <p>星空就该是这种效果——星星加在夜色上，而不是把底下的东西盖住。参数含义是
     * “源颜色乘源 alpha，加到目标颜色上”。</p>
     */
    private static final RenderStateShard.TransparencyStateShard ADDITIVE =
            new RenderStateShard.TransparencyStateShard("silverstar_stars_additive",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(
                                GlStateManager.SourceFactor.SRC_ALPHA,
                                GlStateManager.DestFactor.ONE,
                                GlStateManager.SourceFactor.ONE,
                                GlStateManager.DestFactor.ZERO);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    /** 深度测试用 515（GL_LEQUAL：小于等于就画），让星空层能贴在自己身上而不被自己挡掉。 */
    private static final RenderStateShard.DepthTestStateShard LEQUAL_DEPTH =
            new RenderStateShard.DepthTestStateShard("silverstar_stars_lequal", 515);

    /** 普通半透明：给刀身用，暗的地方会盖住底下的东西，刀看起来才是实心的。 */
    private static final RenderStateShard.TransparencyStateShard TRANSLUCENT =
            new RenderStateShard.TransparencyStateShard("silverstar_stars_translucent",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    /** 跟随世界光照：白天和夜里亮度不一样，不会白天也像夜里那样亮。 */
    private static final RenderStateShard.LightmapStateShard LIGHTMAP =
            new RenderStateShard.LightmapStateShard(true);

    /** 关掉背面剔除：刀光是个薄片，两面都要能看见。 */
    private static final RenderStateShard.CullStateShard NO_CULL =
            new RenderStateShard.CullStateShard(false);

    /** 写颜色但不写深度：星空层不该影响后面的深度判断。 */
    private static final RenderStateShard.WriteMaskStateShard COLOR_WRITE =
            new RenderStateShard.WriteMaskStateShard(true, false);

    private StarRenderTypes() {
    }
}
