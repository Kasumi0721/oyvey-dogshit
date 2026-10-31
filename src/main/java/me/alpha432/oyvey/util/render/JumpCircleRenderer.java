package me.alpha432.oyvey.util.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static me.alpha432.oyvey.util.traits.Util.mc;

public final class JumpCircleRenderer {

    private JumpCircleRenderer() {}

    private static final BlendFunction BLEND_TRANSLUCENT = BlendFunction.TRANSLUCENT;

    private static final RenderPipeline RING_PIPELINE = buildPipeline(
            "jump_circle", "core/jump_circle", DepthTestFunction.LEQUAL_DEPTH_TEST);

    private static final RenderPipeline SHADOW_PIPELINE = buildPipeline(
            "jump_circle_shadow", "core/jump_circle_shadow", DepthTestFunction.LEQUAL_DEPTH_TEST);

    private static final RenderType RING_TYPE =
            createType("oyvey_jump_circle", RING_PIPELINE);
    private static final RenderType SHADOW_TYPE =
            createType("oyvey_jump_circle_shadow", SHADOW_PIPELINE);

    private static RenderPipeline buildPipeline(String path, String shader, DepthTestFunction depth) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("oyvey", "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("oyvey", "core/jump_circle"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("oyvey", shader))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(depth)
                .withBlend(BLEND_TRANSLUCENT)
                .withCull(false)
                .build();
    }

    private static RenderType createType(String name, RenderPipeline pipeline) {
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.NO_LAYERING)
                .createRenderSetup());
    }

    /** 从 (x, y, z) 往下找地面 Y 坐标 */
    public static double findGroundY(double x, double y, double z) {
        if (mc.level == null || mc.player == null) return y;

        BlockHitResult result = mc.level.clip(new ClipContext(
                new Vec3(x, y + 1.0, z),
                new Vec3(x, y - 5.0, z),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return result.getType() == HitResult.Type.MISS ? y : result.getLocation().y;
    }

    /** 画主环。rgb 是 0xRRGGBB；progress 是 0..1 */
    public static void drawJumpCircle(PoseStack poseStack,
                                      double x, double y, double z,
                                      float radius, int rgb, float progress) {
        if (radius <= 0f) return;
        drawQuad(poseStack, x, y, z, radius, rgb, progress, RING_TYPE, 0f);
    }

    /** 画阴影贴图 */
    public static void drawShadowDecal(PoseStack poseStack,
                                       double x, double y, double z,
                                       float radius, int rgb, float opacity, float progress) {
        if (radius <= 0f || opacity <= 0.001f) return;
        // 阴影的 progress 乘上 opacity，交给 shader 里做淡出
        drawQuad(poseStack, x, y, z, radius, rgb, progress * opacity, SHADOW_TYPE, -0.001f);
    }

    private static void drawQuad(PoseStack poseStack,
                                 double x, double y, double z,
                                 float radius, int rgb, float progress,
                                 RenderType type, float yOffset) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        Vec3 camPos = camera.position();
        poseStack.pushPose();
        poseStack.translate(x - camPos.x, y + yOffset - camPos.y, z - camPos.z);
        Matrix4f matrix = poseStack.last().pose();

        int alpha = Math.round(clamp01(progress) * 255f);
        int packedColor = (alpha << 24) | (rgb & 0x00FFFFFF);

        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, -radius, 0f, -radius).setUv(0f, 0f).setColor(packedColor);
        buffer.addVertex(matrix, -radius, 0f,  radius).setUv(0f, 1f).setColor(packedColor);
        buffer.addVertex(matrix,  radius, 0f,  radius).setUv(1f, 1f).setColor(packedColor);
        buffer.addVertex(matrix,  radius, 0f, -radius).setUv(1f, 0f).setColor(packedColor);

        MeshData mesh = buffer.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        type.draw(mesh);
        poseStack.popPose();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}