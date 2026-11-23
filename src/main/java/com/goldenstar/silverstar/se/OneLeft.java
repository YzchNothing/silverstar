package com.goldenstar.silverstar.se;

import com.goldenstar.silverstar.Silverstar;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 特殊效果「唯余其一」。
 *
 * <p>养刀：刀上记着的击杀数越多，挥出去的第二段越重。</p>
 * <ol>
 *   <li>每 300 杀，本体伤害的 15% 变成第二段伤害，不封顶（5400 杀即 +270%）。</li>
 *   <li>第二段在命中后单独结算，劈成两半：一半是必定 2.5 倍暴击的魔法伤害，一半是龙息伤害，都不吃护甲。</li>
 *   <li>每挥 3 刀，第 4 刀本体和第二段一起翻倍。</li>
 * </ol>
 *
 * <p>需求等级 50。击杀数用刀自带的计数器（{@link ISlashBladeState#getKillCount()}），所以换手、重进都跟着刀走。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID)
public class OneLeft extends SpecialEffect {
    public static final ResourceLocation ID = new ResourceLocation(Silverstar.MODID, "one_left");

    /** 生效需求：玩家经验等级达到这个值。 */
    public static final int REQUIRED_LEVEL = 50;
    /**
     * 传给 SpecialEffect 的需求等级要减 1。SlashBlade 判断「是否满足」用的是
     * requestLevel &lt; 玩家等级（严格小于），传 50 的话要 51 级才变红字。传 49 才能让 50 级既生效又是红字。
     */
    private static final int TOOLTIP_REQUEST_LEVEL = REQUIRED_LEVEL - 1;

    private static final String ATTACK_COUNT_KEY = "silverstar_attack_count";
    private static final String POWER_TICK_KEY = "silverstar_power_tick";

    /** 每多少击杀进一档。 */
    private static final int KILLS_PER_STEP = 300;
    /** 每档把本体伤害的多少变成第二段。 */
    private static final double BONUS_PER_STEP = 0.15D;
    /** 挥几刀给一次翻倍。 */
    private static final int ATTACKS_PER_POWER = 3;
    /** 翻倍的倍数。 */
    private static final double POWER_MULTIPLIER = 2.0D;
    /** 第二段里魔法那一半的固定暴击倍率。 */
    private static final double MAGIC_CRIT = 2.5D;

    public OneLeft() {
        super(TOOLTIP_REQUEST_LEVEL);
    }

    /** 挥砍：推进攻击计数，够三刀就把这一刀标成翻倍。 */
    @SubscribeEvent
    public static void onDoSlash(SlashBladeEvent.DoSlashEvent event) {
        if (!(event.getUser() instanceof Player player)) {
            return;
        }
        if (!eligible(player, event.getSlashBladeState())) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        boolean power = data.getLong(POWER_TICK_KEY) == now;

        if (!power) {
            int count = data.getInt(ATTACK_COUNT_KEY) + 1;
            if (count >= ATTACKS_PER_POWER) {
                data.putInt(ATTACK_COUNT_KEY, 0);
                data.putLong(POWER_TICK_KEY, now);
                power = true;
            } else {
                data.putInt(ATTACK_COUNT_KEY, count);
            }
        }

        if (power) {
            event.setDamage(event.getDamage() * POWER_MULTIPLIER);
        }
    }

    /**
     * 命中：按刀的击杀数补第二段伤害，直接并进这一次结算。
     *
     * <p>走 {@code LivingDamageEvent}（护甲算完、扣血之前），所以本来就不吃护甲；再拆成魔法和龙息两种
     * 伤害来源，是为了让第二段在别的 mod 的抗性、减伤判定里各走各的路，不至于被一种抗性全吃掉。</p>
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !target.isAlive()) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        // 只认真正的近战劈砍。第二段自己打出来的魔法/龙息伤害不会再触发一次。
        if (!event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
            return;
        }

        ISlashBladeState state = stateOf(player.getMainHandItem());
        if (!eligible(player, state)) {
            return;
        }

        int kills = state.getKillCount();
        if (kills < KILLS_PER_STEP) {
            return;
        }

        double bonus = (double) (kills / KILLS_PER_STEP) * BONUS_PER_STEP;
        double second = event.getAmount() * bonus;
        if (player.getPersistentData().getLong(POWER_TICK_KEY) == player.level().getGameTime()) {
            second *= POWER_MULTIPLIER;
        }
        if (second <= 0.0D) {
            return;
        }

        double half = second / 2.0D;
        // indirectMagic 的伤害类型是魔法（不吃护甲），但攻击者记在玩家身上，所以补刀也算玩家的击杀。
        dealDamage(target, player.damageSources().indirectMagic(player, player), (float) (half * MAGIC_CRIT));
        if (target.isAlive()) {
            dealDamage(target, player.damageSources().dragonBreath(), (float) half);
        }
    }

    private static boolean eligible(Player player, ISlashBladeState state) {
        return player.experienceLevel >= REQUIRED_LEVEL
                && state != null
                && state.hasSpecialEffect(ID);
    }

    static ISlashBladeState stateOf(ItemStack stack) {
        return stack.getCapability(ItemSlashBlade.BLADESTATE).resolve().orElse(null);
    }

    /**
     * 打之前清掉无敌帧，否则同一 tick 的两次追加只有第一下生效。
     */
    static void dealDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        target.invulnerableTime = 0;
        target.hurt(source, amount);
    }
}
