package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 每把刀自己的「星空调色板」：这一刀该染成什么样的星空。
 *
 * <p>分工是两半的：</p>
 * <ul>
 *   <li>{@link StarSlot} 与 {@link StarShaders} 管<b>怎么染</b>——槽位用哪份程序、几层、多大、加色还是实色、
 *       基础色调与亮度。这部分是机制，写死在代码里，对每把刀都一样。</li>
 *   <li>本类管<b>染成什么色</b>——底色三条正弦曲线的中心与振幅、星点的基色与抖动范围，
 *       以及可以按槽位覆盖的乘色/亮度。这部分是素材参数，从刀自己的 json 读。</li>
 * </ul>
 *
 * <p>于是加一把新刀、或者给一把刀换配色，只需要改它的 define 文件，一行渲染代码都不用碰。</p>
 *
 * <pre>
 * "render": {
 *   "star_render": {
 *     "bg_base":    [0.42, 0.26, 0.09],   // 底色中心
 *     "bg_amp":     [0.06, 0.05, 0.02],   // 底色摆动振幅
 *     "bg_phase":   [0.0, 1.57, 1.2],     // 三通道相位（决定三个颜色错开到什么程度）
 *     "star_base":  [0.85, 0.68, 0.32],   // 星点基础色
 *     "star_jitter":[0.15, 0.18, 0.16],   // 星点随机范围
 *     "brightness": 1.0,
 *     "slots": {
 *       "wave":    { "brightness": 0.5 },
 *       "phantom": { "tint": [1.0, 0.78, 0.42] }
 *     }
 *   }
 * }
 * </pre>
 */
public final class StarRender {

    /** 纯银星那套底色：青 ↔ 深蓝地循环。 */
    public static final float[] SILVER_BG_BASE = {0.10F, 0.225F, 0.30F};
    public static final float[] SILVER_BG_AMP = {0.00F, 0.075F, 0.05F};
    public static final float[] SILVER_BG_PHASE = {0.0F, 0.0F, 1.5707964F};
    public static final float[] SILVER_STAR_BASE = {0.40F, 0.60F, 0.70F};
    public static final float[] SILVER_STAR_JITTER = {0.30F, 0.40F, 0.30F};

    /** 金色星那套底色：暖金 ↔ 暗赤地循环。 */
    public static final float[] GOLD_BG_BASE = {0.42F, 0.26F, 0.09F};
    public static final float[] GOLD_BG_AMP = {0.06F, 0.05F, 0.02F};
    public static final float[] GOLD_BG_PHASE = {0.0F, 1.5707964F, 1.2F};
    public static final float[] GOLD_STAR_BASE = {0.85F, 0.68F, 0.32F};
    public static final float[] GOLD_STAR_JITTER = {0.15F, 0.18F, 0.16F};

    private final ResourceLocation blade;
    private final String family;
    private final float[] tint;
    private final float brightness;
    private final float[] bgBase;
    private final float[] bgAmp;
    private final float[] bgPhase;
    private final float[] starBase;
    private final float[] starJitter;
    private final Map<StarSlot, float[]> slotTints;
    private final Map<StarSlot, Float> slotBrightness;
    private final boolean laneWind;

    private StarRender(ResourceLocation blade, String family, float[] tint, float brightness,
                       float[] bgBase, float[] bgAmp, float[] bgPhase,
                       float[] starBase, float[] starJitter,
                       Map<StarSlot, float[]> slotTints, Map<StarSlot, Float> slotBrightness,
                       boolean laneWind) {
        this.blade = blade;
        this.family = family;
        this.tint = tint;
        this.brightness = brightness;
        this.bgBase = bgBase;
        this.bgAmp = bgAmp;
        this.bgPhase = bgPhase;
        this.starBase = starBase;
        this.starJitter = starJitter;
        this.slotTints = slotTints == null ? new EnumMap<>(StarSlot.class) : slotTints;
        this.slotBrightness = slotBrightness == null ? new EnumMap<>(StarSlot.class) : slotBrightness;
        this.laneWind = laneWind;
    }

    // ---------------------------------------------------------------- 读取

    public ResourceLocation blade() {
        return blade;
    }

    /** 这把刀属于哪一族（只用来认身份，颜色已经不看它了）。 */
    public boolean gold() {
        return "gold".equals(family);
    }

    /** 该槽位的乘色：json 里点了名就用点名的，否则用全局乘色（再乘上槽位自己的基调）。 */
    public float[] tint(StarSlot slot) {
        float[] override = slotTints.get(slot);
        if (override != null) {
            return override.clone();
        }
        float[] base = slot.defaultTint();
        return new float[]{base[0] * tint[0], base[1] * tint[1], base[2] * tint[2]};
    }

    /** 该槽位的亮度：json 里点过名就用点名的，否则槽位基调 × 全局亮度。 */
    public float brightness(StarSlot slot) {
        Float override = slotBrightness.get(slot);
        if (override != null) {
            return override;
        }
        return slot.defaultBrightness() * brightness;
    }

    public float[] bgBase() {
        return bgBase;
    }

    public float[] bgAmp() {
        return bgAmp;
    }

    public float[] bgPhase() {
        return bgPhase;
    }

    public float[] starBase() {
        return starBase;
    }

    public float[] starJitter() {
        return starJitter;
    }

    /** 风纹要不要被它自己那道颜色再染一遍（纯银星要，金色量整片统一暖金，不要）。 */
    public boolean laneWind() {
        return laneWind;
    }

    // ---------------------------------------------------------------- 查表

    /** 每把刀定义里的 star_render，按刀名（如 {@code silverstar:golden_star}）索引。 */
    private static final Map<ResourceLocation, StarRender> BY_BLADE = new HashMap<>();

    /** 资源刚载入时还没有表（主界面之前），这里放兜底，保证任何时候都有答案。 */
    public static final StarRender FALLBACK_SILVER = new StarRender(
            new ResourceLocation(Silverstar.MODID, "silver_star"), "silver",
            new float[]{1.0F, 1.0F, 1.0F}, 1.0F,
            SILVER_BG_BASE, SILVER_BG_AMP, SILVER_BG_PHASE, SILVER_STAR_BASE, SILVER_STAR_JITTER,
            null, null, true);

    public static final StarRender FALLBACK_GOLD = new StarRender(
            new ResourceLocation(Silverstar.MODID, "golden_star"), "gold",
            new float[]{1.0F, 1.0F, 1.0F}, 1.0F,
            GOLD_BG_BASE, GOLD_BG_AMP, GOLD_BG_PHASE, GOLD_STAR_BASE, GOLD_STAR_JITTER,
            goldSlotTints(), goldSlotBrightness(), false);

    private static Map<StarSlot, float[]> goldSlotTints() {
        Map<StarSlot, float[]> map = new EnumMap<>(StarSlot.class);
        map.put(StarSlot.PHANTOM, new float[]{1.0F, 0.78F, 0.42F});
        map.put(StarSlot.WAVE, new float[]{1.0F, 0.72F, 0.30F});
        return map;
    }

    private static Map<StarSlot, Float> goldSlotBrightness() {
        Map<StarSlot, Float> map = new EnumMap<>(StarSlot.class);
        map.put(StarSlot.ARC, 0.25F);
        map.put(StarSlot.WAVE, 0.50F);
        map.put(StarSlot.BLADE, 0.95F);
        return map;
    }

    public static StarRender of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String key = stack.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                .map(state -> state.getTranslationKey())
                .orElse(null);
        return ofTranslationKey(key);
    }

    public static StarRender of(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return null;
        }
        return of(living.getMainHandItem());
    }

    /** translationKey 形如 {@code item.silverstar.golden_star}，转成刀名再查表。 */
    public static StarRender ofTranslationKey(String key) {
        if (key == null) {
            return null;
        }
        if (StarOverlay.GOLDEN_TRANSLATION_KEY.equals(key)) {
            return BY_BLADE.getOrDefault(bladeIdOf(key), FALLBACK_GOLD);
        }
        if (StarOverlay.BLADE_TRANSLATION_KEY.equals(key)) {
            return BY_BLADE.getOrDefault(bladeIdOf(key), FALLBACK_SILVER);
        }
        return BY_BLADE.get(bladeIdOf(key));
    }

    private static ResourceLocation bladeIdOf(String translationKey) {
        // item.<ns>.<path> → <ns>:<path>
        String[] parts = translationKey.split("\\.");
        if (parts.length < 3) {
            return new ResourceLocation(Silverstar.MODID, translationKey);
        }
        return new ResourceLocation(parts[1], parts[2]);
    }

    // ---------------------------------------------------------------- 载入

    /** 读所有 named_blades 的 json，只取我们关心的 star_render 段。 */
    public static class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, StarRender>> {

        public static final Loader INSTANCE = new Loader();

        @Override
        protected Map<ResourceLocation, StarRender> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, StarRender> found = new HashMap<>();
            Map<ResourceLocation, Resource> files =
                    manager.listResources("slashblade/named_blades", path -> path.getPath().endsWith(".json"));
            for (Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
                ResourceLocation file = entry.getKey();
                try (InputStreamReader reader = new InputStreamReader(
                        entry.getValue().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!root.has("render")) {
                        continue;
                    }
                    JsonObject render = root.getAsJsonObject("render");
                    if (!render.has("star_render")) {
                        continue;
                    }
                    JsonObject star = render.getAsJsonObject("star_render");
                    String family = star.has("family") ? star.get("family").getAsString() : "silver";

                    float[] tint = readColour(star, "tint", new float[]{1.0F, 1.0F, 1.0F});
                    float brightness = star.has("brightness") ? star.get("brightness").getAsFloat() : 1.0F;
                    float[] bgBase = readColour(star, "bg_base", SILVER_BG_BASE);
                    float[] bgAmp = readColour(star, "bg_amp", SILVER_BG_AMP);
                    float[] bgPhase = readColour(star, "bg_phase", SILVER_BG_PHASE);
                    float[] starBase = readColour(star, "star_base", SILVER_STAR_BASE);
                    float[] starJitter = readColour(star, "star_jitter", SILVER_STAR_JITTER);

                    Map<StarSlot, float[]> slotTints = new EnumMap<>(StarSlot.class);
                    Map<StarSlot, Float> slotBrightness = new EnumMap<>(StarSlot.class);
                    if (star.has("slots")) {
                        JsonObject slots = star.getAsJsonObject("slots");
                        for (StarSlot slot : StarSlot.values()) {
                            String name = slot.name().toLowerCase(java.util.Locale.ROOT);
                            if (!slots.has(name)) {
                                continue;
                            }
                            JsonObject override = slots.getAsJsonObject(name);
                            if (override.has("tint")) {
                                slotTints.put(slot, readColour(override, "tint", new float[]{1.0F, 1.0F, 1.0F}));
                            }
                            if (override.has("brightness")) {
                                slotBrightness.put(slot, override.get("brightness").getAsFloat());
                            }
                        }
                    }

                    boolean laneWind = !star.has("lane_wind") || star.get("lane_wind").getAsBoolean();

                    // 文件名就是刀名：named_blades/<name>.json
                    String name = file.getPath();
                    name = name.substring(name.lastIndexOf('/') + 1, name.length() - ".json".length());
                    ResourceLocation bladeId = new ResourceLocation(file.getNamespace(), name);
                    found.put(bladeId, new StarRender(bladeId, family, tint, brightness,
                            bgBase, bgAmp, bgPhase, starBase, starJitter,
                            slotTints, slotBrightness, laneWind));
                } catch (Exception ignored) {
                    // 单个文件坏了不该拖垮整个资源重载，跳过它，用兜底值。
                }
            }
            return found;
        }

        private static float[] readColour(JsonObject star, String key, float[] fallback) {
            if (!star.has(key)) {
                return fallback.clone();
            }
            JsonArray array = star.getAsJsonArray(key);
            float[] values = fallback.clone();
            for (int i = 0; i < Math.min(3, array.size()); i++) {
                values[i] = array.get(i).getAsFloat();
            }
            return values;
        }

        @Override
        protected void apply(Map<ResourceLocation, StarRender> found, ResourceManager manager, ProfilerFiller profiler) {
            BY_BLADE.clear();
            BY_BLADE.putAll(found);
        }

        /** 供调试/测试：当前表里有多少把刀。 */
        public static int size() {
            return BY_BLADE.size();
        }
    }
}
