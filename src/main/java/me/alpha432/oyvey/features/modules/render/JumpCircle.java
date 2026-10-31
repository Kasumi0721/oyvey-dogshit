package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.event.impl.entity.MotionEvent;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.JumpCircleRenderer;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class JumpCircle extends Module {
    public static JumpCircle INSTANCE;

    public enum Preset { Arcane, Cyber, Celestial, Inferno, Void, Custom }

    // 触发
    private final Setting<Preset> preset = mode("Preset", Preset.Arcane);
    private final Setting<Boolean> triggerTakeoff = bool("Takeoff", true);
    private final Setting<Boolean> triggerLanding = bool("Landing", true);
    private final Setting<Boolean> triggerHeavy = bool("Heavy Landing", true);

    // 颜色
    private final Setting<Color> takeoffColor = color("Takeoff Color", new Color(0xFFB69CFF, true));
    private final Setting<Color> normalColor = color("Landing Color", new Color(0xFF7DEBFF, true));
    private final Setting<Color> heavyColor = color("Heavy Color", new Color(0xFFFF5A36, true));
    private final Setting<Color> accentColor = color("Accent Color", new Color(0xFFFFD77D, true));
    private final Setting<Color> shadowColor = color("Shadow Color", new Color(0xFF160C2A, true));
    private final Setting<Boolean> dualTone = bool("Dual Tone", true);

    // 几何 / 时间
    private final Setting<Float> maxRadius = num("Radius", 1.85f, 0.4f, 8.0f);
    private final Setting<Integer> duration = num("Duration", 1050, 200, 4000);
    private final Setting<Float> timeScale = num("Time Scale", 1.0f, 0.25f, 3.0f);
    private final Setting<Float> heightOffset = num("Height Offset", 0.018f, 0.002f, 0.12f);
    private final Setting<Float> heavyThreshold = num("Heavy Threshold", 3.0f, 0.5f, 12.0f);
    private final Setting<Boolean> impactScaling = bool("Impact Scaling", true);
    private final Setting<Float> heavyScale = num("Heavy Scale", 1.35f, 1.0f, 2.5f,
            () -> impactScaling.getValue());

    // 阴影
    private final Setting<Float> shadowOpacity = num("Shadow", 0.22f, 0.0f, 1.0f);
    private final Setting<Float> shadowSize = num("Shadow Size", 0.95f, 0.6f, 2.0f,
            () -> shadowOpacity.getValue() > 0.001f);

    // 性能
    private final Setting<Integer> maxCircles = num("Max Circles", 36, 1, 120);
    private final Setting<Float> cullDistance = num("Render Distance", 24.0f, 4.0f, 96.0f);

    private final List<JumpInstance> activeCircles = new ArrayList<>();
    private boolean wasOnGround = true;
    private float peakFallDistance;

    private enum JumpType {
        TAKEOFF, LANDING_NORMAL, LANDING_HEAVY
    }

    private record JumpInstance(double x, double y, double z, long timestamp,
                                JumpType type, float impact) {}

    public JumpCircle() {
        super("JumpCircle", "跳跃/落地圆环冲击波", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        wasOnGround = mc.player == null || mc.player.onGround();
        peakFallDistance = 0f;
    }

    @Override
    public void onDisable() {
        activeCircles.clear();
        peakFallDistance = 0f;
    }

    @Subscribe
    public void onMotion(MotionEvent event) {
        if (nullCheck()) return;
        if (event.getStage() != 0) return; // 0 = Pre

        boolean onGround = mc.player.onGround();
        if (!onGround) {
            peakFallDistance = (float) Math.max(peakFallDistance, mc.player.fallDistance);
        }

        // 起跳
        if (wasOnGround && !onGround && triggerTakeoff.getValue()
                && mc.player.getDeltaMovement().y > 0.05) {
            spawn(mc.player.position(), JumpType.TAKEOFF, 1f);
        }

        // 落地
        if (!wasOnGround && onGround && peakFallDistance > 0.1f) {
            boolean heavy = peakFallDistance >= heavyThreshold.getValue();
            if ((heavy && triggerHeavy.getValue()) || (!heavy && triggerLanding.getValue())) {
                float impact = impactScaling.getValue()
                        ? clamp(0.8f + peakFallDistance
                        / Math.max(heavyThreshold.getValue(), 0.5f) * 0.25f, 0.8f, 1.25f)
                        : 1f;
                spawn(mc.player.position(),
                        heavy ? JumpType.LANDING_HEAVY : JumpType.LANDING_NORMAL, impact);
            }
        }

        if (onGround) peakFallDistance = 0f;
        wasOnGround = onGround;
    }

    private void spawn(Vec3 position, JumpType type, float impact) {
        double groundY = JumpCircleRenderer.findGroundY(position.x, position.y, position.z);
        activeCircles.add(new JumpInstance(position.x, groundY, position.z,
                System.currentTimeMillis(), type, impact));

        int cap = maxCircles.getValue();
        while (activeCircles.size() > cap) activeCircles.remove(0);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || activeCircles.isEmpty()) return;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        long now = System.currentTimeMillis();
        int lifeMs = duration.getValue();
        float cullSq = cullDistance.getValue() * cullDistance.getValue();

        PoseStack poseStack = event.getMatrix();

        activeCircles.removeIf(instance -> {
            double dx = instance.x - camera.position().x;
            double dy = instance.y - camera.position().y;
            double dz = instance.z - camera.position().z;
            if (dx * dx + dy * dy + dz * dz > cullSq) return true;

            float rawProgress = (now - instance.timestamp)
                    / (float) lifeMs * timeScale.getValue();
            if (rawProgress >= 1f) return true;
            float progress = clamp(rawProgress, 0f, 1f);

            float radius = maxRadius.getValue() * instance.impact;
            if (instance.type == JumpType.LANDING_HEAVY) radius *= heavyScale.getValue();
            float halfSize = radius * 1.34f;
            double y = instance.y + heightOffset.getValue();

            // 阴影贴图
            if (shadowOpacity.getValue() > 0.001f) {
                JumpCircleRenderer.drawShadowDecal(
                        poseStack, instance.x, y, instance.z,
                        halfSize * shadowSize.getValue(),
                        shadowColor.getValue().getRGB() & 0xFFFFFF,
                        shadowOpacity.getValue(), progress);
            }

            // 主环
            JumpCircleRenderer.drawJumpCircle(
                    poseStack, instance.x, y, instance.z, halfSize,
                    typeColor(instance.type), progress);

            // 双重色调：同半径，accent 色半透明叠加
            if (dualTone.getValue()) {
                JumpCircleRenderer.drawJumpCircle(
                        poseStack, instance.x, y + 0.0015, instance.z, halfSize,
                        accentColor.getValue().getRGB() & 0xFFFFFF, progress * 0.45f);
            }

            return false;
        });
    }

    private int typeColor(JumpType type) {
        return switch (type) {
            case TAKEOFF -> takeoffColor.getValue().getRGB() & 0xFFFFFF;
            case LANDING_NORMAL -> normalColor.getValue().getRGB() & 0xFFFFFF;
            case LANDING_HEAVY -> heavyColor.getValue().getRGB() & 0xFFFFFF;
        };
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}