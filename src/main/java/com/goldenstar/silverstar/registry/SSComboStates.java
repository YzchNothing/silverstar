package com.goldenstar.silverstar.registry;

import com.goldenstar.silverstar.Silverstar;
import com.goldenstar.silverstar.art.ParadiseFalls;
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
                    // 起手沿用原版拔刀剑自己的动作：0-25 帧就是第一刀（combo_a1 1-10 + 收刀 10-21）。
                    // tickAction 只在状态存活期间跑，而瞬移发生在第 16 tick，帧区间要给够。
                    .startAndEnd(0, 25)
                    .priority(10)
                    .speed(1.0f)
                    // 1200 毫秒，比整段动作长，免得动作还没走完就被超时切走
                    .timeout(1200)
                    .addTickAction(ParadiseFalls::tick)
                    .next(entity -> ComboStateRegistry.STANDBY.getId())
                    .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                    .build());

    /** 收尾状态：动作放完停一下再回到待机，避免和下一刀的输入打架。 */
    public static final RegistryObject<ComboState> PARADISE_FALLS_END = COMBO_STATES.register("paradise_falls_end",
            () -> ComboState.Builder.newInstance()
                    .startAndEnd(0, 5)
                    .speed(1.0f)
                    .timeout(600)
                    .next(entity -> ComboStateRegistry.STANDBY.getId())
                    .nextOfTimeout(entity -> ComboStateRegistry.NONE.getId())
                    .build());

    private SSComboStates() {
    }
}
