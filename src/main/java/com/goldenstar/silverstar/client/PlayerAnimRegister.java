package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.registry.SSComboStates;
import mods.flammpfeil.slashblade.compat.playerAnim.PlayerAnimationOverrider;
import mods.flammpfeil.slashblade.compat.playerAnim.VmdAnimation;
import net.minecraft.resources.ResourceLocation;

/**
 * 把「天堂陨落」塞进拔刀剑的人体动作表。
 *
 * <p>拔刀剑的做法是：注册表里的连段状态只带一个<b>帧区间</b>，具体播哪段人体动作写在
 * {@link PlayerAnimationOverrider} 这张表里（键 = 连段状态 id，值 = VMD 文件 + 起止帧）。
 * 表是写死的白名单，不在表里的状态人体就不会动——我们的状态之前就没在表里。</p>
 *
 * <p>这张表是 public 的可变 Map，SJAP 自己也是靠 put 扩展，所以这条路是官方支持的用法。
 * 本类一被加载就会碰到 playerAnimator 的类型，只能由 {@link PlayerAnimCompat} 在确认装了前置之后才调。</p>
 */
final class PlayerAnimRegister {
    /** 拔刀剑给玩家模型用的动作文件（和给刀用的 combostate/*.vmd 不是同一份）。 */
    private static final ResourceLocation PLAYER_MOTION =
            new ResourceLocation("slashblade", "model/pa/player_motion.vmd");

    /** 普通版：1900 拔刀蓄力 → 1923 斩出 → 1928 收刀 → 1963 结束，63 帧约合 42 tick（2.1 秒）。 */
    private static final double CUT_START = 1900.0D;
    private static final double CUT_END = 1963.0D;

    /** 完美版：直接 1923 斩出（跳过拔刀蓄力）→ 1963 结束，40 帧约合 27 tick（1.3 秒）。 */
    private static final double JUST_START = 1923.0D;
    private static final double JUST_END = 1963.0D;

    private PlayerAnimRegister() {
    }

    static void register() {
        put(SSComboStates.PARADISE_FALLS.getId(), CUT_START, CUT_END);
        put(SSComboStates.PARADISE_FALLS_JUST.getId(), JUST_START, JUST_END);
        // 「余烬」用原版连段 C 那一整段动作（400~488 帧）。
        put(SSComboStates.EMBER.getId(), 400.0D, 488.0D);
    }

    private static void put(net.minecraft.resources.ResourceLocation state, double start, double end) {
        PlayerAnimationOverrider.getInstance().getAnimation().put(
                state, new VmdAnimation(PLAYER_MOTION, start, end, false));
    }
}
