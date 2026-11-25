package com.goldenstar.silverstar.registry;

import com.goldenstar.silverstar.Silverstar;
import com.goldenstar.silverstar.art.Ember;
import com.goldenstar.silverstar.art.ParadiseFalls;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 「天堂陨落」用到的连段状态。
 *
 * <p>剑技本身只负责把玩家切到这个状态，动作全在 {@code tickAction} 里每 tick 跑一次。</p>
 *
 * <p>两个容易搞混的单位，改参数前务必记住：</p>
 * <ul>
 *   <li>{@code startAndEnd} 是<b>动画帧</b>，决定这个状态活多久、动作播到第几帧；</li>
 *   <li>{@code timeout} 是<b>毫秒</b>，超过它就会切到 {@code nextOfTimeout} 指定的状态。</li>
 * </ul>
 *
 * <p>{@code nextOfTimeout} 必须显式给。<b>SlashBlade 不会兜底</b>，不给就保持 null，
 * 一旦超时立刻 NPE 崩游戏——这个错我们真踩过。</p>
 */
public final class SSComboStates {
    public static final DeferredRegister<ComboState> COMBO_STATES =
            DeferredRegister.create(ComboState.REGISTRY_KEY, Silverstar.MODID);

    public static final RegistryObject<ComboState> PARADISE_FALLS = COMBO_STATES.register("paradise_falls",
            () -> ComboState.Builder.newInstance()
                    // 普通版：借原版「次元斩」那一整段 —— 1900 拔刀蓄力 → 1923 斩出 → 1928 收刀 → 1963 结束。
                    // 63 帧约合 42 tick（2.1 秒），人体动作也登记成同一段（见 PlayerAnimRegister）。
                    // motionLoc 取原版动作文件，让“这段帧号属于谁”自洽。
                    .startAndEnd(1900, 1963)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .priority(10)
                    .speed(1.0f)
                    .timeout(2600)
                    .addTickAction(ParadiseFalls::tick)
                    .next(entity -> ComboStateRegistry.STANDBY.getId())
                    .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                    .build());

    /**
     * 完美版：借原版「完美次元斩」那一段 —— 1923 直接斩出（跳过 23 帧拔刀蓄力）→ 1963 结束。
     * 40 帧约合 27 tick（1.3 秒）。
     */
    public static final RegistryObject<ComboState> PARADISE_FALLS_JUST = COMBO_STATES.register("paradise_falls_just",
            () -> ComboState.Builder.newInstance()
                    .startAndEnd(1923, 1963)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .priority(10)
                    .speed(1.0f)
                    .timeout(1800)
                    .addTickAction(ParadiseFalls::tickJust)
                    .next(entity -> ComboStateRegistry.STANDBY.getId())
                    .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                    .build());

    /**
     * 「余烬」：借原版连段 C 那一段（400~488 帧，约 59 tick / 2 秒）。
     * 斩击落在第 30 tick，命中后目标身上留一层三秒后结算的余温。
     */
    public static final RegistryObject<ComboState> EMBER = COMBO_STATES.register("ember",
            () -> ComboState.Builder.newInstance()
                    .startAndEnd(400, 488)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .priority(10)
                    .speed(1.0f)
                    .timeout(2900)
                    .addTickAction(Ember::tick)
                    // next 指回自己 = 这段动作期间“不许打断”。
                    // 原版所有剑技段都是这么写的：progressCombo 看到 next == cur 直接返回 NONE，
                    // 左键挥刀被挡；doChargeAction 也有同形态的保护，蓄力同样被挡。
                    // 什么时候放开？由 Ember 在第 36 tick（伤害结算那一刻）主动切回待机。
                    .next(entity -> new net.minecraft.resources.ResourceLocation(
                            com.goldenstar.silverstar.Silverstar.MODID, "ember"))
                    .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                    .build());

    private SSComboStates() {
    }
}
