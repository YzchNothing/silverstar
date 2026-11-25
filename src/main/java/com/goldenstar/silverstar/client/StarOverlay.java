package com.goldenstar.silverstar.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.goldenstar.silverstar.config.SSConfig;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 把纯银星放出的刀光、幻影剑、次元斩，换成星空材质来画。
 *
 * <p><b>做法是“替换”，不是“叠加”。</b>早期版本是在原版渲染完之后再补画一层星空，结果要自己把整套
 * 位移/旋转/缩放重新算一遍，永远差一点，看起来就是两层刀光错位。现在改成拦截原版的绘制调用：
 * 模型、位置、朝向、组名全部沿用原版已经算好的，只把最后的 {@link RenderType}（用哪张贴图、哪个
 * 着色器、怎么混合）换掉。这样星空层天然和原版同尺寸同方向。</p>
 *
 * <p>这个类本身不注入任何东西，是三个 mixin 的“后勤”：mixin 在渲染器开头调 {@code begin*} 告诉它
 * “现在正在画哪个实体”，在绘制调用处问它“这层该不该换”，画完调 {@code end} 收尾。</p>
 */
public final class StarOverlay {
    /**
     * 判定“这把刀是不是纯银星”用的是语言键而不是物品 id。
     *
     * <p>多种来源（创造模式标签、JEI、别的模组复制）拿到的刀，物品本身可能都是 {@code slashblade:slashblade}，
     * 真正区分是哪把刀的是刀上记着的名字，而名字经 {@code getTranslationKey()} 转出来就是这个键。</p>
     */
    public static final String BLADE_TRANSLATION_KEY = "item.silverstar.silver_star";

    /** 「金色星」——这把是我的刀。判定方式和纯银星一样，看刀上记着的语言键。 */
    public static final String GOLDEN_TRANSLATION_KEY = "item.silverstar.golden_star";

    /**
     * 测试刀的键。换模型试验期间，把它也当"我们的刀"处理，但和纯银星一样只让刀刃是星空——
     * 区别只是遮罩贴图换成按它自己 UV 切的那两张。
     */
    private static final String TEST_BLADE_KEY = "item.silverstar.test_annihilation";

    /**
     * 这团特效“属不属于纯银星”的结论，在它第一次被画的那一帧算一次，然后写进实体自己的持久化数据里。
     *
     * <p>不能每帧都去问“放招的人现在手上拿的是什么”：刀光比挥砍活得久，挥完立刻切刀的话，
     * 已经放出去的刀光会被后来的刀重新判一遍，颜色当场变掉。</p>
     */
    private static final String BELONGS_TAG = "silverstar_starry";

    /** 当前这一次渲染调用里，“正在画的这团特效属于纯银星”是否成立。 */
    private static final ThreadLocal<Boolean> STARRY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 当前这一层的不透明度，用来做淡入淡出，免得特效到时间直接消失。 */
    private static final ThreadLocal<Float> FADE = ThreadLocal.withInitial(() -> 1.0F);

    /**
     * 开了光影时替换用的三个颜色。
     *
     * <p>Iris 会在实体渲染之后重置核心着色器，我们自己的星空着色器在那条管线里画不出来，
     * 所以干脆不画星空，改为把原版那几层的颜色换掉——观感上仍然是“这把刀不一样”，又不用跟管线打架。</p>
     */
    public static final int CYAN = 0x2E8FA8;
    public static final int DEEP_BLUE = 0x16214F;
    public static final int PURPLE = 0x5E3486;

    private static boolean shaderPackActive;
    private static long nextPackProbe;
    private static Object irisApi;
    private static java.lang.reflect.Method isPackInUse;

    private StarOverlay() {
    }

    /**
     * 现在有没有开着光影。
     *
     * <p>每 500 毫秒最多探一次，而且只在真的要画我们自己的层时才探——读一个布尔值而已，
     * 开销可以忽略，但没必要每帧都去碰反射。</p>
     */
    public static boolean shaderPackActive() {
        long now = System.currentTimeMillis();
        if (now >= nextPackProbe) {
            nextPackProbe = now + 500L;
            shaderPackActive = probeShaderPack();
        }
        return shaderPackActive;
    }

    /** 这层属于纯银星，但必须退回原版外观（开光影时的分支）。 */
    public static boolean vanillaLook() {
        return STARRY.get() && shaderPackActive();
    }

    /**
     * 用反射问 Iris（光影模组）有没有在跑光影。
     *
     * <p>用反射是为了不把 Iris 变成硬依赖：没装 Iris 时这里抛异常、我们当成“没开光影”就好，
     * 模组照样能启动。反射出来的类和 Method 会缓存起来，避免每次调用都重新查一遍。</p>
     */
    private static boolean probeShaderPack() {
        try {
            if (isPackInUse == null) {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = apiClass.getMethod("getInstance").invoke(null);
                isPackInUse = apiClass.getMethod("isShaderPackInUse");
            }
            return Boolean.TRUE.equals(isPackInUse.invoke(irisApi));
        } catch (Throwable ignored) {
            isPackInUse = null;
            irisApi = null;
            return false;
        }
    }

    /**
     * 只替换 ARGB 颜色里的红绿蓝，<b>alpha 原样保留</b>。
     *
     * <p>原版每层用的 alpha 不只是透明度，它同时是那一层的亮度档位；刀光淡出也是靠降 alpha 实现的。
     * 把 alpha 一起换掉的话，亮的那层会变暗、淡出也会失效。</p>
     */
    public static int recolour(int argb, int rgb) {
        return (argb & 0xFF000000) | (rgb & 0xFFFFFF);
    }

    public static java.awt.Color recolour(java.awt.Color colour, int rgb) {
        return new java.awt.Color((colour.getAlpha() << 24) | (rgb & 0xFFFFFF), true);
    }

    /**
     * 这道非星空刀光该乘的颜色（-1 = 不处理）。
     *
     * <p>只认余烬自己生成的刀光：它们在颜色的高 8 位带着标记位，低 24 位是刀本身的颜色。
     * 颜色是同步字段、客户端读得到；`getPersistentData()` 只在服务端，用不了（踩过一次）。</p>
     */
    private static int bladeColour(Entity entity) {
        if (!(entity instanceof mods.flammpfeil.slashblade.entity.EntitySlashEffect slash)) {
            return -1;
        }
        int raw = slash.getColor();
        if ((raw & 0xFF000000) != 0x01000000) {
            return -1;
        }
        return raw & 0xFFFFFF;
    }

    /** 渲染器问：这道非星空刀光该乘什么颜色（-1 = 不处理）。 */
    public static int slashTint() {
        return SLASH_COLOR.get();
    }

    /** 刀光渲染器开头调用：判定这次要画的刀光是不是纯银星放出来的。 */
    public static void begin(Entity entity, float partialTicks) {
        if (!isSilverStarSlash(entity)) {
            STARRY.set(Boolean.FALSE);
            FADE.set(1.0F);
            BLUE_SLASH.set(Boolean.FALSE);
            GOLD_SLASH.set(Boolean.FALSE);
            // 不是我们的刀：走原版渲染，但要把它自己刀的颜色乘进去（原版那几层本身偏白）。
            SLASH_COLOR.set(bladeColour(entity));
            return;
        }
        SLASH_COLOR.set(-1);
        STARRY.set(Boolean.TRUE);
        FADE.set(fade(entity, partialTicks));
        // 完美次元斩额外斩出的那几刀由 SlashMarker 打了标记，这里跟着切换配色。
        BLUE_SLASH.set(entity.getPersistentData().getBoolean(BLUE_SLASH_TAG));
        // 「金色星」放出来的刀光走金色那一份程序。
        GOLD_SLASH.set(isGolden(entity));
    }

    /** 渲染器结束时调用，把状态清掉，免得影响下一个实体。 */
    public static void end() {
        DIM_GOLD.set(Boolean.FALSE);
        STARRY.set(Boolean.FALSE);
        SLASH_COLOR.set(-1);
    }

    public static boolean active() {
        return STARRY.get();
    }

    /**
     * 刀光的淡出曲线：前半段满亮度，最后 45% 的寿命里平滑降到 0。
     *
     * <p>原版是到点直接消失，看起来像“啪”一下没了；加个淡出柔和很多。</p>
     *
     * <p>用的是 smoothstep 曲线（{@code t * t * (3 - 2t)}），它的特点是两头变化慢、中间快，
     * 比线性淡出更像人眼期待的样子。{@code partialTicks} 是一帧内的插值进度，
     * 用上它才能让淡出跟画面帧率无关。</p>
     */
    private static float fade(Entity entity, float partialTicks) {
        if (!(entity instanceof EntitySlashEffect slash)) {
            return 1.0F;
        }
        float lifetime = Math.max(1.0F, slash.getLifetime());
        float progress = Math.min(lifetime, slash.tickCount + partialTicks) / lifetime;
        // 0.65 而不是更小的数：实体是在 tickCount 走到 lifetime 时被删掉的，
        // 最后一帧通常只到 progress ≈ 0.9，淡出窗口太窄的话那一刻还剩十几的透明度，
        // 删掉时就会“啪”一下不见。窗口留宽，最后一帧才真的接近 0。
        float remain = 1.0F - progress;
        float t = Math.min(1.0F, remain / 0.65F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static boolean isSilverStarSlash(Entity entity) {
        if (!(entity instanceof EntitySlashEffect)) {
            return false;
        }
        return isStarry(entity);
    }

    /**
     * “这团特效属于纯银星”只在它第一次被画时判定一次，结论存进实体自己的持久化数据。
     *
     * <p>见 {@link #BELONGS_TAG} 上的说明。</p>
     */
    private static boolean isStarry(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(BELONGS_TAG)) {
            Entity shooter = shooterOf(entity);
            data.putBoolean(BELONGS_TAG, isSilverStarShooter(shooter) || isGoldenStarShooter(shooter));
            data.putBoolean(GOLD_TAG, isGoldenStarShooter(shooter));
        }
        return data.getBoolean(BELONGS_TAG);
    }

    /** 这团特效是不是「金色星」放的（决定刀光用金色那一份程序）。 */
    private static boolean isGolden(Entity entity) {
        if (!(entity instanceof EntitySlashEffect)) {
            return false;
        }
        return isStarry(entity) && entity.getPersistentData().getBoolean(GOLD_TAG);
    }

    /** 放招的人手上是不是「金色星」。 */
    private static boolean isGoldenStarShooter(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return false;
        }
        ItemStack held = living.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        return held.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> GOLDEN_TRANSLATION_KEY.equals(state.getTranslationKey()))
                .orElse(Boolean.FALSE);
    }

    /** 三种特效各自的“是谁放的”取法不一样，这里统一一下。 */
    private static Entity shooterOf(Entity entity) {
        if (entity instanceof EntitySlashEffect slash) {
            return slash.getShooter();
        }
        if (entity instanceof EntityAbstractSummonedSword sword) {
            return sword.getShooter();
        }
        if (entity instanceof EntityJudgementCut dim) {
            return dim.getShooter();
        }
        if (entity instanceof EntityDrive drive) {
            return drive.getShooter();
        }
        return null;
    }

    /** 幻影剑渲染器开头调用：幻影剑是“凭空出现”的，所以前 6 tick 做个淡入。 */
    public static void beginSword(Entity entity, float partialTicks) {
        if (!(entity instanceof EntityAbstractSummonedSword sword) || !isStarry(sword)) {
            STARRY.set(Boolean.FALSE);
            FADE.set(1.0F);
            return;
        }
        STARRY.set(Boolean.TRUE);
        // 不透明度恒为 1：着色器上的不透明度是全局参数，顶点攒成一批一起提交，
        // 新剑要是从 0 淡入，那个低值会把同批里所有还活着的剑一起压暗，看着就是"一起闪一下"。
        FADE.set(1.0F);
    }

    /** 次元斩渲染器开头调用：次元斩前 25% 的寿命做淡入，让它像“张开”而不是“闪一下”。 */
    public static void beginDim(Entity entity, float partialTicks) {
        if (!(entity instanceof EntityJudgementCut dim) || !isStarry(dim)) {
            STARRY.set(Boolean.FALSE);
            FADE.set(1.0F);
            return;
        }
        STARRY.set(Boolean.TRUE);
        DIM_GOLD.set(isGoldenStarShooter(shooterOf(dim)));
        float lifetime = Math.max(1.0F, dim.getLifetime());
        float progress = Math.min(lifetime, dim.tickCount + partialTicks) / lifetime;
        float t = Math.min(1.0F, progress / 0.25F);
        FADE.set(t * t * (3.0F - 2.0F * t));
    }

    /**
     * 斩击波渲染器开头调用。
     *
     * <p>{@code EntityDrive} 是波刀龙胆、纵斩/横斩波这些招式共用的实体，原版一次只画一层，
     * 所以这里只需要判一次归属、算一次浓淡，没有刀光那种“四层各画一遍”的问题。
     * 寿命末尾做淡出，理由同刀光：实体是按 tick 到点删的，最后一帧还留着一截透明度。</p>
     */
    public static void beginWave(Entity entity, float partialTicks) {
        if (!(entity instanceof EntityDrive drive) || !isStarry(drive)) {
            STARRY.set(Boolean.FALSE);
            FADE.set(1.0F);
            return;
        }
        STARRY.set(Boolean.TRUE);
        float lifetime = Math.max(1.0F, drive.getLifetime());
        float progress = Math.min(lifetime, drive.tickCount + partialTicks) / lifetime;
        float t = Math.min(1.0F, (1.0F - progress) / 0.5F);
        FADE.set(t * t * (3.0F - 2.0F * t));
    }

    /** 放招的人手上是不是捏着纯银星。 */    private static boolean isSilverStarShooter(Entity shooter) {
        if (!(shooter instanceof LivingEntity living)) {
            return false;
        }
        ItemStack held = living.getMainHandItem();
        if (held.isEmpty()) {
            return false;
        }
        return held.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> BLADE_TRANSLATION_KEY.equals(state.getTranslationKey()))
                .orElse(Boolean.FALSE);
    }

    /**
     * 用星空着色器画一层原版刀光。由 mixin 的 Redirect 调用，
     * 所以进来时位姿栈和面部状态就是原版渲染器当时算好的那一份，不用也不该重新算。
     */
    public static void draw(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                            PoseStack pose, MultiBufferSource buffers, int light) {
        // 用原版刀光自己的贴图当 Sampler0：它的 alpha 就是那道弧的形状，
        // 星点则由 cosmic 按空间方向算，于是四层虽然 UV 各不相同，看到的仍是同一片星空。
        boolean blue = BLUE_SLASH.get();
        RenderType type = blue
                ? StarRenderTypes.cosmicArcBlue(texture)
                : (GOLD_SLASH.get() ? StarRenderTypes.cosmicArcGold(texture)
                        : StarRenderTypes.cosmicArc(texture));
        drawWith(stack, model, group, texture, pose, buffers, light, type);
    }

    /** 幻影剑：整把剑都在发光，星点稀疏一些，染成深紫。 */
    public static void drawSword(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                 PoseStack pose, MultiBufferSource buffers, int light) {
        drawWith(stack, model, group, texture, pose, buffers, light,
                StarRenderTypes.cosmicPhantom(texture));
    }

    /**
     * 斩击波（波刀龙胆这类 drive 招式）：整片换成星空。
     *
     * <p>和刀光同一条路子——把原版自己的贴图当 Sampler0，用它的 alpha 当形状，
     * 星点由 cosmic 按空间方向算，所以一片飞过去时星星是“印在同一片天上”的，不会跟着片走。</p>
     */
    public static void drawWave(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                PoseStack pose, MultiBufferSource buffers, int light) {
        drawWith(stack, model, group, texture, pose, buffers, light,
                StarRenderTypes.cosmicWave(texture));
    }

    /** 次元斩的球：原版贴图是纯白，形状完全靠模型几何——这颗球已经退回原版，这里保留给以后备用。 */
    public static void drawDim(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                               PoseStack pose, MultiBufferSource buffers, int light) {
        drawWith(stack, model, group, texture, pose, buffers, light,
                StarRenderTypes.dim(StarRenderTypes.STARS_PLAIN));
    }

    /**
     * 锁定目标的那个圆环：手上捏着纯银星时整圈换成星空。
     *
     * <p>它不是实体，是 {@code RenderLivingEvent} 里现画的三个组（底座、血条遮罩、血条），
     * 所以没有“属于谁”可以查，只能看本地玩家手上拿的是什么。</p>
     */
    public static boolean lockonStarry() {
        return !vanillaLook() && isSilverStarShooter(Minecraft.getInstance().player);
    }

    /** 圆环的三个组都用同一套星空画，形状仍由原版贴图的 alpha 决定。 */
    public static void drawLockon(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                  PoseStack pose, MultiBufferSource buffers, int light) {
        // 用单独一份着色器程序（cosmic_lockon）+ 固定 1.0 的不透明度。
        // 混用 cosmic_arc 的话，刀光淡出到 0 的那一帧圆环会跟着熄一下（uniform 是程序级状态）。
        Minecraft minecraft = Minecraft.getInstance();
        float time = minecraft.level == null ? 0.0F : (float) minecraft.level.getGameTime() * 0.05F;
        StarShaders.updateLockon(time);

        RenderType renderType = StarRenderTypes.cosmicLockon(texture);
        renderGroup(stack, model, group, texture, pose, buffers, light, renderType);
        flush(buffers, renderType);
    }

    /**
     * 绕着次元斩球转的那五道风纹。
     *
     * <p>原版是循环画五道，每一道的颜色和透明度都不一样，都靠画之前设的那个颜色值区分。
     * 我们在拦下“设颜色”那一步时把它存到这里，再当作 tint 塞进 cosmic，
     * 于是风纹的星域和刀光是同一套算法、同一个色调，只是每道自身的浓淡被保留下来。</p>
     */
    public static void drawWind(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        float time = minecraft.level == null ? 0.0F : (float) minecraft.level.getGameTime() * 0.05F;

        float alpha = WIND_ALPHA.get();
        boolean gold = GOLD_SLASH.get();
        if (gold) {
            // 金色星：换一份金色片元，参数完全一样（tint 是程序级状态，共用会把纯银星也染金）。
            StarShaders.updateWindGold(time, FADE.get() * alpha);
        } else {
            // 色调固定给白：刀光那层 cosmic 的 tint 就是白，风纹要保持“同一种颜色”，
            // 原版的颜色值只用来取这一道自身的透明度（每一道的浓淡不同）。
            StarShaders.updateWind(time, FADE.get() * alpha, 1.0F, 1.0F, 1.0F);
        }

        RenderType renderType = gold
                ? StarRenderTypes.cosmicWindGold(texture)
                : StarRenderTypes.cosmicWind(texture);
        renderGroup(stack, model, group, texture, pose, buffers, light, renderType);

        // 每一道单独提交：色调和透明度是每道自己的，混在一批里会被最后一个覆盖。
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(renderType);
        }
    }

    /**
     * 这团次元斩的球该用什么颜色。
     *
     * <p>球走的是 {@code renderOverridedReverseLuminous}（反色那条路），所以这里给的是
     * <b>目标颜色的反相</b>：反色之后才会变回我们要的青 / 金。</p>
     *
     * @return 要给球设置的颜色；-1 表示这团不是我们的，按原版处理
     */
    public static int dimSphereColour() {
        if (!STARRY.get()) {
            return -1;
        }
        int target = DIM_GOLD.get() ? GOLD : CYAN;
        return (~target) & 0xFFFFFF;
    }

    /** 当前这一道风纹自身的不透明度（0~1，来自原版设置的颜色值里的 alpha）。 */
    private static final ThreadLocal<Float> WIND_ALPHA = ThreadLocal.withInitial(() -> 1.0F);

    /** 由 mixin 在拦下原版“给风纹设颜色”时调用，把这一道的浓淡记下来。 */
    public static void setWindTint(java.awt.Color colour) {
        WIND_ALPHA.set(colour.getAlpha() / 255.0F);
    }

    /** 正在用星空画刀身本体，用来防止我们的重画又绕回同一个拦截点（无限递归）。 */
    private static final ThreadLocal<Boolean> BLADE_BUSY = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 这一刀是不是完美次元斩额外斩出来的（由 {@code SlashMarker} 打标记）。 */
    public static final String BLUE_SLASH_TAG = "silverstar_blue_slash";

    /** 当前这一刀要不要用深蓝配色，跟风纹统一。 */
    private static final ThreadLocal<Boolean> BLUE_SLASH = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 这团特效是不是「金色星」放的（决定刀光用金色那份着色器）。同一个实体只判定一次。 */
    private static final String GOLD_TAG = "silverstar_gold_starry";
    private static final ThreadLocal<Boolean> GOLD_SLASH = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 这团次元斩是不是金色星放的（决定球和风纹用哪个颜色）。 */
    private static final ThreadLocal<Boolean> DIM_GOLD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 金色刀光的颜色（0xE8C46A），球和风纹都往它靠。 */
    public static final int GOLD = 0xE8C46A;

    /** 非星空刀光要乘上去的刀色（-1 = 不处理）。 */
    private static final ThreadLocal<Integer> SLASH_COLOR = ThreadLocal.withInitial(() -> -1);

    /** 这几个组会被拦下来重画：刀身、破损刀身、刀鞘。图标和附魔光保持原样。 */
    private static boolean isBladeGroup(String target) {
        return "blade".equalsIgnoreCase(target)
                || "blade_damaged".equalsIgnoreCase(target)
                || "sheath".equalsIgnoreCase(target);
    }

    /** 刀身类（含刀柄，柄就在 blade 组里）。只有这两个组会按 UV 切成"身体 + 刀刃"两半。 */
    private static boolean isBodyGroup(String target) {
        return "blade".equalsIgnoreCase(target)
                || "blade_damaged".equalsIgnoreCase(target);
    }

    /**
     * 模型自带的额外发光层（组名里带 luminous），不包含物品图标那几组。
     * 只在测试刀上被掐掉，用来确认"双影"是不是它造成的。
     */
    private static boolean isLuminousExtra(String target) {
        if (target == null) {
            return false;
        }
        String t = target.toLowerCase(java.util.Locale.ROOT);
        return t.contains("luminous") && !t.startsWith("item");
    }

    /** 刀贴图挖掉刀刃那条之后的版本：刀柄、护手这些从这张出，走原版混合。 */
    private static final ResourceLocation BLADE_BODY_TEXTURE =
            new ResourceLocation("silverstar", "model/silver_star_body.png");

    /** 只有刀刃那条有像素的版本：它的 alpha 就是星空的形状遮罩。 */
    private static final ResourceLocation BLADE_MASK_TEXTURE =
            new ResourceLocation("silverstar", "model/silver_star_blade.png");

    /** 换模型试验刀的两张：按湮灭之刃自己的 UV 位置切的，只在测试刀上用。 */
    private static final ResourceLocation TEST_BODY_TEXTURE =
            new ResourceLocation("silverstar", "model/test_annihilation_body.png");
    private static final ResourceLocation TEST_MASK_TEXTURE =
            new ResourceLocation("silverstar", "model/test_annihilation_mask.png");

    /**
     * 「金色星」的刀身贴图：同一把散华模型，把银色换成暗金。
     * 刀刃那条同样是挖空的 —— 它由金色调的着色器程序补上星空。
     */
    private static final ResourceLocation GOLDEN_BODY_TEXTURE =
            new ResourceLocation("silverstar", "model/golden_star_body.png");

    /** 这个物品栈是不是纯银星（或换模型试验用的测试刀、或金色星）。和特效那套判定同源，都是看刀上记着的语言键。 */
    public static boolean isOurBlade(ItemStack stack) {
        String key = bladeKey(stack);
        return BLADE_TRANSLATION_KEY.equals(key) || TEST_BLADE_KEY.equals(key)
                || GOLDEN_TRANSLATION_KEY.equals(key);
    }

    /** 刀上记着的语言键；不是拔刀剑或读不到时返回空串。 */
    private static String bladeKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return stack.getCapability(ItemSlashBlade.BLADESTATE)
                .map(state -> state.getTranslationKey())
                .orElse("");
    }

    /**
     * 把“正在画纯银星刀身”这一层换成星空。返回 true 表示已经画完，调用方应当取消原调用。
     *
     * <p>由 {@code BladeRenderStateMixin} 在 {@code renderOverrided} 开头调用。三处门槛：</p>
     * <ul>
     *   <li>开着光影时返回 false——Iris 会重置核心着色器，我们的星空在那条管线上根本画不出来，
     *       硬画只会得到一块黑，不如老老实实用原版材质</li>
     *   <li>只接刀身和刀鞘这几种组，图标（item_blade）和附魔光保持原样</li>
     *   <li>{@link #BLADE_BUSY} 防止我们自己的 {@code renderOverrided} 调用又被拦一次</li>
     * </ul>
     */
    public static boolean tryBladeStar(ItemStack stack, WavefrontObject model, String target,
                                       ResourceLocation texture, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
        if (BLADE_BUSY.get()) {
            return false;
        }
        String key = bladeKey(stack);
        // 有些模型自带一层"发光描边"（组名带 luminous），叠在星空上会糊成一片双影。
        // 测试刀先把它整组掐掉，返回 true 表示"这层我处理了"（画什么都不画）。
        if (TEST_BLADE_KEY.equals(key) && isLuminousExtra(target)) {
            return true;
        }
        if (!isOurBlade(stack) || !isBladeGroup(target)) {
            return false;
        }
        // 开关只管"整把刀"，刀刃永远走下面的两遍绘制，所以这把刀的任何状态都能看到星空刃。
        // 测试刀用自己那两张按湮灭之刃 UV 切的贴图，但同样只让刀刃是星空。
        boolean testBlade = TEST_BLADE_KEY.equals(key);
        boolean golden = GOLDEN_TRANSLATION_KEY.equals(key);
        boolean whole = SSConfig.bladeStarRender() && !testBlade;
        if (shaderPackActive()) {
            // 光影下 Iris 接管核心着色器，我们的 cosmic 程序不会被调用，第二遍等于没画。
            // 这时候如果还拦下来，第一遍用的是"刀刃被挖空"的 body 贴图，刀刃就直接变成空气。
            // 所以光影下一律不拦，交回原版用完整贴图——那张贴图的刀刃条里烤了星空，静态但有图。
            return false;
        }
        if (!whole && !isBodyGroup(target)) {
            // 关着开关时刀鞘保持原版：不拦，交给原渲染。
            return false;
        }
        BLADE_BUSY.set(Boolean.TRUE);
        // 刀身有自己的不透明度，不跟刀光那条淡出曲线走。
        // FADE 是 ThreadLocal，刀光算完留在里面的是「刀光寿命末尾的 0」，
        // 如果直接拿来用，刀身要等到下一次刀光才看得见——这就是"刀光消失、刀上的星空也一起没了"。
        float previousFade = FADE.get();
        FADE.set(1.0F);
        try {
            // 刀身那份程序的不透明度恒为 1，这里只刷动画相位；
            // 不能走 StarShaders.update()，否则刀光淡到 0 时会把刀身一起写暗。
            Minecraft minecraft = Minecraft.getInstance();
            float time = minecraft.level == null ? 0.0F : (float) minecraft.level.getGameTime() * 0.05F;
            if (golden) {
                // 金色星用自己那份程序：金色写在 cosmic_gold.json 的 tint 默认值里，
                // 和刀身共用一份的话会把纯银星也一起染黄。
                StarShaders.updateBladeGold(time);
            } else {
                StarShaders.updateBlade(time);
            }
            if (whole) {
                // 开关打开：整组一次画成星空，刀身、刀柄、刀鞘一起。
                drawWith(stack, model, target, texture, pose, buffers, light,
                        golden ? StarRenderTypes.cosmicGold(texture) : StarRenderTypes.cosmic(texture));
            } else {
                // 开关关闭（或测试刀）：只让刀刃是星空，刀柄和护手保持原贴图。
                ResourceLocation bodyTex = testBlade ? TEST_BODY_TEXTURE
                        : (golden ? GOLDEN_BODY_TEXTURE : BLADE_BODY_TEXTURE);
                ResourceLocation maskTex = testBlade ? TEST_MASK_TEXTURE : BLADE_MASK_TEXTURE;
                // 第一遍：原版混合，贴图里刀刃那条已被挖空，所以只出刀柄、护手。
                RenderType bodyType = BladeRenderState.getSlashBladeBlend(bodyTex);
                renderGroup(stack, model, target, bodyTex, pose, buffers, light, bodyType);
                // 立刻提交，别让这批顶点留到帧末——那时别处可能已经把全局状态改掉了。
                flush(buffers, bodyType);
                // 第二遍：遮罩贴图的 alpha 只在刀刃那条上，星点也只落在刀刃。
                drawWith(stack, model, target, maskTex, pose, buffers, light,
                        golden ? StarRenderTypes.cosmicGold(maskTex) : StarRenderTypes.cosmic(maskTex));
            }
        } finally {
            FADE.set(previousFade);
            BLADE_BUSY.set(Boolean.FALSE);
        }
        return true;
    }

    /**
     * 用指定的 RenderType 把模型的一组画出去，并处理颜色置白/复位。
     *
     * <p>{@code setCol(0xFFFFFFFF)} 是必须的——原版画刀光前会把颜色设成当层配色，
     * 我们的星空颜色全在着色器里算，这里不先置白的话会被残留的颜色染一遍，然后画完再复位。</p>
     */
    private static void renderGroup(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                    PoseStack pose, MultiBufferSource buffers, int light,
                                    RenderType renderType) {
        BladeRenderState.setCol(0xFFFFFFFF);
        BladeRenderState.renderOverrided(stack, model, group, texture, pose, buffers, light,
                ignored -> renderType, false);
        BladeRenderState.resetCol();
    }

    /**
     * 四种特效最终都走这里：刷一次着色器的动画参数，然后把这一层画出去。
     */
    private static void drawWith(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                 PoseStack pose, MultiBufferSource buffers, int light,
                                 RenderType renderType) {
        drawWith(stack, model, group, texture, pose, buffers, light, renderType, FADE.get());
    }

    /**
     * 同上，但自己指定不透明度。
     *
     * <p>用于那些"不属于刀光"的绘制（比如锁定目标的圆环）：它们和刀光共用同一个着色器程序，
     * 一旦跟着刀光的淡出值走，就会在挥刀的最后一帧一起消失。</p>
     */
    private static void drawWith(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                                 PoseStack pose, MultiBufferSource buffers, int light,
                                 RenderType renderType, float opacity) {
        Minecraft minecraft = Minecraft.getInstance();
        // 游戏时间推进星空漂移；乘 0.05 只是把“每 tick”换成着色器里好用的量级。
        float time = minecraft.level == null ? 0.0F : (float) minecraft.level.getGameTime() * 0.05F;

        StarShaders.update(time, opacity);

        renderGroup(stack, model, group, texture, pose, buffers, light, renderType);

        // 立刻把这一批顶点提交掉。
        // 不这么做的话，同一个缓冲区里会攒着好几个实体/好几层的顶点，而 uniform 是每批提交时才读的，
        // 结果全批都用了“最后一个”的不透明度——召唤一把新幻影剑时，已经存在的那些会跟着它一起淡入闪一下。
        flush(buffers, renderType);
    }

    /**
     * 把某个 RenderType 的顶点立刻提交掉。
     *
     * <p>两道保险：传进来的 {@code buffers} 不一定就是 {@code BufferSource}（发光描边之类会包一层），
     * 所以再让全局的层级缓冲区也刷一次这个 RenderType——同一份顶点只会在一个地方攒着，
     * 谁攒着谁刷，不会重复绘制。</p>
     */
    private static void flush(MultiBufferSource buffers, RenderType renderType) {
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(renderType);
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.renderBuffers().bufferSource().endBatch(renderType);
        }
    }
}
