package com.goldenstar.silverstar.client;

import com.goldenstar.silverstar.Silverstar;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import mods.flammpfeil.slashblade.client.renderer.model.obj.Face;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
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

import java.awt.Color;
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
 * 重放时直接在单位视图矩阵下乘这份位姿，顶点仍由同一个模型（{@code WavefrontObject}）生成。</p>
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

    /**
     * 一笔待画的星空层。位姿存副本，原 PoseStack 早就被弹掉了。
     *
     * <p>除了位姿，还要把拔刀剑那几个<b>静态</b>绘制状态一起记下来：
     * 颜色、透明度处理器、UV 滚动、光照。原版渲染器每画一层都会重设它们，
     * 重放时如果不还原，模型顶点会采样到贴图上的空白区（alpha 为 0），看起来就是"什么都没画"。</p>
     */
    private record Entry(ItemStack stack, WavefrontObject model, String group, ResourceLocation texture,
                         int light, Matrix4f pose, StarRender palette, float opacity, float time,
                         RenderType type,
                         Color faceCol,
                         java.util.function.BiFunction<org.joml.Vector4f, Integer, Integer> faceAlpha,
                         org.joml.Vector4f faceUv,
                         int faceLight) {
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
                new Matrix4f(pose.last().pose()), palette, opacity, time, type,
                Face.col, Face.alphaOverride, new org.joml.Vector4f(Face.uvOperator), Face.lightmap));
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

                // 用原版共享的批量缓冲，画完立刻提交。
                // 关键：把原版那一层用的静态状态（颜色 / 透明度处理器 / UV 滚动 / 光照）原样装回去，
                // 否则模型顶点会采样到贴图空白区，alpha 为 0，看着就是"什么都没画"。
                RenderType drawType = entry.type();
                MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
                VertexConsumer vc = buffers.getBuffer(drawType);
                Face.setMatrix(pose);
                if (entry.faceCol() != null) {
                    Face.setCol(entry.faceCol());
                }
                if (entry.faceAlpha() != null) {
                    Face.setAlphaOverride(entry.faceAlpha());
                }
                if (entry.faceUv() != null) {
                    Face.setUvOperator(entry.faceUv().x, entry.faceUv().y, entry.faceUv().z, entry.faceUv().w);
                }
                Face.setLightMap(entry.faceLight() != 0 ? entry.faceLight() : entry.light());
                try {
                    entry.model().tessellateOnly(vc, entry.group());
                } finally {
                    Face.resetMatrix();
                    Face.resetCol();
                    Face.resetAlphaOverride();
                    Face.resetUvOperator();
                    Face.resetLightMap();
                }
                buffers.endBatch(drawType);

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
