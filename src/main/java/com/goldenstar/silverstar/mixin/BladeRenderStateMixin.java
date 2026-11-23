package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * 让纯银星的刀身本体和刀鞘直接以星空材质出现。
 *
 * <p>拔刀剑画模型只有一个总出口：{@code BladeRenderState.renderOverrided}。
 * 模型、位姿、朝向、组名，全都是它上游算好之后传进来的，我们只要在它动手之前插一脚，
 * 把这一层改用我们自己的 RenderType 重画一遍，然后把原调用取消掉——位置、大小、角度天然一致，
 * 不需要自己算任何变换。</p>
 *
 * <p>挡的是哪几层见 {@link StarOverlay#tryBladeStar}。附魔光（renderOverridedLuminous）、
 * 物品栏图标（item_blade）走的是别的方法或别的组名，不受影响。</p>
 *
 * <p>注意这里的 handler 必须是 <b>static</b>：被注入的 {@code renderOverrided} 本身就是静态方法，
 * 修饰符必须和目标方法一致，否则启动时会报
 * {@code 'static' modifier of handler method does not match target}。</p>
 */
@Mixin(BladeRenderState.class)
public class BladeRenderStateMixin {
    private static final String RENDER_OVERRIDED = SSDescriptors.OVERRIDED;

    @Inject(method = RENDER_OVERRIDED, at = @At("HEAD"), cancellable = true)
    private static void silverstar$starBlade(ItemStack stack, WavefrontObject model, String target,
                                             ResourceLocation texture, PoseStack pose,
                                             MultiBufferSource buffers, int light,
                                             Function<ResourceLocation, RenderType> renderTypeGetter,
                                             boolean useGlint, CallbackInfo ci) {
        if (StarOverlay.tryBladeStar(stack, model, target, texture, pose, buffers, light)) {
            ci.cancel();
        }
    }
}
