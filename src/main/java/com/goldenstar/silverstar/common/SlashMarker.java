package com.goldenstar.silverstar.common;

import com.goldenstar.silverstar.Silverstar;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 给「完美次元斩」额外斩出来的那几刀打个标记。
 *
 * <p>那一招的流程是：先放一颗次元斩的球，球炸开时再额外甩出几道普通斩击。那几道斩击和玩家自己挥刀
 * 放出来的是同一种实体，光看实体本身分不出来，所以这里换一个判据——<b>它出生的时候，同一个施法者
 * 身边正好有一颗次元斩的球</b>，那就认定它是这一招的一部分。</p>
 *
 * <p>标记写进实体自己的持久化数据，渲染那边读到就用深蓝配色，跟球旁边的风纹统一。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID)
public final class SlashMarker {
    /** 渲染那边按这个键读标记，两边必须是同一个字符串。 */
    public static final String BLUE_SLASH_TAG = "silverstar_blue_slash";

    /** 球存在的时间很短，抓它的搜索半径不必太大。 */
    private static final double SEARCH_RADIUS = 24.0;

    private SlashMarker() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof EntitySlashEffect slash)) {
            return;
        }
        if (slash.level().isClientSide()) {
            return;
        }
        Entity shooter = slash.getShooter();
        if (shooter == null) {
            return;
        }
        boolean fromJudgementCut = !slash.level()
                .getEntitiesOfClass(EntityJudgementCut.class,
                        slash.getBoundingBox().inflate(SEARCH_RADIUS),
                        cut -> cut.getShooter() == shooter)
                .isEmpty();
        if (fromJudgementCut) {
            slash.getPersistentData().putBoolean(BLUE_SLASH_TAG, true);
        }
    }
}
