package com.goldenstar.silverstar.se;

import com.goldenstar.silverstar.Silverstar;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.registry.specialeffects.SpecialEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 特殊效果「流萤」。
 *
 * <p>（原名「流萤」，与某热门游戏角色同名，已改掉以免争议。）</p>
 *
 * <p>两段机制，都不需要目标或自己有标签：</p>
 *
 * <h3>一、星屑三连（每次命中都触发）</h3>
 * <p>砍中之后，同一目标身上再落下 <b>3 段</b>追加伤害，分别在 1 / 3 / 5 tick 之后，
 * 每段是面板攻击力的 <b>20%</b>（合计 60%）。像刀尖甩出去的三粒星屑还在烧。</p>
 *
 * <h3>二、残光（按目标当前血量决定）</h3>
 * <p>这一刀本身的追加伤害随目标「已经掉了多少血」变高：
 * 满血 10% → 半血 50% → 残血 90%（面板攻击力比例）。</p>
 *
 * <p>两段叠在同一次命中里：主伤害结算时吃残光，随后三 tick 内吃三连。</p>
 *
 * <p>需求等级 30。名字取自「碎金」：刀劈下去，溅出来的不是火星，是碎掉的金屑 —— 正好是主伤害
 * 之后追上去的那三段。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID)
public class GoldShards extends SpecialEffect {
    public static final ResourceLocation ID = new ResourceLocation(Silverstar.MODID, "gold_shards");

    /** 生效需求：玩家经验等级达到这个值。 */
    public static final int REQUIRED_LEVEL = 30;
    /** 传需求等级减 1，让刚好 30 级既生效又显示红字。 */
    private static final int TOOLTIP_REQUEST_LEVEL = REQUIRED_LEVEL - 1;

    /** 三连的落点（相对命中那一 tick）。 */
    private static final int[] SEGMENT_DELAYS = {1, 3, 5};
    /** 每一段占面板攻击力的比例。 */
    private static final double SEGMENT_PERCENT = 0.20D;

    /** 残光：满血时给的比例。 */
    private static final double EXECUTE_MIN = 0.10D;
    /** 残光：从满血到空血额外增加的比例（10% + 80% = 90%）。 */
    private static final double EXECUTE_SPAN = 0.80D;

    /** 等待落下的星屑。只跑在服务端线程上，不需要同步。 */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(LivingEntity target, Player source, int ticksLeft) {
    }

    public GoldShards() {
        super(TOOLTIP_REQUEST_LEVEL);
    }

    /** 主命中：结算「残光」，并把「星屑三连」挂进待办。 */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        // 只认近战劈砍，避免我们自己追加的那几段再触发一层，滚成雪球。
        if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player player)) {
            return;
        }
        if (!eligible(player, OneLeft.stateOf(player.getMainHandItem()))) {
            return;
        }

        double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        // 残光：目标掉得越多，这一刀追加得越狠。
        double ratio = Math.min(1.0D, Math.max(0.0D, target.getHealth() / Math.max(1.0F, target.getMaxHealth())));
        double execute = attack * (EXECUTE_MIN + EXECUTE_SPAN * (1.0D - ratio));
        event.setAmount(event.getAmount() + (float) execute);

        for (int delay : SEGMENT_DELAYS) {
            PENDING.add(new Pending(target, player, delay));
        }
    }

    /** 服务端每 tick 推进一批待落的星屑。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.ticksLeft() <= 1) {
                due.add(p);
                it.remove();
            } else {
                it.remove();
                PENDING.add(new Pending(p.target(), p.source(), p.ticksLeft() - 1));
            }
        }
        for (Pending p : due) {
            strike(p.target(), p.source());
        }
    }

    /** 落下一粒星屑：一段伤害 + 一点火星。 */
    private static void strike(LivingEntity target, Player source) {
        if (!target.isAlive() || target.level() != source.level()) {
            return;
        }
        if (!eligible(source, OneLeft.stateOf(source.getMainHandItem()))) {
            return;
        }
        double damage = source.getAttributeValue(Attributes.ATTACK_DAMAGE) * SEGMENT_PERCENT;
        OneLeft.dealDamage(target, source.damageSources().indirectMagic(source, source), (float) damage);

        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMALL_FLAME,
                    target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
                    2, 0.25D, 0.25D, 0.25D, 0.01D);
        }
    }

    private static boolean eligible(Player player, ISlashBladeState state) {
        return player.experienceLevel >= REQUIRED_LEVEL
                && state != null
                && state.hasSpecialEffect(ID);
    }
}
