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
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static me.alpha432.oyvey.util.traits.Util.mc;

public final class SweepAttackRenderer {

    private SweepAttackRenderer() {}

    // ★ 顺序关键：ADDITIVE 必须在 pipeline 声明之前
    private static final BlendFunction ADDITIVE = BlendFunction.OVERLAY;

    private static final RenderPipeline SWEEP_ARC_PIPE =
            buildArcPipe("sweep_arc", DepthTestFunction.LEQUAL_DEPTH_TEST);
    private static final RenderPipeline SWEEP_PARTICLE_PIPE =
            buildArcPipe("sweep_particle", DepthTestFunction.LEQUAL_DEPTH_TEST);
    private static final RenderPipeline SWEEP_RING_PIPE =
            buildArcPipe("sweep_ring", DepthTestFunction.LEQUAL_DEPTH_TEST);

    private static final RenderType ARC_TYPE = createType("oyvey_sweep_arc", SWEEP_ARC_PIPE);
    private static final RenderType PARTICLE_TYPE = createType("oyvey_sweep_particle", SWEEP_PARTICLE_PIPE);
    private static final RenderType RING_TYPE = createType("oyvey_sweep_ring", SWEEP_RING_PIPE);

    private static final Vector3f CAM_UP = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    private static RenderPipeline buildArcPipe(String path, DepthTestFunction depth) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("oyvey", "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("oyvey", "core/sweep_arc"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("oyvey", "core/sweep_arc"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(depth)
                .withBlend(ADDITIVE)
                .withCull(false)
                .build();
    }

    private static RenderType createType(String name, RenderPipeline pipeline) {
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.NO_LAYERING)
                .createRenderSetup());
    }

    public record Config(
            int style, int colorMode, int quality,
            int layers, int echoes, int ringCount, int speedLineCount,
            boolean arc, boolean speedLines, boolean particles,
            boolean lightning, boolean ring, boolean coreBurst,
            float intensity, float opacity, float radius, float thickness,
            float verticalLift, float glow, float noise, float flowSpeed,
            float echoSpacing, float ringScale, float ringThickness,
            float lineLength, float lineWidth, float lightningWidth,
            float primaryR, float primaryG, float primaryB,
            float accentR, float accentG, float accentB,
            float coreR, float coreG, float coreB
    ) {}

    private static void updateCameraVectors() {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;
        var rotation = camera.rotation();
        CAM_UP.set(0f, 1f, 0f);
        rotation.transform(CAM_UP);
        CAM_RIGHT.set(1f, 0f, 0f);
        rotation.transform(CAM_RIGHT);
    }

    public static void draw(PoseStack poseStack, SweepAttackInstance inst, long nowMs, Config config) {
        updateCameraVectors();
        float sweep = inst.sweepProgress(nowMs);
        float effect = inst.effectProgress(nowMs);
        float particleAlpha = inst.particleAlpha(nowMs);
        float ringProgress = inst.ringProgress(nowMs);
        float ringAlpha = inst.ringAlpha(nowMs);
        float lightningAlpha = inst.lightningAlpha(nowMs);
        float arcAlpha = inst.arcAlpha(nowMs);
        float burstAlpha = inst.burstAlpha(nowMs);

        if (config.arc()) drawArc(poseStack, inst, sweep, arcAlpha, config);
        if (config.speedLines()) drawSpeedLines(poseStack, inst, sweep, arcAlpha, config);
        if (config.particles()) drawParticles(poseStack, inst, particleAlpha, config);
        if (config.lightning()) drawLightning(poseStack, inst, lightningAlpha, config);
        if (config.ring()) drawPhotonRings(poseStack, inst, ringProgress, ringAlpha, config);
        if (config.coreBurst()) drawCoreBurst(poseStack, inst, effect, burstAlpha, config);
    }

    // ==================== Arc ====================
    private static void drawArc(PoseStack poseStack, SweepAttackInstance inst,
                                float sweepProgress, float arcAlpha, Config config) {
        if (sweepProgress < 0.001f || arcAlpha < 0.004f) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        int segments = switch (config.quality()) {
            case 0 -> 32;
            case 1 -> 48;
            case 3 -> 96;
            default -> 72;
        };

        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int echoCount = Math.min(Math.max(config.echoes(), 0), 5);
        for (int echo = echoCount; echo >= 0; echo--) {
            float echoProgress = clamp01(sweepProgress - echo * config.echoSpacing());
            if (echoProgress < 0.002f) continue;
            float echoFade = echo == 0 ? 1f : (float) Math.pow(0.62f, echo);
            float radius = config.radius() * (1f - echo * 0.035f);
            float innerRadius = radius * 0.42f;
            float outerRadius = radius * 1.22f;
            float endAngle = inst.arcStart + (inst.arcEnd - inst.arcStart) * echoProgress;

            float layerColorR = config.primaryR() * (1f - echo / 5f) + config.accentR() * (echo / 5f);
            float layerColorG = config.primaryG() * (1f - echo / 5f) + config.accentG() * (echo / 5f);
            float layerColorB = config.primaryB() * (1f - echo / 5f) + config.accentB() * (echo / 5f);
            float energy = config.intensity() * arcAlpha * echoFade;

            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                float angle0 = inst.arcStart + (endAngle - inst.arcStart) * t0;
                float angle1 = inst.arcStart + (endAngle - inst.arcStart) * t1;
                float lift0 = config.verticalLift() * (float) Math.sin(t0 * Math.PI);
                float lift1 = config.verticalLift() * (float) Math.sin(t1 * Math.PI);
                float bright0 = 0.55f + 0.45f * (float) Math.sin(t0 * Math.PI);
                float bright1 = 0.55f + 0.45f * (float) Math.sin(t1 * Math.PI);

                int color0 = packColor(layerColorR * bright0, layerColorG, layerColorB, energy);
                int color1 = packColor(layerColorR * bright1, layerColorG, layerColorB, energy);

                addArcVertex(buffer, matrix, inst, angle0, innerRadius, lift0,
                        cameraX, cameraY, cameraZ, t0, 0f, color0);
                addArcVertex(buffer, matrix, inst, angle0, outerRadius, lift0,
                        cameraX, cameraY, cameraZ, t0, 1f, color0);
                addArcVertex(buffer, matrix, inst, angle1, outerRadius, lift1,
                        cameraX, cameraY, cameraZ, t1, 1f, color1);
                addArcVertex(buffer, matrix, inst, angle1, innerRadius, lift1,
                        cameraX, cameraY, cameraZ, t1, 0f, color1);
            }
        }
        drawMesh(buffer, ARC_TYPE);
    }

    private static void addArcVertex(BufferBuilder buffer, Matrix4f matrix,
                                     SweepAttackInstance inst, float angle, float radius,
                                     float lift, float cameraX, float cameraY, float cameraZ,
                                     float u, float v, int color) {
        buffer.addVertex(matrix,
                        (float) (inst.x + Math.cos(angle) * radius) - cameraX,
                        (float) inst.y + lift - cameraY,
                        (float) (inst.z + Math.sin(angle) * radius) - cameraZ)
                .setUv(u, v)
                .setColor(color);
    }

    // ==================== Speed Lines ====================
    private static void drawSpeedLines(PoseStack poseStack, SweepAttackInstance inst,
                                       float sweepProgress, float arcAlpha, Config config) {
        int count = Math.min(Math.max(config.speedLineCount(), 0), 64);
        float alpha = sweepProgress * arcAlpha * config.intensity() * 0.72f;
        if (count == 0 || alpha < 0.004f) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        float range = positiveAngleRange(inst.arcStart, inst.arcEnd);
        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < count; i++) {
            float t = (i + 0.35f) / count;
            float jitter = (float) Math.sin(inst.seed * 1.7f + i * 12.9898f) * 0.018f;
            float angle = inst.arcStart + range * clamp01(t + jitter);
            float radialX = (float) Math.cos(angle);
            float radialZ = (float) Math.sin(angle);
            float perpX = -radialZ, perpZ = radialX;
            float startRadius = config.radius() * (0.62f + 0.2f * pseudo(i, inst.seed));
            float length = config.radius() * config.lineLength()
                    * (0.55f + 0.7f * pseudo(i + 17, inst.seed));
            float endRadius = startRadius + length;
            float width = config.lineWidth() * (0.55f + pseudo(i + 41, inst.seed));
            float y = (float) inst.y + config.verticalLift() * (float) Math.sin(t * Math.PI);
            float startX = (float) inst.x + radialX * startRadius;
            float startZ = (float) inst.z + radialZ * startRadius;
            float endX = (float) inst.x + radialX * endRadius;
            float endZ = (float) inst.z + radialZ * endRadius;

            float cr = config.primaryR() * pseudo(i + 9, inst.seed) + config.accentR() * 0.3f;
            float cg = config.primaryG() * pseudo(i + 9, inst.seed) + config.accentG() * 0.3f;
            float cb = config.primaryB() * pseudo(i + 9, inst.seed) + config.accentB() * 0.3f;
            int color = packColor(cr, cg, cb, alpha * (0.45f + 0.55f * pseudo(i + 3, inst.seed)));

            buffer.addVertex(matrix, startX - perpX * width - cameraX, y - cameraY,
                            startZ - perpZ * width - cameraZ)
                    .setUv(0f, -1f).setColor(color);
            buffer.addVertex(matrix, startX + perpX * width - cameraX, y - cameraY,
                            startZ + perpZ * width - cameraZ)
                    .setUv(0f, 1f).setColor(color);
            buffer.addVertex(matrix, endX + perpX * width - cameraX, y - cameraY,
                            endZ + perpZ * width - cameraZ)
                    .setUv(1f, 1f).setColor(color);
            buffer.addVertex(matrix, endX - perpX * width - cameraX, y - cameraY,
                            endZ - perpZ * width - cameraZ)
                    .setUv(1f, -1f).setColor(color);
        }
        drawMesh(buffer, PARTICLE_TYPE);
    }

    // ==================== Particles ====================
    private static void drawParticles(PoseStack poseStack, SweepAttackInstance inst,
                                      float particleAlpha, Config config) {
        if (particleAlpha < 0.004f || inst.particleCount == 0) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;

        for (int i = 0; i < inst.particleCount; i++) {
            if (inst.particleLife[i] <= 0f) continue;
            float life = inst.particleLife[i] / Math.max(inst.particleMaxLife[i], 0.001f);
            float alpha = life * life * particleAlpha * config.intensity();
            if (alpha < 0.004f) continue;
            float halfSize = inst.particleSize[i] * 0.5f;
            float x = inst.particleX[i] - cameraX;
            float y = inst.particleY[i] - cameraY;
            float z = inst.particleZ[i] - cameraZ;
            float rX = CAM_RIGHT.x * halfSize, rY = CAM_RIGHT.y * halfSize, rZ = CAM_RIGHT.z * halfSize;
            float uX = CAM_UP.x * halfSize, uY = CAM_UP.y * halfSize, uZ = CAM_UP.z * halfSize;

            float cr = config.primaryR() * life + config.coreR() * (1f - life);
            float cg = config.primaryG() * life + config.coreG() * (1f - life);
            float cb = config.primaryB() * life + config.coreB() * (1f - life);
            int color = packColor(cr, cg, cb, alpha);

            buffer.addVertex(matrix, x - rX - uX, y - rY - uY, z - rZ - uZ)
                    .setUv(0f, 0f).setColor(color);
            buffer.addVertex(matrix, x - rX + uX, y - rY + uY, z - rZ + uZ)
                    .setUv(0f, 1f).setColor(color);
            buffer.addVertex(matrix, x + rX + uX, y + rY + uY, z + rZ + uZ)
                    .setUv(1f, 1f).setColor(color);
            buffer.addVertex(matrix, x + rX - uX, y + rY - uY, z + rZ - uZ)
                    .setUv(1f, 0f).setColor(color);
            drawn++;
        }
        if (drawn > 0) drawMesh(buffer, PARTICLE_TYPE);
    }

    // ==================== Lightning ====================
    private static void drawLightning(PoseStack poseStack, SweepAttackInstance inst,
                                      float lightningAlpha, Config config) {
        if (lightningAlpha < 0.004f || inst.boltCount == 0) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int drawn = 0;
        float cr = config.accentR(), cg = config.accentG(), cb = config.accentB();

        for (int bolt = 0; bolt < inst.boltCount; bolt++) {
            for (int seg = 0; seg < inst.boltSegments[bolt] - 1; seg++) {
                float x0 = inst.boltX[bolt][seg], y0 = inst.boltY[bolt][seg], z0 = inst.boltZ[bolt][seg];
                float x1 = inst.boltX[bolt][seg + 1], y1 = inst.boltY[bolt][seg + 1], z1 = inst.boltZ[bolt][seg + 1];
                float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
                float tcx = cameraX - (x0 + x1) * 0.5f;
                float tcy = cameraY - (y0 + y1) * 0.5f;
                float tcz = cameraZ - (z0 + z1) * 0.5f;
                float nx = dy * tcz - dz * tcy;
                float ny = dz * tcx - dx * tcz;
                float nz = dx * tcy - dy * tcx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 0.0001f) continue;
                float width = config.lightningWidth() * (0.7f + 0.5f * pseudo(bolt * 11 + seg, inst.seed));
                nx = nx / nl * width;
                ny = ny / nl * width;
                nz = nz / nl * width;
                int color = packColor(cr, cg, cb, lightningAlpha * config.intensity());

                buffer.addVertex(matrix, x0 - nx - cameraX, y0 - ny - cameraY, z0 - nz - cameraZ)
                        .setUv(0f, -1f).setColor(color);
                buffer.addVertex(matrix, x0 + nx - cameraX, y0 + ny - cameraY, z0 + nz - cameraZ)
                        .setUv(0f, 1f).setColor(color);
                buffer.addVertex(matrix, x1 + nx - cameraX, y1 + ny - cameraY, z1 + nz - cameraZ)
                        .setUv(1f, 1f).setColor(color);
                buffer.addVertex(matrix, x1 - nx - cameraX, y1 - ny - cameraY, z1 - nz - cameraZ)
                        .setUv(1f, -1f).setColor(color);
                drawn++;
            }
        }
        if (drawn > 0) drawMesh(buffer, PARTICLE_TYPE);
    }

    // ==================== Photon Rings ====================
    private static void drawPhotonRings(PoseStack poseStack, SweepAttackInstance inst,
                                        float progress, float alpha, Config config) {
        if (progress < 0.003f || alpha < 0.004f || config.ringCount() <= 0) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();

        for (int ring = 0; ring < Math.min(config.ringCount(), 5); ring++) {
            float dp = clamp01(progress - ring * 0.09f);
            if (dp <= 0.002f) continue;
            float radius = config.radius() * config.ringScale() * dp * (1f + ring * 0.13f);
            float quadSize = radius * 1.34f + 0.08f;
            float y = (float) inst.y - 0.12f + ring * 0.025f;

            float cr = config.primaryR() * (1f - ring / 5f) + config.accentR() * (ring / 5f);
            float cg = config.primaryG() * (1f - ring / 5f) + config.accentG() * (ring / 5f);
            float cb = config.primaryB() * (1f - ring / 5f) + config.accentB() * (ring / 5f);
            int color = packColor(cr, cg, cb, alpha * config.intensity() * (1f - ring * 0.11f));

            BufferBuilder buffer = OyVeyTesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float x = (float) inst.x - cameraX;
            float z = (float) inst.z - cameraZ;
            buffer.addVertex(matrix, x - quadSize, y - cameraY, z - quadSize)
                    .setUv(0f, 0f).setColor(color);
            buffer.addVertex(matrix, x - quadSize, y - cameraY, z + quadSize)
                    .setUv(0f, 1f).setColor(color);
            buffer.addVertex(matrix, x + quadSize, y - cameraY, z + quadSize)
                    .setUv(1f, 1f).setColor(color);
            buffer.addVertex(matrix, x + quadSize, y - cameraY, z - quadSize)
                    .setUv(1f, 0f).setColor(color);
            drawMesh(buffer, RING_TYPE);
        }
    }

    // ==================== Core Burst ====================
    private static void drawCoreBurst(PoseStack poseStack, SweepAttackInstance inst,
                                      float progress, float alpha, Config config) {
        if (alpha < 0.004f) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        float cameraX = (float) camera.position().x;
        float cameraY = (float) camera.position().y;
        float cameraZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();
        float size = config.radius() * (0.32f + progress * 0.72f);
        float rX = CAM_RIGHT.x * size, rY = CAM_RIGHT.y * size, rZ = CAM_RIGHT.z * size;
        float uX = CAM_UP.x * size, uY = CAM_UP.y * size, uZ = CAM_UP.z * size;
        float x = (float) inst.x + inst.dirX * config.radius() * 0.56f - cameraX;
        float y = (float) inst.y + config.verticalLift() * 0.5f - cameraY;
        float z = (float) inst.z + inst.dirZ * config.radius() * 0.56f - cameraZ;
        int color = packColor(config.coreR(), config.coreG(), config.coreB(), alpha * config.intensity());

        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x - rX - uX, y - rY - uY, z - rZ - uZ)
                .setUv(0f, 0f).setColor(color);
        buffer.addVertex(matrix, x - rX + uX, y - rY + uY, z - rZ + uZ)
                .setUv(0f, 1f).setColor(color);
        buffer.addVertex(matrix, x + rX + uX, y + rY + uY, z + rZ + uZ)
                .setUv(1f, 1f).setColor(color);
        buffer.addVertex(matrix, x + rX - uX, y + rY - uY, z + rZ - uZ)
                .setUv(1f, 0f).setColor(color);
        drawMesh(buffer, RING_TYPE);
    }

    private static void drawMesh(BufferBuilder buffer, RenderType type) {
        MeshData mesh = buffer.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) {
            mesh.close();
            return;
        }
        type.draw(mesh);
    }

    private static float positiveAngleRange(float start, float end) {
        float range = end - start;
        return range < 0f ? range + (float) (Math.PI * 2.0) : range;
    }

    private static float pseudo(int index, float seed) {
        double v = Math.sin(index * 12.9898 + seed * 78.233) * 43758.5453;
        return (float) (v - Math.floor(v));
    }

    private static int packColor(float r, float g, float b, float a) {
        int ri = Math.round(clamp01(r) * 255f);
        int gi = Math.round(clamp01(g) * 255f);
        int bi = Math.round(clamp01(b) * 255f);
        int ai = Math.round(clamp01(a) * 255f);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}