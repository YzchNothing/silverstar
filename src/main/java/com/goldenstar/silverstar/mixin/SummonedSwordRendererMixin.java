package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.SummonedSwordRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把纯银星召唤出来的幻影剑换成星空层。
 *
 * <p>原版幻影剑渲染器整把剑只调一次绘制，走 {@code renderOverridedLuminous}，
 * 所以这里只有一个 Redirect 点。是纯银星的幻影剑就换着色器，否则原样放行。</p>
 *
 * <p><b>注意处理方法不能加 static。</b>这里踩过一次坑：被注入的 {@code render(...)} 是实例方法，
 * Mixin 要求处理方法的修饰符跟它一致，写成 static 会启动崩溃并报
 * {@code 'static' modifier of handler method does not match target}。</p>
 */
@Mixin(SummonedSwordRenderer.class)
public abstract class SummonedSwordRendererMixin {
    /** 真正干活的方法（泛型桥接 {@code m_7392_} 只是转调到这里）。 */
    private static final String RENDER_IMPL =
            "render(Lmods/flammpfeil/slashblade/entity/EntityAbstractSummonedSword;"
                    + SSDescriptors.RENDER_REST;

    /** 幻影剑唯一的绘制调用。 */
    private static final String LUMINOUS_TARGET = SSDescriptors.LUMINOUS;

    /** 渲染开始：判定这次画的幻影剑是不是纯银星的。 */
    @Inject(method = RENDER_IMPL, at = @At("HEAD"))
    private void silverstar$beginSword(EntityAbstractSummonedSword sword, float entityYaw, float partialTicks,
                                       PoseStack pose, MultiBufferSource buffers, int light,
                                       CallbackInfo ci) {
        StarOverlay.beginSword(sword, partialTicks);
    }

    /** 渲染结束：清状态。 */
    @Inject(method = RENDER_IMPL, at = @At("RETURN"))
    private void silverstar$endSword(EntityAbstractSummonedSword sword, float entityYaw, float partialTicks,
                                     PoseStack pose, MultiBufferSource buffers, int light,
                                     CallbackInfo ci) {
        StarOverlay.end();
    }

    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = LUMINOUS_TARGET))
    private void silverstar$luminousSword(ItemStack stack, WavefrontObject model, String group,
                                          ResourceLocation texture, PoseStack pose,
                                          MultiBufferSource buffers, int light) {
        if (StarOverlay.active()) {
            // 正常情况：画星空层
            StarOverlay.drawSword(stack, model, group, texture, pose, buffers, light);
        } else {
            // 不是纯银星的幻影剑（或开着光影）：完全交给原版，颜色也归原版
            BladeRenderState.renderOverridedLuminous(stack, model, group, texture, pose, buffers, light);
        }
    }
}
