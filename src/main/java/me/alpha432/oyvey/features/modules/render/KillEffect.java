package me.alpha432.oyvey.features.modules.render;

import me.alpha432.oyvey.event.impl.entity.EntityRemoveEvent;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.KillEffectInstance;
import me.alpha432.oyvey.util.render.KillEffectRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

public class KillEffect extends Module {
    public static KillEffect INSTANCE;

    // ==================== 配置 ====================

    private final Setting<Integer> maxEffects = num("Max Effects", 8, 1, 16);
    private final Setting<Float> intensity = num("Intensity", 1.0f, 0.1f, 2.0f);
    private final Setting<Color> circleColor = color("Circle Color", new Color(0xFFFFB833, true));
    private final Setting<Boolean> enableParticles = bool("Particles", true);
    private final Setting<Boolean> cameraShake = bool("Camera Shake", true);
    private final Setting<Float> shakeStrength = num("Shake Strength", 1.0f, 0.0f, 3.0f,
            () -> cameraShake.getValue());
    private final Setting<Float> bloomStrength = num("Bloom", 0.7f, 0f, 2.0f);
    private final Setting<Float> nearbyRange = num("Nearby Range", 5.0f, 1.0f, 20.0f);

    // ==================== 阶段开关 ====================
    /** 魔法圈 / 魔法塔（阶段 1-2，塔→黑洞过渡前段）。 */
    private final Setting<Boolean> renderMagic = bool("Magic", true);
    /** 黑洞（阶段 3-5，含事件视界、光子环、吸积盘、透镜）。 */
    private final Setting<Boolean> renderBlackHole = bool("Black Hole", true);
    /** 白洞（阶段 7-10，闪光 → 超新星 → 余辉 → 淡出）。 */
    private final Setting<Boolean> renderWhiteHole = bool("White Hole", true);

    // ==================== 常量 ====================

    private static final int MAX_EFFECTS = 16;
    private static final int MAX_PARTICLE_BATCH = 4096;

    private final KillEffectInstance[] effects = new KillEffectInstance[MAX_EFFECTS];
    private int effectCount;

    private final float[] particleBatch = new float[MAX_PARTICLE_BATCH * 8];

    private float shakeIntensity;

    private long lastSpawnTimeMs;
    private double lastSpawnX, lastSpawnY, lastSpawnZ;
    private int mergeCount;

    private static final long MERGE_WINDOW_MS = 50;
    private static final double MERGE_RADIUS = 3.0;

    public KillEffect() {
        super("KillEffect", "击杀附近玩家超新星特效", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        clearAllEffects();
    }

    // ==================== 实体死亡检测 ====================

    @Subscribe
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!event.dead()) return;
        if (nullCheck()) return;
        if (mc.player.tickCount <= 1) return;
        if (event.entity() == mc.player) return;
        if (!(event.entity() instanceof Player)) return;

        double distCheckSq = mc.player.distanceToSqr(event.entity());
        float range = nearbyRange.getValue();
        if (distCheckSq > range * range) return;

        double px = event.entity().position().x;
        double py = event.entity().position().y;
        double pz = event.entity().position().z;
        long now = System.currentTimeMillis();

        double dx = px - lastSpawnX;
        double dy = py - lastSpawnY;
        double dz = pz - lastSpawnZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (mergeCount > 0
                && (now - lastSpawnTimeMs) < MERGE_WINDOW_MS
                && distSq < MERGE_RADIUS * MERGE_RADIUS) {
            mergeCount++;
            float w = 1.0f / mergeCount;
            lastSpawnX = lastSpawnX * (1f - w) + px * w;
            lastSpawnY = lastSpawnY * (1f - w) + py * w;
            lastSpawnZ = lastSpawnZ * (1f - w) + pz * w;
            lastSpawnTimeMs = now;

            for (int i = effectCount - 1; i >= 0; i--) {
                if (effects[i] != null && effects[i].alive) {
                    effects[i].mergeCount = mergeCount;
                    effects[i].position = new Vec3(lastSpawnX, lastSpawnY, lastSpawnZ);
                    break;
                }
            }
        } else {
            mergeCount = 1;
            lastSpawnX = px;
            lastSpawnY = py;
            lastSpawnZ = pz;
            lastSpawnTimeMs = now;
            spawnEffect(new Vec3(px, py, pz));
        }
    }

    // ==================== Tick ====================

    @Override
    public void onTick() {
        if (nullCheck()) return;

        long nowMs = System.currentTimeMillis();
        float dt = 0.05f;

        float shakeDecay = 0.9f;
        shakeIntensity *= shakeDecay;
        if (shakeIntensity < 0.001f) shakeIntensity = 0f;

        for (int i = 0; i < effectCount; i++) {
            KillEffectInstance inst = effects[i];
            if (!inst.alive) continue;

            int stage = inst.currentStage(nowMs);

            if (enableParticles.getValue() && stage >= 1 && stage <= KillEffectInstance.STAGE_COLLAPSE) {
                inst.updateParticles(dt, stage, nowMs);
            }

            if (enableParticles.getValue() && stage >= KillEffectInstance.STAGE_FLASH
                    && stage <= KillEffectInstance.STAGE_AFTERGLOW) {
                float mergeScale = 1.0f + (inst.mergeCount - 1) * 0.25f;
                inst.updateBurstParticles(dt, stage, nowMs, mergeScale);
            }

            if (cameraShake.getValue()) {
                if (stage == KillEffectInstance.STAGE_COLLAPSE) {
                    float progress = inst.stageProgress(nowMs);
                    shakeIntensity = Math.max(shakeIntensity, progress * 4.0f);
                } else if (stage == KillEffectInstance.STAGE_VOID) {
                    float progress = inst.stageProgress(nowMs);
                    float d = 1f - progress;
                    shakeIntensity = Math.max(shakeIntensity, 4.0f * d * d);
                } else if (renderWhiteHole.getValue() && stage == KillEffectInstance.STAGE_FLASH) {
                    float progress = inst.stageProgress(nowMs);
                    float t = progress;
                    float bell = t < 0.4f
                            ? (float) Math.exp(-((t - 0.4f) * (t - 0.4f)) / 0.04f)
                            : (float) Math.exp(-((t - 0.4f) * (t - 0.4f)) / 0.12f);
                    shakeIntensity = Math.max(shakeIntensity, 8.5f * bell);
                } else if (renderWhiteHole.getValue() && stage == KillEffectInstance.STAGE_HYPERNOVA) {
                    float progress = inst.stageProgress(nowMs);
                    float attack = Math.min(progress / 0.05f, 1f);
                    attack = attack * attack * (3f - 2f * attack);
                    float sustain = 1.0f - progress * 0.62f;
                    float pulse = 0.82f + 0.18f
                            * Math.abs((float) Math.sin(progress * Math.PI * 7.0f));
                    float novaShake = sustain * pulse * 7.5f * attack;
                    shakeIntensity = Math.max(shakeIntensity, novaShake);
                }
            }

            if (stage < 0) {
                inst.alive = false;
            }
        }

        compactEffects();
    }

    // ==================== 渲染 ====================

    @Override
    public void onRender3D(Render3DEvent event) {
        if (effectCount == 0) return;

        long nowMs = System.currentTimeMillis();
        float globalIntensity = intensity.getValue();

        for (int i = 0; i < effectCount; i++) {
            KillEffectInstance inst = effects[i];
            if (!inst.alive) continue;

            int stage = inst.currentStage(nowMs);
            if (stage < 0) continue;

            float mergeScale = 1.0f + (inst.mergeCount - 1) * 0.25f;
            float scaledIntensity = globalIntensity * mergeScale;

            // ★ 魔法圈/塔开关
            if (renderMagic.getValue() && inst.shouldRenderMagic(nowMs)) {
                KillEffectRenderer.drawMagic(event.getMatrix(), inst, nowMs);
            }

            // ★ 黑洞开关
            if (renderBlackHole.getValue() && inst.shouldRenderBlackHole(nowMs)) {
                KillEffectRenderer.drawBlackHole(event.getMatrix(), inst, nowMs);
            }

            if (enableParticles.getValue() && stage >= 1
                    && stage <= KillEffectInstance.STAGE_COLLAPSE
                    && inst.getParticleCount() > 0) {
                int particleCount = inst.fillParticleBatch(
                        particleBatch, MAX_PARTICLE_BATCH, nowMs, scaledIntensity);
                if (particleCount > 0) {
                    KillEffectRenderer.drawParticles(
                            event.getMatrix(), particleBatch, particleCount);
                }
            }

            // ★ 白洞粒子（爆炸碎片）开关
            if (enableParticles.getValue() && renderWhiteHole.getValue()
                    && stage >= KillEffectInstance.STAGE_FLASH
                    && stage <= KillEffectInstance.STAGE_AFTERGLOW
                    && inst.getBurstCount() > 0) {
                int burstCount = inst.fillBurstBatch(
                        particleBatch, MAX_PARTICLE_BATCH, nowMs, scaledIntensity);
                if (burstCount > 0) {
                    KillEffectRenderer.drawParticles(
                            event.getMatrix(), particleBatch, burstCount);
                }
            }

            // ★ 白洞主体（超新星 / 余辉 / 淡出）开关
            if (renderWhiteHole.getValue()
                    && (stage == KillEffectInstance.STAGE_HYPERNOVA
                    || stage == KillEffectInstance.STAGE_AFTERGLOW
                    || stage == KillEffectInstance.STAGE_FADE_OUT)) {
                KillEffectRenderer.drawHypernova(event.getMatrix(), inst, nowMs);
            }
        }
    }

    // ==================== 内部 ====================

    private void spawnEffect(Vec3 position) {
        int max = maxEffects.getValue();
        while (effectCount >= max) {
            removeOldestEffect();
        }

        int slot = effectCount;
        if (slot >= MAX_EFFECTS) return;

        effects[slot] = new KillEffectInstance(position, System.currentTimeMillis());
        effectCount++;
    }

    private void removeOldestEffect() {
        if (effectCount == 0) return;
        long oldestTime = Long.MAX_VALUE;
        int oldestIdx = -1;
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive && effects[i].startTimeMs < oldestTime) {
                oldestTime = effects[i].startTimeMs;
                oldestIdx = i;
            }
        }
        if (oldestIdx >= 0) {
            effects[oldestIdx].alive = false;
        }
    }

    private void compactEffects() {
        int w = 0;
        for (int r = 0; r < effectCount; r++) {
            if (!effects[r].alive) continue;
            if (r != w) {
                effects[w] = effects[r];
                effects[r] = null;
            }
            w++;
        }
        effectCount = w;
    }

    private void clearAllEffects() {
        for (int i = 0; i < effectCount; i++) {
            effects[i] = null;
        }
        effectCount = 0;
    }

    // ==================== 公开查询 ====================

    public boolean hasActiveEffects() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) return true;
        }
        return false;
    }

    public float getBloomStrength() {
        return bloomStrength.getValue();
    }

    public float getShakeIntensity() {
        return shakeIntensity * shakeStrength.getValue();
    }

    public double[] getPrimaryEffectCenter() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return new double[]{
                        effects[i].position.x,
                        effects[i].position.y + 1.5,
                        effects[i].position.z
                };
            }
        }
        return null;
    }

    public float getPrimaryProgress(long nowMs) {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].stageProgress(nowMs);
            }
        }
        return 0f;
    }

    public int getPrimaryStage(long nowMs) {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].currentStage(nowMs);
            }
        }
        return -1;
    }

    public int getPrimaryMergeCount() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].mergeCount;
            }
        }
        return 1;
    }

    public long getPrimaryStartTime() {
        for (int i = 0; i < effectCount; i++) {
            if (effects[i] != null && effects[i].alive) {
                return effects[i].startTimeMs;
            }
        }
        return 0L;
    }
}