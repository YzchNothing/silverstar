package com.goldenstar.silverstar.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /** 星空手臂的三种模式，取值和配置文件里的数字一一对应。 */
    public static final int ARM_MODE_DEFAULT = 0;
    public static final int ARM_MODE_HOTBAR = 1;
    public static final int ARM_MODE_ALWAYS = 2;

    /** 左臂星空的颜色：自动（跟着触发的刀走）/ 固定青色（纯银星）/ 固定金色（金色星）。 */
    public static final int ARM_COLOR_AUTO = 0;
    public static final int ARM_COLOR_SILVER = 1;
    public static final int ARM_COLOR_GOLD = 2;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 刀身（以及刀鞘）是否也画成星空。默认关，只保留刀光、斩击波这些特效上的星空。 */
    private static final ForgeConfigSpec.BooleanValue BLADE_STAR_RENDER = BUILDER
            .comment(
                    "纯银星的刀身/刀鞘是否使用星空渲染。",
                    "关（默认）：刀身上是刀本身的贴图。",
                    "开：刀身也铺星空，和刀光同一套算法。",
                    "改完直接生效，不用重启游戏。")
            .define("bladeStarRender", false);

    /** 左手星空壳什么时候出现。 */
    private static final ForgeConfigSpec.IntValue ARM_RENDER_MODE = BUILDER
            .comment(
                    "左臂星空壳的出现条件。纯银星和金色星都算“我们的刀”。",
                    "0（默认）：第 1 格是我们的刀、并且主手拿的不是我们的刀时才显示。",
                    "1：只要快捷栏（第 1~9 格）里任意一格有我们的刀就显示；"
                            + "此时主手如果正拿着刀，左臂自动用那把刀的颜色。",
                    "2：不管背包里有什么，左臂一律是星空；"
                            + "此时主手如果正拿着刀，左臂自动用那把刀的颜色。",
                    "也就是说：只要不是 0，手持纯银星就是青色、手持金色星就是金色，"
                            + "优先于 armColorMode。",
                    "改完直接生效，不用重启游戏。")
            .defineInRange("armRenderMode", ARM_MODE_DEFAULT, ARM_MODE_DEFAULT, ARM_MODE_ALWAYS);

    /**
     * 左臂星空用谁的颜色。
     *
     * <p>只在 {@code armRenderMode} 为 1 或 2 时才有意义：
     * 那两种模式下左臂不看主手拿着什么，所以必须有个地方决定它染成谁的颜色。</p>
     */
    private static final ForgeConfigSpec.IntValue ARM_COLOR_MODE = BUILDER
            .comment(
                    "左臂星空壳用谁的颜色（仅当 armRenderMode = 1 或 2 时生效）。",
                    "注意：主手正拿着我们的刀时，颜色由手上那把决定，本项不参与。",
                    "0（默认）：自动——触发它的是哪把刀就用哪把刀的颜色。",
                    "1：固定青色（纯银星）。",
                    "2：固定金色（金色星）。",
                    "改完直接生效，不用重启游戏。")
            .defineInRange("armColorMode", ARM_COLOR_AUTO, ARM_COLOR_AUTO, ARM_COLOR_GOLD);

    /** 交给 Forge 注册的那份规格。 */
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 配置文件自己；Forge 只会把文件写到这个位置。 */
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("silverstar-client.toml");

    /** 上次看到的文件修改时间（毫秒）；-1 表示还没看过。 */
    private static long cachedModified = -1L;

    /** 上次检查文件的时间（毫秒），用来把轮询限制在每秒两次。 */
    private static long lastCheck;

    /** 文件里那一行一行的键值对，文件没变就一直用它。 */
    private static final Map<String, String> parsed = new HashMap<>();

    /** 解析失败时的兜底：用 Forge 读到的值。 */
    private static boolean forgeBladeStar;
    private static int forgeArmMode = ARM_MODE_DEFAULT;
    private static int forgeArmColor = ARM_COLOR_AUTO;

    /**
     * 刀身星空开关。
     *
     * <p>Forge 的配置值只在启动时读一次，玩家在游戏里把文件改掉要重启才生效——这种事已经踩过两次，
     * 所以这里不用 {@code get()}，改成自己盯着 {@code silverstar-client.toml} 的修改时间：
     * 文件一变就重新解析，最多半秒就能跟上，改完立刻看得见效果。</p>
     */
    public static boolean bladeStarRender() {
        refresh();
        String raw = parsed.get("bladeStarRender");
        if (raw != null) {
            if (raw.equalsIgnoreCase("true")) {
                return true;
            }
            if (raw.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return forgeBladeStar;
    }

    /**
     * 左臂星空壳的出现条件：{@link #ARM_MODE_DEFAULT} / {@link #ARM_MODE_HOTBAR} / {@link #ARM_MODE_ALWAYS}。
     *
     * <p>和刀身开关共用同一次文件读取，所以两个选项的“改完立刻生效”是同一套机制。</p>
     */
    public static int armRenderMode() {
        refresh();
        String raw = parsed.get("armRenderMode");
        if (raw != null) {
            try {
                int mode = Integer.parseInt(raw);
                if (mode >= ARM_MODE_DEFAULT && mode <= ARM_MODE_ALWAYS) {
                    return mode;
                }
            } catch (NumberFormatException notANumber) {
                // 写坏了就用 Forge 那份，不要让渲染线程崩掉。
            }
        }
        return forgeArmMode;
    }

    /**
     * 左臂星空用谁的颜色：{@link #ARM_COLOR_AUTO} / {@link #ARM_COLOR_SILVER} / {@link #ARM_COLOR_GOLD}。
     *
     * <p>和另外两个选项同一套热加载机制，改完半秒内生效。</p>
     */
    public static int armColorMode() {
        refresh();
        String raw = parsed.get("armColorMode");
        if (raw != null) {
            try {
                int mode = Integer.parseInt(raw);
                if (mode >= ARM_COLOR_AUTO && mode <= ARM_COLOR_GOLD) {
                    return mode;
                }
            } catch (NumberFormatException notANumber) {
                // 写坏了就用 Forge 那份。
            }
        }
        return forgeArmColor;
    }

    /** 半秒最多检查一次文件，其余时候直接用手上的缓存。 */
    private static void refresh() {
        long now = System.currentTimeMillis();
        if (cachedModified >= 0L && now - lastCheck < 500L) {
            return;
        }
        lastCheck = now;
        reloadIfChanged();
    }

    /** 文件修改时间变了就重新读一遍；读不到就退回 Forge 的值。 */
    private static void reloadIfChanged() {
        long modified;
        try {
            modified = Files.getLastModifiedTime(FILE).toMillis();
        } catch (IOException noFileYet) {
            cachedModified = -1L;
            parsed.clear();
            fromForge();
            return;
        }
        if (modified == cachedModified) {
            return;
        }
        cachedModified = modified;
        if (parseFile()) {
            return;
        }
        parsed.clear();
        fromForge();
    }

    /** 把 {@code key = value} 的行都收进 {@link #parsed}；整个文件读不动才返回 false。 */
    private static boolean parseFile() {
        List<String> lines;
        try {
            lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return false;
        }
        parsed.clear();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            parsed.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        return true;
    }

    /** Forge 内存里那份值；屏幕还没打开时可能抛异常，那就用默认值。 */
    private static void fromForge() {
        try {
            forgeBladeStar = BLADE_STAR_RENDER.get();
            forgeArmMode = ARM_RENDER_MODE.get();
            forgeArmColor = ARM_COLOR_MODE.get();
        } catch (IllegalStateException notLoadedYet) {
            forgeBladeStar = false;
            forgeArmMode = ARM_MODE_DEFAULT;
            forgeArmColor = ARM_COLOR_AUTO;
        }
    }
}
