package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 光影下把星空层"记下来、晚一步再画"的排队处。
 *
 * <p><b>为什么要排队：</b>开着光影时，Iris 在世界渲染期间把颜色写入关掉了
 * （{@code glColorMask(0,0,0,0)}），那一刻画什么都落不到屏幕上。
 * 它做完最后的合成才放开，也就是 {@code AFTER_LEVEL}。</p>
 *
 * <p><b>怎么保证位置不歪：</b>原版渲染器算好的位姿（{@code PoseStack} 里的矩阵）原样记下来，
 * 重放时先装回相机视图矩阵，再乘这份位姿，顶点还是由同一段
 * {@link BladeRenderState#renderOverrided} 生成。</p>
 *
 * <p><b>视图矩阵保持单位阵。</b>原版把相机旋转写进了 {@code PoseStack}，顶点已经是相机相对坐标；
 * 再叠一层相机旋转只会把刀光甩成碎块（实测三种都试过）。</p>
 *
 * <p>只记录、不自己算位置——以后接幻影剑、斩击波、次元斩，都只是"多记一种槽位"。</p>
 */
@Mod.EventBusSubscriber(modid = Silverstar.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StarDeferred {

    /** 一帧最多记这么多笔，防止某个特效刷屏把内存吃光。 */
    private static final int MAX_ENTRIES = 512;

    /** 一笔待画的星空层。位姿存副本，原 PoseStack 早就被弹掉了。 */
    private record Entry(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                         int light, Matrix4f pose, StarRender palette, float opacity, float time,
                         RenderType type) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    private static final Logger LOG = LoggerFactory.getLogger("silverstar/deferred");
    private static boolean loggedFailure;

    private StarDeferred() {
    }

    /** 由 {@link StarOverlay} 在"有光影、这层本来是星空"时调用。 */
    public static void record(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                              int light, PoseStack pose,
                              StarRender palette, float opacity, float time, RenderType type) {
        if (ENTRIES.size() >= MAX_ENTRIES) {
            return;
        }
        ENTRIES.add(new Entry(stack, model, group, texture, light,
                new Matrix4f(pose.last().pose()), palette, opacity, time, type));
    }

    /** 光影的合成已经做完、颜色写入重新放开——把这一帧记下的星空层一次性画上去。 */
    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ENTRIES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        // Iris 的 final pass 输出到主 RenderTarget 的颜色纹理，深度也挂在这上面，绑定它就能正常遮挡。
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        // 世界渲染时顶点的空间已经由位姿本身决定了（原版把相机旋转写进了 PoseStack），
        // 这里保持单位阵，别再叠一层相机旋转——实测叠上去会把刀光甩成碎块。
        PoseStack mv = RenderSystem.getModelViewStack();
        try {
            for (Entry entry : ENTRIES) {
                mv.pushPose();
                mv.last().pose().identity();
                mv.last().normal().identity();
                RenderSystem.applyModelViewMatrix();

                PoseStack pose = new PoseStack();
                pose.mulPoseMatrix(entry.pose());

                StarShaders.update(entry.time(), entry.opacity(), entry.palette());

                // 用原版共享的批量缓冲，画完立刻 endBatch 提交；
                // 这一版对应 22:12 那次能正常出刀光的配置。
                RenderType type = entry.type();
                MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
                BladeRenderState.setCol(0xFFFFFFFF);
                BladeRenderState.renderOverrided(entry.stack(), entry.model(), entry.group(), entry.texture(),
                        pose, buffers, entry.light(), ignored -> type, false);
                BladeRenderState.resetCol();
                buffers.endBatch(type);

                mv.popPose();
            }
        } catch (Throwable failure) {
            if (!loggedFailure) {
                loggedFailure = true;
                LOG.error("deferred replay failed", failure);
            }
        } finally {
            ENTRIES.clear();
            RenderSystem.depthMask(true);
            RenderSystem.applyModelViewMatrix();
        }
    }
}
