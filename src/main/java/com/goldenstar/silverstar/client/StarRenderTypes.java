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

/**
 * 星空层的 {@link RenderType}（渲染类型）。
 *
 * <p>RenderType 可以理解成“这一批三角形该怎么画”的一整套开关：用哪个着色器、贴哪张图、要不要混合、
 * 要不要测深度、写不写颜色……原版把它切成一个个小碎片（StateShard），拼起来复用。</p>
 *
 * <p>这里所有碎片都是自己 new 的，没用原版现成的常量——那些常量原来是 {@code protected static final}，
 * 外部拿不到，要拿就得加 Access Transformer 改访问权限，不如自己造一份干净。</p>
 *
 * <p><b>只有一条入口：</b>{@link #type(StarSlot, ResourceLocation)}。
 * 用哪份程序、怎么混合、图元类型，全部由槽位决定（见 {@link StarSlot}），
 * 缓存键也由槽位自动拼出来，不再靠手写字符串——以前漏打一个 {@code "cpg@"} 就会让两把刀共用同一个
 * RenderType，于是金色幻影剑被画成银的。</p>
 */
public final class StarRenderTypes {
    /** 只有星星、A 通道全满，给次元斩球用（形状由模型几何决定）。 */
    public static final ResourceLocation STARS_PLAIN =
            new ResourceLocation(Silverstar.MODID, "textures/misc/stars_plain.png");

    /** 每种（程序, 贴图）组合只造一个 RenderType，造过就缓存。 */
    private static final Map<String, RenderType> CACHE = new HashMap<>();

    /** 次元斩球：几乎是个洞，亮度压到很低。不属于任何一族，只有一份。 */
    public static RenderType dim(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("dim@" + sheet,
                key -> create("stars_dim", StarSlot.Kind.ADDITIVE, sheet, StarShaders::dim));
    }

    /** 完美次元斩额外斩出的那几刀：深蓝，不属于任何一族，两把刀都会用到。 */
    public static RenderType arcBlue(ResourceLocation sheet) {
        return CACHE.computeIfAbsent("arc_blue@" + sheet,
                key -> create("cosmic_arc_blue", StarSlot.Kind.ADDITIVE, sheet, StarShaders::arcBlue));
    }

    /**
     * 某个槽位用的渲染类型。
     *
     * <p>{@code sheet} 一般是原版自己的贴图：它的 alpha 就是形状（那道弧、那把剑、那片波），
     * 星点则由 cosmic 按空间方向算，于是形状无论怎么动，星域都是“印在同一片天上”的。</p>
     */
    public static RenderType type(StarSlot slot, ResourceLocation sheet) {
        String program = slot.program();
        return CACHE.computeIfAbsent(program + "@" + sheet, key -> create(program, slot.kind(),
                sheet, () -> StarShaders.get(slot)));
    }

    /**
     * 按槽位的混合方式造一个 RenderType。
     *
     * <p>缓冲区和图元类型是踩过坑的地方，注释留在下面各处。</p>
     */
    private static RenderType create(String name, StarSlot.Kind kind, ResourceLocation sheet,
                                     java.util.function.Supplier<ShaderInstance> shader) {
        boolean arm = kind == StarSlot.Kind.ARM;
        boolean solid = kind == StarSlot.Kind.SOLID;
        return RenderType.create(
                "silverstar_stars_" + name,
                DefaultVertexFormat.BLOCK,
                // 刀身和光效都是 .obj 模型 → 三角形；左臂是我们自己拼的四边形 → QUADS。
                arm ? VertexFormat.Mode.QUADS : VertexFormat.Mode.TRIANGLES,
                // 左臂只有一个小壳，缓冲区小一点；光效一层能铺满半个屏幕，给大的。
                arm ? 2048 : 2097152,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(shader))
                        .setTextureState(new RenderStateShard.TextureStateShard(sheet, false, false))
                        // 刀身是实体，必须普通半透明混合，否则星域暗的地方会透过去，像刀是镂空的；
                        // 光效是光，加法叠在夜色上。
                        .setTransparencyState(solid || arm ? TRANSLUCENT : ADDITIVE)
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

    /** 普通半透明：给刀身和左臂用，暗的地方会盖住底下的东西。 */
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
