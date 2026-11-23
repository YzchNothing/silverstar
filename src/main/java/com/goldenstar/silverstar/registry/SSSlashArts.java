package com.goldenstar.silverstar.registry;

import com.goldenstar.silverstar.Silverstar;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 剑技（SA）「天堂陨落」。
 *
 * <p>拔刀剑里一个剑技要做的事很少：告诉游戏“按这个键该切到哪个连段状态”。
 * 真正的动作写在那个状态里（见 {@link com.goldenstar.silverstar.art.ParadiseFalls}）。</p>
 *
 * <p>这里返回的 {@link ResourceLocation} 就是连段状态的名字，必须和 {@link SSComboStates}
 * 里登记的名字一模一样（{@code silverstar:paradise_falls}），对不上就是按了没反应。</p>
 */
public final class SSSlashArts {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, Silverstar.MODID);

    public static final RegistryObject<SlashArts> PARADISE_FALLS = SLASH_ARTS.register("paradise_falls",
            () -> new SlashArts(entity -> new ResourceLocation(Silverstar.MODID, "paradise_falls")));

    private SSSlashArts() {
    }
}
