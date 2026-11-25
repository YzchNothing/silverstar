package com.goldenstar.silverstar.art;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.concentrationrank.ConcentrationRankCapabilityProvider;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 「天堂陨落」分两段：
 * 前半段——以使用者碰撞箱为中心向外 9 格扫一遍范围，对范围内所有可锁定目标召唤次元斩并施加 2 秒缓慢 XI，
 * 把这一批目标的 UUID 记下来；一个目标都没有时什么也不做（不空放）。
 * 停顿一会儿后，在这批目标里挑出次元斩打完之后剩余血量最低的那只。
 * 它上空 3 格是通的，就瞬移到它上方 4 格接空坠斩；被方块挡住，就瞬移到它背后接虚无刀界。
 */
public final class ParadiseFalls {
    private static final double RANGE = 9.0D;
    private static final double DROP_HEIGHT = 4.0D;
    /** 判定目标上方是否有足够空间的检查高度。 */
    private static final int CLEARANCE_CHECK = 3;
    /** 走虚无刀界时瞬移到目标背后的距离。 */
    private static final double BEHIND_DISTANCE = 2.0D;
    private static final int SLOW_TICKS = 40;
    private static final int SLOW_AMPLIFIER = 10;
    /**
     * 普通版第几 tick 放次元斩。15 对应原版动作的 1923 帧（1900 起算 23 帧 ≈ 15 tick），
     * 早于此值人体还在拔刀蓄力。
     */
    private static final long CAST_TICK = 15L;
    /**
     * 完美版第几 tick 放：动作从 1923 帧（斩出那一帧）直接起算，没有蓄力，所以第 1 tick 就该出刀。
     * 用 1 而不是 0，是因为不能赌 tickAction 在 elapsed=0 时一定被调到。
     */
    private static final long JUST_CAST_TICK = 1L;
    /** 次元斩放完后的停顿（约 0.7 秒）才瞬移，要留在 startAndEnd 的帧区间内。 */
    private static final long DROP_DELAY = 14L;
    /** 前半段那一批目标存在玩家的持久化数据里，跨 tick 传。 */
    private static final String TARGETS_KEY = "silverstar:paradise_falls_targets";

    private ParadiseFalls() {
    }

    /** 普通版（蓄力满再松手）。 */
    public static void tick(LivingEntity user) {
        tick(user, false);
    }

    /** 完美版（在充满的那几 tick 内松手）：不蓄力、直接斩出，球带 Critical。 */
    public static void tickJust(LivingEntity user) {
        tick(user, true);
    }

    private static void tick(LivingEntity user, boolean just) {
        if (user.level().isClientSide()) {
            return;
        }
        long castTick = just ? JUST_CAST_TICK : CAST_TICK;
        long elapsed = ComboState.getElapsed(user);
        if (elapsed == castTick) {
            castSlashes(user, just);
        } else if (elapsed == castTick + DROP_DELAY) {
            strikeWorst(user);
        }
    }

    /** 前半段：范围内每个目标各吃一次缓慢 XI 和一个次元斩，并记下这批目标。不瞬移。 */
    private static void castSlashes(LivingEntity user, boolean just) {
        Level level = user.level();
        ItemStack blade = user.getMainHandItem();

        // 必须用四参重载：三参重载内部会把距离限制在 getResolvedReach(user)（约 4 格），
        // 9 格的范围会被它裁掉。
        List<Entity> targets = TargetSelector.getTargettableEntitiesWithinAABB(
                level, user, user.getBoundingBox().inflate(RANGE), RANGE);

        List<UUID> ids = new ArrayList<>();
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity victim)) {
                continue;
            }

            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER));

            // 自己造球、摆到目标身上（这条路的目标位置是准的）。完美版再多打一个 Critical 标记：
            // 原版 doJudgementCutJust 相对普通版的全部差异就是这一句 —— 击退改抛飞、多甩一道刀光。
            EntityJudgementCut cut = new EntityJudgementCut(SlashBlade.RegistryEvents.JudgementCut, level);
            // 高度对齐普通/完美次元斩（它落在准心指到的那一点，约等于生物眼睛高度），
            // 而不是 doJudgementCutSuper 那样落在脚下。
            Vec3 eye = victim.getEyePosition();
            cut.setPos(eye.x, eye.y, eye.z);
            cut.setShooter(user);
            blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> cut.setColor(state.getColorCode()));
            user.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank -> cut.setRank(rank.getRankLevel(level.getGameTime())));
            cut.setIsCritical(just);
            level.addFreshEntity(cut);

            ids.add(victim.getUUID());
        }

        if (!ids.isEmpty()) {
            user.getPersistentData().putLongArray(TARGETS_KEY, flatten(ids));
        }
    }

    /** 后半段：挑出这批目标里剩余血量最低的那只，按上方是否通畅决定是空坠斩还是虚无刀界。 */
    private static void strikeWorst(LivingEntity user) {
        CompoundTag data = user.getPersistentData();
        if (!data.contains(TARGETS_KEY)) {
            return;
        }
        List<UUID> ids = unflatten(data.getLongArray(TARGETS_KEY));
        data.remove(TARGETS_KEY);

        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }

        LivingEntity weakest = null;
        for (UUID id : ids) {
            if (!(level.getEntity(id) instanceof LivingEntity victim) || !victim.isAlive()) {
                continue;
            }
            if (weakest == null || victim.getHealth() < weakest.getHealth()) {
                weakest = victim;
            }
        }

        if (weakest == null) {
            return;
        }

        ItemStack blade = user.getMainHandItem();
        if (hasClearanceAbove(weakest)) {
            user.teleportTo(weakest.getX(), weakest.getY() + DROP_HEIGHT, weakest.getZ());
            blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> state.updateComboSeq(user, ComboStateRegistry.AERIAL_CLEAVE.getId()));
        } else {
            Vec3 dest = behindOf(weakest);
            user.teleportTo(dest.x, dest.y, dest.z);
            blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> state.updateComboSeq(user, ComboStateRegistry.VOID_SLASH.getId()));
        }
    }

    /** 目标上方 3 格内是否没有带碰撞的方块（也就是能站人）。 */
    private static boolean hasClearanceAbove(LivingEntity victim) {
        Level level = victim.level();
        BlockPos base = victim.blockPosition();
        for (int dy = 1; dy <= CLEARANCE_CHECK; dy++) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 目标背后 2 格的位置。 */
    private static Vec3 behindOf(LivingEntity victim) {
        Vec3 look = victim.getLookAngle();
        Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
        if (back.lengthSqr() < 1.0E-4D) {
            back = new Vec3(0.0D, 0.0D, 1.0D);
        }
        back = back.normalize().scale(BEHIND_DISTANCE);
        return new Vec3(victim.getX() + back.x, victim.getY(), victim.getZ() + back.z);
    }

    private static long[] flatten(List<UUID> ids) {
        long[] out = new long[ids.size() * 2];
        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);
            out[i * 2] = id.getMostSignificantBits();
            out[i * 2 + 1] = id.getLeastSignificantBits();
        }
        return out;
    }

    private static List<UUID> unflatten(long[] bits) {
        List<UUID> ids = new ArrayList<>(bits.length / 2);
        for (int i = 0; i + 1 < bits.length; i += 2) {
            ids.add(new UUID(bits[i], bits[i + 1]));
        }
        return ids;
    }
}
