package com.goldenstar.silverstar;

import com.goldenstar.silverstar.client.PlayerAnimCompat;
import com.goldenstar.silverstar.config.SSConfig;
import com.goldenstar.silverstar.registry.SSComboStates;
import com.goldenstar.silverstar.registry.SSSlashArts;
import com.goldenstar.silverstar.registry.SSSpecialEffects;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 模组入口。
 *
 * <p>Forge 在游戏启动时扫到 {@link Mod} 注解，就会 new 一个这个类的实例。构造器里做的事只有一件：
 * 把本模组自定义的三类东西（剑技、连段状态、特殊效果）登记进 Forge 的注册表。</p>
 *
 * <p>为什么要“登记”而不是直接 new：这几类对象在拔刀剑里都是<b>注册表</b>管理的——注册表相当于一张
 * 名字到对象的对照表，游戏存档里只存名字（比如 {@code silverstar:paradise_falls}），读档时再按名字查回对象。
 * 不登记的话，存档里写了名字也查不到，刀就读不出来。{@code DeferredRegister} 是 Forge 提供的延迟登记器：
 * 构造器执行得太早，注册表还没准备好，所以先把要登记的东西排队，等 Forge 开表时统一放进去。</p>
 */
@Mod(Silverstar.MODID)
public class Silverstar {
    /** 模组 id，同时是资源路径的命名空间（{@code assets/silverstar/...}、{@code data/silverstar/...}）。 */
    public static final String MODID = "silverstar";

    public Silverstar() {
        // mod 事件总线：注册表、着色器、模型加载这类“游戏启动期”的事件都走它。
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SSSlashArts.SLASH_ARTS.register(bus);
        SSComboStates.COMBO_STATES.register(bus);
        SSSpecialEffects.SPECIAL_EFFECT.register(bus);

        // 人体动作：把「天堂陨落」登记进拔刀剑的动作表（只在装了 playerAnimator 的客户端生效）。
        bus.addListener(PlayerAnimCompat::onClientSetup);

        // 客户端配置：刀身是否也画成星空。注册成 CLIENT 类型，文件落在 config/silverstar-client.toml。
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SSConfig.SPEC);
    }
}
