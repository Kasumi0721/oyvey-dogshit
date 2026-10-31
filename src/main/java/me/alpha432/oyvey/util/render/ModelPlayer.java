package me.alpha432.oyvey.util.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import me.alpha432.oyvey.mixin.accessors.ModelPartAccessor;
import me.alpha432.oyvey.mixin.accessors.ModelPartCubeAccessor;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public class ModelPlayer implements Util {

    private static final Vector4f TMP = new Vector4f();
    private static final float DEG2RAD = (float) (Math.PI / 180.0);

    private final Player player;
    private final double snapX, snapY, snapZ;
    private final float snapBodyRot;

    private final ModelPart root;
    private final ModelPart head, body, leftArm, rightArm, leftLeg, rightLeg;

    private final float headYDefault, rightArmYDefault, leftArmYDefault;

    public ModelPlayer(Player player) {
        this.player = player;
        this.snapX = player.getX();
        this.snapY = player.getY();
        this.snapZ = player.getZ();
        this.snapBodyRot = player.yBodyRot;

        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        this.root = mesh.getRoot().bake(64, 64);

        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");

        head.getChild("hat").visible = false;
        body.getChild("jacket").visible = false;
        rightArm.getChild("right_sleeve").visible = false;
        leftArm.getChild("left_sleeve").visible = false;
        rightLeg.getChild("right_pants").visible = false;
        leftLeg.getChild("left_pants").visible = false;

        this.headYDefault = head.y;
        this.rightArmYDefault = rightArm.y;
        this.leftArmYDefault = leftArm.y;
    }

    public void render(PoseStack poseStack, Color color, Color lineColor, boolean outline,
                       float alpha, double yaw, double scale, double yOffset, boolean noLimb) {

        poseStack.pushPose();

        var camera = mc.gameRenderer.getMainCamera().position();
        poseStack.translate(
                snapX - camera.x,
                snapY - camera.y + yOffset,
                snapZ - camera.z
        );

        poseStack.mulPose(Axis.YP.rotationDegrees(180f - snapBodyRot + (float) yaw));
        poseStack.scale(-1f, -1f, 1f);
        poseStack.translate(0f, -1.501f, 0f);
        poseStack.scale((float) scale, (float) scale, (float) scale);

        updatePose(noLimb);

        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;

        // ── 填充 ──
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        renderPart(buffer, poseStack, root, r, g, b, alpha);

        Layers.quads().draw(buffer.buildOrThrow());

        // ── 描边 ──
        if (outline) {
            float lr = lineColor.getRed() / 255f;
            float lg = lineColor.getGreen() / 255f;
            float lb = lineColor.getBlue() / 255f;
            float la = (lineColor.getAlpha() / 255f) * alpha;

            BufferBuilder lineBuf = Tesselator.getInstance().begin(
                    VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            renderOutline(lineBuf, poseStack, root, lr, lg, lb, la);
            Layers.lines().draw(lineBuf.buildOrThrow());
        }

        poseStack.popPose();
    }

    private void updatePose(boolean noLimb) {
        float limbSwing = noLimb ? 0f : player.walkAnimation.position(1f);
        float limbSwingAmount = noLimb ? 0f : player.walkAnimation.speed(1f);
        float speed = Math.min(limbSwingAmount, 1.0f);

        float netHeadYaw = (player.yHeadRot - player.yBodyRot) * DEG2RAD;
        float headPitch = player.getXRot() * DEG2RAD;

        head.yRot = netHeadYaw;
        head.xRot = headPitch;

        float armRight = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 2.0f * speed * 0.5f;
        float armLeft  = (float) Math.cos(limbSwing * 0.6662f) * 2.0f * speed * 0.5f;
        float legRight = (float) Math.cos(limbSwing * 0.6662f) * 1.4f * speed;
        float legLeft  = (float) Math.cos(limbSwing * 0.6662f + Math.PI) * 1.4f * speed;

        rightArm.xRot = armRight;
        leftArm.xRot  = armLeft;
        rightLeg.xRot = legRight;
        leftLeg.xRot  = legLeft;

        boolean crouching = player.isCrouching();
        if (crouching) {
            body.xRot = 0.5f;
            head.y = headYDefault - 4.0f;
            rightArm.xRot = armRight + 0.4f;
            leftArm.xRot  = armLeft  + 0.4f;
            rightArm.y = rightArmYDefault - 2.0f;
            leftArm.y  = leftArmYDefault  - 2.0f;
        } else {
            body.xRot = 0f;
            head.y = headYDefault;
            rightArm.y = rightArmYDefault;
            leftArm.y  = leftArmYDefault;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  填充
    // ════════════════════════════════════════════════════════════════

    private void renderPart(BufferBuilder buffer, PoseStack poseStack, ModelPart part,
                            float r, float g, float b, float a) {
        if (!part.visible) return;

        poseStack.pushPose();
        part.translateAndRotate(poseStack);

        ModelPartAccessor acc = (ModelPartAccessor) (Object) part;

        List<ModelPart.Cube> cubes = acc.getCubes();
        if (cubes != null && !cubes.isEmpty()) {
            Matrix4f mat = poseStack.last().pose();
            for (ModelPart.Cube cube : cubes) {
                renderCube(buffer, mat, cube, r, g, b, a);
            }
        }

        Map<String, ModelPart> children = acc.getChildren();
        if (children != null) {
            for (ModelPart child : children.values()) {
                renderPart(buffer, poseStack, child, r, g, b, a);
            }
        }

        poseStack.popPose();
    }

    private void renderCube(BufferBuilder buffer, Matrix4f mat, ModelPart.Cube cube,
                            float r, float g, float b, float a) {
        ModelPart.Polygon[] polys = ((ModelPartCubeAccessor) (Object) cube).getPolygons();
        if (polys == null) return;

        for (ModelPart.Polygon poly : polys) {
            ModelPart.Vertex[] verts = poly.vertices();
            if (verts == null || verts.length != 4) continue;

            for (int i = 0; i < 4; i++) {
                ModelPart.Vertex v = verts[i];
                TMP.set(v.worldX(), v.worldY(), v.worldZ(), 1f);
                TMP.mul(mat);
                buffer.addVertex(TMP.x, TMP.y, TMP.z).setColor(r, g, b, a);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  描边
    // ════════════════════════════════════════════════════════════════

    private void renderOutline(BufferBuilder buffer, PoseStack poseStack, ModelPart part,
                               float r, float g, float b, float a) {
        if (!part.visible) return;

        poseStack.pushPose();
        part.translateAndRotate(poseStack);

        ModelPartAccessor acc = (ModelPartAccessor) (Object) part;

        List<ModelPart.Cube> cubes = acc.getCubes();
        if (cubes != null && !cubes.isEmpty()) {
            Matrix4f mat = poseStack.last().pose();
            for (ModelPart.Cube cube : cubes) {
                renderCubeOutline(buffer, mat, cube, r, g, b, a);
            }
        }

        Map<String, ModelPart> children = acc.getChildren();
        if (children != null) {
            for (ModelPart child : children.values()) {
                renderOutline(buffer, poseStack, child, r, g, b, a);
            }
        }

        poseStack.popPose();
    }

    private void renderCubeOutline(BufferBuilder buffer, Matrix4f mat, ModelPart.Cube cube,
                                   float r, float g, float b, float a) {
        float x1 = cube.minX / 16f, y1 = cube.minY / 16f, z1 = cube.minZ / 16f;
        float x2 = cube.maxX / 16f, y2 = cube.maxY / 16f, z2 = cube.maxZ / 16f;

        float[][] v = {
                {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2},
                {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}
        };

        int[][] edges = {
                {0,1},{1,2},{2,3},{3,0},
                {4,5},{5,6},{6,7},{7,4},
                {0,4},{1,5},{2,6},{3,7}
        };

        for (int[] e : edges) {
            float[] p1 = v[e[0]];
            float[] p2 = v[e[1]];
            emitLineVertex(buffer, mat, p1[0], p1[1], p1[2], r, g, b, a);
            emitLineVertex(buffer, mat, p2[0], p2[1], p2[2], r, g, b, a);
        }
    }

    private void emitLineVertex(BufferBuilder buffer, Matrix4f mat,
                                float x, float y, float z,
                                float r, float g, float b, float a) {
        TMP.set(x, y, z, 1f);
        TMP.mul(mat);
        buffer.addVertex(TMP.x, TMP.y, TMP.z).setColor(r, g, b, a);
    }
}