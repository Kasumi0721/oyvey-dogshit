package me.alpha432.oyvey.manager;

import me.alpha432.oyvey.util.MathUtil;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class RotationManager implements Util {
    private float yaw;
    private float pitch;

    public void updateRotations() {
        if (mc.player != null) {
            this.yaw = mc.player.getYRot();
        }
        if (mc.player != null) {
            this.pitch = mc.player.getXRot();
        }
    }

    public void restoreRotations() {
        if (mc.player != null) {
            mc.player.setYRot(yaw);
        }
        if (mc.player != null) {
            mc.player.yHeadRot = yaw;
        }
        if (mc.player != null) {
            mc.player.setXRot(pitch);
        }
    }

    public void setPlayerRotations(float yaw, float pitch) {
        if (mc.player != null) {
            mc.player.setYRot(yaw);
        }
        if (mc.player != null) {
            mc.player.yHeadRot = yaw;
        }
        if (mc.player != null) {
            mc.player.setXRot(pitch);
        }
    }

    public void setPlayerYaw(float yaw) {
        if (mc.player != null) {
            mc.player.setYRot(yaw);
        }
        if (mc.player != null) {
            mc.player.yHeadRot = yaw;
        }
    }

    public void setPlayerPitch(float pitch) {
        if (mc.player != null) {
            mc.player.setXRot(pitch);
        }
    }

    public void lookAtPos(BlockPos pos) {
        float[] angle = null;
        if (mc.player != null) {
            angle = MathUtil.calcAngle(
                    mc.player.getEyePosition(),
                    new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }
        if (angle != null) {
            this.setPlayerRotations(angle[0], angle[1]);
        }
    }

    public void lookAtVec3d(Vec3 vec3d) {
        float[] angle = null;
        if (mc.player != null) {
            angle = MathUtil.calcAngle(mc.player.getEyePosition(), vec3d);
        }
        if (angle != null) {
            this.setPlayerRotations(angle[0], angle[1]);
        }
    }

    public void lookAtVec3d(double x, double y, double z) {
        this.lookAtVec3d(new Vec3(x, y, z));
    }

    public void lookAtEntity(Entity entity) {
        float[] angle = null;
        if (mc.player != null) {
            angle = MathUtil.calcAngle(mc.player.getEyePosition(), entity.getEyePosition());
        }
        if (angle != null) {
            this.setPlayerRotations(angle[0], angle[1]);
        }
    }

    // ==================== 为 PacketMine 新增 ====================

    /**
     * 恢复玩家原来的朝向。
     * PacketMine / BlockUtil 在旋转结束后调用它回到原视角。
     */
    public void snapBack() {
        this.restoreRotations();
    }

    /**
     * 判断某个坐标是否在玩家的 FOV 范围内。
     *
     * @param target 世界坐标
     * @param fov    角度阈值（度），例如 20 表示准星 20 度内
     * @return 目标是否在 FOV 内
     */
    public boolean inFov(Vec3 target, float fov) {
        float[] angle = null;
        if (mc.player != null) {
            angle = MathUtil.calcAngle(mc.player.getEyePosition(), target);
        }
        float yawDiff = 0;
        if (angle != null) {
            yawDiff = Mth.wrapDegrees(angle[0] - mc.player.getYRot());
        }
        float pitchDiff = 0;
        if (mc.player != null) {
            pitchDiff = angle[1] - mc.player.getXRot();
        }
        return yawDiff * yawDiff + pitchDiff * pitchDiff <= fov * fov;
    }

    // ==================== Getter / Setter ====================

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}