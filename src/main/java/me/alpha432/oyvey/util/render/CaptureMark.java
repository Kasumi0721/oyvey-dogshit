package me.alpha432.oyvey.util.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;

public class CaptureMark implements Util {

    private static float espValue = 1.0F;
    private static float espSpeed = 1.0F;
    private static boolean flipSpeed;
    private static float hue = 0.0F;

    private static float pulseTime = 0f;
    private static float pulse = 0f;

    /**
     * 自己构造完整的 model-view 矩阵：
     *
     *   M = V · T(-camPos) · T(world) · V⁻¹ · Rz · S
     *
     * 其中 V = Rx(pitch) · Ry(yaw + 180)（与 MixinLevelRenderer 里一致）。
     *
     * - T(-camPos) · T(world) 把目标放到视图空间正确位置
     * - V⁻¹ 让 quad 在视图空间里永远面向 +Z（也就是面向相机）→ 真正的 billboard
     * - Rz 让 quad 在自己平面内自转
     */
    public static void render(Entity target, Color color, boolean rainbow, float delta) {
        if (mc.level == null || mc.player == null || target == null) return;

        Camera cam = mc.getEntityRenderDispatcher().camera;
        if (cam == null) return;
        Vec3 camPos = cam.position();

        // ---- 帧插值 ----
        double ix = Mth.lerp(delta, target.xo, target.getX());
        double iy = Mth.lerp(delta, target.yo, target.getY());
        double iz = Mth.lerp(delta, target.zo, target.getZ());

        float yaw   = cam.yRot() + 180f;
        float pitch = cam.xRot();

        // ---- 构造 model-view ----
        PoseStack ps = new PoseStack();

        // V = Rx(pitch) · Ry(yaw + 180)
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));

        // T(-camPos)
        ps.translate(-camPos.x, -camPos.y, -camPos.z);

        // T(world)（此时坐标系原点已在相机，直接加世界坐标即可）
        ps.translate(ix, iy + target.getEyeHeight() / 2.0, iz);

        // V⁻¹ = Ry(-(yaw + 180)) · Rx(-pitch)
        // ★ 关键：这就是 billboard，让 quad 在视图空间永远面向相机
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(-pitch));

        // 自转动画
        ps.mulPose(Axis.ZP.rotationDegrees(espValue));

        // 缩放
        float sizeMul = 1.0f + pulse * 0.12f;
        ps.scale(sizeMul, sizeMul, 1f);

        Matrix4f mat = ps.last().pose();
        float alpha = color.getAlpha() / 255f;

        float r1, g1, b1;
        float r2, g2, b2;
        float r3, g3, b3;
        float r4, g4, b4;

        if (rainbow) {
            float[] c1 = getRainbowColor(hue);
            float[] c2 = getRainbowColor((hue + 0.25f) % 1f);
            float[] c3 = getRainbowColor((hue + 0.50f) % 1f);
            float[] c4 = getRainbowColor((hue + 0.75f) % 1f);
            r1 = c1[0]; g1 = c1[1]; b1 = c1[2];
            r2 = c2[0]; g2 = c2[1]; b2 = c2[2];
            r3 = c3[0]; g3 = c3[1]; b3 = c3[2];
            r4 = c4[0]; g4 = c4[1]; b4 = c4[2];
        } else {
            float r = color.getRed() / 255f;
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;
            r1 = r2 = r3 = r4 = r;
            g1 = g2 = g3 = g4 = g;
            b1 = b2 = b3 = b4 = b;
        }

        float half = 0.75f;

        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        buf.addVertex(mat, -half, -half, 0f).setUv(0f, 1f).setColor(r1, g1, b1, alpha);
        buf.addVertex(mat, -half,  half, 0f).setUv(0f, 0f).setColor(r2, g2, b2, alpha);
        buf.addVertex(mat,  half,  half, 0f).setUv(1f, 0f).setColor(r3, g3, b3, alpha);
        buf.addVertex(mat,  half, -half, 0f).setUv(1f, 1f).setColor(r4, g4, b4, alpha);

        Layers.glowQuad().draw(buf.buildOrThrow());
    }

    public static void tick() {
        espValue = espValue + espSpeed;
        if (espSpeed > 25.0F) flipSpeed = true;
        if (espSpeed < -25.0F) flipSpeed = false;
        espSpeed = flipSpeed ? espSpeed - 0.5F : espSpeed + 0.5F;

        hue = (hue + 0.01F) % 1.0F;

        pulseTime += 0.12f;
        if (pulseTime > (float) (Math.PI * 2)) pulseTime -= (float) (Math.PI * 2);
        pulse = (float) Math.sin(pulseTime);
    }

    private static float[] getRainbowColor(float hue) {
        float h = hue * 6.0F;
        int i = (int) h;
        float f = h - i;
        float q = 1.0F - f;
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = 1f; g = f; b = 0f; }
            case 1 -> { r = q; g = 1f; b = 0f; }
            case 2 -> { r = 0f; g = 1f; b = f; }
            case 3 -> { r = 0f; g = q; b = 1f; }
            case 4 -> { r = f; g = 0f; b = 1f; }
            default -> { r = 1f; g = 0f; b = q; }
        }
        return new float[]{r, g, b};
    }
}