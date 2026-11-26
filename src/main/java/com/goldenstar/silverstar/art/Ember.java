package com.goldenstar.silverstar.art;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

/**
 * 剑技「余烬」。
 *
 * <p>向前劈一刀，刀光与伤害同一 tick 出现；命中的目标挨<b>三段龙息伤害</b>，并被点燃。</p>
 * <p>三段不是三次独立的判定，而是同一 tick 内连打三下：这样目标吃到的是一口气烧出来的灼伤，
 * 而不是三刀分开的斩击，也不会把它打飞出攻击范围。</p>
 */
public final class Ember {
    /** 第几 tick 真正挥出去（动作取 400~488 帧，按每帧 0.6667 tick 折算，斩击落在第 36 tick 附近）。 */
    public static final long CAST_TICK = 36L;
    /**
     * 第几 tick 主动收招、切回待机。
     *
     * <p>和 {@link #CAST_TICK} 取同一个数：三道刀光的伤害结算完的那一 tick 就交还控制权，
     * 玩家可以立刻接着挥刀或者再蓄一次余烬。早一点收，手感才不像“放完一刀被冻住”。</p>
     *
     * <p>为什么不更早（比如挥刀前就收）：伤害和刀光都在 {@link #CAST_TICK} 结算，
     * 收招必须先于“下一次蓄力”的判定重新打开，所以最早只能到这个点。</p>
     */
    public static final long END_TICK = 36L;
    /** 三段伤害合计这么多倍面板攻击力。 */
    public static final float TOTAL_DAMAGE = 1.5F;
    /** 分成几段。 */
    public static final int HITS = 3;
    /** 命中判定距离（格）。 */
    public static final double RANGE = 4.5D;
    /** 点燃时长（秒）。 */
    public static final int FIRE_SECONDS = 4;
    /** 余烬的兜底刀光色（读不到刀状态时才用）。 */
    public static final int FIRE = 0xFF7A18;
    /** 颜色高 8 位的标记位：标记“这道刀光是余烬的”。原版渲染会 & 0xFFFFFF 忽略它。 */
    public static final int MARK = 0x01000000;

    /** 每个玩家在这个连段状态里跑了几 tick（用实体 id 记，状态一结束自然过期）。 */
    private static final Map<Integer, Integer> ELAPSED = new HashMap<>();

    private Ember() {
    }

    /** 每 tick 调一次（挂在连段状态的 tickAction 上）。 */
    public static void tick(LivingEntity user) {
        if (user.level().isClientSide()) {
            return;
        }
        int id = user.getId();
        int elapsed = ELAPSED.getOrDefault(id, 0) + 1;
        // 计数器只在本段状态里有效；收招时会在下面清零，下一位玩家从 1 重新数。
        if (elapsed > 58) {
            ELAPSED.remove(id);
            return;
        }
        ELAPSED.put(id, elapsed);
        if (elapsed == 1) {
            // 动作开始那一 tick 的动静：挥刀的风声 + 一团火被抽出来的“呼”。
            // 放在这里而不是放在出手那一 tick —— 玩家按下之后得先听见“有人在动”，
            // 否则前面 1.8 秒的锁定会像卡住了。
            playSound(user, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.75F);
            playSound(user, SoundEvents.FIRECHARGE_USE, 0.7F, 0.95F);
        }
        if (elapsed == CAST_TICK) {
            strike(user);
        }
        // 挥出去之后再留十来 tick 收势，然后主动切回待机。
        // 为什么必须自己收：连段状态的 timeout 只有在“下一次蓄力/推进时”才会被结算；
        // 没人来推它，状态就一直挂着、tickAction 也会一直跑，计数从头再来一遍，
        // 于是不到两秒又劈出第二道刀光。收招越晚，“放完一刀按不出来”的空窗就越长，
        // 所以这里紧跟着挥刀结束 —— 尾段动作由动画层自己播完，不受状态机影响。
        if (elapsed >= END_TICK) {
            ELAPSED.remove(id);
            user.getMainHandItem().getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> state.updateComboSeq(user,
                            mods.flammpfeil.slashblade.registry.ComboStateRegistry.STANDBY.getId()));
        }
    }

    /** 在玩家身上放一声（以玩家为声源，别人也听得到）。 */
    private static void playSound(LivingEntity user, net.minecraft.sounds.SoundEvent event,
                                  float volume, float pitch) {
        user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
                event, SoundSource.PLAYERS, volume, pitch);
    }

    private static void strike(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 look = user.getLookAngle();

        // 刀光先出：和伤害同一 tick，视觉上刀到伤到。
        spawnSlash(level, user, look);
        // 刀光自己的动静：凋零受伤那一声（听起来像“空间被烫了一下”），
        // 挥刀的风声已经挪到动作起始那一 tick 了，这里不再叠。
        level.playSound(null, user.getX() + look.x * 1.6D, user.getY() + 1.1D, user.getZ() + look.z * 1.6D,
                SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.75F, 0.95F);

        Vec3 eye = user.getEyePosition();
        AABB box = new AABB(eye, eye.add(look.scale(RANGE))).inflate(1.5D, 1.2D, 1.5D);
        float attack = (float) user.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float perHit = attack * TOTAL_DAMAGE / HITS;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != user && e.isAlive())) {
            Vec3 to = target.position().subtract(user.position());
            if (to.lengthSqr() > RANGE * RANGE) {
                continue;
            }
            if (to.normalize().dot(look) < 0.2D) {
                continue;
            }
            for (int i = 0; i < HITS; i++) {
                target.invulnerableTime = 0;
                // 伤害类型仍是龙息（无视护甲），但把发起者记成玩家本人：
                // 这样死亡讯息、经验归属、以及别家模组的"谁打的"判定都会算到玩家头上。
                target.hurt(new DamageSource(
                        level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(DamageTypes.DRAGON_BREATH),
                        user, user), perHit);
            }
            target.setSecondsOnFire(FIRE_SECONDS);
            level.sendParticles(ParticleTypes.LAVA,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    10, 0.3D, 0.3D, 0.3D, 0.02D);
        }

        level.sendParticles(ParticleTypes.FLAME,
                user.getX() + look.x * 1.2D, user.getY() + 1.0D, user.getZ() + look.z * 1.2D,
                14, 0.25D, 0.2D, 0.25D, 0.01D);
    }

    /**
     * 甩出三道刀光。
     *
     * <p>拔刀剑的刀光是一个真的实体（{@code EntitySlashEffect}），不是粒子：它有朝向、有滚转、会自己消失，
     * 而且渲染时会走刀光的着色器——所以星空皮肤能直接接管它。</p>
     */
    private static void spawnSlash(ServerLevel level, LivingEntity user, Vec3 look) {
        float baseYaw = (float) (Math.atan2(look.x, look.z) * 180.0D / Math.PI);
        float[] rolls = {35.0F, -25.0F, 70.0F};
        double[] push = {1.2D, 1.6D, 2.0D};
        // 刀光颜色取刀自身的颜色（和原版 doSlash 一样）。
        // 高 8 位不是颜色，原版渲染会 & 0xFFFFFF 去掉它；我们拿它当“这是余烬的刀光”的标记，
        // 因为颜色是同步字段、客户端读得到，而 persistentData 只在服务端。
        int base = FIRE;
        var bladeState = user.getMainHandItem().getCapability(
                mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE).resolve();
        if (bladeState.isPresent()) {
            base = bladeState.get().getColorCode() & 0xFFFFFF;
        }
        int color = MARK | base;
        for (int i = 0; i < rolls.length; i++) {
            EntitySlashEffect slash = new EntitySlashEffect(SlashBlade.RegistryEvents.SlashEffect, level);
            slash.setPos(
                    user.getX() + look.x * push[i],
                    user.getY() + 1.1D + i * 0.15D,
                    user.getZ() + look.z * push[i]);
            slash.setShooter(user);
            // 颜色跟刀走（和原版 doSlash 一样），高 8 位留着当标记，原版渲染会 & 0xFFFFFF 忽略它。
            slash.setColor(color);
            slash.setDamage(0.0D);
            slash.setIsCritical(false);
            slash.setLifetime(10);
            // 实体朝向取玩家朝向的相反数：这一步在南北方向是“原地不动”
            //（0° 和 180° 取反还是自己），只把东西方向（±90°）翻到正确的一侧。
            slash.setYRot(-baseYaw);
            slash.setRotationOffset((i - 1) * 12.0F);
            slash.setRotationRoll(rolls[i]);
            level.addFreshEntity(slash);
        }
    }
}
