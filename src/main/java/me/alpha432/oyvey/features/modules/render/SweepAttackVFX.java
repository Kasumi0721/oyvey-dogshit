package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.SweepAttackInstance;
import me.alpha432.oyvey.util.render.SweepAttackRenderer;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class SweepAttackVFX extends Module {
    public static SweepAttackVFX INSTANCE;

    private static final int MAX_EFFECTS = 12;

    public enum Preset { Celestial, Arcane, Cyber, Inferno, Void, Prismatic, Custom }
    public enum SlashStyle { Blade, Crescent, Runic, Plasma, Shatter }
    public enum ColorMode { Static, Gradient, Rainbow, Pulse }
    public enum Quality { Low, Medium, High, Ultra }

    private final Setting<Preset> preset = mode("Preset", Preset.Celestial);
    private final Setting<SlashStyle> style = mode("Slash Style", SlashStyle.Crescent);
    private final Setting<ColorMode> colorMode = mode("Color Flow", ColorMode.Gradient);
    private final Setting<Quality> quality = mode("Quality", Quality.High);

    private final Setting<Color> primaryColor = color("Primary Color", new Color(0xFF64D8FF, true));
    private final Setting<Color> accentColor = color("Accent Color", new Color(0xFFC27CFF, true));
    private final Setting<Color> coreColor = color("Core Color", new Color(0xFFFFFFFF, true));

    private final Setting<Float> intensity = num("Intensity", 1.25f, 0.1f, 3f);
    private final Setting<Float> opacity = num("Opacity", 0.92f, 0.05f, 1f);
    private final Setting<Float> radius = num("Radius", 2.35f, 0.5f, 6f);
    private final Setting<Float> arcAngle = num("Arc Angle", 145f, 35f, 320f);
    private final Setting<Float> heightOffset = num("Height", 1.05f, 0.05f, 2.8f);
    private final Setting<Float> verticalLift = num("Arc Lift", 0.42f, -1.5f, 1.5f);
    private final Setting<Float> thickness = num("Thickness", 0.82f, 0.12f, 2.5f);
    private final Setting<Float> glow = num("Glow", 1.55f, 0f, 3f);
    private final Setting<Float> noise = num("Turbulence", 0.35f, 0f, 1f);
    private final Setting<Float> flowSpeed = num("Energy Flow", 1.25f, -3f, 4f);
    private final Setting<Integer> duration = num("Duration", 1450, 250, 4000);
    private final Setting<Float> animationSpeed = num("Animation Speed", 1f, 0.25f, 3f);

    private final Setting<Boolean> enableArc = bool("Energy Arc", true, () -> true);
    private final Setting<Integer> layers = num("Arc Layers", 4, 1, 5, () -> enableArc.getValue());
    private final Setting<Integer> echoes = num("Afterimages", 3, 0, 5, () -> enableArc.getValue());
    private final Setting<Float> echoSpacing = num("Echo Spacing", 0.075f, 0.02f, 0.22f,
            () -> enableArc.getValue() && echoes.getValue() > 0);

    private final Setting<Boolean> enableSpeedLines = bool("Speed Lines", true, () -> true);
    private final Setting<Integer> speedLineCount = num("Line Count", 30, 0, 64, () -> enableSpeedLines.getValue());
    private final Setting<Float> lineLength = num("Line Length", 0.95f, 0.15f, 2.5f, () -> enableSpeedLines.getValue());
    private final Setting<Float> lineWidth = num("Line Width", 0.028f, 0.005f, 0.12f, () -> enableSpeedLines.getValue());

    private final Setting<Boolean> enableParticles = bool("Particles", true, () -> true);
    private final Setting<Integer> particleCount = num("Particle Count", 88, 0, 128, () -> enableParticles.getValue());
    private final Setting<Float> particleSpeed = num("Particle Speed", 6.5f, 0.5f, 14f, () -> enableParticles.getValue());
    private final Setting<Float> particleSpread = num("Particle Spread", 0.7f, 0f, 2f, () -> enableParticles.getValue());
    private final Setting<Float> particleSize = num("Particle Size", 0.18f, 0.025f, 0.6f, () -> enableParticles.getValue());
    private final Setting<Float> particleGravity = num("Particle Gravity", 2.6f, -4f, 9f, () -> enableParticles.getValue());

    private final Setting<Boolean> enableLightning = bool("Lightning", true, () -> true);
    private final Setting<Integer> lightningBolts = num("Bolt Count", 7, 0, 12, () -> enableLightning.getValue());
    private final Setting<Float> lightningWidth = num("Bolt Width", 0.032f, 0.006f, 0.12f, () -> enableLightning.getValue());

    private final Setting<Boolean> enableRing = bool("Shockwave Rings", true, () -> true);
    private final Setting<Integer> ringCount = num("Ring Count", 3, 1, 5, () -> enableRing.getValue());
    private final Setting<Float> ringScale = num("Ring Scale", 1.45f, 0.3f, 3f, () -> enableRing.getValue());
    private final Setting<Float> ringThickness = num("Ring Thickness", 0.34f, 0.02f, 1f, () -> enableRing.getValue());
    private final Setting<Boolean> enableCoreBurst = bool("Core Burst", true, () -> true);

    private final Setting<Integer> maxEffects = num("Max Effects", 5, 1, MAX_EFFECTS);
    private final Setting<Float> renderDistance = num("Render Distance", 32f, 6f, 96f);

    private final SweepAttackInstance[] effects = new SweepAttackInstance[MAX_EFFECTS];
    private int effectCount;
    private long lastUpdateNanos;

    public SweepAttackVFX() {
        super("SweepingAttackVFX", "打人时挥舞特效", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        lastUpdateNanos = System.nanoTime();
    }

    @Override
    public void onDisable() {
        clearAllEffects();
    }

    /**
     * ★ 在目标位置生成特效
     * 参数：
     *   player  - 攻击者（用来取朝向）
     *   targetX/Y/Z - 目标的世界坐标（弧线画在这里）
     */
    public void spawnSweepEffect(Player player, double targetX, double targetY, double targetZ) {
        if (mc.player == null || mc.level == null) return;

        // 从玩家到目标的水平方向（决定弧线朝向）
        double deltaX = targetX - player.getX();
        double deltaZ = targetZ - player.getZ();
        double length = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float directionX, directionZ;
        if (length > 0.001) {
            directionX = (float) (deltaX / length);
            directionZ = (float) (deltaZ / length);
        } else {
            // 目标在自己身上：用自己朝向
            float yaw = (float) Math.toRadians(player.getYRot());
            directionX = (float) -Math.sin(yaw);
            directionZ = (float) Math.cos(yaw);
        }

        float baseAngle = (float) Math.atan2(directionZ, directionX);
        float range = (float) Math.toRadians(arcAngle.getValue());

        // ★ 特效生成位置 = 目标位置
        Vec3 spawnPos = new Vec3(targetX, targetY, targetZ);
        spawnEffect(spawnPos, directionX, directionZ,
                baseAngle - range * 0.5f, baseAngle + range * 0.5f);
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        long nowNanos = System.nanoTime();
        float deltaTime = lastUpdateNanos == 0L
                ? 0.05f
                : Math.min(0.1f, Math.max(0.001f, (nowNanos - lastUpdateNanos) / 1_000_000_000f));
        lastUpdateNanos = nowNanos;
        long nowMs = System.currentTimeMillis();

        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance inst = effects[i];
            if (inst == null || !inst.alive) continue;
            if (enableParticles.getValue()) inst.updateParticles(deltaTime);
            if (!inst.isAlive(nowMs)) inst.alive = false;
        }
        compactEffects();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck() || effectCount == 0) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        PoseStack poseStack = event.getMatrix();
        long nowMs = System.currentTimeMillis();
        float maxDistance = renderDistance.getValue();
        float maxDistanceSq = maxDistance * maxDistance;

        SweepAttackRenderer.Config config = createRenderConfig();
        for (int i = 0; i < effectCount; i++) {
            SweepAttackInstance inst = effects[i];
            if (inst == null || !inst.alive) continue;
            double dx = inst.x - camera.position().x;
            double dy = inst.y - camera.position().y;
            double dz = inst.z - camera.position().z;
            if (dx * dx + dy * dy + dz * dz > maxDistanceSq) continue;
            SweepAttackRenderer.draw(poseStack, inst, nowMs, config);
        }
    }

    private SweepAttackRenderer.Config createRenderConfig() {
        return new SweepAttackRenderer.Config(
                style.getValue().ordinal(), colorMode.getValue().ordinal(), quality.getValue().ordinal(),
                layers.getValue(), echoes.getValue(), ringCount.getValue(), speedLineCount.getValue(),
                enableArc.getValue(), enableSpeedLines.getValue(), enableParticles.getValue(),
                enableLightning.getValue(), enableRing.getValue(), enableCoreBurst.getValue(),
                intensity.getValue(), opacity.getValue(), radius.getValue(),
                thickness.getValue(), verticalLift.getValue(), glow.getValue(),
                noise.getValue(), flowSpeed.getValue(), echoSpacing.getValue(),
                ringScale.getValue(), ringThickness.getValue(),
                lineLength.getValue(), lineWidth.getValue(), lightningWidth.getValue(),
                primaryColor.getValue().getRed() / 255f, primaryColor.getValue().getGreen() / 255f, primaryColor.getValue().getBlue() / 255f,
                accentColor.getValue().getRed() / 255f, accentColor.getValue().getGreen() / 255f, accentColor.getValue().getBlue() / 255f,
                coreColor.getValue().getRed() / 255f, coreColor.getValue().getGreen() / 255f, coreColor.getValue().getBlue() / 255f
        );
    }

    private void spawnEffect(Vec3 position, float dirX, float dirZ, float arcStart, float arcEnd) {
        int limit = maxEffects.getValue();
        while (effectCount >= limit) removeOldestEffect();
        if (effectCount >= MAX_EFFECTS) return;
        effects[effectCount++] = new SweepAttackInstance(
                position, dirX, dirZ, arcStart, arcEnd,
                heightOffset.getValue(), duration.getValue(), animationSpeed.getValue(),
                particleCount.getValue(), particleSpeed.getValue(),
                particleSpread.getValue(), particleSize.getValue(),
                particleGravity.getValue(), lightningBolts.getValue());
    }

    private void removeOldestEffect() {
        if (effectCount == 0) return;
        for (int i = 1; i < effectCount; i++) effects[i - 1] = effects[i];
        effects[--effectCount] = null;
    }

    private void compactEffects() {
        int write = 0;
        for (int read = 0; read < effectCount; read++) {
            SweepAttackInstance inst = effects[read];
            if (inst == null || !inst.alive) continue;
            effects[write++] = inst;
        }
        for (int i = write; i < effectCount; i++) effects[i] = null;
        effectCount = write;
    }

    private void clearAllEffects() {
        for (int i = 0; i < effectCount; i++) effects[i] = null;
        effectCount = 0;
    }
}