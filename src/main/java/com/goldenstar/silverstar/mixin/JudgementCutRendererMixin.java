package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.JudgementCutRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 次元斩的配色。
 *
 * <p>原版这个渲染器一共画三段：</p>
 * <ol>
 *   <li>里面那颗球（缩放 0.95），走 {@code renderOverridedReverseLuminous}——这条路会反色，球才变黑；</li>
 *   <li>外面套的一圈 15 倍大的扁平光晕，同样走反色那条；</li>
 *   <li>绕着球转的五道风纹，循环五次，走 {@code renderOverridedColorWrite}。</li>
 * </ol>
 *
 * <p>球和光晕都保持原版——那两颗本来是“空间被切开的洞”，靠反色压黑才成立，套星空会变成一颗发光实心球，
 * 语义就变了。我们只把五道风纹换成星空那一路（开光影时整层交回原版）。</p>
 */
@Mixin(JudgementCutRenderer.class)
public abstract class JudgementCutRendererMixin {
    /** 真正干活的方法（泛型桥接 {@code m_7392_} 只是转调到这里）。 */
    private static final String RENDER_IMPL =
            "render(Lmods/flammpfeil/slashblade/entity/EntityJudgementCut;" + SSDescriptors.RENDER_REST;

    /** 五道风纹走的那一路。 */
    private static final String COLOR_WRITE_TARGET = SSDescriptors.COLOR_WRITE;

    /** 原版设置颜色（Color 对象形式），风纹用这个。 */
    private static final String SET_COL_COLOR = SSDescriptors.SET_COL_COLOR;

    /**
     * 五道风纹的颜色。
     *
     * <p>原版每画一道就先设一次颜色（颜色对象里带着这一道的 alpha）。这里把值留一份给星空那路当 tint，
     * 颜色本身照原样设下去——染色归原版，我们不插手。</p>
     */
    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = SET_COL_COLOR))
    private void silverstar$setWindCol(java.awt.Color colour) {
        if (StarOverlay.active()) {
            StarOverlay.setWindTint(colour);
        }
        BladeRenderState.setCol(colour);
    }

    /** 渲染开始：判定这次画的次元斩是不是纯银星的。 */
    @Inject(method = RENDER_IMPL, at = @At("HEAD"))
    private void silverstar$beginDim(EntityJudgementCut dim, float entityYaw, float partialTicks,
                                     PoseStack pose, MultiBufferSource buffers, int light,
                                     CallbackInfo ci) {
        StarOverlay.beginDim(dim, partialTicks);
    }

    /** 渲染结束：清状态。 */
    @Inject(method = RENDER_IMPL, at = @At("RETURN"))
    private void silverstar$endDim(EntityJudgementCut dim, float entityYaw, float partialTicks,
                                   PoseStack pose, MultiBufferSource buffers, int light,
                                   CallbackInfo ci) {
        StarOverlay.end();
    }

    /** 风纹：不装光影时用星空（和刀光同一套），装了光影就整层交回原版。 */
    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = COLOR_WRITE_TARGET))
    private void silverstar$wind(ItemStack stack, WavefrontObject model, String group,
                                 ResourceLocation texture, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        if (StarOverlay.active()) {
            StarOverlay.drawWind(stack, model, group, texture, pose, buffers, light);
        } else {
            BladeRenderState.renderOverridedColorWrite(stack, model, group, texture, pose, buffers, light);
        }
    }
}
