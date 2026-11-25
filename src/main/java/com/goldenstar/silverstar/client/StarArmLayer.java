package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import com.goldenstar.silverstar.config.SSConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

/**
 * 左臂的星空壳。
 *
 * <p><b>为什么是"壳"而不是"贴图"：</b>直接改玩家皮肤会把整个模型都换掉，而我们只想让小臂下半段变样。
 * 所以做法是在小臂外面再套一层比它略大的长方体，用星空着色器画、半透明叠加——皮肤一点没动，
 * 只是"外面多了一层雾"。</p>
 *
 * <p><b>怎么跟着手臂动：</b>原版 {@code ModelPart} 自带 {@code translateAndRotate}，调一次就把这条手臂
 * 当前的位移和旋转压进 {@link PoseStack}。走路摆臂、潜行、游泳、举盾这些姿态原版早就算好了，
 * 我们照抄，一行手算都不用写。</p>
 *
 * <p><b>上淡下浓：</b>透明度由遮罩贴图 {@code textures/misc/arm_mask.png} 的 alpha 决定，竖着铺在手臂上——
 * 上面几乎看不见，到手腕处最浓。</p>
 *
 * <p><b>触发条件只有一条：</b>纯银星在第 1 格、且不在主手上。刀在手上、刀在别的格子、没带刀，一律不画。
 * 每个客户端各算各的，所以别人看你也能看见。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class StarArmLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** 竖向渐变遮罩（alpha 决定透明度，见 _材质/手臂/make_arm_mask.py）。 */
    private static final ResourceLocation MASK =
            new ResourceLocation(Silverstar.MODID, "textures/misc/arm_mask.png");

    /** 壳比手臂外扩多少（模型像素，16 像素 = 1 格）：0.3 像素，几乎贴着小臂。 */
    private static final float PAD = 0.3F;

    /** 模型坐标是"像素"，16 像素 = 1 格；顶点要自己乘回去。 */
    private static final float U = 1.0F / 16.0F;

    /** 小臂模型里 y 从 -2（肘）到 10（腕），这里只盖下半段：第 2 段的起点到手腕。 */
    private static final float TOP = 1.4F;
    private static final float BOTTOM = 11.0F;

    /** 触发它的刀：0 = 不画，1 = 纯银星，2 = 金色星。 */
    private static final int NONE = 0;
    private static final int SILVER = 1;
    private static final int GOLD = 2;

    /** 超过 30 格就不画：远处一个像素的东西，不值得跑一遍 16 层星空。 */
    private static final double MAX_DIST_SQR = 30.0D * 30.0D;

    public StarArmLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int blade = bladeShown(player);
        if (blade == NONE) {
            return;
        }
        // 光影接管核心着色器时我们的星空画不出来，与其画成一块黑，不如当作"这片力量隐形"。
        if (StarOverlay.shaderPackActive()) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        // 原版左臂的方块是 addBox(-1, -2, -2, w, 12, 4)：x 从 -1 起，宽度普通模型 4、纤细模型 3。
        // PlayerModel 里的 slim 是私有字段，只能问玩家自己的皮肤类型。
        boolean slim = "slim".equals(player.getModelName());
        float x0 = -1.0F - PAD;
        float x1 = (slim ? 2.0F : 3.0F) + PAD;
        float z0 = -2.0F - PAD;
        float z1 = 2.0F + PAD;
        float y0 = TOP;
        float y1 = BOTTOM;

        float time = (Minecraft.getInstance().level == null ? 0L
                : Minecraft.getInstance().level.getGameTime()) * 0.05F;
        boolean gold = useGold(blade, heldOf(player));
        if (gold) {
            StarShaders.updateArmGold(time);
        } else {
            StarShaders.updateArm(time);
        }

        RenderType type = gold ? StarRenderTypes.cosmicArmGold(MASK) : StarRenderTypes.cosmicArm(MASK);
        VertexConsumer vc = buffers.getBuffer(type);

        pose.pushPose();
        model.leftArm.translateAndRotate(pose);
        Matrix4f mat = pose.last().pose();

        // 六个面：颜色统一给白，透明度全交给遮罩贴图的 alpha。
        // 前后左右四个面按 y 取 v，于是"上淡下浓"是连续的；上下两个小面 v 固定。
        quad(vc, mat, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(vc, mat, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(vc, mat, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(vc, mat, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(vc, mat, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(vc, mat, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);

        pose.popPose();

        if (buffers instanceof MultiBufferSource.BufferSource source) {
            // 立刻提交：这一层的 uniform 是按程序存的，留着不画会被别的绘制覆盖掉。
            source.endBatch(type);
        }
    }

    /** 画一个四边形，v 坐标按它在手臂上的高度算，让渐变的走向和贴图一致。 */
    private static void quad(VertexConsumer vc, Matrix4f mat, int light,
                             float xa, float ya, float za,
                             float xb, float yb, float zb,
                             float xc, float yc, float zc,
                             float xd, float yd, float zd) {
        float[] xs = {xa, xb, xc, xd};
        float[] ys = {ya, yb, yc, yd};
        float[] zs = {za, zb, zc, zd};
        for (int i = 0; i < 4; i++) {
            float v = (ys[i] - TOP) / (BOTTOM - TOP);
            vc.vertex(mat, xs[i] * U, ys[i] * U, zs[i] * U)
                    .color(255, 255, 255, 255)
                    .uv(0.5F, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(0.0F, 0.0F, 1.0F)
                    .endVertex();
        }
    }

    /**
     * 该不该画，以及用哪把刀的颜色染。返回 {@link #NONE} 表示不画。
     *
     * <p>出现条件由配置 {@code armRenderMode} 决定：</p>
     * <ul>
     *   <li>默认：第 1 格是我们的刀（纯银星或金色星）、但主手上拿的是别的东西；</li>
     *   <li>选项一：快捷栏任意一格有我们的刀就行（主手拿着刀时也显示）；</li>
     *   <li>选项二：不看背包，一律显示。</li>
     * </ul>
     *
     * <p>旁观、隐身、离得太远这三种情况三种模式都要排除：隐身的人身上发光会露馅，
     * 远处的星空壳只是一小块噪点，白跑一遍星空着色器。</p>
     */
    private static int bladeShown(AbstractClientPlayer player) {
        if (player.isSpectator() || player.isInvisible()) {
            return NONE;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return NONE;
        }
        if (mc.player.distanceToSqr(player) > MAX_DIST_SQR) {
            return NONE;
        }
        int mode = SSConfig.armRenderMode();
        if (mode != SSConfig.ARM_MODE_DEFAULT) {
            // 拿着刀的时候由手上的刀说了算：这是最直接的一票，比背包里放着什么更该信。
            int held = kindOf(player.getMainHandItem());
            if (held != NONE) {
                return held;
            }
        }
        if (mode == SSConfig.ARM_MODE_ALWAYS) {
            // 不看背包也必须有个颜色。“自动”时这样决定：快捷栏里能找到金色星就用金，
            // 否则用青——这样选项二不至于永远只有一种颜色。
            Inventory hotbar = player.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (kindOf(hotbar.getItem(slot)) == GOLD) {
                    return GOLD;
                }
            }
            return SILVER;
        }
        if (mode == SSConfig.ARM_MODE_HOTBAR) {
            // 快捷栏 = 背包的第 0~8 格。
            Inventory slots = player.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                int kind = kindOf(slots.getItem(slot));
                if (kind != NONE) {
                    return kind;
                }
            }
            return NONE;
        }
        if (kindOf(player.getMainHandItem()) != NONE) {
            return NONE;                         // 刀在手上，力量在刀上
        }
        return kindOf(player.getInventory().getItem(0));
    }

    /**
     * 这一层该用金色还是青色。
     *
     * <p>手上的刀排最前：{@code armRenderMode} 不为 0 时，主手拿着金色星就是金色、拿着纯银星就是青色，
     * 这时候配置里的固定色让位——毕竟人手上正握着哪把，是最直接的一票。
     * 没拿刀时就按 {@code armColorMode}：固定青 / 固定金 / 自动（跟着触发它的那把刀走）。</p>
     */
    private static boolean useGold(int blade, int held) {
        int mode = SSConfig.armColorMode();
        if (blade != NONE && blade == held) {
            // 这个 blade 就是这个人手上那把，直接用它自己的颜色。
            return blade == GOLD;
        }
        if (mode == SSConfig.ARM_COLOR_GOLD) {
            return true;
        }
        if (mode == SSConfig.ARM_COLOR_SILVER) {
            return false;
        }
        return blade == GOLD;
    }

    /** 这个玩家主手上拿着我们哪把刀（0 = 没有）。 */
    private static int heldOf(AbstractClientPlayer player) {
        return kindOf(player.getMainHandItem());
    }

    /** 这把刀是我们的哪一把：0 = 不是，1 = 纯银星，2 = 金色星。 */
    private static int kindOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return NONE;
        }
        String key = stack.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getTranslationKey())
                .orElse(null);
        if (key == null) {
            return NONE;
        }
        if (StarOverlay.BLADE_TRANSLATION_KEY.equals(key)) {
            return SILVER;
        }
        if (StarOverlay.GOLDEN_TRANSLATION_KEY.equals(key)) {
            return GOLD;
        }
        return NONE;
    }
}
