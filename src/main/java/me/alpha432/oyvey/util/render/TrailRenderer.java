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
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

import static me.alpha432.oyvey.util.traits.Util.mc;

public final class TrailRenderer {

    public record TrailConfig(
            int primaryColor, int secondaryColor, int accentColor,
            int colorMode, int orientation,
            float width, float tailWidth,
            float opacity, float brightness, float glow, float coreWidth,
            float edgeGlow, float flowSpeed,
            boolean softBlend, boolean throughWalls
    ) {}

    private TrailRenderer() {}

    private static final BlendFunction BLEND_ADD = BlendFunction.OVERLAY;

    private static final RenderPipeline TRAIL_PIPE =
            createPipeline("trail_ribbon", DepthTestFunction.LEQUAL_DEPTH_TEST);
    private static final RenderPipeline TRAIL_XRAY =
            createPipeline("trail_ribbon_xray", DepthTestFunction.NO_DEPTH_TEST);

    private static final RenderType TRAIL_TYPE = createType("oyvey_trail_ribbon", TRAIL_PIPE);
    private static final RenderType TRAIL_XRAY_TYPE = createType("oyvey_trail_ribbon_xray", TRAIL_XRAY);

    private static final Vector3f CAM_UP = new Vector3f();
    private static final Vector3f CAM_RIGHT = new Vector3f();
    private static final Vector3f CAM_LOOK = new Vector3f();

    private static RenderPipeline createPipeline(String path, DepthTestFunction depth) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("oyvey", "pipeline/" + path))
                .withVertexShader(Identifier.fromNamespaceAndPath("oyvey", "core/trail_ribbon"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("oyvey", "core/trail_ribbon"))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(depth)
                .withBlend(BLEND_ADD)
                .withCull(false)
                .build();
    }

    private static RenderType createType(String name, RenderPipeline pipeline) {
        return RenderType.create(name, RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.NO_LAYERING)
                .createRenderSetup());
    }

    private static void updateCameraVectors(Camera camera) {
        Quaternionf rotation = camera.rotation();
        CAM_UP.set(0, 1, 0); rotation.transform(CAM_UP);
        CAM_RIGHT.set(1, 0, 0); rotation.transform(CAM_RIGHT);
        CAM_LOOK.set(0, 0, -1); rotation.transform(CAM_LOOK);
    }

    public static void draw(PoseStack poseStack, List<TrailPoint> points, TrailConfig config) {
        if (points.size() < 2 || config.width() <= 0f || config.opacity() < 0.005f) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;
        updateCameraVectors(camera);

        final int size = points.size();
        final float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;

        // 各点的世界坐标
        final Vector3d[] centers = new Vector3d[size];
        for (int i = 0; i < size; i++) {
            centers[i] = new Vector3d(points.get(i).pos.x, points.get(i).pos.y, points.get(i).pos.z);
        }

        // 各点的宽度方向（垂直于轨迹线）
        final Vector3d[] sides = new Vector3d[size];
        for (int i = 0; i < size; i++) {
            Vector3d before = centers[Math.max(0, i - 1)];
            Vector3d after = centers[Math.min(size - 1, i + 1)];
            Vector3d tangent = new Vector3d(after).sub(before);
            if (tangent.lengthSquared() < 1.0e-10) {
                tangent.set(CAM_RIGHT.x, CAM_RIGHT.y, CAM_RIGHT.z);
            } else {
                tangent.normalize();
            }

            Vector3d side;
            if (config.orientation() == 1) {
                side = new Vector3d(0.0, 1.0, 0.0);
            } else if (config.orientation() == 2) {
                side = tangent.cross(new Vector3d(0.0, 1.0, 0.0), new Vector3d());
            } else {
                side = tangent.cross(new Vector3d(CAM_LOOK.x, CAM_LOOK.y, CAM_LOOK.z), new Vector3d());
            }
            if (side.lengthSquared() < 1.0e-10) {
                side.set(CAM_RIGHT.x, CAM_RIGHT.y, CAM_RIGHT.z);
            } else {
                side.normalize();
            }

            float u = i / (float) (size - 1);
            // 从 head 到 tail 的宽度渐变（tailWidth 越小人越小）
            float taper = 1.0f + (config.tailWidth() - 1.0f) * smoothstep(u);
            sides[i] = side.mul(config.width() * taper);
        }

        float camX = (float) camera.position().x;
        float camY = (float) camera.position().y;
        float camZ = (float) camera.position().z;
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder buffer = OyVeyTesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < size - 1; i++) {
            TrailPoint a = points.get(i);
            TrailPoint b = points.get(i + 1);
            Vector3d ca = centers[i];
            Vector3d cb = centers[i + 1];
            Vector3d sa = sides[i];
            Vector3d sb = sides[i + 1];

            float u0 = i / (float) (size - 1);
            float u1 = (i + 1) / (float) (size - 1);

            int colA = trailColor(a, u0, time, config);
            int colB = trailColor(b, u1, time, config);

            // uv0.x 里加时间偏移，让 shader 里能看到"流动"
            float flowU0 = u0 + time * config.flowSpeed() * 0.08f;
            float flowU1 = u1 + time * config.flowSpeed() * 0.08f;

            vertex(buffer, matrix, ca, sa, -1, camX, camY, camZ, flowU0, -1f, colA);
            vertex(buffer, matrix, ca, sa,  1, camX, camY, camZ, flowU0,  1f, colA);
            vertex(buffer, matrix, cb, sb,  1, camX, camY, camZ, flowU1,  1f, colB);
            vertex(buffer, matrix, cb, sb, -1, camX, camY, camZ, flowU1, -1f, colB);
        }

        MeshData mesh = buffer.buildOrThrow();
        if (mesh.drawState().vertexCount() == 0) {
            mesh.close();
            return;
        }
        RenderType type = config.throughWalls() ? TRAIL_XRAY_TYPE : TRAIL_TYPE;
        type.draw(mesh);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                               Vector3d center, Vector3d side, int sign,
                               float camX, float camY, float camZ,
                               float u, float v, int color) {
        buffer.addVertex(matrix,
                        (float) (center.x + side.x * sign) - camX,
                        (float) (center.y + side.y * sign) - camY,
                        (float) (center.z + side.z * sign) - camZ)
                .setUv(u, v)
                .setColor(color);
    }

    private static int trailColor(TrailPoint point, float u, float time, TrailConfig config) {
        float alpha = point.alpha() * config.opacity();
        int rgb;
        switch (config.colorMode()) {
            case 1: { // Rainbow
                rgb = hsv((time * 0.10f + u * 0.80f) % 1.0f, 0.75f, 1.0f);
                break;
            }
            case 2: { // Pulse
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 4.0 + u * 10.0);
                rgb = mixColor(config.primaryColor(), config.secondaryColor(), pulse);
                break;
            }
            case 3: { // Static
                rgb = config.primaryColor();
                break;
            }
            default: { // Gradient
                rgb = mixColor(config.primaryColor(), config.secondaryColor(), u);
                break;
            }
        }
        return withAlpha(rgb, alpha);
    }

    private static int mixColor(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int hsv(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6f;
        int i = (int) hh;
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255) << 16)
                | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    private static float smoothstep(float x) {
        float t = clamp01(x);
        return t * t * (3f - 2f * t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}