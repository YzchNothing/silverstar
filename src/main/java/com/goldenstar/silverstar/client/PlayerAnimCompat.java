package com.goldenstar.silverstar.client;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 人体动作的兼容入口。
 *
 * <p>拔刀剑的人体动作交给前置模组 <b>playerAnimator</b> 播放，所以本模组对它只是<b>软依赖</b>：
 * 装了才登记动作，没装就什么都不做（剑技照样能用，只是人不动）。</p>
 *
 * <p>这里特意拆成两个类：这个类<b>不碰</b> playerAnimator 的任何类型，
 * 真正用到它的是 {@link PlayerAnimRegister}。JVM 是“用到哪个类才加载哪个类”，
 * 所以只要不在没装前置时走到下面那行，那些类就不会被加载，也就不会抛 ClassNotFound。</p>
 */
public final class PlayerAnimCompat {
    private PlayerAnimCompat() {
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("playeranimator")) {
            return;
        }
        // enqueueWork：客户端初始化是并行的，改动画表得回到主线程排队做。
        event.enqueueWork(PlayerAnimRegister::register);
    }
}
