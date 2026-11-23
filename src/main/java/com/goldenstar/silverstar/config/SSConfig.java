package com.goldenstar.silverstar.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

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
                    "开：刀身也铺星空，和刀光同一套算法。",
                    "改完直接生效，不用重启游戏。")
            .define("bladeStarRender", false);

    /** 交给 Forge 注册的那份规格。 */
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 配置文件自己；Forge 只会把文件写到这个位置。 */
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("silverstar-client.toml");

    /** 上次看到的文件修改时间（毫秒）；-1 表示还没看过。 */
    private static long cachedModified = -1L;

    /** 上次检查文件的时间（毫秒），用来把轮询限制在每秒两次。 */
    private static long lastCheck;

    /** 从文件里读出来的值，文件没变就一直用它。 */
    private static boolean cachedValue;

    /**
     * 刀身星空开关。
     *
     * <p>Forge 的配置值只在启动时读一次，玩家在游戏里把文件改掉要重启才生效——这种事已经踩过两次，
     * 所以这里不用 {@code get()}，改成自己盯着 {@code silverstar-client.toml} 的修改时间：
     * 文件一变就重新解析那一行，最多半秒就能跟上，改完立刻看得见效果。</p>
     *
     * <p>文件不存在或者读不动（比如第一次启动还没生成），就退回 Forge 读到的值，
     * 再不行就当“关”——这个开关只影响好看的画面，绝不该让渲染线程崩掉。</p>
     */
    public static boolean bladeStarRender() {
        long now = System.currentTimeMillis();
        if (cachedModified < 0L || now - lastCheck >= 500L) {
            lastCheck = now;
            reloadIfChanged();
        }
        return cachedValue;
    }

    /** 文件修改时间变了就重新读一遍。 */
    private static void reloadIfChanged() {
        long modified;
        try {
            modified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException noFileYet) {
            cachedModified = -1L;
            cachedValue = fromForge();
            return;
        }
        if (modified == cachedModified) {
            return;
        }
        cachedModified = modified;
        Boolean parsed = parseFile();
        cachedValue = parsed != null ? parsed : fromForge();
    }

    /** 从配置文件里找出 {@code bladeStarRender} 那一行；找不到返回 null。 */
    private static Boolean parseFile() {
        List<String> lines;
        try {
            lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (!"bladeStarRender".equals(line.substring(0, eq).trim())) {
                continue;
            }
            String value = line.substring(eq + 1).trim();
            if (value.equalsIgnoreCase("true")) {
                return Boolean.TRUE;
            }
            if (value.equalsIgnoreCase("false")) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /** Forge 内存里那份值；屏幕还没打开时可能抛异常，那就当“关”。 */
    private static boolean fromForge() {
        try {
            return BLADE_STAR_RENDER.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }
}
