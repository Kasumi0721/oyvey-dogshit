package me.alpha432.oyvey.util;

import me.alpha432.oyvey.event.impl.entity.MoveEvent;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public class MovementUtil implements Util {

    /** 基础速度（无任何加速） */
    public static double getSpeed() {
        return getSpeed(false);
    }

    /** 带 slowness 修正的基础速度 */
    public static double getSpeed(boolean slow) {
        return getSpeed(slow, 1.0);
    }

    /**
     * 基础速度 + 自定义倍率
     * @param slow              是否考虑挖掘疲劳
     * @param strafeSpeedFactor 自定义倍率（默认 1.0）
     */
    public static double getSpeed(boolean slow, double strafeSpeedFactor) {
        double base = 0.2873;
        Player p = mc.player;
        if (p == null) return base;

        // 速度药水效果
        if (p.hasEffect(MobEffects.SPEED)) {
            int amp = p.getEffect(MobEffects.SPEED).getAmplifier();
            base *= 1.0 + 0.2 * (amp + 1);
        }

        // 缓慢效果
        if (slow && p.hasEffect(MobEffects.SLOWNESS)) {
            int amp = p.getEffect(MobEffects.SLOWNESS).getAmplifier();
            base /= 1.0 + 0.2 * (amp + 1);
        }

        return base * strafeSpeedFactor;
    }

    /** 水平面移动距离（当前 tick） */
    public static double getDistance2D() {
        Player p = mc.player;
        if (p == null) return 0.0;
        double dx = p.getX() - p.xOld;
        double dz = p.getZ() - p.zOld;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 是否正在移动（按键） */
    public static boolean isMoving() {
        Player p = mc.player;
        if (p == null) return false;
        return p.zza != 0.0F || p.xxa != 0.0F;
    }

    /** 是否完全没按方向键 */
    public static boolean noMovementKeys() {
        return !isMoving();
    }

    /** 是否正在跳跃（按着空格） */
    public static boolean isJumping() {
        return mc.options.keyJump.isDown();
    }

    /** 玩家当前跳跃初速度（考虑跳跃药水） */
    public static double getJumpSpeed() {
        Player p = mc.player;
        if (p == null) return 0.0;
        double jump = 0.42;
        if (p.hasEffect(MobEffects.JUMP_BOOST)) {
            int amp = p.getEffect(MobEffects.JUMP_BOOST).getAmplifier();
            jump += 0.1 * (amp + 1);
        }
        return jump;
    }
    /** 玩家是否"静止"（没按 WASD 且水平速度接近 0） */
    public static boolean isStatic() {
        if (mc.player == null) return true;
        return noMovementKeys()
                && Math.abs(mc.player.getDeltaMovement().x) < 0.005
                && Math.abs(mc.player.getDeltaMovement().z) < 0.005;
    }
    /**
     * 把速度应用到移动向量上（strafe 核心）
     * @param event  MoveEvent
     * @param speed  目标水平速度
     */
    public static void strafe(MoveEvent event, double speed) {
        if (!isMoving()) return;

        Player p = mc.player;
        float forward = p.zza;
        float strafe = p.xxa;
        float yaw = p.getYRot();

        // 把按键输入转成世界方向
        float sin = (float) Math.sin(Math.toRadians(yaw));
        float cos = (float) Math.cos(Math.toRadians(yaw));

        double x = strafe * cos - forward * sin;
        double z = forward * cos + strafe * sin;

        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0E-4) return;

        event.setX(x / len * speed);
        event.setZ(z / len * speed);
    }

    /** 判断某个爆点是否在玩家"前进方向"上 */
    public static boolean isInMovementDirection(double x, double y, double z) {
        Player p = mc.player;
        if (p == null) return false;

        double dx = x - p.getX();
        double dz = z - p.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return false;

        float yaw = p.getYRot();
        double fx = -Math.sin(Math.toRadians(yaw));
        double fz = Math.cos(Math.toRadians(yaw));

        double dot = (dx / len) * fx + (dz / len) * fz;
        return dot > 0.0;
    }
}