package com.goldenstar.silverstar.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 本模组的客户端配置。
 *
 * <p>配置项写在玩家自己电脑上的 {@code config/silverstar-client.toml} 里，不进存档，
 * 所以在服务器上玩的时候，开关只影响“我这台电脑看到的画面”，不会改变别人看到的样子。</p>
 *
 * <p>为什么放在客户端：星空渲染纯粹是画面上的一层替换，服务端根本不知道也不关心，
 * 硬塞进通用配置反而会让服务器多出一份没人看的文件。</p>
 */
public final class SSConfig {
    private SSConfig() {
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 刀身（以及刀鞘）是否也画成星空。默认关，只保留刀光、斩击波这些特效上的星空。 */
    private static final ForgeConfigSpec.BooleanValue BLADE_STAR_RENDER = BUILDER
            .comment(
                    "纯银星的刀身/刀鞘是否使用星空渲染。",
                    "关（默认）：刀身上是刀本身的贴图。",
                    "开：刀身也铺星空，和刀光同一套算法。")
            .define("bladeStarRender", false);

    /** 交给 Forge 注册的那份规格。 */
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /**
     * 刀身星空开关。
     *
     * <p>配置在屏幕还没打开、或者玩家在服务器上刚进世界时可能还没读出来，
     * 这时候 {@code get()} 会抛异常。这里直接当成“关”来处理，
     * 免得为了一个装饰性效果把渲染线程搞崩。</p>
     */
    public static boolean bladeStarRender() {
        try {
            return BLADE_STAR_RENDER.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }
}
