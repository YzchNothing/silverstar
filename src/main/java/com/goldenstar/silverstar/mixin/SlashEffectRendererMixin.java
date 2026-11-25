package com.goldenstar.silverstar.mixin;

import com.goldenstar.silverstar.client.StarOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.entity.SlashEffectRenderer;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把纯银星放出的刀光换成星空层。
 *
 * <p>原版渲染器画一道刀光要调好几次绘制（按评级档位走不同分支）。我们<b>不额外加一遍</b>，
 * 而是把那几次调用接管掉：只有确认这刀光出自手持纯银星的玩家时，才换一个 {@link net.minecraft.client.renderer.RenderType} 给它，
 * 其余参数（位姿、模型、组名、颜色、alpha、UV 偏移）原样保留。</p>
 *
 * <p>两个容易踩的点，改这里之前先看：</p>
 * <ul>
 *   <li><b>要拦的是 {@code render(...)} 这个具体方法，不是 {@code m_7392_}。</b>
 *       {@code m_7392_} 只是泛型桥接，里面没有绘制调用点，拦它结果是“0/1 命中”直接加载失败。</li>
 *   <li><b>Redirect 的处理方法必须是实例方法（现在就是），不能加 static。</b>
 *       规则是被注入的那个 {@code render} 是实例方法，处理方法的修饰符必须跟它一致；
 *       写成 static 会在启动时报 {@code 'static' modifier of handler method does not match target}。</li>
 * </ul>
 */
@Mixin(SlashEffectRenderer.class)
public abstract class SlashEffectRendererMixin {
    /**
     * 真正干活的方法名。生产的 jar 里没有混淆，所以名字就是 {@code render}；
     * 参数是实体、两个 float（实体朝向和帧插值）、位姿栈、顶点缓冲、光照值。
     */
    private static final String RENDER_IMPL =
            "render(Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;" + SSDescriptors.RENDER_REST;

    /** 绘制调用之一：不透明/带混合的那一路（Mixin 靠它精确定位调用点）。 */
    private static final String COLOR_WRITE_TARGET = SSDescriptors.COLOR_WRITE;

    /** 绘制调用之二：自发光那一路。 */
    private static final String LUMINOUS_TARGET = SSDescriptors.LUMINOUS;

    /** 原版设置当层颜色的调用。 */
    private static final String SET_COL_INT = SSDescriptors.SET_COL_INT;

    /** 渲染开始：让 StarOverlay 判定这次画的是不是纯银星的刀光，并重置“只画一层”的计数。 */
    @Inject(method = RENDER_IMPL, at = @At("HEAD"))
    private void silverstar$begin(EntitySlashEffect entity, float entityYaw, float partialTicks,
                                  PoseStack pose, MultiBufferSource buffers, int light,
                                  CallbackInfo ci) {
        StarOverlay.begin(entity, partialTicks);
    }

    /** 渲染结束：清掉状态，免得影响下一个实体。 */
    @Inject(method = RENDER_IMPL, at = @At("RETURN"))
    private void silverstar$end(EntitySlashEffect entity, float entityYaw, float partialTicks,
                                PoseStack pose, MultiBufferSource buffers, int light,
                                CallbackInfo ci) {
        StarOverlay.end();
    }

    /**
     * 开了光影时不画星空，保留原版这一层，但把颜色换成青色。
     *
     * <p>只换 RGB，alpha 不能动——原版拿 alpha 当“这一层多亮”以及淡出的通道，换了会出怪事。</p>
     *
     * <p>其余情况一律原样放行：刀光的颜色应当由刀本身决定（每把刀在状态里存着自己的效果色），
     * 渲染端不要再插手。</p>
     */
    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = SET_COL_INT))
    private void silverstar$setCol(int argb) {
        if (StarOverlay.vanillaLook()) {
            BladeRenderState.setCol(StarOverlay.recolour(argb, StarOverlay.CYAN));
        } else {
            // 非星空刀光（别的刀放余烬）：原版这几层本身偏白，把刀自己的颜色乘上去。
            int tint = StarOverlay.slashTint();
            BladeRenderState.setCol(tint < 0 ? argb : multiply(argb, tint));
        }
    }

    /** 把颜色的 RGB 三个通道按刀色缩放，alpha 原样保留。 */
    private static int multiply(int argb, int rgb) {
        int a = argb & 0xFF000000;
        int r = ((argb >> 16) & 0xFF) * ((rgb >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((rgb >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (rgb & 0xFF) / 255;
        return a | (r << 16) | (g << 8) | b;
    }

    /** 原版的混合绘制：是纯银星的刀光就换成星空着色器画，否则原样放行。 */
    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = COLOR_WRITE_TARGET))
    private void silverstar$colorWrite(ItemStack stack, WavefrontObject model, String group,
                                       ResourceLocation texture, PoseStack pose,
                                       MultiBufferSource buffers, int light) {
        if (StarOverlay.active() && !StarOverlay.vanillaLook()) {
            StarOverlay.draw(stack, model, group, texture, pose, buffers, light);
        } else {
            BladeRenderState.renderOverridedColorWrite(stack, model, group, texture, pose, buffers, light);
        }
    }

    /** 原版的自发光绘制，同上。 */
    @Redirect(method = RENDER_IMPL, at = @At(value = "INVOKE", target = LUMINOUS_TARGET))
    private void silverstar$luminous(ItemStack stack, WavefrontObject model, String group,
                                     ResourceLocation texture, PoseStack pose,
                                     MultiBufferSource buffers, int light) {
        if (StarOverlay.active() && !StarOverlay.vanillaLook()) {
            StarOverlay.draw(stack, model, group, texture, pose, buffers, light);
        } else {
            BladeRenderState.renderOverridedLuminous(stack, model, group, texture, pose, buffers, light);
        }
    }
}
