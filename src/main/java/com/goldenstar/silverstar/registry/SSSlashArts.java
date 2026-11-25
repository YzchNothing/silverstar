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
 * <p>一个剑技在拔刀剑里最多挂三个连段状态，靠<b>松手的时机</b>分流：</p>
 * <ul>
 *   <li>蓄力不足（不到充满）→ {@code Fail}，这里没给，按了没反应；</li>
 *   <li>正好在充满的那几 tick 内松手 → {@code Jackpot}，走 {@code setComboStateJust} 那一条（完美版）；</li>
 *   <li>充满之后继续按住再松手 → {@code Success}，走构造器那一条（普通版）。</li>
 * </ul>
 * <p>这正是原版「次元斩 / 完美次元斩」的做法，我们照抄成两段。</p>
 */
public final class SSSlashArts {
    public static final DeferredRegister<SlashArts> SLASH_ARTS =
            DeferredRegister.create(SlashArts.REGISTRY_KEY, Silverstar.MODID);

    public static final RegistryObject<SlashArts> PARADISE_FALLS = SLASH_ARTS.register("paradise_falls",
            () -> new SlashArts(entity -> new ResourceLocation(Silverstar.MODID, "paradise_falls"))
                    .setComboStateJust(entity -> new ResourceLocation(Silverstar.MODID, "paradise_falls_just")));

    /** 「余烬」：只有一段，没有完美版。 */
    public static final RegistryObject<SlashArts> EMBER = SLASH_ARTS.register("ember",
            () -> new SlashArts(entity -> new ResourceLocation(Silverstar.MODID, "ember")));

    private SSSlashArts() {
    }
}
