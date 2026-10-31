package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.entity.MotionEvent;
import me.alpha432.oyvey.event.impl.entity.MoveEvent;
import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.MathUtil;
import me.alpha432.oyvey.util.MovementUtil;
import me.alpha432.oyvey.util.PositionUtil;
import me.alpha432.oyvey.util.Timer;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {
    public static Speed INSTANCE;

    private final Setting<SpeedMode> mode = mode("Mode", SpeedMode.Instant);
    private final Setting<Boolean> jump = bool("Jump", true,
            () -> mode.getValue() == SpeedMode.Strafe);
    private final Setting<Boolean> inWater = bool("InWater", false, () -> true);
    private final Setting<Double> strafeSpeed = num("StrafeSpeed", 287.3, 100.0, 1000.0,
            () -> mode.getValue() == SpeedMode.Strafe);
    private final Setting<Double> speedSet = num("Speed", 4.0, 0.1, 10.0,
            () -> mode.getValue() == SpeedMode.Vanilla);
    private final Setting<Integer> constTicks = num("ConstTicks", 10, 1, 40,
            () -> mode.getValue() == SpeedMode.Constantiam);
    private final Setting<Integer> constOff = num("ConstOff", 3, 1, 10,
            () -> mode.getValue() == SpeedMode.Constantiam);
    private final Setting<Double> constFactor = num("ConstFactor", 2.149, 1.0, 5.0,
            () -> mode.getValue() == SpeedMode.Constantiam);
    private final Setting<Boolean> useTimer = bool("UseTimer", false,
            () -> mode.getValue() == SpeedMode.Strafe || mode.getValue() == SpeedMode.LowHop);
    private final Setting<Boolean> noWaterInstant = bool("NoLiquidInstant", false,
            () -> mode.getValue() == SpeedMode.Instant);

    // ==================== 击退相关（原 VelocityModule + Speed 受击加速）====================
    /** 完全无击退（等价原独立 Velocity 模块） */
    private final Setting<Boolean> noVelocity = bool("NoVelocity", true, () -> true);
    /** 受击加速：读包计算增益 */
    private final Setting<Boolean> velocity = bool("Velocity", false, () -> true);
    /** 爆炸加速 */
    private final Setting<Boolean> explosions = bool("Explosions", false, () -> true);
    /** 神龟药水补偿 */
    private final Setting<Boolean> antiTurtle = bool("AntiTurtle", false, () -> true);
    /** 空中是否保留原版击退（Y 轴） */
    private final Setting<Boolean> keepAirKnockback = bool("KeepAirKnockback", true, () -> true);

    private final Setting<Float> multiplier = num("H-Factor", 1.0f, 0.0f, 5.0f, () -> true);
    private final Setting<Float> vertical = num("V-Factor", 1.0f, 0.0f, 5.0f, () -> true);
    private final Setting<Integer> coolDown = num("CoolDown", 1000, 0, 5000, () -> true);
    private final Setting<Boolean> directional = bool("Directional", false, () -> true);
    private final Setting<Boolean> lagOut = bool("LagOutBlocks", false, () -> true);
    private final Setting<Integer> lagTime = num("LagTime", 500, 0, 1000,
            () -> mode.getValue() == SpeedMode.Strafe);
    private final Setting<Double> cap = num("Cap", 10.0, 0.0, 10.0, () -> true);
    private final Setting<Boolean> scaleCap = bool("ScaleCap", false, () -> true);
    private final Setting<Boolean> slow = bool("Slowness", false, () -> true);
    private final Setting<Boolean> modify = bool("Modify", false, () -> true).setParent();
    private final Setting<Double> xzFactor = num("XZ-Factor", 1.0, 0.0, 5.0,
            () -> modify.isOpen());
    private final Setting<Double> yFactor = num("Y-Factor", 1.0, 0.0, 5.0,
            () -> modify.isOpen());

    private final Timer expTimer = new Timer();
    private final Timer lagTimer = new Timer();

    private boolean stop;
    private int vanillaStage, onGroundStage = 2, oldGroundStage = 2;
    private double speed, distance;
    private int gayStage = 1, stage = 4, ncpStage, bhopStage = 4, vStage = 1, lowStage = 4, constStage;
    private double lastExp, lastDist;
    private boolean boost;

    public Speed() {
        super("Speed", "3ar", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            this.speed = MovementUtil.getSpeed();
            this.distance = MovementUtil.getDistance2D();
        }
        vanillaStage = 0;
        onGroundStage = 2;
        oldGroundStage = 2;
        ncpStage = 0;
        gayStage = 1;
        vStage = 1;
        bhopStage = 4;
        stage = 4;
        lowStage = 4;
        lastDist = 0.0;
        constStage = 0;
    }

    @Override
    public void onDisable() {
        OyVey.TIMER = 1.0f;
    }

    // ==================== 辅助方法 ====================
    private void setMotionY(double y) {
        Vec3 m = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(m.x, y, m.z);
    }

    private void setMotionXZ(double factor) {
        Vec3 m = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(m.x * factor, m.y, m.z * factor);
    }

    private double getBaseSpeed() {
        return MovementUtil.getSpeed(slow.getValue());
    }

    private double getBaseSpeed(double strafeFactor) {
        return MovementUtil.getSpeed(slow.getValue(), strafeFactor);
    }

    /** 神龟药水反补偿 */
    private double compensateSlowness(double gain) {
        if (!antiTurtle.getValue()) return gain;
        if (!mc.player.hasEffect(MobEffects.SLOWNESS)) return gain;
        int amp = mc.player.getEffect(MobEffects.SLOWNESS).getAmplifier();
        return gain * (1.0 + 0.15 * amp);
    }

    /** 沿玩家朝向施加加速增益（只加水平） */
    private void applyForwardGain(double gain) {
        float yawRad = (float) Math.toRadians(mc.player.getYRot());
        double addX = -Math.sin(yawRad) * gain;
        double addZ = Math.cos(yawRad) * gain;

        Vec3 cur = mc.player.getDeltaMovement();
        double newY = cur.y;
        if (cur.y > 0 && vertical.getValue() != 0f) {
            newY = cur.y * vertical.getValue();
        }
        mc.player.setDeltaMovement(cur.x + addX, newY, cur.z + addZ);

        speed = Math.max(speed, getBaseSpeed()) + gain;
        distance += gain;
    }

    // ==================== 受击 / 爆炸加速 + 无击退 ====================
    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        // ---- 受击包 ----
        if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet) {
            if (packet.getId() != mc.player.getId()) return;

            Vec3 m = packet.getMovement();

            // 1) 计算受击加速增益
            double gain = 0;
            if (velocity.getValue()) {
                if (!directional.getValue() || !MovementUtil.isInMovementDirection(
                        mc.player.getX() + m.x,
                        mc.player.getY() + m.y,
                        mc.player.getZ() + m.z)) {

                    double spd = Math.sqrt(m.x * m.x + m.z * m.z) / 8000.0;
                    lastExp = expTimer.passedMs(coolDown.getValue()) ? spd : spd - lastExp;
                    if (lastExp > 0) {
                        expTimer.reset();
                        gain = compensateSlowness(lastExp * multiplier.getValue());
                    }
                }
            }

            // 2) 决定是否接管原版击退
            //    - NoVelocity=true 或 Velocity=true → 取消原包，由我们处理
            if (noVelocity.getValue() || velocity.getValue()) {
                event.cancel();

                double finalGain = gain;
                mc.execute(() -> {
                    Vec3 cur = mc.player.getDeltaMovement();

                    // 保留 Y 轴原版击退（跳起来被打会弹一下）
                    // 如果想彻底无击退（连 Y 也不保留），把下面这段 if 删掉
                    double keepY = 0;
                    if (keepAirKnockback.getValue() && !mc.player.onGround()) {
                        // 空中保留原 Y 速度（被弹起的感觉）
                        keepY = cur.y;
                    }

                    if (finalGain > 0) {
                        // 有加速：清零水平 + 加朝向增益
                        mc.player.setDeltaMovement(0, keepY, 0);
                        applyForwardGain(finalGain);
                    } else {
                        // 无加速：清零水平（如果 NoVelocity 开着）
                        if (noVelocity.getValue()) {
                            mc.player.setDeltaMovement(0, keepY, 0);
                        }
                    }
                });
            }
            // 否则：正常击退，什么都不做
        }
        // ---- 爆炸包 ----
        else if (event.getPacket() instanceof ClientboundExplodePacket packet) {
            // 无爆炸击退（仅当 NoVelocity 且未开 Explosions）
            if (noVelocity.getValue() && !explosions.getValue()) {
                event.cancel();
                return;
            }

            if (!explosions.getValue()) return;
            if (!MovementUtil.isMoving()) return;

            Vec3 center = packet.center();
            if (mc.player.distanceToSqr(center.x, center.y, center.z) > 100.0) return;

            Vec3 m = packet.playerKnockback().orElse(Vec3.ZERO);
            double spd = Math.sqrt(m.x * m.x + m.z * m.z);

            lastExp = expTimer.passedMs(coolDown.getValue()) ? spd : spd - lastExp;
            if (lastExp > 0) {
                expTimer.reset();
                double gain = compensateSlowness(lastExp * multiplier.getValue());
                mc.execute(() -> {
                    // 爆炸：清零水平 + 加朝向增益
                    Vec3 cur = mc.player.getDeltaMovement();
                    double keepY = keepAirKnockback.getValue() && !mc.player.onGround() ? cur.y : 0;
                    mc.player.setDeltaMovement(0, keepY, 0);
                    applyForwardGain(gain);
                });
            }
        }
    }

    // ==================== MotionEvent ====================
    @Subscribe
    public void onMotion(MotionEvent event) {
        if (nullCheck()) return;
        if (LongJump.skipTicks > 0) return;
        if (MovementUtil.noMovementKeys()) {
            Vec3 m = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0, m.y, 0);
        }

        if (mode.getValue() == SpeedMode.OldGround) {
            switch (event.getStage()) {
                case 0 -> {
                    if (notColliding()) ++oldGroundStage;
                    else oldGroundStage = 0;

                    if (oldGroundStage == 4) {
                        event.setY(event.getY() + (PositionUtil.isBoxColliding() ? 0.2 : 0.4)
                                + MovementUtil.getJumpSpeed());
                    }
                }
                case 1 -> {
                    if (oldGroundStage == 3) {
                        setMotionXZ(3.25);
                    } else if (oldGroundStage == 4) {
                        setMotionXZ(1.0 / 1.4);
                        oldGroundStage = 2;
                    }
                }
            }
        }

        this.distance = MovementUtil.getDistance2D();

        if (mode.getValue() == SpeedMode.OnGround && onGroundStage == 3) {
            event.setY(event.getY() + (PositionUtil.isBoxColliding() ? 0.2 : 0.4)
                    + MovementUtil.getJumpSpeed());
        }
    }

    // ==================== MoveEvent ====================
    @Subscribe
    public void onMove(MoveEvent event) {
        if (nullCheck()) return;
        if (LongJump.skipTicks > 0) return;
        if ((inWater.getValue() || (!PositionUtil.inLiquid() && !PositionUtil.inLiquid(true)))
                && !mc.player.onClimbable()
                && !mc.player.isInWall()) {

            if (stop) {
                stop = false;
                return;
            }

            double base = getBaseSpeed();
            if (speed > base) {
                speed = Math.max(base, speed * 0.92);
            }

            move(mode.getValue(), event);

            if (modify.getValue()) {
                event.setX(event.getX() * xzFactor.getValue());
                event.setY(event.getY() * yFactor.getValue());
                event.setZ(event.getZ() * xzFactor.getValue());
            }
        } else {
            stop = true;
        }
    }

    // ==================== 核心 move 逻辑 ====================
    private void move(SpeedMode mode, MoveEvent event) {
        switch (mode) {
            case Instant -> {
                if (mc.player.isFallFlying()) return;
                if (!noWaterInstant.getValue() || (!mc.player.isInWater() && !mc.player.isInLava())) {
                    double target = Math.max(getBaseSpeed(), this.speed);
                    MovementUtil.strafe(event, target);
                }
            }

            case OnGround -> {
                if (mc.player.onGround() || onGroundStage == 3) {
                    if ((!mc.player.horizontalCollision && mc.player.zza != 0)
                            || mc.player.xxa != 0) {

                        if (onGroundStage == 2) {
                            speed *= 2.149;
                            onGroundStage = 3;
                        } else if (onGroundStage == 3) {
                            onGroundStage = 2;
                            speed = distance - 0.66 * (distance - getBaseSpeed());
                        } else if (PositionUtil.isBoxColliding() || mc.player.verticalCollision) {
                            onGroundStage = 1;
                        }
                    }
                    speed = Math.min(speed, getCap());
                    speed = Math.max(speed, getBaseSpeed());
                    MovementUtil.strafe(event, speed);
                }
            }

            case Vanilla -> {
                double base = speedSet.getValue() / 10.0;
                double target = Math.max(base, this.speed);
                MovementUtil.strafe(event, target);
            }

            case NCP -> {
                if (mc.player.isFallFlying()) return;

                switch (ncpStage) {
                    case 0 -> { ncpStage++; lastDist = 0.0; }
                    case 2 -> {
                        if ((mc.player.zza != 0 || mc.player.xxa != 0) && mc.player.onGround()) {
                            double yMotion = (PositionUtil.isBoxColliding() ? 0.2 : 0.3999)
                                    + MovementUtil.getJumpSpeed();
                            setMotionY(yMotion);
                            event.setY(yMotion);
                            speed *= 2.149;
                        }
                    }
                    case 3 -> speed = lastDist - 0.7095 * (lastDist - getBaseSpeed());
                    default -> {
                        if ((!mc.level.noCollision(mc.player,
                                mc.player.getBoundingBox().move(0, mc.player.getDeltaMovement().y, 0))
                                || mc.player.verticalCollision) && ncpStage > 0) {
                            ncpStage = (mc.player.zza == 0 && mc.player.xxa == 0) ? 0 : 1;
                        }
                        speed = lastDist - lastDist / 159.0;
                    }
                }
                speed = Math.min(speed, getCap());
                speed = Math.max(speed, getBaseSpeed());
                MovementUtil.strafe(event, speed);
                ncpStage++;
            }

            case Strafe -> {
                if (!MovementUtil.isMoving()) return;
                if (mc.player.isFallFlying()) return;
                if (!lagTimer.passedMs(lagTime.getValue())) return;

                if (useTimer.getValue() && lagTimer.passedMs(250L)) {
                    if (OyVey.TIMER == 1.0f) OyVey.TIMER = 1.0888f;
                } else if (OyVey.TIMER == 1.0888f) {
                    OyVey.TIMER = 1.0f;
                }

                if (stage == 1 && MovementUtil.isMoving()) {
                    speed = 1.35 * getBaseSpeed(strafeSpeed.getValue() / 1000.0) - 0.01;
                } else if (stage != 2 || !MovementUtil.isMoving()
                        || (!MovementUtil.isJumping() && !jump.getValue())) {
                    if (stage == 3) {
                        speed = distance - 0.66 * (distance - getBaseSpeed(strafeSpeed.getValue() / 1000.0));
                        boost = !boost;
                    } else {
                        if ((!mc.level.noCollision(mc.player,
                                mc.player.getBoundingBox().move(0, mc.player.getDeltaMovement().y, 0))
                                || mc.player.verticalCollision) && stage > 0) {
                            stage = MovementUtil.isMoving() ? 1 : 0;
                        }
                        speed = distance - distance / 159.0;
                    }
                } else {
                    double yMotion = 0.3999 + MovementUtil.getJumpSpeed();
                    setMotionY(yMotion);
                    event.setY(yMotion);
                    speed *= boost ? 1.6835 : 1.395;
                }

                speed = Math.min(speed, getCap());
                speed = Math.max(speed, getBaseSpeed(strafeSpeed.getValue() / 1000.0));
                MovementUtil.strafe(event, speed);
                if (MovementUtil.isMoving()) stage++;
            }

            case Bhop -> {
                if (!lagTimer.passedMs(100L)) { bhopStage = 4; return; }

                if (MathUtil.round(mc.player.getY() - (int) mc.player.getY(), 3) == MathUtil.round(0.138, 3)) {
                    setMotionY(mc.player.getDeltaMovement().y - 0.08 - MovementUtil.getJumpSpeed());
                    event.setY(event.getY() - (0.0931 + MovementUtil.getJumpSpeed()));
                }

                if (bhopStage == 2 && MovementUtil.isMoving()) {
                    double yMotion = (PositionUtil.isBoxColliding() ? 0.2 : 0.4) + MovementUtil.getJumpSpeed();
                    setMotionY(yMotion);
                    event.setY(yMotion);
                    speed *= 2.149;
                } else if (bhopStage == 3) {
                    speed = distance - 0.66 * (distance - getBaseSpeed());
                } else {
                    if (mc.player.onGround()) bhopStage = 1;
                    speed = distance - distance / 159.0;
                }

                speed = Math.min(speed, getCap());
                speed = Math.max(speed, getBaseSpeed());
                MovementUtil.strafe(event, speed);
                bhopStage++;
            }

            case LowHop -> {
                if (!lagTimer.passedMs(100L)) return;

                if (useTimer.getValue() && lagTimer.passedMs(250L)) {
                    if (OyVey.TIMER == 1.0f) OyVey.TIMER = 1.0888f;
                } else if (OyVey.TIMER == 1.0888f) {
                    OyVey.TIMER = 1.0f;
                }

                if (!mc.player.horizontalCollision) {
                    double frac = MathUtil.round(mc.player.getY() - (int) mc.player.getY(), 3);
                    double targetY = Double.NaN;
                    if (frac == MathUtil.round(0.4, 3)) {
                        targetY = 0.31 + MovementUtil.getJumpSpeed();
                    } else if (frac == MathUtil.round(0.71, 3)) {
                        targetY = 0.04 + MovementUtil.getJumpSpeed();
                    } else if (frac == MathUtil.round(0.75, 3)) {
                        targetY = -0.2 - MovementUtil.getJumpSpeed();
                    } else if (frac == MathUtil.round(0.55, 3)) {
                        targetY = -0.14 + MovementUtil.getJumpSpeed();
                    } else if (frac == MathUtil.round(0.41, 3)) {
                        targetY = -0.2 + MovementUtil.getJumpSpeed();
                    }
                    if (!Double.isNaN(targetY)) {
                        setMotionY(targetY);
                        event.setY(targetY);
                    }
                }

                if (lowStage == 1 && MovementUtil.isMoving()) {
                    speed = 1.35 * getBaseSpeed() - 0.01;
                } else if (lowStage == 2 && MovementUtil.isMoving()) {
                    double yMotion = (PositionUtil.isBoxColliding() ? 0.2 : 0.3999)
                            + MovementUtil.getJumpSpeed();
                    setMotionY(yMotion);
                    event.setY(yMotion);
                    speed *= boost ? 1.5685 : 1.3445;
                } else if (lowStage == 3) {
                    speed = distance - 0.66 * (distance - getBaseSpeed());
                    boost = !boost;
                } else {
                    if (mc.player.onGround() && lowStage > 0) {
                        lowStage = MovementUtil.isMoving() ? 1 : 0;
                    }
                    speed = distance - distance / 159.0;
                }

                speed = Math.min(speed, getCap());
                speed = Math.max(speed, getBaseSpeed());
                MovementUtil.strafe(event, speed);
                if (MovementUtil.isMoving()) lowStage++;
            }

            case Constantiam -> {
                if (!lagTimer.passedMs(100L)) { constStage = 0; return; }
                if (!MovementUtil.isMoving()) speed = getBaseSpeed();

                if (constStage == 0 && MovementUtil.isMoving() && mc.player.onGround()) {
                    speed = 0.08;
                } else if (constStage == 1 && mc.player.verticalCollision && MovementUtil.isMoving()) {
                    speed = 0.25 + getBaseSpeed() - 0.01;
                } else if (constStage == 2 && mc.player.verticalCollision && MovementUtil.isMoving()) {
                    double yMotion = (PositionUtil.isBoxColliding() ? 0.2 : 0.4) + MovementUtil.getJumpSpeed();
                    setMotionY(yMotion);
                    event.setY(yMotion);
                    speed *= constFactor.getValue();
                } else if (constStage == 3) {
                    speed = distance - 0.66 * (distance - getBaseSpeed());
                } else {
                    if (mc.player.onGround() && constStage > 0) constStage = 0;

                    if (!mc.player.onGround()
                            && constStage > constOff.getValue()
                            && constStage < constTicks.getValue()) {
                        event.setY(mc.player.tickCount % 2 == 0 ? 0.00118212 : -0.00118212);
                    }
                    speed = distance - distance / 159.0;
                }

                speed = Math.min(speed, getCap());
                if (constStage != 0) speed = Math.max(speed, getBaseSpeed());
                MovementUtil.strafe(event, speed);
                if (MovementUtil.isMoving()) constStage++;
            }

            default -> {}
        }
    }

    public boolean notColliding() {
        return mc.player.onGround()
                && !PositionUtil.inLiquid()
                && !PositionUtil.inLiquid(true);
    }

    public double getCap() {
        double ret = cap.getValue();
        if (!scaleCap.getValue()) return ret;

        if (mc.player.hasEffect(MobEffects.SPEED)) {
            var eff = mc.player.getEffect(MobEffects.SPEED);
            if (eff != null) ret *= 1.0 + 0.2 * (eff.getAmplifier() + 1);
        }
        if (slow.getValue() && mc.player.hasEffect(MobEffects.SLOWNESS)) {
            var eff = mc.player.getEffect(MobEffects.SLOWNESS);
            if (eff != null) ret /= 1.0 + 0.2 * (eff.getAmplifier() + 1);
        }
        return ret;
    }

    public enum SpeedMode {
        Instant, OldGround, OnGround, Vanilla, NCP, Strafe,
        GayHop, Bhop, VHop, LowHop, Constantiam, None
    }
}