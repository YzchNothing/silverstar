package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.DriveRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 斩击波（{@code EntityDrive}）的星空层。
 *
 * <p>波刀龙胆、纵斩波、横斩波都是同一个实体，原版这个渲染器只画一次
 * {@code renderOverridedLuminous(model/util/drive.obj, "base", model/util/ss.png)}，
 * 没有分层，所以这里只要把这唯一的一次换成星空就够了。</p>
 */
@Mixin(DriveRenderer.class)
public abstract class DriveRendererMixin {
    /** 真正干活的方法（泛型桥接 {@code m_7392_} 只是转调到这里）。 */
    private static final String RENDER_IMPL =
            "render(Lmods/flammpfeil/slashblade/entity/EntityDrive;" + SSDescriptors.RENDER_REST;

    /** 斩击波那唯一的一层。 */
    private static final String LUMINOUS_TARGET = SSDescriptors.LUMINOUS;

    /** 渲染开始：判定这一片是不是纯银星放出来的，顺便算好这一帧的浓淡。 */
    @Inject(method = RENDER_IMPL, at = @At("HEAD"))
    private void silverstar$beginWave(EntityDrive drive, float entityYaw, float partialTicks,
                                      PoseStack pose, MultiBufferSource buffers, int light,
                                      CallbackInfo ci) {
        StarOverlay.beginWave(drive, partialTicks);
    }

    /** 渲染结束：清状态。 */
    @Inject(method = RENDER_IMPL, at = @At("RETURN"))
    private void silverstar$endWave(EntityDrive drive, float entityYaw, float partialTicks,
                                    PoseStack pose, MultiBufferSource buffers, int light,
                                    CallbackInfo ci) {
        StarOverlay.end();
    }

    /** 那唯一的一次绘制：自己是纯银星就换星空，否则原样转发。 */
    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = LUMINOUS_TARGET))
    private void silverstar$wave(ItemStack stack, WavefrontObject model, String group,
                                 ResourceLocation texture, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        if (StarOverlay.active() && !StarOverlay.vanillaLook()) {
            StarOverlay.drawWave(stack, model, group, texture, pose, buffers, light);
        } else {
            BladeRenderState.renderOverridedLuminous(stack, model, group, texture, pose, buffers, light);
        }
    }
}
