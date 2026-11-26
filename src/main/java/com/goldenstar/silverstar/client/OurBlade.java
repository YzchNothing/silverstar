package com.goldenstar.silverstar.client;

import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 「我们的刀」的唯一判定入口。
 *
 * <p>以前这件事散在 {@link StarOverlay} 里：{@code isSilverStarShooter}、{@code isGoldenStarShooter}，
 * 外加四个各管一段的 ThreadLocal 标记。每加一处用途（刀光、幻影剑、波刃、风纹、锁环、左臂……）
 * 就得记得在每处判断里都把金色星算进去，漏一处就会出现「金色星的某个特效还是蓝的」。
 *
 * <p>现在统一成这个枚举：先问出「是谁」，再按它去查表。新增用途时只需要认这一处。
 */
public enum OurBlade {
    /** 不是我们的刀。 */
    NONE,
    /** 纯银星：星域是冷白偏青。 */
    SILVER,
    /** 金色星：同一片星域，暖金。 */
    GOLD;

    /** 会不会走星域渲染（两把都算）。 */
    public boolean starry() {
        return this != NONE;
    }

    /** 是不是金色那一份程序。 */
    public boolean gold() {
        return this == GOLD;
    }

    /**
     * 这把刀该用哪套调色板（颜色本身在那把刀自己的 json 里）。
     *
     * <p>{@link #NONE} 也返回银：调用方本来都先判过 {@link #starry()}，这里的兜底只是为了让
     * 「查表」这件事永远有个确定的答案，不用到处写 null 判断。</p>
     */
    public StarRender palette() {
        if (this == GOLD) {
            return StarRender.ofTranslationKey(StarOverlay.GOLDEN_TRANSLATION_KEY);
        }
        if (this == SILVER) {
            return StarRender.ofTranslationKey(StarOverlay.BLADE_TRANSLATION_KEY);
        }
        return StarRender.FALLBACK_SILVER;
    }

    /** 从一把刀（物品）判定：查它自己 json 里的 star_render。 */
    public static OurBlade of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return NONE;
        }
        String key = stack.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getTranslationKey())
                .orElse(null);
        return ofTranslationKey(key);
    }

    /** 从「放招的人」判定：看他主手拿的是哪把。 */
    public static OurBlade of(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return NONE;
        }
        return of(living.getMainHandItem());
    }

    /** 从刀状态里的 translationKey 判定：值来自刀自己的 json，不再写死在这里。 */
    public static OurBlade ofTranslationKey(String key) {
        StarRender render = StarRender.ofTranslationKey(key);
        if (render == null) {
            return NONE;
        }
        return render.gold() ? GOLD : SILVER;    }
}
