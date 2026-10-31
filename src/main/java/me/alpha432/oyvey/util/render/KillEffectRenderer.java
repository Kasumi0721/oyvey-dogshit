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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static me.alpha432.oyvey.util.traits.Util.mc;

/**
 * GPU-accelerated Hypernova Kill Effect renderer.
 * 完整移植 Gemini 26.1.2 版本，1.21.11 pipeline API 适配。
 */
public final class KillEffectRenderer {

    private KillEffectRenderer() {}

    // ════════════════════════════════════════════════════════════════
    //  Pipelines
    // ════════════════════════════════════════════════════════════════

    private static final BlendFunction ADDITIVE = BlendFunction.OVERLAY;
    private static final BlendFunction ALPHA_BLEND = BlendFunction.TRANSLUCENT;

    public static final RenderPipeline MAGIC_PIPE = createPipeline(
            "kill_effect_magic", "core/kill_effect", "core/kill_effect_magic",
            DepthTestFunction.LEQUAL_DEPTH_TEST, ADDITIVE);

    public static final RenderPipeline BLACK_HOLE_PIPE = createPipeline(
            "kill_effect_hole", "core/kill_effect", "core/kill_effect_hole",
            DepthTestFunction.LEQUAL_DEPTH_TEST, ADDITIVE);

    public static final RenderPipeline PARTICLE_PIPE = createPipeline(
            "kill_effect_particle", "core/kill_effect", "core/kill_effect_particle",
            DepthTestFunction.LEQUAL_DEPTH_TEST, ADDITIVE);

    public static final RenderPipeline HYPERNOVA_PIPE = createPipeline(
            "kill_effect_nova", "core/kill_effect", "core/kill_effect_nova",
            DepthTestFunction.NO_DEPTH_TEST, ADDITIVE);

    public static final RenderPipeline ORB_PIPE = createPipeline(
            "kill_effect_orb", "core/kill_effect_orb", "core/kill_effect_orb",
            DepthTestFunction.LEQUAL_DEPTH_TEST, ADDITIVE);

    public static final RenderPipeline RAY_PIPE = createPipeline(
            "kill_effect_ray", "core/kill_effect", "core/kill_effect_ray",
            DepthTestFunction.LEQUAL_DEPTH_TEST, ADDITIVE);

    private static RenderPipeline createPipeline(
            String path, String vsh, String fsh,
            DepthTestFunction depth, BlendFunction blend) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("oyvey", "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("oyvey", vsh))
                .withFragmentShader(Identifier.fromNamespaceAndPath("oyvey", fsh))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(depth)
                .withBlend(blend)
                .withCull(false)
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  Render types
    // ════════════════════════════════════════════════════════════════

    private static final RenderType MAGIC_TYPE       = createType("oyvey_kill_magic", MAGIC_PIPE);
    private static final RenderType BLACK_HOLE_TYPE  = createType("oyvey_kill_hole", BLACK_HOLE_PIPE);
    private static final RenderType PARTICLE_TYPE    = createType("oyvey_kill_particle", PARTICLE_PIPE);
    private static final RenderType HYPERNOVA_TYPE   = createType("oyvey_kill_nova", HYPERNOVA_PIPE);
    private static final RenderType ORB_TYPE         = createType("oyvey_kill_orb", ORB_PIPE);
    private static final RenderType RAY_TYPE         = createType("oyvey_kill_ray", RAY_PIPE);

    private static RenderType createType(String name, RenderPipeline pipeline) {
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.NO_LAYERING)
                .createRenderSetup());
    }

    // ════════════════════════════════════════════════════════════════
    //  Camera helpers
    // ════════════════════════════════════════════════════════════════

    private static final Vector3f CAM_UP    = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();

    private static void updateCameraVectors() {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        Quaternionf rot = cam.rotation();
        CAM_UP.set(0, 1, 0);
        rot.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0);
        rot.transform(CAM_RIGHT);
    }

    // ════════════════════════════════════════════════════════════════
    //  Billboard quad emitter
    // ════════════════════════════════════════════════════════════════

    private static void emitBillboard(BufferBuilder buf, Matrix4f vm,
                                      float cx, float cy, float cz,
                                      float px, float py, float pz,
                                      float halfSize,
                                      float u0, float v0, float u1, float v1,
                                      int rgba) {
        float rx = px - cx, ry = py - cy, rz = pz - cz;

        float rpx = CAM_RIGHT.x * halfSize, rpy = CAM_RIGHT.y * halfSize, rpz = CAM_RIGHT.z * halfSize;
        float upx = CAM_UP.x    * halfSize, upy = CAM_UP.y    * halfSize, upz = CAM_UP.z    * halfSize;

        buf.addVertex(vm, rx - rpx - upx, ry - rpy - upy, rz - rpz - upz)
                .setUv(u0, v0).setColor(rgba);
        buf.addVertex(vm, rx - rpx + upx, ry - rpy + upy, rz - rpz + upz)
                .setUv(u0, v1).setColor(rgba);
        buf.addVertex(vm, rx + rpx + upx, ry + rpy + upy, rz + rpz + upz)
                .setUv(u1, v1).setColor(rgba);
        buf.addVertex(vm, rx + rpx - upx, ry + rpy - upy, rz + rpz - upz)
                .setUv(u1, v0).setColor(rgba);
    }

    private static int packColor(float r, float g, float b, float a) {
        int ir = (int) (clamp01(r) * 255f);
        int ig = (int) (clamp01(g) * 255f);
        int ib = (int) (clamp01(b) * 255f);
        int ia = (int) (clamp01(a) * 255f);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ════════════════════════════════════════════════════════════════
    //  Magic Circle + Tower
    // ════════════════════════════════════════════════════════════════

    public static void drawMagic(PoseStack poseStack, KillEffectInstance inst, long nowMs) {
        if (!inst.shouldRenderMagic(nowMs)) return;

        int stage = inst.currentStage(nowMs);
        float progress = inst.stageProgress(nowMs);

        float shaderStage;
        if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
            shaderStage = KillEffectInstance.STAGE_MAGIC_TOWER;
        } else {
            shaderStage = (float) stage;
        }

        float alpha = inst.magicTransitionAlpha(nowMs);

        float px = (float) inst.position.x;
        float py = (float) inst.position.y;
        float pz = (float) inst.position.z;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float dx = px - cx, dy = py - cy, dz = pz - cz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.01f;
        float baseSize = 1.5f * (1f + dist * 0.08f);

        BufferBuilder buf = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        if (stage == KillEffectInstance.STAGE_MAGIC_CIRCLE) {
            float size = baseSize * (0.5f + progress * 0.5f);
            float circleAlpha = alpha * (progress < 0.2f ? progress / 0.2f : 1f);
            float yOffset = 0.02f;

            int rgba = packColor(progress, 1f / 8f, 0.8f, circleAlpha);

            float rx = px - cx, ry = py + yOffset - cy, rz = pz - cz;
            buf.addVertex(vm, rx - size, ry, rz - size).setUv(0f, 0f).setColor(rgba);
            buf.addVertex(vm, rx - size, ry, rz + size).setUv(0f, 1f).setColor(rgba);
            buf.addVertex(vm, rx + size, ry, rz + size).setUv(1f, 1f).setColor(rgba);
            buf.addVertex(vm, rx + size, ry, rz - size).setUv(1f, 0f).setColor(rgba);

        } else {
            int towerLayers = 12;
            float transT = 0f;
            boolean isTransition = false;

            if (stage == KillEffectInstance.STAGE_MAGIC_TOWER && progress > 1f - 0.30f) {
                isTransition = true;
                transT = (progress - (1f - 0.30f)) / 0.30f;
            } else if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
                isTransition = true;
                transT = 1f;
            }

            // Circle→tower crossfade
            if (stage == KillEffectInstance.STAGE_MAGIC_TOWER && progress < 0.25f) {
                float circleFade = 1.0f - progress / 0.25f;
                float size = baseSize * (1.0f + progress * 0.4f);
                int circleRgba = packColor(1f, 1f / 8f, 0.8f, alpha * circleFade);
                float crx = px - cx, cry = py + 0.02f - cy, crz = pz - cz;
                buf.addVertex(vm, crx - size, cry, crz - size).setUv(0f, 0f).setColor(circleRgba);
                buf.addVertex(vm, crx - size, cry, crz + size).setUv(0f, 1f).setColor(circleRgba);
                buf.addVertex(vm, crx + size, cry, crz + size).setUv(1f, 1f).setColor(circleRgba);
                buf.addVertex(vm, crx + size, cry, crz - size).setUv(1f, 0f).setColor(circleRgba);
            }

            for (int i = 0; i < towerLayers; i++) {
                float layerProgress = (float) i / towerLayers;
                float scale = (float) Math.pow(0.9, i);
                float height = i * 0.3f * baseSize;
                float layerAlpha = (float) Math.pow(0.82, i) * alpha;
                float yOffset = 0.02f + height;

                float riseDelay = i * 0.05f;
                float riseProgress = clamp01((progress - riseDelay) / 0.15f);
                layerAlpha *= riseProgress;

                if (isTransition) {
                    float squash = 1.0f - transT * 0.85f;
                    yOffset = 0.02f + height * squash;
                    float bottomFade = 1.0f - layerProgress * transT * 0.7f;
                    layerAlpha *= bottomFade;
                }

                float size = baseSize * scale;
                int rgba = packColor(progress, shaderStage / 8f, 0.8f, layerAlpha);
                float rx = px - cx, ry = py + yOffset - cy, rz = pz - cz;
                buf.addVertex(vm, rx - size, ry, rz - size).setUv(0f, 0f).setColor(rgba);
                buf.addVertex(vm, rx - size, ry, rz + size).setUv(0f, 1f).setColor(rgba);
                buf.addVertex(vm, rx + size, ry, rz + size).setUv(1f, 1f).setColor(rgba);
                buf.addVertex(vm, rx + size, ry, rz - size).setUv(1f, 0f).setColor(rgba);
            }
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        MAGIC_TYPE.draw(mesh);
    }

    // ════════════════════════════════════════════════════════════════
    //  Black Hole
    // ════════════════════════════════════════════════════════════════

    public static void drawBlackHole(PoseStack poseStack, KillEffectInstance inst, long nowMs) {
        if (!inst.shouldRenderBlackHole(nowMs)) return;

        int stage = inst.currentStage(nowMs);
        float progress = inst.stageProgress(nowMs);

        float alpha = inst.blackHoleTransitionAlpha(nowMs);
        if (alpha < 0.001f) return;

        float holeSize = 1.5f;
        float brightness = 1f;

        if (stage == KillEffectInstance.STAGE_MAGIC_TOWER) {
            float t = (progress - (1f - 0.30f)) / 0.30f;
            holeSize = 0.02f + t * t * 0.28f;
            brightness = 0.3f + t * 0.7f;
        } else if (stage == KillEffectInstance.STAGE_BLACK_HOLE) {
            holeSize = 0.3f + progress * 1.2f;
            if (progress < 0.30f) {
                float t = progress / 0.30f;
                holeSize = Math.max(holeSize, 0.05f + t * 0.5f);
                brightness = 0.5f + t * 0.5f;
            } else {
                brightness = 1f;
            }
            alpha = progress < 0.15f ? Math.max(alpha, progress / 0.15f) : Math.max(alpha, 1f);
        } else if (stage == KillEffectInstance.STAGE_COLLAPSE) {
            holeSize = 1.5f * (1f - progress * 0.8f);
            brightness = 1f + progress * 3f;
        } else {
            holeSize = 1.5f;
        }

        alpha = clamp01(alpha);

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y + 1.5f;
        float pz = (float) inst.position.z;

        BufferBuilder buf = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int rgba = packColor(progress, stage / 8f, brightness / 4f, alpha);
        float halfSize = holeSize * 3.25f;

        emitBillboard(buf, vm, cx, cy, cz, px, py, pz, halfSize, 0f, 0f, 1f, 1f, rgba);

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        BLACK_HOLE_TYPE.draw(mesh);
    }

    // ════════════════════════════════════════════════════════════════
    //  Particles
    // ════════════════════════════════════════════════════════════════

    public static void drawParticles(PoseStack poseStack, float[] batch, int count) {
        if (count == 0) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        BufferBuilder buf = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < count; i++) {
            int off = i * 8;
            float px = batch[off], py = batch[off + 1], pz = batch[off + 2];
            float sz = batch[off + 3];
            float cr = batch[off + 4], cg = batch[off + 5], cb = batch[off + 6];
            float ca = batch[off + 7];

            int rgba = packColor(cr, cg, cb, ca);
            emitBillboard(buf, vm, cx, cy, cz, px, py, pz, sz, 0f, 0f, 1f, 1f, rgba);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) { mesh.close(); return; }
        PARTICLE_TYPE.draw(mesh);
    }

    // ════════════════════════════════════════════════════════════════
    //  Hypernova
    // ════════════════════════════════════════════════════════════════

    public static void drawHypernova(PoseStack poseStack, KillEffectInstance inst, long nowMs) {
        int stage = inst.currentStage(nowMs);
        if (stage != KillEffectInstance.STAGE_HYPERNOVA
                && stage != KillEffectInstance.STAGE_AFTERGLOW
                && stage != KillEffectInstance.STAGE_FADE_OUT) return;

        float progress = inst.stageProgress(nowMs);
        float alpha;

        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            alpha = 1.0f - (float) Math.pow(2.0, -6.0 * progress);
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            float fade = 1.0f - progress;
            alpha = 0.08f + fade * fade * fade * 0.92f;
        } else {
            alpha = 0.08f * inst.getFadeOutAlpha(nowMs);
        }

        if (alpha < 0.005f) return;

        updateCameraVectors();
        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;
        Matrix4f vm = poseStack.last().pose();

        float px = (float) inst.position.x;
        float py = (float) inst.position.y + 2.5f;
        float pz = (float) inst.position.z;

        float dx = px - cx, dy = py - cy, dz = pz - cz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 0.1f;

        // ── Layer 0: Ground shock ring ────────────────────────────
        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            float groundY = (float) inst.position.y + 0.04f;
            float ringSize = dist * 0.58f * (1.0f + progress * 2.1f);
            float fade = 1.0f - progress;
            float ringAlpha = alpha * fade * (float) Math.sqrt(fade);

            int ringRgba = packColor(progress * 0.62f, 0f, 2.4f / 4f, ringAlpha);
            float echoProgress = clamp01((progress - 0.12f) / 0.88f);
            float echoSize = ringSize * 0.72f;
            int echoRgba = packColor(echoProgress * 0.58f, 0f, 1.55f / 4f, ringAlpha * 0.62f);

            BufferBuilder ringBuf = OyVeyTesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            float rx = px - cx, ry = groundY - cy, rz = pz - cz;
            ringBuf.addVertex(vm, rx - ringSize, ry, rz - ringSize).setUv(0f, 0f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx - ringSize, ry, rz + ringSize).setUv(0f, 1f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx + ringSize, ry, rz + ringSize).setUv(1f, 1f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx + ringSize, ry, rz - ringSize).setUv(1f, 0f).setColor(ringRgba);
            ringBuf.addVertex(vm, rx - echoSize, ry + 0.015f, rz - echoSize).setUv(0f, 0f).setColor(echoRgba);
            ringBuf.addVertex(vm, rx - echoSize, ry + 0.015f, rz + echoSize).setUv(0f, 1f).setColor(echoRgba);
            ringBuf.addVertex(vm, rx + echoSize, ry + 0.015f, rz + echoSize).setUv(1f, 1f).setColor(echoRgba);
            ringBuf.addVertex(vm, rx + echoSize, ry + 0.015f, rz - echoSize).setUv(1f, 0f).setColor(echoRgba);
            MeshData ringMesh = ringBuf.buildOrThrow();
            if (ringMesh.drawState().vertexCount() > 0) HYPERNOVA_TYPE.draw(ringMesh);
            else ringMesh.close();
        }

        // ── Layer 1: Flash overlay ────────────────────────────────
        float novaSize = dist * 0.44f;
        float lightSize = dist * 0.29f;
        float flashTime = 0.33f + 0.045f * (float) Math.sin(progress * Math.PI * 4.0);
        float flashIntensity = 3.85f + (float) Math.sin(progress * Math.PI * 3.5f)
                * 0.15f * (1.0f - progress);

        BufferBuilder buf = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float novaProgress = stage == KillEffectInstance.STAGE_HYPERNOVA ? progress : 1.0f;
        float novaIntensity = stage == KillEffectInstance.STAGE_HYPERNOVA ? 1.0f
                : stage == KillEffectInstance.STAGE_AFTERGLOW ? 0.65f : 0.25f;
        int novaRgba = packColor(novaProgress, 0.5f, novaIntensity, alpha);
        emitBillboard(buf, vm, cx, cy, cz, px, py, pz, novaSize, 0f, 0f, 1f, 1f, novaRgba);

        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            float corePulse = 0.72f + 0.28f * (float) Math.sin(progress * Math.PI * 5.0);
            int flashRgba = packColor(flashTime, 0f, flashIntensity / 4f,
                    alpha * clamp01(corePulse));
            emitBillboard(buf, vm, cx, cy, cz, px, py, pz, lightSize, 0f, 0f, 1f, 1f, flashRgba);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() > 0) HYPERNOVA_TYPE.draw(mesh);
        else mesh.close();

        // ── Layer 2: Volumetric orb ──────────────────────────────
        drawGlowSphere(poseStack, px, py, pz, progress, alpha, stage);

        // ── Layer 3: Radial rays ─────────────────────────────────
        drawRadialRays(vm, cx, cy, cz, px, py, pz, dist, progress, alpha, stage);
    }

    // ════════════════════════════════════════════════════════════════
    //  Volumetric orb — true 3D sphere, ray-marched
    // ════════════════════════════════════════════════════════════════

    private static final int SPHERE_LAT = 14;
    private static final int SPHERE_LON = 24;

    private static float[][] unitSphereVerts;

    private static float[][] getUnitSphere() {
        if (unitSphereVerts != null) return unitSphereVerts;
        float[][] v = new float[SPHERE_LAT * SPHERE_LON * 4][3];
        int idx = 0;
        for (int i = 0; i < SPHERE_LAT; i++) {
            double t0 = Math.PI * i / SPHERE_LAT;
            double t1 = Math.PI * (i + 1) / SPHERE_LAT;
            for (int j = 0; j < SPHERE_LON; j++) {
                double p0 = 2.0 * Math.PI * j / SPHERE_LON;
                double p1 = 2.0 * Math.PI * (j + 1) / SPHERE_LON;
                v[idx++] = sphPoint(t0, p0);
                v[idx++] = sphPoint(t0, p1);
                v[idx++] = sphPoint(t1, p1);
                v[idx++] = sphPoint(t1, p0);
            }
        }
        unitSphereVerts = v;
        return v;
    }

    private static float[] sphPoint(double theta, double phi) {
        return new float[]{
                (float) (Math.sin(theta) * Math.cos(phi)),
                (float) Math.cos(theta),
                (float) (Math.sin(theta) * Math.sin(phi))
        };
    }

    private static void drawGlowSphere(PoseStack poseStack,
                                       float px, float py, float pz,
                                       float progress, float alpha, int stage) {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        float cx = (float) cam.position().x;
        float cy = (float) cam.position().y;
        float cz = (float) cam.position().z;

        float radius;
        float heat;
        float boost;
        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            float expansion = 1.0f - (float) Math.pow(1.0f - progress, 2.4f);
            radius = 2.0f + expansion * 8.0f;
            heat   = 1.0f - progress * 0.48f;
            boost  = 0.75f + progress * 1.35f;
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            float decay = 1.0f - progress;
            radius = 10.0f - progress * 5.5f;
            heat   = 0.52f - progress * 0.30f;
            boost  = 0.32f + decay * decay * 1.78f;
        } else {
            float fade = 1.0f - progress * progress * (3.0f - 2.0f * progress);
            radius = 1.0f + 3.5f * fade;
            heat   = 0.22f * fade;
            boost  = 0.32f * fade;
        }

        float intensity = boost * 2.2f * alpha;
        int rgba = packColor(progress, heat, intensity / 4f, alpha);

        poseStack.pushPose();
        poseStack.translate(px - cx, py - cy, pz - cz);
        Matrix4f svm = poseStack.last().pose();

        float[][] sphere = getUnitSphere();
        BufferBuilder buf = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < sphere.length; i += 4) {
            for (int k = 0; k < 4; k++) {
                float[] v = sphere[i + k];
                buf.addVertex(svm, v[0] * radius, v[1] * radius, v[2] * radius)
                        .setUv(radius, 0f).setColor(rgba);
            }
        }
        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() > 0) ORB_TYPE.draw(mesh);
        else mesh.close();
        poseStack.popPose();
    }

    // ════════════════════════════════════════════════════════════════
    //  Radial rays
    // ════════════════════════════════════════════════════════════════

    private static final int RAY_COUNT = 32;
    private static final float RAY_LENGTH_FACTOR = 2.75f;
    private static final float RAY_TIP_WIDTH = 0.25f;

    private static void drawRadialRays(Matrix4f vm,
                                       float cx, float cy, float cz,
                                       float px, float py, float pz,
                                       float dist, float progress, float alpha, int stage) {
        int rayCount;
        float rayAlpha;
        float rayIntensity;

        if (stage == KillEffectInstance.STAGE_HYPERNOVA) {
            rayCount = RAY_COUNT;
            float pulse = 0.82f + 0.18f * (float) Math.sin(progress * Math.PI * 9.0f);
            rayAlpha = alpha * (0.55f + progress * 0.45f) * pulse;
            rayIntensity = 1.0f + progress * 1.5f;
        } else if (stage == KillEffectInstance.STAGE_AFTERGLOW) {
            rayCount = RAY_COUNT;
            float decay = 1.0f - progress;
            rayAlpha = alpha * decay * decay;
            rayIntensity = 2.5f * decay * decay;
        } else {
            return;
        }

        if (rayAlpha < 0.005f) return;

        float rayLength = dist * RAY_LENGTH_FACTOR * (0.8f + progress * 0.5f);
        float rayWidth = RAY_TIP_WIDTH * (1.0f + progress * 0.5f);

        float ox = px - cx;
        float oy = py - cy;
        float oz = pz - cz;

        BufferBuilder buf = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < rayCount; i++) {
            float angle = (float) (2.0 * Math.PI * i / rayCount);
            float screenDirX = (float) Math.cos(angle);
            float screenDirY = (float) Math.sin(angle);

            float variation = 0.65f + 0.35f * (float) Math.sin(i * 2.7 + 1.3);
            float rayI = rayIntensity * variation;

            int rgba = packColor(
                    progress,
                    (float) i / rayCount,
                    rayI / 4f,
                    rayAlpha);

            emitRayQuad(buf, vm, ox, oy, oz,
                    screenDirX, screenDirY,
                    rayLength, rayWidth,
                    0f, 0f, 1f, 1f, rgba);
        }

        MeshData mesh = buf.buildOrThrow();
        if (mesh.drawState().vertexCount() > 0) RAY_TYPE.draw(mesh);
        else mesh.close();
    }

    private static void emitRayQuad(BufferBuilder buf, Matrix4f vm,
                                    float ox, float oy, float oz,
                                    float screenDirX, float screenDirY,
                                    float length, float width,
                                    float u0, float v0, float u1, float v1,
                                    int rgba) {
        float worldDX = CAM_RIGHT.x * screenDirX + CAM_UP.x * screenDirY;
        float worldDY = CAM_RIGHT.y * screenDirX + CAM_UP.y * screenDirY;
        float worldDZ = CAM_RIGHT.z * screenDirX + CAM_UP.z * screenDirY;

        float perpDX = CAM_RIGHT.x * (-screenDirY) + CAM_UP.x * screenDirX;
        float perpDY = CAM_RIGHT.y * (-screenDirY) + CAM_UP.y * screenDirX;
        float perpDZ = CAM_RIGHT.z * (-screenDirY) + CAM_UP.z * screenDirX;

        float halfLen = length * 0.5f;
        float halfW = width * 0.5f;

        buf.addVertex(vm, ox - worldDX * halfLen - perpDX * halfW,
                        oy - worldDY * halfLen - perpDY * halfW,
                        oz - worldDZ * halfLen - perpDZ * halfW)
                .setUv(u0, v0).setColor(rgba);
        buf.addVertex(vm, ox - worldDX * halfLen + perpDX * halfW,
                        oy - worldDY * halfLen + perpDY * halfW,
                        oz - worldDZ * halfLen + perpDZ * halfW)
                .setUv(u0, v1).setColor(rgba);
        buf.addVertex(vm, ox + worldDX * halfLen + perpDX * halfW,
                        oy + worldDY * halfLen + perpDY * halfW,
                        oz + worldDZ * halfLen + perpDZ * halfW)
                .setUv(u1, v1).setColor(rgba);
        buf.addVertex(vm, ox + worldDX * halfLen - perpDX * halfW,
                        oy + worldDY * halfLen - perpDY * halfW,
                        oz + worldDZ * halfLen - perpDZ * halfW)
                .setUv(u1, v0).setColor(rgba);
    }
}