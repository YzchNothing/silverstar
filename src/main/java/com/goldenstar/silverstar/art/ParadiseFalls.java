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
    private static final long CAST_TICK = 2L;
    /** 次元斩放完后的停顿：CAST_TICK 之后第 14 tick 才瞬移（约 0.7 秒），要留在 startAndEnd 的帧区间内。 */
    private static final long DROP_TICK = CAST_TICK + 14L;
    /** 前半段那一批目标存在玩家的持久化数据里，跨 tick 传。 */
    private static final String TARGETS_KEY = "silverstar:paradise_falls_targets";

    private ParadiseFalls() {
    }

    public static void tick(LivingEntity user) {
        if (user.level().isClientSide()) {
            return;
        }
        long elapsed = ComboState.getElapsed(user);
        if (elapsed == CAST_TICK) {
            castSlashes(user);
        } else if (elapsed == DROP_TICK) {
            strikeWorst(user);
        }
    }

    /** 前半段：范围内每个目标各吃一次缓慢 XI 和一个次元斩，并记下这批目标。不瞬移。 */
    private static void castSlashes(LivingEntity user) {
        Level level = user.level();
        ItemStack blade = user.getMainHandItem();

        // 必须用四参重载：三参重载内部会把距离限制在 getResolvedReach(user)（约 4 格），
        // 6 格的范围会被它裁掉。
        List<Entity> targets = TargetSelector.getTargettableEntitiesWithinAABB(
                level, user, user.getBoundingBox().inflate(RANGE), RANGE);

        List<UUID> ids = new ArrayList<>();
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity victim)) {
                continue;
            }

            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER));

            EntityJudgementCut cut = new EntityJudgementCut(SlashBlade.RegistryEvents.JudgementCut, level);
            cut.setPos(victim.getX(), victim.getY(), victim.getZ());
            cut.setShooter(user);
            blade.getCapability(ItemSlashBlade.BLADESTATE)
                    .ifPresent(state -> cut.setColor(state.getColorCode()));
            user.getCapability(ConcentrationRankCapabilityProvider.RANK_POINT)
                    .ifPresent(rank -> cut.setRank(rank.getRankLevel(level.getGameTime())));
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
