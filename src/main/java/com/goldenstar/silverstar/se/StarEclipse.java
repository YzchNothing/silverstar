package com.goldenstar.silverstar.se;

import com.goldenstar.silverstar.Silverstar;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 特殊效果「星蚀」。
 *
 * <p>给敌人打标记，标记会累积、会引爆、会传染：</p>
 * <ol>
 *   <li>每次命中给目标叠一层「星痕」（最多 5 层）。</li>
 *   <li>每层给这一击加攻击力 4% 的额外伤害。</li>
 *   <li>同一目标连着命中 5 次触发「蚀爆」，打一笔无来源的魔法伤害，数值取「目标血上限 15%」与「面板攻击力」的较大者，然后清零。</li>
 *   <li>带星痕的目标死亡时，按 层数 × 它自己最大生命 8% 在 4 格内炸开，并把一半层数传给周围的人。</li>
 * </ol>
 *
 * <p>需求等级 20。额外伤害走 {@code LivingDamageEvent}，直接并进这一次打击，不明显多跳一次数字。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID)
public class StarEclipse extends SpecialEffect {
    public static final ResourceLocation ID = new ResourceLocation(Silverstar.MODID, "star_eclipse");

    /** 生效需求：玩家经验等级达到这个值。 */
    public static final int REQUIRED_LEVEL = 20;
    /** 同 OneLeft：传需求等级减 1，让刚好 20 级既生效又是红字。 */
    private static final int TOOLTIP_REQUEST_LEVEL = REQUIRED_LEVEL - 1;

    private static final String SCAR_KEY = "silverstar_scar";
    private static final String CHAIN_KEY = "silverstar_scar_chain";

    /** 星痕层数上限。 */
    private static final int MAX_SCAR = 5;
    /** 每层星痕带来的额外伤害，按攻击力比例算。 */
    private static final double DAMAGE_PER_SCAR = 0.04D;
    /** 连击多少次触发蚀爆。 */
    private static final int BURST_CHAIN = 5;
    /** 蚀爆除以攻击力为基数外，还可以取目标血上限的比例，两者取大。 */
    private static final double BURST_HEALTH_PERCENT = 0.15D;
    /** 死亡爆炸半径（格）。 */
    private static final double DEATH_RADIUS = 4.0D;
    /** 死亡爆炸取死者最大生命的比例，乘以星痕层数。 */
    private static final double DEATH_PERCENT = 0.08D;
    /** 传染给周围敌人时，层数除以这个数。 */
    private static final int SPREAD_DIVISOR = 2;

    public StarEclipse() {
        super(TOOLTIP_REQUEST_LEVEL);
    }

    /** 命中：叠层，并把层数换成额外伤害加进这一击。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        // 只认真正的近战劈砍，避开我们自己追加的火焰/魔法收尾再触发一次叠层。
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        if (!eligible(player, OneLeft.stateOf(player.getMainHandItem()))) {
            return;
        }

        CompoundTag data = target.getPersistentData();
        int scar = Math.min(MAX_SCAR, data.getInt(SCAR_KEY) + 1);
        int chain = data.getInt(CHAIN_KEY) + 1;
        data.putInt(SCAR_KEY, scar);

        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        if (chain >= BURST_CHAIN) {
            data.putInt(CHAIN_KEY, 0);
            data.putInt(SCAR_KEY, 0);
            // 蚀爆单独打一笔无来源的魔法伤害：不吃护甲，也不记在谁头上（击杀、死亡爆炸都不认它）。
            // 基数取「目标血上限 15%」与「面板攻击力」里大的那个：小怪按攻击力、厚血目标按血上限，
            // 这样整合包后期怪物血量膨胀时它也跟着膨胀，不会缩成一个添头。
            double burst = Math.max(target.getMaxHealth() * BURST_HEALTH_PERCENT, attack);
            OneLeft.dealDamage(target, target.damageSources().magic(), (float) burst);
            return;
        }

        data.putInt(CHAIN_KEY, chain);
        event.setAmount(event.getAmount() + (float) (attack * DAMAGE_PER_SCAR * scar));
    }

    /** 带星痕的目标死亡：按它自己的血上限炸开，并把星痕传出去。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        CompoundTag data = dead.getPersistentData();
        int scar = data.getInt(SCAR_KEY);
        if (scar <= 0) {
            return;
        }
        data.putInt(SCAR_KEY, 0);
        data.putInt(CHAIN_KEY, 0);

        if (dead.level().isClientSide()) {
            return;
        }

        Player source = event.getSource().getEntity() instanceof Player killer ? killer : null;
        double explosion = dead.getMaxHealth() * DEATH_PERCENT * scar;
        int spread = Math.max(1, scar / SPREAD_DIVISOR);

        for (LivingEntity other : dead.level().getEntitiesOfClass(
                LivingEntity.class,
                dead.getBoundingBox().inflate(DEATH_RADIUS),
                candidate -> candidate != dead && candidate.isAlive() && !(candidate instanceof Player))) {
            if (source != null) {
                OneLeft.dealDamage(other, source.damageSources().indirectMagic(source, source), (float) explosion);
            }
            CompoundTag otherData = other.getPersistentData();
            otherData.putInt(SCAR_KEY, Math.min(MAX_SCAR, otherData.getInt(SCAR_KEY) + spread));
        }
    }

    private static boolean eligible(Player player, ISlashBladeState state) {
        return player.experienceLevel >= REQUIRED_LEVEL
                && state != null
                && state.hasSpecialEffect(ID);
    }
}
