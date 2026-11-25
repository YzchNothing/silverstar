package com.goldenstar.silverstar.se;

import com.goldenstar.silverstar.Silverstar;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 特殊效果「余温」。
 *
 * <p>核心是<b>我们自己记的一个印记</b>，不借用「燃烧」状态。</p>
 *
 * <p>为什么不用 {@code isOnFire()}：整合包里大量怪物带火焰免疫（烈焰人、恶魂、各种模组怪），
 * 对它们点火点不着，{@code isOnFire()} 永远是 false —— 那这个 SE 就等于不存在。
 * 余烬的伤害当初特意改成龙息伤害，也是同一个原因：火焰伤害在整合包里不可靠。</p>
 *
 * <p>所以改成：</p>
 * <ol>
 *   <li>命中时给目标盖一枚「余温」印记，持续 {@value #MARK_SECONDS} 秒（写进 persistentData，服务端自己记）。</li>
 *   <li>已经有印记的目标再挨打：追加攻击力 35% 的伤害，并把印记<b>刷新</b>回满时长 —— 连续挥砍就一直烫着。</li>
 *   <li>带着印记被打死：自己回 3% 最大生命，4 格内其他敌对目标补上印记。</li>
 * </ol>
 *
 * <p>印记的可见反馈是少量火星粒子，不依赖目标能不能着火。</p>
 *
 * <p>需求等级 1（跟着刀走，几乎等于没有门槛）。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID)
public class Afterglow extends SpecialEffect {
    public static final ResourceLocation ID = new ResourceLocation(Silverstar.MODID, "afterglow");

    /** 生效需求：玩家经验等级达到这个值。 */
    public static final int REQUIRED_LEVEL = 1;
    /** 和另外两个 SE 一样，传需求等级减 1，让刚满足等级时既生效又显示红字。 */
    private static final int TOOLTIP_REQUEST_LEVEL = REQUIRED_LEVEL - 1;

    /** 印记键：存到期时刻（游戏总 tick）。 */
    private static final String MARK_KEY = "silverstar_afterglow_until";
    /** 印记持续秒数。 */
    private static final int MARK_SECONDS = 6;
    /** 已有印记时追加的伤害，按面板攻击力比例。 */
    private static final double BURN_BONUS = 0.35D;
    /** 击杀带印记目标时回复的最大生命比例。 */
    private static final double HEAL_PERCENT = 0.30D;
    /** 击杀带印记目标时，给周围补印记的半径（格）。 */
    private static final double SPREAD_RADIUS = 4.0D;

    public Afterglow() {
        super(TOOLTIP_REQUEST_LEVEL);
    }

    /** 命中：先看有没有印记，有就多打一笔并刷新，没有就盖一枚。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        // 只认近战劈砍，避开余烬那种自己追加的龙息/魔法收尾再触发一次。
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (!eligible(player, OneLeft.stateOf(player.getMainHandItem()))) {
            return;
        }

        long now = target.level().getGameTime();
        CompoundTag data = target.getPersistentData();
        long until = data.getLong(MARK_KEY);

        if (until > now) {
            double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            event.setAmount(event.getAmount() + (float) (attack * BURN_BONUS));
            spawnSparks(target, 4);
        } else {
            spawnSparks(target, 2);
        }

        data.putLong(MARK_KEY, now + MARK_SECONDS * 20L);
    }

    /** 击杀：带印记的目标死了，余温不能白留 —— 回一点血，并把印记递给旁边的人。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        CompoundTag data = dead.getPersistentData();
        if (data.getLong(MARK_KEY) <= dead.level().getGameTime()) {
            return;
        }
        data.remove(MARK_KEY);

        if (!(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }
        if (!eligible(killer, OneLeft.stateOf(killer.getMainHandItem()))) {
            return;
        }

        killer.heal((float) (killer.getMaxHealth() * HEAL_PERCENT));

        long until = dead.level().getGameTime() + MARK_SECONDS * 20L;
        AABB area = dead.getBoundingBox().inflate(SPREAD_RADIUS);
        for (LivingEntity other : dead.level().getEntitiesOfClass(
                LivingEntity.class,
                area,
                candidate -> candidate != dead && candidate != killer
                        && candidate.isAlive() && !(candidate instanceof Player))) {
            other.getPersistentData().putLong(MARK_KEY, until);
            spawnSparks(other, 3);
        }
    }

    /** 火星粒子：印记的唯一可见反馈，和目标能不能着火无关。 */
    private static void spawnSparks(LivingEntity target, int count) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SMALL_FLAME,
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                count, 0.3D, 0.3D, 0.3D, 0.02D);
    }

    private static boolean eligible(Player player, ISlashBladeState state) {
        return player.experienceLevel >= REQUIRED_LEVEL
                && state != null
                && state.hasSpecialEffect(ID);
    }
}
