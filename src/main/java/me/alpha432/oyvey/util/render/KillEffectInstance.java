package me.alpha432.oyvey.util.render;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Hypernova Kill Effect 的每实例状态。
 *
 * <h3>时间轴（10 阶段，共约 17.2 秒）</h3>
 * <pre>
 * Stage 1: Magic Circle Birth   0.0 – 0.8s   — 魔法圈浮现，天空粒子开始生成
 * Stage 2: Magic Tower          0.8 – 2.4s   — 12 层堆叠圆环上升
 * Stage 3: Black Hole Forming   2.4 – 3.4s   — 事件视界 + 光子环，粒子被吸引
 * Stage 4: Accretion            3.4 – 5.0s   — 粒子高速螺旋吸入
 * Stage 5: Collapse             5.0 – 6.0s   — 黑洞收缩，极端粒子拉力
 * Stage 6: Void                 6.0 – 7.5s   — 死寂，所有粒子被吞噬
 * Stage 7: Flash                7.5 – 8.2s   — 多层光环光脉冲
 * Stage 8: Hypernova            8.2 – 12.5s  — 持续冲击波 + 火球 + 闪电 + 星云
 * Stage 9: Afterglow            12.5 – 15.0s — 冷却恒星残骸
 * Stage 10: Fade-out            15.0 – 17.2s — 平滑消散
 * </pre>
 */
public class KillEffectInstance {

    /** 时间轴阶段标识 —— 与 fragment shader 阶段分发一致。 */
    public static final int STAGE_MAGIC_CIRCLE  = 1;
    public static final int STAGE_MAGIC_TOWER   = 2;
    public static final int STAGE_BLACK_HOLE    = 3;
    public static final int STAGE_ACCRETION     = 4;
    public static final int STAGE_COLLAPSE      = 5;
    public static final int STAGE_VOID          = 6;
    public static final int STAGE_FLASH         = 7;
    public static final int STAGE_HYPERNOVA     = 8;
    public static final int STAGE_AFTERGLOW     = 9;
    public static final int STAGE_FADE_OUT      = 10;

    // 时间轴边界（秒）
    private static final double T_CIRCLE_END    = 0.8;
    private static final double T_TOWER_END     = 2.4;
    private static final double T_HOLE_END      = 3.4;
    private static final double T_ACCRETION_END = 5.0;
    private static final double T_COLLAPSE_END  = 6.0;
    private static final double T_VOID_END      = 7.5;
    private static final double T_FLASH_END     = 8.2;
    private static final double T_NOVA_END      = 12.5;
    private static final double T_AFTERGLOW_END = 15.0;
    private static final double T_FADE_OUT_END  = 17.2;

    // ==================== 核心状态 ====================

    public Vec3 position;          // 可修改 —— AoE 合并时更新重心
    public final long startTimeMs;
    public boolean alive = true;

    /** 合并到本效果的击杀数（1 = 单个，> 1 = AoE 增强）。 */
    public int mergeCount = 1;

    // ==================== 粒子系统（吸积阶段）====================

    private static final int MAX_PARTICLES = 3000;
    private static final Random RNG = new Random();

    /** [x, y, z, vx, vy, vz, life, maxLife] × MAX_PARTICLES */
    private final float[] particleData = new float[MAX_PARTICLES * 8];
    /** true = 天空粒子（BH 前漂浮），false = 吸积粒子（BH 轨道） */
    private final boolean[] particleIsSky = new boolean[MAX_PARTICLES];
    private int particleCount;

    // ==================== 爆发粒子系统（超新星碎片）====================

    private static final int MAX_BURST = 1800;

    /** [x, y, z, vx, vy, vz, life, maxLife] × MAX_BURST */
    private final float[] burstData = new float[MAX_BURST * 8];
    private int burstCount;
    private boolean burstSpawned;

    // ==================== 构造 ====================

    public KillEffectInstance(Vec3 position, long startTimeMs) {
        this.position = position;
        this.startTimeMs = startTimeMs;
    }

    // ==================== 时间查询 ====================

    /** 已流逝时间（秒）。 */
    public double elapsedSec(long nowMs) {
        return (nowMs - startTimeMs) / 1000.0;
    }

    /** 当前阶段。 */
    public int currentStage(long nowMs) {
        double t = elapsedSec(nowMs);
        if (t < T_CIRCLE_END)    return STAGE_MAGIC_CIRCLE;
        if (t < T_TOWER_END)     return STAGE_MAGIC_TOWER;
        if (t < T_HOLE_END)      return STAGE_BLACK_HOLE;
        if (t < T_ACCRETION_END) return STAGE_ACCRETION;
        if (t < T_COLLAPSE_END)  return STAGE_COLLAPSE;
        if (t < T_VOID_END)      return STAGE_VOID;
        if (t < T_FLASH_END)     return STAGE_FLASH;
        if (t < T_NOVA_END)      return STAGE_HYPERNOVA;
        if (t < T_AFTERGLOW_END) return STAGE_AFTERGLOW;
        if (t < T_FADE_OUT_END)  return STAGE_FADE_OUT;
        return -1;
    }

    /** 当前阶段内的归一化进度（0→1）。 */
    public float stageProgress(long nowMs) {
        double t = elapsedSec(nowMs);
        int stage = currentStage(nowMs);
        switch (stage) {
            case STAGE_MAGIC_CIRCLE:  return (float) (t / T_CIRCLE_END);
            case STAGE_MAGIC_TOWER:   return (float) ((t - T_CIRCLE_END) / (T_TOWER_END - T_CIRCLE_END));
            case STAGE_BLACK_HOLE:    return (float) ((t - T_TOWER_END) / (T_HOLE_END - T_TOWER_END));
            case STAGE_ACCRETION:     return (float) ((t - T_HOLE_END) / (T_ACCRETION_END - T_HOLE_END));
            case STAGE_COLLAPSE:      return (float) ((t - T_ACCRETION_END) / (T_COLLAPSE_END - T_ACCRETION_END));
            case STAGE_VOID:          return (float) ((t - T_COLLAPSE_END) / (T_VOID_END - T_COLLAPSE_END));
            case STAGE_FLASH:         return (float) ((t - T_VOID_END) / (T_FLASH_END - T_VOID_END));
            case STAGE_HYPERNOVA:     return (float) ((t - T_FLASH_END) / (T_NOVA_END - T_FLASH_END));
            case STAGE_AFTERGLOW:     return (float) ((t - T_NOVA_END) / (T_AFTERGLOW_END - T_NOVA_END));
            case STAGE_FADE_OUT:      return (float) ((t - T_AFTERGLOW_END) / (T_FADE_OUT_END - T_AFTERGLOW_END));
            default: return 0f;
        }
    }

    /** 跨阶段过渡 alpha。fadeIn/fadeOut 控制是否启用淡入/淡出。 */
    public float transitionAlpha(long nowMs, boolean fadeIn, boolean fadeOut) {
        float progress = stageProgress(nowMs);
        float alpha = 1f;
        if (fadeIn && progress < 0.2f) {
            float t = progress / 0.2f;
            alpha = 1.0f - (float) Math.pow(2.0, -10.0 * t);
        }
        if (fadeOut && progress > 0.8f) {
            float t = (progress - 0.8f) / 0.2f;
            alpha = (float) Math.pow(2.0, 10.0 * (t - 1.0));
        }
        return clamp(alpha, 0f, 1f);
    }

    // ==================== 跨阶段过渡（魔法塔 ↔ 黑洞）====================

    /** 过渡重叠：每阶段中两个效果同时渲染的比例。 */
    private static final float TRANSITION_OVERLAP = 0.30f;

    /** 魔法（圆/塔）此刻是否可见。包含 BH 阶段开头做交叉淡出。 */
    public boolean shouldRenderMagic(long nowMs) {
        int stage = currentStage(nowMs);
        if (stage == STAGE_MAGIC_CIRCLE || stage == STAGE_MAGIC_TOWER) return true;
        if (stage == STAGE_BLACK_HOLE && stageProgress(nowMs) < TRANSITION_OVERLAP) return true;
        return false;
    }

    /** 黑洞此刻是否可见。包含塔阶段结尾做交叉淡出，排除 VOID 阶段。 */
    public boolean shouldRenderBlackHole(long nowMs) {
        int stage = currentStage(nowMs);
        if (stage >= STAGE_BLACK_HOLE && stage <= STAGE_COLLAPSE) return true;
        if (stage == STAGE_MAGIC_TOWER && stageProgress(nowMs) > 1.0f - TRANSITION_OVERLAP) return true;
        return false;
    }

    /** 魔法渲染在塔→BH 过渡中的 alpha。 */
    public float magicTransitionAlpha(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0f - TRANSITION_OVERLAP) {
                return 1.0f - (progress - (1.0f - TRANSITION_OVERLAP)) / TRANSITION_OVERLAP;
            }
            return 1.0f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < TRANSITION_OVERLAP) {
                return 1.0f - (1.0f - TRANSITION_OVERLAP + progress) / (1.0f + TRANSITION_OVERLAP);
            }
            return 0f;
        }
        return 1.0f;
    }

    /** 黑洞渲染在塔→BH 过渡中的 alpha。 */
    public float blackHoleTransitionAlpha(long nowMs) {
        int stage = currentStage(nowMs);
        float progress = stageProgress(nowMs);

        if (stage == STAGE_MAGIC_TOWER) {
            if (progress > 1.0f - TRANSITION_OVERLAP) {
                float t = (progress - (1.0f - TRANSITION_OVERLAP)) / TRANSITION_OVERLAP;
                return t * 0.35f;
            }
            return 0f;
        }
        if (stage == STAGE_BLACK_HOLE) {
            if (progress < TRANSITION_OVERLAP) {
                float t = progress / TRANSITION_OVERLAP;
                return 0.35f + (1.0f - 0.35f) * t;
            }
            return transitionAlpha(nowMs, false, false);
        }
        if (stage == STAGE_ACCRETION || stage == STAGE_COLLAPSE) {
            return transitionAlpha(nowMs, false, stage == STAGE_COLLAPSE);
        }
        return 0f;
    }

    /** Afterglow 之后平滑消散的 alpha。反向 smoothstep 曲线。 */
    public float getFadeOutAlpha(long nowMs) {
        if (currentStage(nowMs) != STAGE_FADE_OUT) return 1.0f;
        float t = stageProgress(nowMs);
        return 1.0f - t * t * (3.0f - 2.0f * t);
    }

    // ==================== 粒子管理 ====================

    public int getParticleCount() {
        return particleCount;
    }

    /** 生成一个天空粒子（高海拔）。 */
    private void spawnSkyParticle(int index) {
        int off = index * 8;
        float px = (float) position.x;
        float py = (float) position.y;
        float pz = (float) position.z;

        double theta = RNG.nextDouble() * Math.PI * 2.0;
        double radius = 3.0 + RNG.nextDouble() * 16.0;

        particleData[off]     = px + (float) (Math.cos(theta) * radius);
        particleData[off + 1] = py + 8.0f + RNG.nextFloat() * 25.0f;
        particleData[off + 2] = pz + (float) (Math.sin(theta) * radius);

        particleData[off + 3] = (RNG.nextFloat() - 0.5f) * 0.6f;
        particleData[off + 4] = (RNG.nextFloat() - 0.5f) * 0.3f;
        particleData[off + 5] = (RNG.nextFloat() - 0.5f) * 0.6f;

        particleData[off + 6] = 0f;
        particleData[off + 7] = 2.0f + RNG.nextFloat() * 4.0f;

        particleIsSky[index] = true;
    }

    /** 在赤道盘面上重生一个吸积粒子。三层温度分布。 */
    private void respawnAccretionParticle(int index) {
        int off = index * 8;
        float cx = (float) position.x;
        float cy = (float) position.y + 1.5f;
        float cz = (float) position.z;

        double theta = RNG.nextDouble() * Math.PI * 2.0;

        double radius;
        float layerPick = RNG.nextFloat();
        if (layerPick < 0.42f) {
            radius = 0.75 + RNG.nextDouble() * 1.55;
        } else if (layerPick < 0.82f) {
            radius = 1.8 + RNG.nextDouble() * 3.8;
        } else {
            radius = 5.0 + RNG.nextDouble() * 4.5;
        }

        float diskThickness = 0.08f + (float) (radius * 0.028);
        float yOffset = (RNG.nextFloat() - 0.5f) * 2f * diskThickness;

        particleData[off]     = cx + (float) (Math.cos(theta) * radius);
        particleData[off + 1] = cy + yOffset;
        particleData[off + 2] = cz + (float) (Math.sin(theta) * radius);

        // 开普勒轨道速度：∝ 1/√r
        double orbitalSpeed = 5.2 / Math.sqrt(Math.max(radius, 0.35));
        double vx = -Math.sin(theta) * orbitalSpeed;
        double vz = Math.cos(theta) * orbitalSpeed;
        double radialDrift = -0.32 / Math.max(radius, 0.45);

        particleData[off + 3] = (float) (vx + Math.cos(theta) * radialDrift);
        particleData[off + 4] = (float) (RNG.nextDouble() * 0.2 - 0.1);
        particleData[off + 5] = (float) (vz + Math.sin(theta) * radialDrift);

        particleData[off + 6] = 0f;
        particleData[off + 7] = 2.2f + RNG.nextFloat() * 3.2f;

        particleIsSky[index] = false;
    }

    /** 根据当前阶段更新粒子位置。 */
    public void updateParticles(float dt, int stage, long nowMs) {
        if (stage < 1 || stage > STAGE_COLLAPSE) return;

        float cx = (float) position.x;
        float cy = (float) position.y + 1.5f;
        float cz = (float) position.z;

        boolean isPreBH = stage == STAGE_MAGIC_CIRCLE || stage == STAGE_MAGIC_TOWER;
        boolean isBH = stage >= STAGE_BLACK_HOLE && stage <= STAGE_COLLAPSE;

        // BH 前阶段持续生成天空粒子
        if (isPreBH && particleCount < MAX_PARTICLES) {
            int spawnPerTick = 30;
            for (int s = 0; s < spawnPerTick && particleCount < MAX_PARTICLES; s++) {
                spawnSkyParticle(particleCount);
                particleCount++;
            }
        }

        // 每阶段引力参数
        float accelBase;
        float speedCap;
        if (stage == STAGE_BLACK_HOLE) {
            float progress = stageProgress(nowMs);
            accelBase = 15f + progress * 60f;
            speedCap  = 10f + progress * 40f;
        } else if (stage == STAGE_ACCRETION) {
            accelBase = 80f;
            speedCap  = 55f;
        } else {
            accelBase = 150f;
            speedCap  = 80f;
        }

        for (int i = 0; i < particleCount; i++) {
            int off = i * 8;
            float life = particleData[off + 6] + dt;
            particleData[off + 6] = life;

            if (life >= particleData[off + 7]) {
                if (isBH) {
                    respawnAccretionParticle(i);
                } else if (isPreBH) {
                    spawnSkyParticle(i);
                }
                continue;
            }

            if (isPreBH) {
                // 平滑正弦漂移
                float driftX = (float) Math.sin(i * 1.7f + life * 0.8f) * 0.15f;
                float driftY = (float) Math.cos(i * 2.3f + life * 0.6f) * 0.08f;
                float driftZ = (float) Math.cos(i * 1.9f + life * 0.7f) * 0.15f;

                particleData[off]     += driftX * dt;
                particleData[off + 1] += driftY * dt;
                particleData[off + 2] += driftZ * dt;

            } else if (isBH) {
                // 线性拉向黑洞中心
                float dx = cx - particleData[off];
                float dy = cy - particleData[off + 1];
                float dz = cz - particleData[off + 2];
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist < 0.01f) {
                    particleData[off + 6] = particleData[off + 7];
                    continue;
                }

                float invDist = 1.0f / dist;
                float dirX = dx * invDist;
                float dirY = dy * invDist;
                float dirZ = dz * invDist;

                float accelMag = accelBase * (0.5f + 0.5f * clamp(dist / 8f, 0f, 1f));

                float vx = particleData[off + 3];
                float vy = particleData[off + 4];
                float vz = particleData[off + 5];

                float targetSpeed = Math.min(speedCap, accelMag * dist * 0.3f);
                float blend = clamp(accelBase * dt * 0.15f, 0.01f, 0.3f);

                float tvx;
                float tvy;
                float tvz;

                if (!particleIsSky[i] && stage != STAGE_COLLAPSE) {
                    // 保持开普勒轨道
                    float hR = (float) Math.sqrt(dx * dx + dz * dz);
                    float invH = 1.0f / Math.max(hR, 0.05f);
                    float radX = -dx * invH;
                    float radZ = -dz * invH;
                    float tanX = -radZ;
                    float tanZ = radX;
                    float orbitSpeed = 5.8f / (float) Math.sqrt(Math.max(hR, 0.4f));
                    float inwardSpeed = 0.45f + 0.65f / Math.max(hR, 0.6f);

                    tvx = tanX * orbitSpeed - radX * inwardSpeed;
                    tvy = dy * 2.8f;
                    tvz = tanZ * orbitSpeed - radZ * inwardSpeed;
                    blend = clamp(dt * 3.8f, 0.04f, 0.22f);
                } else {
                    // 天空流和最终坍缩直接冲向视界
                    tvx = dirX * targetSpeed;
                    tvy = dirY * targetSpeed;
                    tvz = dirZ * targetSpeed;
                }

                particleData[off + 3] = vx + (tvx - vx) * blend;
                particleData[off + 4] = vy + (tvy - vy) * blend;
                particleData[off + 5] = vz + (tvz - vz) * blend;

                particleData[off]     += particleData[off + 3] * dt;
                particleData[off + 1] += particleData[off + 4] * dt;
                particleData[off + 2] += particleData[off + 5] * dt;

                // 在事件视界吞噬粒子
                float d2 = (float) Math.sqrt(
                        (particleData[off] - cx) * (particleData[off] - cx) +
                                (particleData[off + 1] - cy) * (particleData[off + 1] - cy) +
                                (particleData[off + 2] - cz) * (particleData[off + 2] - cz));
                if (d2 < 0.38f) {
                    particleData[off + 6] = particleData[off + 7];
                }
            }
        }
    }

    /** 填充粒子批次用于渲染：[x,y,z,size,r,g,b,a] × count */
    public int fillParticleBatch(float[] batch, int maxCount, long nowMs, float alpha) {
        if (particleCount == 0) return 0;

        int count = 0;
        for (int i = 0; i < particleCount && count < maxCount; i++) {
            int off = i * 8;
            float life = particleData[off + 6];
            float maxLife = particleData[off + 7];
            if (life >= maxLife) continue;

            float lifeRatio = life / maxLife;
            float pAlpha = alpha * (1f - lifeRatio) * (lifeRatio < 0.1f ? lifeRatio / 0.1f : 1f);

            int bo = count * 8;

            if (particleIsSky[i]) {
                batch[bo]     = particleData[off];
                batch[bo + 1] = particleData[off + 1];
                batch[bo + 2] = particleData[off + 2];
                batch[bo + 3] = 0.06f * (1f - lifeRatio * 0.4f);
                batch[bo + 4] = lifeRatio * 0.3f;
                batch[bo + 5] = 1.0f;
                batch[bo + 6] = 0f;
                batch[bo + 7] = pAlpha;
            } else {
                batch[bo]     = particleData[off];
                batch[bo + 1] = particleData[off + 1];
                batch[bo + 2] = particleData[off + 2];
                batch[bo + 3] = 0.045f * (1f - lifeRatio * 0.55f);
                batch[bo + 4] = lifeRatio;
                batch[bo + 5] = 0f;
                batch[bo + 6] = 0f;
                batch[bo + 7] = pAlpha;
            }
            count++;
        }
        return count;
    }

    /** 重置粒子系统。 */
    public void resetParticles() {
        particleCount = 0;
    }

    // ==================== 爆发粒子（爆炸碎片）====================

    public int getBurstCount() {
        return burstCount;
    }

    /** 更新爆发碎片。 */
    public void updateBurstParticles(float dt, int stage, long nowMs, float mergeScale) {
        if (!burstSpawned && stage == STAGE_FLASH && stageProgress(nowMs) >= 0.30f) {
            spawnBurst(mergeScale);
        }
        if (burstCount == 0) return;

        float drag = (float) Math.exp(-1.15 * dt);
        float grav = 1.65f * dt;

        for (int i = 0; i < burstCount; i++) {
            int off = i * 8;
            float life = burstData[off + 6] + dt;
            burstData[off + 6] = life;
            if (life >= burstData[off + 7]) continue;

            burstData[off + 3] *= drag;
            burstData[off + 4] = burstData[off + 4] * drag - grav;
            burstData[off + 5] *= drag;

            burstData[off]     += burstData[off + 3] * dt;
            burstData[off + 1] += burstData[off + 4] * dt;
            burstData[off + 2] += burstData[off + 5] * dt;
        }
    }

    /** 生成爆炸爆发：均匀球面方向，二次速度分布。 */
    private void spawnBurst(float mergeScale) {
        burstSpawned = true;
        float clampedScale = Math.min(mergeScale, 3f);
        int count = Math.min(MAX_BURST, 1050 + (int) (clampedScale * 220f));
        float speedScale = (float) Math.sqrt(clampedScale);

        float cx = (float) position.x;
        float cy = (float) position.y + 1.6f;
        float cz = (float) position.z;

        for (int i = 0; i < count; i++) {
            int off = i * 8;

            double theta = RNG.nextDouble() * Math.PI * 2.0;
            double cosPhi = RNG.nextDouble() * 2.0 - 1.0;
            double sinPhi = Math.sqrt(1.0 - cosPhi * cosPhi);
            float dx = (float) (sinPhi * Math.cos(theta));
            float dy = (float) (cosPhi * 0.75 + 0.30);
            float dz = (float) (sinPhi * Math.sin(theta));

            float speed = (6.5f + RNG.nextFloat() * RNG.nextFloat() * 31.0f) * speedScale;

            burstData[off]     = cx + dx * 0.3f;
            burstData[off + 1] = cy + dy * 0.3f;
            burstData[off + 2] = cz + dz * 0.3f;
            burstData[off + 3] = dx * speed;
            burstData[off + 4] = dy * speed;
            burstData[off + 5] = dz * speed;
            burstData[off + 6] = 0f;
            burstData[off + 7] = 3.4f + RNG.nextFloat() * 2.4f;
        }
        burstCount = count;
    }

    /** 填充爆发碎片批次。 */
    public int fillBurstBatch(float[] batch, int maxCount, long nowMs, float alpha) {
        if (burstCount == 0) return 0;

        int count = 0;
        for (int i = 0; i < burstCount && count < maxCount; i++) {
            int off = i * 8;
            float life = burstData[off + 6];
            float maxLife = burstData[off + 7];
            if (life >= maxLife) continue;

            float lifeRatio = life / maxLife;
            float decay = 1f - lifeRatio;
            float pAlpha = Math.min(alpha * decay * decay, 1f);
            if (pAlpha < 0.004f) continue;

            float size = 0.055f * (1f + lifeRatio * 1.6f);

            int bo = count * 8;
            batch[bo]     = burstData[off];
            batch[bo + 1] = burstData[off + 1];
            batch[bo + 2] = burstData[off + 2];
            batch[bo + 3] = size;
            batch[bo + 4] = lifeRatio;
            batch[bo + 5] = 0.5f;
            batch[bo + 6] = 0f;
            batch[bo + 7] = pAlpha;
            count++;
        }
        return count;
    }

    // ==================== 工具 ====================

    /** 兼容旧 Java 的 clamp（Math.clamp 是 Java 21+ 才有的）。 */
    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}