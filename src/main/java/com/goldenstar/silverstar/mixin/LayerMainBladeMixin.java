package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;

import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让纯银星不挂在腰上。
 *
 * <p>拔刀剑会给背包里的刀额外画一层“待命”模型：不在主手时，刀挂在角色的腰后。
 * 这一层走的是 {@code LayerMainBlade.renderStandbyBlade}，和手持时画的完全不是一条路。</p>
 *
 * <p>这里直接在开头掐掉，且<b>只对我们的刀生效</b>，别人的刀照旧挂着。
 * 注意 handler 是实例方法：被注入的 {@code renderStandbyBlade} 是实例方法，
 * 写成 static 会在启动时报 “static modifier of handler method does not match target”。</p>
 */
@Mixin(LayerMainBlade.class)
public class LayerMainBladeMixin {

    @Inject(method = "renderStandbyBlade", at = @At("HEAD"), cancellable = true)
    private void silverstar$hideStandby(PoseStack pose, MultiBufferSource buffers, int light,
                                        ItemStack stack, LivingEntity entity, CallbackInfo ci) {
        if (StarOverlay.isOurBlade(stack)) {
            ci.cancel();
        }
    }
}
