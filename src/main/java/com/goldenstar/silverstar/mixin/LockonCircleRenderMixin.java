package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.LockonCircleRender;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 锁定目标时那个圆环，换成星空。
 *
 * <p>这个环不是实体，是拔刀剑在 {@code RenderLivingEvent} 里顺手画的：一个底座加两条血条弧，
 * 一共三次 {@code BladeRenderState.renderOverridedLuminous*} 调用。形状来自
 * {@code slashblade:model/util/lockon.obj} 的三个组（{@code lockonBase}、{@code lockonHealthMask}、
 * {@code lockonHealth}），颜色来自 {@code setCol}。</p>
 *
 * <p>做法和刀光一样：<b>不额外画一层</b>，只把这三次调用的 {@link net.minecraft.client.renderer.RenderType}
 * 换掉，位姿、模型、组名、贴图、光照全用原版算好的那份，形状不会跑。</p>
 *
 * <p>它没有“主人”可查，判定只能落到本地玩家手上那把刀——也就是说，别人手上是纯银星时，
 * 你看到他锁定的圆环仍是原样（他那边看是星空）。这一点和刀的星空渲染一致。</p>
 */
@Mixin(LockonCircleRender.class)
public abstract class LockonCircleRenderMixin {
    /** 被注入的方法：{@code onRenderLiving(RenderLivingEvent)}。 */
    private static final String RENDER_LIVING =
            "onRenderLiving(Lnet/minecraftforge/client/event/RenderLivingEvent;)V";

    /** 底座那一层（自发光、不写深度）。 */
    private static final String LUMINOUS_TARGET = SSDescriptors.LUMINOUS;

    /** 血条那两层（自发光 + 写深度）。 */
    private static final String DEPTH_WRITE_TARGET = SSDescriptors.LUMINOUS_DEPTH_WRITE;

    @Redirect(method = RENDER_LIVING, at = @At(value = "INVOKE", target = LUMINOUS_TARGET))
    private void silverstar$lockonBase(ItemStack stack, WavefrontObject model, String group,
                                       ResourceLocation texture, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
        if (StarOverlay.lockonStarry()) {
            StarOverlay.drawLockon(stack, model, group, texture, pose, buffers, light);
        } else {
            BladeRenderState.renderOverridedLuminous(stack, model, group, texture, pose, buffers, light);
        }
    }

    @Redirect(method = RENDER_LIVING, at = @At(value = "INVOKE", target = DEPTH_WRITE_TARGET))
    private void silverstar$lockonHealth(ItemStack stack, WavefrontObject model, String group,
                                         ResourceLocation texture, PoseStack pose,
                                         MultiBufferSource buffers, int light) {
        if (StarOverlay.lockonStarry()) {
            StarOverlay.drawLockon(stack, model, group, texture, pose, buffers, light);
        } else {
            BladeRenderState.renderOverridedLuminousDepthWrite(stack, model, group, texture, pose, buffers, light);
        }
    }
}
