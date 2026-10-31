package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.event.impl.entity.MotionEvent;
import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;

public class LongJump extends Module {
    public static LongJump INSTANCE;
    /** 让 Speed 暂时让路 */
    public static int skipTicks = 0;

    private final Setting<Float> speed = num("Speed", 1.2f, 0.3f, 5.0f, () -> true);
    private final Setting<Float> yMotion = num("Y", 0.42f, 0.0f, 0.6f, () -> true);
    private final Setting<Integer> ticks = num("Ticks", 10, 1, 60, () -> true);
    private final Setting<Float> maxDist = num("MaxDist", 10f, 1f, 30f, () -> true);
    private final Setting<Integer> armWindow = num("ArmWindow", 20, 5, 100, () -> true);
    private final Setting<Boolean> requireExplosion = bool("RequireExplosion", true, () -> true);
    private final Setting<Boolean> autoDisable = bool("AutoDisable", true, () -> true);

    private boolean armed = false;
    private int armedTimer = 0;
    private boolean jumping = false;
    private int jumpTicks = 0;

    public LongJump() {
        super("LongJump", "被炸后长跳", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        armed = false;
        armedTimer = 0;
        jumping = false;
        jumpTicks = 0;
        skipTicks = 0;
    }

    @Override
    public void onDisable() {
        armed = false;
        jumping = false;
        skipTicks = 0;
    }

    @Override
    public void onTick() {
        if (skipTicks > 0) skipTicks--;

        // 蓄力窗口倒计时
        if (armed) {
            armedTimer++;
            if (armedTimer > armWindow.getValue()) {
                armed = false;
            }
        }
    }

    // ==================== 爆炸蓄力 ====================
    @Subscribe(priority = 100)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (!(event.getPacket() instanceof ClientboundExplodePacket packet)) return;

        Vec3 center = packet.center();
        if (mc.player.distanceToSqr(center.x, center.y, center.z)
                > maxDist.getValue() * maxDist.getValue()) return;

        Vec3 kb = packet.playerKnockback().orElse(Vec3.ZERO);
        if (kb.lengthSqr() < 1.0E-4) return;

        // 取消原版爆炸击退（原地不动，等待你主动跳）
        event.cancel();

        // 蓄力：接下来 armWindow tick 内按跳就飞
        armed = true;
        armedTimer = 0;
    }

    // ==================== 检测跳跃 + 飞行 ====================
    @Subscribe
    public void onMotion(MotionEvent event) {
        if (nullCheck()) return;
        if (event.getStage() != 0) return;

        // RequireExplosion=true 时，没被炸不能触发
        if (requireExplosion.getValue() && !armed) return;

        // 检测跳跃开始（离地 + 上升）
        if (!jumping) {
            if (!mc.player.onGround() && mc.player.getDeltaMovement().y > 0.1) {
                jumping = true;
                jumpTicks = 0;
                armed = false;    // 用掉了
            }
            return;
        }

        jumpTicks++;

        // 到达持续时间，结束
        if (jumpTicks > ticks.getValue()) {
            jumping = false;
            if (autoDisable.getValue()) disable();
            return;
        }

        // 让 Speed 暂时让路
        skipTicks = 2;

        Vec3 cur = mc.player.getDeltaMovement();

        // 水平方向：沿当前动量方向（没动量就用朝向）
        double hLen = Math.sqrt(cur.x * cur.x + cur.z * cur.z);
        double addX, addZ;
        if (hLen < 0.01) {
            float yawRad = (float) Math.toRadians(mc.player.getYRot());
            addX = -Math.sin(yawRad) * speed.getValue();
            addZ = Math.cos(yawRad) * speed.getValue();
        } else {
            double factor = speed.getValue() / hLen;
            addX = cur.x * factor;
            addZ = cur.z * factor;
        }

        // Y 轴：第一 tick 给初速度，之后保留自然重力
        double newY = cur.y;
        if (jumpTicks == 1) {
            newY = yMotion.getValue();
        }

        mc.player.setDeltaMovement(addX, newY, addZ);
    }
}