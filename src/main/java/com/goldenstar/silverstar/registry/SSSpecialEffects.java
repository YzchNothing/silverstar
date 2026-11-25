package com.goldenstar.silverstar.registry;

import com.goldenstar.silverstar.Silverstar;
import com.goldenstar.silverstar.se.Afterglow;
import com.goldenstar.silverstar.se.GoldShards;
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

    /** 余温：只认「身上还在烧」的目标，追加伤害并续火；烧死时回血、把火递给周围的人。 */
    public static final RegistryObject<SpecialEffect> AFTERGLOW =
            SPECIAL_EFFECT.register("afterglow", Afterglow::new);

    /** 碎金：残光（按目标已掉血量追加）+ 三段追伤（后续 1/3/5 tick 各追一段）。 */
    public static final RegistryObject<SpecialEffect> GOLD_SHARDS =
            SPECIAL_EFFECT.register("gold_shards", GoldShards::new);

    private SSSpecialEffects() {
    }
}
