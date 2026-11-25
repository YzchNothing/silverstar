package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把 {@link StarArmLayer} 挂到玩家模型上。
 *
 * <p>只注册玩家：原版有两种手臂模型——{@code default}（4 像素宽）和 {@code slim}（3 像素宽，细手臂皮肤），
 * 两个渲染器各挂一份。别的生物不挂，所以僵尸、村民身上不会长星空。</p>
 *
 * <p>这个事件由 Forge 在客户端启动时发一次，那时所有实体渲染器都造好了，正好往里塞层。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StarArmLayers {

    private StarArmLayers() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : new String[]{"default", "slim"}) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new StarArmLayer(renderer));
            }
        }
    }
}
