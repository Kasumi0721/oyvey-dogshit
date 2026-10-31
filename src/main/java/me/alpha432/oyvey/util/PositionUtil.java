package me.alpha432.oyvey.util;

import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PositionUtil implements Util {

    /** 玩家碰撞箱在当前 Y + motionY 是否会撞到方块 */
    public static boolean isBoxColliding() {
        Player p = mc.player;
        if (p == null || mc.level == null) return false;

        AABB box = p.getBoundingBox().move(0.0, p.getDeltaMovement().y, 0.0);
        return !mc.level.noCollision(p, box);
    }

    /** 是否在水中或岩浆中 */
    public static boolean inLiquid() {
        return inLiquid(false);
    }

    /**
     * @param noWater  true = 只算岩浆，不算水
     */
    public static boolean inLiquid(boolean noWater) {
        Player p = mc.player;
        if (p == null) return false;

        if (noWater) {
            return p.isInLava();
        }
        return p.isInWater() || p.isInLava();
    }

    /** 备用：玩家当前所在位置是否液体 */
    public static boolean isInLiquidPos(Vec3 pos) {
        if (mc.level == null) return false;
        var state = mc.level.getBlockState(
                net.minecraft.core.BlockPos.containing(pos));
        return !state.getFluidState().isEmpty();
    }
}