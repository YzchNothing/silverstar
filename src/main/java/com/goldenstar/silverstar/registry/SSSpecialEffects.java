package com.goldenstar.silverstar.registry;

import com.goldenstar.silverstar.Silverstar;
import com.goldenstar.silverstar.se.OneLeft;
import com.goldenstar.silverstar.se.StarEclipse;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 特殊效果（SE）。
 *
 * <p>注册名就是刀的数据文件 {@code special_effects} 里要写的名字，三者必须一致：
 * 这里的 {@code "one_left"}、刀 JSON 里的 {@code "silverstar:one_left"}、
 * 以及语言文件里的 {@code se.silverstar.one_left}。</p>
 */
public final class SSSpecialEffects {
    public static final DeferredRegister<SpecialEffect> SPECIAL_EFFECT =
            DeferredRegister.create(SpecialEffect.REGISTRY_KEY, Silverstar.MODID);

    /** 唯余其一：按击杀数堆第二段伤害，每三刀翻倍一次。 */
    public static final RegistryObject<SpecialEffect> ONE_LEFT =
            SPECIAL_EFFECT.register("one_left", OneLeft::new);

    /** 星蚀：给目标叠星痕，连击引爆、死亡传染。 */
    public static final RegistryObject<SpecialEffect> STAR_ECLIPSE =
            SPECIAL_EFFECT.register("star_eclipse", StarEclipse::new);

    private SSSpecialEffects() {
    }
}
