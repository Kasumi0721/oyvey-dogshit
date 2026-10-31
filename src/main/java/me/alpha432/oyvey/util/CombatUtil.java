package me.alpha432.oyvey.util;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.player.Player;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public class CombatUtil implements Util {

    public static final Timer breakTimer = new Timer();

    /** 攻击间隔（毫秒），原版是 AntiCheat.attackDelay * 1000 */
    private static final long ATTACK_DELAY_MS = 50;
    /** 反作弊范围，原版是 AntiCheat.ieRange */
    private static final double IE_RANGE = 6.0;

    /**
     * 攻击一个方块周围的水晶（PacketMine 用）
     */
    public static void attackCrystal(BlockPos pos, boolean rotate, boolean eatingPause) {
        attackCrystal(BlockUtil.blockAABB(pos), rotate, eatingPause);
    }

    /**
     * 攻击一个 AABB 范围内的所有水晶
     */
    public static List<Player> getEnemies(double range) {
        List<Player> list = new ArrayList<>();
        if (mc.level == null) return list;
        for (Player player : mc.level.players()) {
            if (isValid(player, range)) {
                list.add(player);
            }
        }
        return list;
    }
    public static boolean isValid(Entity entity, double range) {
        if (entity == null) return false;
        if (!entity.isAlive()) return false;
        if (entity.equals(mc.player)) return false;
        if (!(entity instanceof Player)) return false;   // 只挑玩家
        if (mc.player.distanceTo(entity) > range) return false;
        return true;
    }
    public static Player getClosestEnemy(double range) {
        Player closest = null;
        for (Player player : getEnemies(range)) {
            if (closest == null) {
                closest = player;
            } else if (mc.player.distanceToSqr(player.position())
                    < mc.player.distanceToSqr(closest.position())) {
                closest = player;
            }
        }
        return closest;
    }
    public static void attackCrystal(AABB box, boolean rotate, boolean eatingPause) {
        for (EndCrystal crystal : BlockUtil.getEndCrystals(box)) {
            attackWithDelay(crystal, rotate, eatingPause);
        }
    }

    private static void attackWithDelay(Entity entity, boolean rotate, boolean usingPause) {
        if (!breakTimer.passed(ATTACK_DELAY_MS)) return;
        if (mc.player != null && usingPause && mc.player.isUsingItem()) return;
        attack(entity, rotate);
    }

    /**
     * 攻击单个实体
     */
    public static void attack(Entity entity, boolean rotate) {
        if (entity == null || mc.player == null) return;

        Vec3 attackVec = getClosestPointToBox(mc.player.getEyePosition(), entity.getBoundingBox());
        if (mc.player.getEyePosition().distanceTo(attackVec) > IE_RANGE) return;

        breakTimer.reset();

        if (rotate) {
            OyVey.rotationManager.lookAtVec3d(attackVec);
        }

        Objects.requireNonNull(mc.getConnection()).send(ServerboundInteractPacket.createAttackPacket(
                entity, mc.player.isShiftKeyDown()));
        mc.player.resetAttackStrengthTicker();
        EntityUtil.swingHand(InteractionHand.MAIN_HAND, SwingSide.All);

        if (rotate) {
            OyVey.rotationManager.snapBack();
        }
    }

    /** AABB 上离 vec 最近的点 */
    public static Vec3 getClosestPointToBox(Vec3 vec, AABB box) {
        return new Vec3(
                Math.max(box.minX, Math.min(vec.x, box.maxX)),
                Math.max(box.minY, Math.min(vec.y, box.maxY)),
                Math.max(box.minZ, Math.min(vec.z, box.maxZ)));
    }
}