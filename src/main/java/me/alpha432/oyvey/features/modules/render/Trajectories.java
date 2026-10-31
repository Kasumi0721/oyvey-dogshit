package me.alpha432.oyvey.features.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.TrajectoriesRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.*;

public class Trajectories extends Module {
    public static Trajectories INSTANCE;

    // ==================== 枚举 ====================
    public enum GuideStyle { Solid, Dashed, Comet }
    public enum MarkerStyle { Ring, Reticle, Arcane }
    public enum TrailColors { Entity, Custom, Rainbow }
    public enum DepthMode { Occluded, ThroughWalls }

    // ==================== Prediction 设置 ====================
    private final Setting<Boolean> prediction = bool("Prediction", true, () -> true);
    private final Setting<Boolean> showSelf = bool("Predict Self", true, () -> prediction.getValue());
    private final Setting<Boolean> showOthers = bool("Predict Others", false, () -> prediction.getValue());
    private final Setting<Boolean> predictBows = bool("Bows", true, () -> prediction.getValue());
    private final Setting<Boolean> predictPearls = bool("Pearls", true, () -> prediction.getValue());
    private final Setting<Boolean> predictThrowables = bool("Eggs & Snowballs", true, () -> prediction.getValue());
    private final Setting<Boolean> predictPotions = bool("Potions", true, () -> prediction.getValue());
    private final Setting<Boolean> predictRods = bool("Fishing Rods", true, () -> prediction.getValue());
    private final Setting<Boolean> predictTridents = bool("Tridents", true, () -> prediction.getValue());

    // ==================== Prediction 外观 ====================
    private final Setting<GuideStyle> predictionStyle = mode("Guide Style", GuideStyle.Comet, () -> prediction.getValue());
    private final Setting<Color> guideStartColor = color("Guide Start", new Color(0xFF72F6FF, true), () -> prediction.getValue());
    private final Setting<Color> guideEndColor = color("Guide End", new Color(0xFF9B62FF, true), () -> prediction.getValue());
    private final Setting<Color> entityHitColor = color("Entity Hit", new Color(0xFFFF496C, true), () -> prediction.getValue());
    private final Setting<Float> guideWidth = num("Guide Width", 0.055f, 0.01f, 0.30f, () -> prediction.getValue());
    private final Setting<Float> guideOpacity = num("Guide Opacity", 0.92f, 0.05f, 1.0f, () -> prediction.getValue());
    private final Setting<Float> guideGlow = num("Guide Glow", 1.35f, 0.0f, 3.0f, () -> prediction.getValue());
    private final Setting<Float> guideFlowSpeed = num("Guide Animation", 1.0f, 0.0f, 4.0f, () -> prediction.getValue());
    private final Setting<Float> dashDensity = num("Dash Density", 11.0f, 2.0f, 30.0f,
            () -> prediction.getValue() && predictionStyle.getValue() == GuideStyle.Dashed);
    private final Setting<Integer> simulationSteps = num("Prediction Steps", 220, 40, 400, () -> prediction.getValue());

    // ==================== 落点标记 ====================
    private final Setting<Boolean> landingMarker = bool("Landing Marker", true, () -> prediction.getValue());
    private final Setting<MarkerStyle> markerStyle = mode("Marker Style", MarkerStyle.Arcane,
            () -> prediction.getValue() && landingMarker.getValue());
    private final Setting<Float> markerSize = num("Marker Size", 0.72f, 0.2f, 2.5f,
            () -> prediction.getValue() && landingMarker.getValue());
    private final Setting<Float> markerPulse = num("Marker Pulse", 1.0f, 0.0f, 3.0f,
            () -> prediction.getValue() && landingMarker.getValue());

    // ==================== 飞行 FX 设置 ====================
    private final Setting<Boolean> flightEffects = bool("Flight Effects", true, () -> true);
    private final Setting<Boolean> fxArrows = bool("Arrows & Tridents", true, () -> flightEffects.getValue());
    private final Setting<Boolean> fxPearls = bool("Ender Pearls", true, () -> flightEffects.getValue());
    private final Setting<Boolean> fxThrowables = bool("Thrown Items", true, () -> flightEffects.getValue());
    private final Setting<Boolean> fxPotions = bool("Thrown Potions", true, () -> flightEffects.getValue());
    private final Setting<Boolean> fxMagic = bool("Magic Projectiles", true, () -> flightEffects.getValue());
    private final Setting<Boolean> fxHooks = bool("Fishing Hooks", false, () -> flightEffects.getValue());

    // ==================== 飞行拖尾 ====================
    private final Setting<Boolean> energyTrail = bool("Energy Ribbon", true, () -> flightEffects.getValue());
    private final Setting<TrailColors> trailColorMode = mode("Trail Colors", TrailColors.Entity, () -> flightEffects.getValue() && energyTrail.getValue());
    private final Setting<Color> trailPrimary = color("Trail Primary", new Color(0xFF58E8FF, true),
            () -> flightEffects.getValue() && energyTrail.getValue() && trailColorMode.getValue() == TrailColors.Custom);
    private final Setting<Color> trailSecondary = color("Trail Secondary", new Color(0xFFFF62D4, true),
            () -> flightEffects.getValue() && energyTrail.getValue() && trailColorMode.getValue() == TrailColors.Custom);
    private final Setting<Float> trailWidth = num("Ribbon Width", 0.105f, 0.015f, 0.50f, () -> flightEffects.getValue() && energyTrail.getValue());
    private final Setting<Float> trailLifetime = num("Trail Lifetime", 1.35f, 0.15f, 6.0f, () -> flightEffects.getValue() && energyTrail.getValue());
    private final Setting<Integer> trailPoints = num("Trail Detail", 48, 8, 160, () -> flightEffects.getValue() && energyTrail.getValue());
    private final Setting<Float> trailOpacity = num("Trail Opacity", 0.88f, 0.05f, 1.0f, () -> flightEffects.getValue() && energyTrail.getValue());
    private final Setting<Float> trailGlow = num("Trail Glow", 1.65f, 0.0f, 3.0f, () -> flightEffects.getValue() && energyTrail.getValue());

    // ==================== 装饰层 ====================
    private final Setting<Boolean> headFlare = bool("Projectile Halo", true, () -> flightEffects.getValue());
    private final Setting<Float> flareSize = num("Halo Size", 0.34f, 0.08f, 1.5f, () -> flightEffects.getValue() && headFlare.getValue());
    private final Setting<Float> flareIntensity = num("Halo Intensity", 1.25f, 0.1f, 3.0f, () -> flightEffects.getValue() && headFlare.getValue());
    private final Setting<Boolean> trailSparks = bool("Trail Sparks", true, () -> flightEffects.getValue());
    private final Setting<Integer> sparkDensity = num("Spark Density", 3, 1, 8, () -> flightEffects.getValue() && trailSparks.getValue());
    private final Setting<Float> sparkSize = num("Spark Size", 0.095f, 0.025f, 0.35f, () -> flightEffects.getValue() && trailSparks.getValue());
    private final Setting<Boolean> impactBurst = bool("Impact Burst", true, () -> flightEffects.getValue());
    private final Setting<Float> impactDuration = num("Impact Duration", 0.75f, 0.15f, 2.5f, () -> flightEffects.getValue() && impactBurst.getValue());
    private final Setting<Float> impactSize = num("Impact Size", 1.15f, 0.25f, 4.0f, () -> flightEffects.getValue() && impactBurst.getValue());

    // ==================== 全局 ====================
    private final Setting<DepthMode> depthMode = mode("Depth Mode", DepthMode.Occluded, () -> true);
    private final Setting<Float> renderDistance = num("Render Distance", 72.0f, 12.0f, 192.0f, () -> true);

    // ==================== 状态 ====================
    private final Map<Integer, ProjectileTrack> projectileTracks = new HashMap<>();
    private final List<ImpactEffect> impacts = new ArrayList<>();

    private record TrailPoint(Vec3 pos, long timestamp) {}
    private record ImpactEffect(Vec3 pos, long timestamp, ProjectileKind kind) {}
    private record TrajectoryResult(List<Vec3> points, Vec3 landingPos, boolean hitEntity) {}

    private static final class ProjectileTrack {
        Entity entity;
        final ProjectileKind kind;
        final ArrayDeque<TrailPoint> points = new ArrayDeque<>();
        long lastSeen;
        boolean active = true;
        ProjectileTrack(Entity e, ProjectileKind k, long now) { entity = e; kind = k; lastSeen = now; }
    }

    private enum ProjectileKind {
        ARROW(0xFFFFB84D, 0xFFFF5A5F),
        PEARL(0xFFB86BFF, 0xFF4AFFF3),
        POTION(0xFFFF70D2, 0xFF8C62FF),
        THROWABLE(0xFFE9FAFF, 0xFF73CAFF),
        MAGIC(0xFFFFE85A, 0xFFFF6A2B),
        HOOK(0xFF82E6FF, 0xFFFFFFFF);
        final int primary, secondary;
        ProjectileKind(int p, int s) { primary = p; secondary = s; }
    }

    public Trajectories() {
        super("Trajectories", "抛物线预测 + 抛射物拖尾", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        projectileTracks.clear();
        impacts.clear();
    }

    // ==================== 每 tick 更新拖尾数据 ====================
    @Override
    public void onTick() {
        if (nullCheck()) return;

        if (!flightEffects.getValue()) {
            projectileTracks.clear();
            impacts.clear();
            return;
        }

        long now = System.currentTimeMillis();
        long trailLifeMs = Math.max(1L, (long) (trailLifetime.getValue() * 1000.0f));
        Set<Integer> seen = new HashSet<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            ProjectileKind kind = classifyProjectile(entity);
            if (kind == null || !flightKindEnabled(kind)) continue;

            int id = entity.getId();
            seen.add(id);
            if (!withinRenderDistance(entity.position())) continue;

            ProjectileTrack track = projectileTracks.get(id);
            if (track == null || track.entity != entity || track.kind != kind) {
                track = new ProjectileTrack(entity, kind, now);
                projectileTracks.put(id, track);
            }

            track.entity = entity;
            track.active = true;
            track.lastSeen = now;
            pruneTrail(track.points, now, trailLifeMs);

            Vec3 position = entity.position();
            TrailPoint newest = track.points.peekLast();
            if (newest == null || newest.pos().distanceToSqr(position) > 0.0004) {
                while (track.points.size() >= trailPoints.getValue()) track.points.removeFirst();
                track.points.addLast(new TrailPoint(position, now));
            }
        }

        Iterator<Map.Entry<Integer, ProjectileTrack>> it = projectileTracks.entrySet().iterator();
        while (it.hasNext()) {
            ProjectileTrack track = it.next().getValue();
            pruneTrail(track.points, now, trailLifeMs);

            if (track.active && !seen.contains(track.entity.getId())) {
                track.active = false;
                TrailPoint last = track.points.peekLast();
                if (impactBurst.getValue() && last != null && track.points.size() >= 2) {
                    impacts.add(new ImpactEffect(last.pos(), now, track.kind));
                }
            }

            if (!track.active && track.points.isEmpty()) it.remove();
        }

        long impactLifeMs = Math.max(1L, (long) (impactDuration.getValue() * 1000.0f));
        impacts.removeIf(impact -> now - impact.timestamp() > impactLifeMs);
    }

    // ==================== 渲染 ====================
    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        PoseStack poseStack = event.getMatrix();
        float partialTick = event.getDelta();

        if (prediction.getValue()) renderPredictions(poseStack, partialTick);
        if (flightEffects.getValue()) renderFlightEffects(poseStack);
    }

    private void renderPredictions(PoseStack poseStack, float partialTick) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player && !showSelf.getValue()) continue;
            if (entity != mc.player && !showOthers.getValue()) continue;
            if (!withinRenderDistance(entity.getPosition(partialTick))) continue;

            TrajectoryResult result = calculateTrajectory(living, partialTick);
            if (result != null) renderPrediction(poseStack, result);
        }
    }

    private void renderPrediction(PoseStack poseStack, TrajectoryResult result) {
        if (result.points().size() < 2) return;

        float[] positions = flatten(result.points());
        int start = withAlpha(toArgb(guideStartColor.getValue()), guideOpacity.getValue());
        int end = withAlpha(toArgb(result.hitEntity() ? entityHitColor.getValue() : guideEndColor.getValue()), guideOpacity.getValue());
        float time = System.currentTimeMillis() * 0.001f * guideFlowSpeed.getValue();
        int style = predictionStyle.getValue() == GuideStyle.Solid ? 0
                : predictionStyle.getValue() == GuideStyle.Dashed ? 1 : 2;
        boolean xray = depthMode.getValue() == DepthMode.ThroughWalls;

        if (guideGlow.getValue() > 0.01f) {
            int glowStart = scaleAlpha(start, 0.20f * guideGlow.getValue());
            int glowEnd = scaleAlpha(end, 0.20f * guideGlow.getValue());
            TrajectoriesRenderer.drawRibbon(poseStack, positions, result.points().size(),
                    glowStart, glowEnd, guideWidth.getValue() * (2.2f + guideGlow.getValue()),
                    style, dashDensity.getValue(), time, xray);
        }

        TrajectoriesRenderer.drawRibbon(poseStack, positions, result.points().size(),
                start, end, guideWidth.getValue(), style, dashDensity.getValue(), time, xray);

        if (landingMarker.getValue() && result.landingPos() != null) {
            float pulse = 1.0f + 0.10f * markerPulse.getValue()
                    * (float) Math.sin(System.currentTimeMillis() * 0.006 * markerPulse.getValue());
            int markerColor = result.hitEntity() ? toArgb(entityHitColor.getValue()) : toArgb(guideEndColor.getValue());
            int markerType = markerStyle.getValue() == MarkerStyle.Ring ? 0
                    : markerStyle.getValue() == MarkerStyle.Reticle ? 1 : 2;
            TrajectoriesRenderer.drawMarker(poseStack, result.landingPos(),
                    markerSize.getValue() * pulse, withAlpha(markerColor, guideOpacity.getValue()),
                    markerType, time, xray);
        }
    }

    private void renderFlightEffects(PoseStack poseStack) {
        long now = System.currentTimeMillis();
        long lifeMs = Math.max(1L, (long) (trailLifetime.getValue() * 1000.0f));
        boolean xray = depthMode.getValue() == DepthMode.ThroughWalls;

        for (ProjectileTrack track : projectileTracks.values()) {
            if (track.points.isEmpty()) continue;
            TrailPoint newest = track.points.peekLast();
            if (newest == null || !withinRenderDistance(newest.pos())) continue;

            List<TrailPoint> points = new ArrayList<>(track.points);
            int count = points.size();
            float[] positions = new float[count * 3];
            int[] colors = new int[count];

            for (int i = 0; i < count; i++) {
                TrailPoint point = points.get(i);
                positions[i * 3] = (float) point.pos().x;
                positions[i * 3 + 1] = (float) point.pos().y;
                positions[i * 3 + 2] = (float) point.pos().z;

                float ageAlpha = 1.0f - clamp01((now - point.timestamp()) / (float) lifeMs);
                float lengthAlpha = (i + 1f) / count;
                int color = trailColor(track.kind, i / (float) Math.max(1, count - 1), now);
                colors[i] = withAlpha(color, ageAlpha * lengthAlpha * trailOpacity.getValue());
            }

            if (energyTrail.getValue() && count >= 2) {
                if (trailGlow.getValue() > 0.01f) {
                    int[] glowColors = new int[count];
                    for (int i = 0; i < count; i++) {
                        glowColors[i] = scaleAlpha(colors[i], 0.18f * trailGlow.getValue());
                    }
                    TrajectoriesRenderer.drawColoredRibbon(poseStack, positions, glowColors, count,
                            trailWidth.getValue() * (2.0f + trailGlow.getValue()), xray);
                }
                TrajectoriesRenderer.drawColoredRibbon(poseStack, positions, colors, count,
                        trailWidth.getValue(), xray);
            }

            if (trailSparks.getValue() && count > 1) {
                int spacing = Math.max(1, 9 - sparkDensity.getValue());
                for (int i = (track.entity == null ? 0 : track.entity.getId()) % spacing;
                     i < count - 1; i += spacing) {
                    float flicker = 0.65f + 0.35f * (float) Math.sin(now * 0.012 + i * 2.17);
                    TrajectoriesRenderer.drawFlare(poseStack, points.get(i).pos(),
                            sparkSize.getValue() * flicker, colors[i], now * 0.001f + i, xray);
                }
            }

            if (headFlare.getValue() && track.active && track.entity != null) {
                int headColor = trailColor(track.kind, 1.0f, now);
                float speed = (float) track.entity.getDeltaMovement().length();
                float scale = flareSize.getValue() * (1.0f + Math.min(speed, 2.0f) * 0.22f);
                int color = withAlpha(headColor, clamp01(0.55f * flareIntensity.getValue()));
                TrajectoriesRenderer.drawFlare(poseStack, track.entity.position(), scale, color,
                        now * 0.001f, xray);
            }
        }

        if (impactBurst.getValue()) {
            float durationMs = Math.max(1.0f, impactDuration.getValue() * 1000.0f);
            for (ImpactEffect impact : impacts) {
                if (!withinRenderDistance(impact.pos())) continue;
                float progress = clamp01((now - impact.timestamp()) / durationMs);
                float eased = 1.0f - (1.0f - progress) * (1.0f - progress);
                float alpha = (1.0f - progress) * (1.0f - progress);
                int primary = withAlpha(trailColor(impact.kind(), progress, now), alpha);
                int secondary = withAlpha(impact.kind().secondary, alpha * 0.75f);
                float size = impactSize.getValue() * (0.20f + eased);

                TrajectoriesRenderer.drawMarker(poseStack, impact.pos(), size, primary, 2, now * 0.001f, xray);
                TrajectoriesRenderer.drawFlare(poseStack, impact.pos(), size * 0.72f, secondary, now * 0.001f, xray);
            }
        }
    }

    // ==================== 抛物线模拟 ====================
    private TrajectoryResult calculateTrajectory(LivingEntity entity, float partialTick) {
        ItemStack heldStack = entity.isUsingItem() ? entity.getUseItem() : entity.getMainHandItem();
        if (heldStack.isEmpty()) heldStack = entity.getOffhandItem();
        if (heldStack.isEmpty()) return null;

        Item item = heldStack.getItem();
        boolean directAim = false;
        float motionFactor = 1.5f;
        float motionSlowdown = 0.99f;
        float gravity;
        float size;
        float pitchOffset = 0.0f;

        if (item instanceof BowItem) {
            if (!predictBows.getValue() || !entity.isUsingItem()) return null;
            directAim = true;
            gravity = 0.05f; size = 0.3f;
            if (entity instanceof Player) {
                float power = entity.getTicksUsingItem() / 20.0f;
                power = (power * power + power * 2.0f) / 3.0f;
                if (power < 0.1f) return null;
                motionFactor = Math.min(power, 1.0f) * 3.0f;
            } else motionFactor = 3.0f;
        } else if (item instanceof TridentItem) {
            if (!predictTridents.getValue() || !entity.isUsingItem() || entity.getTicksUsingItem() < 10) return null;
            directAim = true;
            gravity = 0.05f; size = 0.3f; motionFactor = 2.5f;
        } else if (item instanceof FishingRodItem) {
            if (!predictRods.getValue()) return null;
            gravity = 0.04f; size = 0.25f; motionSlowdown = 0.92f;
        } else if (item instanceof ThrowablePotionItem) {
            if (!predictPotions.getValue()) return null;
            gravity = 0.05f; size = 0.25f; motionFactor = 0.5f; pitchOffset = -20.0f;
        } else if (item instanceof EnderpearlItem) {
            if (!predictPearls.getValue()) return null;
            gravity = 0.03f; size = 0.25f;
        } else if (item instanceof SnowballItem || item instanceof EggItem) {
            if (!predictThrowables.getValue()) return null;
            gravity = 0.03f; size = 0.25f;
        } else return null;

        float yaw = entity.getViewYRot(partialTick);
        float pitch = entity.getViewXRot(partialTick);
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch + pitchOffset);
        Vec3 base = entity.getPosition(partialTick);

        double posX = base.x - Math.cos(yawRad) * 0.16;
        double posY = base.y + entity.getEyeHeight() - 0.1;
        double posZ = base.z - Math.sin(yawRad) * 0.16;
        double aimScale = directAim ? 1.0 : 0.4;
        double motionX = -Math.sin(yawRad) * Math.cos(pitchRad) * aimScale;
        double motionY = -Math.sin(pitchRad) * aimScale;
        double motionZ = Math.cos(yawRad) * Math.cos(pitchRad) * aimScale;
        double distance = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (distance < 1.0e-7) return null;

        motionX = motionX / distance * motionFactor;
        motionY = motionY / distance * motionFactor;
        motionZ = motionZ / distance * motionFactor;

        List<Vec3> points = new ArrayList<>();
        Vec3 landingPos = null;
        boolean hitEntity = false;
        boolean landed = false;
        int maxSteps = simulationSteps.getValue();

        while (!landed && posY > mc.level.getMinY() - 8.0 && points.size() < maxSteps) {
            Vec3 before = new Vec3(posX, posY, posZ);
            Vec3 after = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            points.add(before);

            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    before, after, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (blockHit.getType() != HitResult.Type.MISS) {
                landed = true;
                landingPos = blockHit.getLocation();
                after = landingPos;
            }

            AABB sweptBox = new AABB(posX - size, posY - size, posZ - size,
                    posX + size, posY + size, posZ + size)
                    .expandTowards(motionX, motionY, motionZ).inflate(1.0);

            for (Entity possible : mc.level.getEntities(entity, sweptBox,
                    p -> p.canBeCollidedWith(null) && p != entity)) {
                Optional<Vec3> hit = possible.getBoundingBox().inflate(size).clip(before, after);
                if (hit.isPresent()) {
                    hitEntity = true; landed = true; landingPos = hit.get(); break;
                }
            }

            if (landed && landingPos != null) { points.add(landingPos); break; }

            posX = after.x; posY = after.y; posZ = after.z;
            if (mc.level.getFluidState(BlockPos.containing(posX, posY, posZ)).is(Fluids.WATER)) {
                motionX *= 0.6; motionY *= 0.6; motionZ *= 0.6;
            } else {
                motionX *= motionSlowdown; motionY *= motionSlowdown; motionZ *= motionSlowdown;
            }
            motionY -= gravity;
        }

        if (!landed) {
            landingPos = new Vec3(posX, posY, posZ);
            if (points.isEmpty() || points.getLast().distanceToSqr(landingPos) > 1.0e-6) {
                points.add(landingPos);
            }
        }

        return points.size() >= 2 ? new TrajectoryResult(points, landingPos, hitEntity) : null;
    }

    // ==================== 分类 ====================
    private ProjectileKind classifyProjectile(Entity entity) {
        if (entity instanceof AbstractArrow) return ProjectileKind.ARROW;
        if (entity instanceof ThrownEnderpearl) return ProjectileKind.PEARL;
        if (entity instanceof AbstractThrownPotion) return ProjectileKind.POTION;
        if (entity instanceof Snowball || entity instanceof ThrownEgg
                || entity instanceof ThrownExperienceBottle) return ProjectileKind.THROWABLE;
        if (entity instanceof AbstractHurtingProjectile) return ProjectileKind.MAGIC;
        if (entity instanceof FishingHook) return ProjectileKind.HOOK;
        return null;
    }

    private boolean flightKindEnabled(ProjectileKind kind) {
        return switch (kind) {
            case ARROW -> fxArrows.getValue();
            case PEARL -> fxPearls.getValue();
            case POTION -> fxPotions.getValue();
            case THROWABLE -> fxThrowables.getValue();
            case MAGIC -> fxMagic.getValue();
            case HOOK -> fxHooks.getValue();
        };
    }

    private int trailColor(ProjectileKind kind, float progress, long now) {
        if (trailColorMode.getValue() == TrailColors.Custom) {
            return blendColor(toArgb(trailPrimary.getValue()), toArgb(trailSecondary.getValue()), progress);
        }
        if (trailColorMode.getValue() == TrailColors.Rainbow) {
            return hsv((now * 0.00012f + progress * 0.72f) % 1.0f, 0.68f, 1.0f);
        }
        return blendColor(kind.primary, kind.secondary, progress);
    }

    private boolean withinRenderDistance(Vec3 position) {
        if (mc.player == null) return false;
        double max = renderDistance.getValue();
        return mc.player.position().distanceToSqr(position) <= max * max;
    }

    private static void pruneTrail(ArrayDeque<TrailPoint> trail, long now, long lifeMs) {
        while (!trail.isEmpty() && now - trail.peekFirst().timestamp() > lifeMs) trail.removeFirst();
    }

    private static float[] flatten(List<Vec3> points) {
        float[] flat = new float[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = points.get(i);
            flat[i * 3] = (float) p.x;
            flat[i * 3 + 1] = (float) p.y;
            flat[i * 3 + 2] = (float) p.z;
        }
        return flat;
    }

    private static int toArgb(Color c) {
        return (c.getAlpha() << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }

    private static int withAlpha(int argb, float alpha) {
        return (Math.round(clamp01(alpha) * 255.0f) << 24) | (argb & 0x00FFFFFF);
    }

    private static int scaleAlpha(int argb, float scale) {
        float alpha = ((argb >>> 24) & 0xFF) / 255.0f;
        return withAlpha(argb, alpha * scale);
    }

    private static int blendColor(int first, int second, float amount) {
        float t = clamp01(amount);
        int r = Math.round(((first >> 16) & 0xFF) * (1.0f - t) + ((second >> 16) & 0xFF) * t);
        int g = Math.round(((first >> 8) & 0xFF) * (1.0f - t) + ((second >> 8) & 0xFF) * t);
        int b = Math.round((first & 0xFF) * (1.0f - t) + (second & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int hsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) h;
        float fraction = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * fraction);
        float t = value * (1.0f - saturation * (1.0f - fraction));
        float r, g, b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return 0xFF000000 | (Math.round(r * 255.0f) << 16) | (Math.round(g * 255.0f) << 8) | Math.round(b * 255.0f);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}