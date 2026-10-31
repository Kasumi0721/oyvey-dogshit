package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.event.impl.entity.VelocityUpdateEvent;
import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.EntityUtil;
import me.alpha432.oyvey.util.MovementUtil;
import me.alpha432.oyvey.util.Timer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class VelocityModule extends Module {
    public static VelocityModule INSTANCE;

    public enum Mode { Plain, Grim, Wall, None }

    private final Setting<Mode> mode = mode("Mode", Mode.Plain);
    private final Setting<Integer> lagPause = num("LagPause", 50, 0, 500,
            () -> mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall);
    public final Setting<Boolean> ignorePearlLag = bool("IgnorePearlLag", true,
            () -> mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall).setParent();
    private final Setting<Integer> phaseTime = num("PhaseTime", 250, 0, 1000,
            () -> (mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall) && ignorePearlLag.isOpen());
    public final Setting<Boolean> noRotation = bool("NoRotation", false,
            () -> mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall);
    public final Setting<Boolean> flagInWall = bool("FlagInWall", false,
            () -> mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall).setParent();
    public final Setting<Boolean> whenPushOutOfBlocks = bool("WhilePushOut", false,
            () -> (mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall) && flagInWall.isOpen());
    public final Setting<Boolean> staticSetting = bool("Static", false,
            () -> mode.getValue() == Mode.Grim);
    public final Setting<Boolean> cancelAll = bool("CancelAll", false,
            () -> mode.getValue() != Mode.None);
    private final Setting<Integer> horizontal = num("Horizontal", 0, 0, 100,
            () -> mode.getValue() != Mode.None && !cancelAll.getValue());
    private final Setting<Integer> vertical = num("Vertical", 0, 0, 100,
            () -> mode.getValue() != Mode.None && !cancelAll.getValue());

    public final Setting<Boolean> whileLiquid = bool("WhileLiquid", false, () -> true);
    public final Setting<Boolean> whileElytra = bool("FallFlying", false, () -> true);
    public final Setting<Boolean> noClimb = bool("NoClimb", false, () -> true);
    public final Setting<Boolean> waterPush = bool("NoWaterPush", false, () -> true);
    public final Setting<Boolean> entityPush = bool("NoEntityPush", true, () -> true);
    public final Setting<Boolean> blockPush = bool("NoBlockPush", true, () -> true);
    public final Setting<Boolean> fishBob = bool("NoFishBob", true, () -> true);

    private final Timer pearlTimer = new Timer();
    private final Timer lagBackTimer = new Timer();
    private boolean flag;
    public static boolean pushOutOfBlocks = false;

    public VelocityModule() {
        super("Velocity", "反击退", Category.PLAYER);
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if (mode.getValue() == Mode.None) return null;
        return mode.getValue().name() + ", "
                + (cancelAll.getValue() ? "Cancel"
                : horizontal.getValue() + "%, " + vertical.getValue() + "%");
    }

    public boolean noRotation() {
        return isEnabled()
                && (mode.getValue() == Mode.Grim || mode.getValue() == Mode.Wall)
                && EntityUtil.isInsideBlock()
                && noRotation.getValue();
    }

    // ==================== 击退主逻辑 ====================
    @Subscribe
    public void onVelocity(VelocityUpdateEvent event) {
        if (nullCheck()) return;
        if (event.getEntity() != mc.player) return;
        if (mode.getValue() == Mode.None) return;
        if (mc.player.isInWater() && !whileLiquid.getValue()) return;
        if (mc.player.isFallFlying() && !whileElytra.getValue()) return;

        switch (mode.getValue()) {
            case Grim -> {
                if (!lagBackTimer.passedMs(lagPause.getValue())) return;
                if (!EntityUtil.isInsideBlock()
                        && getPos() == null
                        && (!staticSetting.getValue() || !MovementUtil.isStatic())) return;
                if (event.getX() != 0.0 || event.getZ() != 0.0) flag = true;
            }
            case Wall -> {
                if (!lagBackTimer.passedMs(lagPause.getValue())) return;
                if (!EntityUtil.isInsideBlock()) return;
                if (event.getX() != 0.0 || event.getZ() != 0.0) flag = true;
            }
            default -> {}
        }

        if (cancelAll.getValue()) {
            event.cancel();
        } else {
            double h = horizontal.getValue() / 100.0;
            double v = vertical.getValue() / 100.0;
            event.setX(event.getX() * h);
            event.setZ(event.getZ() * h);
            event.setY(event.getY() * v);
        }
    }

    // ==================== 鱼竿 / 珍珠 ====================
    @Subscribe
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket
                && (!ignorePearlLag.getValue() || pearlTimer.passedMs(phaseTime.getValue()))) {
            lagBackTimer.reset();
        }

        if (nullCheck()) return;
        if (mc.player.isInWater() && !whileLiquid.getValue()) return;

        if (fishBob.getValue() && event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() != 31) return;
            var entity = packet.getEntity(mc.level);
            if (entity instanceof FishingHook hook && hook.getHookedIn() == mc.player) {
                event.cancel();
            }
        }
    }

    // ==================== Tick ====================
    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (mc.player.isInWater() && !whileLiquid.getValue()) return;

        if (flagInWall.getValue()) {
            pushOutOfBlocks = false;
            double w = mc.player.getBbWidth() * 0.35;
            pushOutCheck(mc.player.getX() - w, mc.player.getZ() + w);
            pushOutCheck(mc.player.getX() - w, mc.player.getZ() - w);
            pushOutCheck(mc.player.getX() + w, mc.player.getZ() - w);
            pushOutCheck(mc.player.getX() + w, mc.player.getZ() + w);
        }

        // Grim 模式的反向拉回暂时禁用（PosRot 构造器在 1.21.11 签名不同）
        // 如果以后要用，参照 1.21.11 的 ServerboundMovePlayerPacket 内部类
        if (flag) {
            flag = false;
        }
    }

    public BlockPos getPos() {
        BlockPos below = mc.player.blockPosition().below();
        return mc.level.getBlockState(below).getBlock() == Blocks.OBSIDIAN ? below : null;
    }

    private static void pushOutCheck(double x, double z) {
        BlockPos blockPos = BlockPos.containing(x, mc.player.getY(), z);
        if (wouldCollideAt(blockPos)) {
            double d = x - blockPos.getX();
            double e = z - blockPos.getZ();
            Direction direction = null;
            double f = Double.MAX_VALUE;
            Direction[] directions = {Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

            for (Direction direction2 : directions) {
                double g = direction2.getAxis().choose(d, 0.0, e);
                double h = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - g : g;
                if (h < f && !wouldCollideAt(blockPos.relative(direction2))) {
                    f = h;
                    direction = direction2;
                }
            }

            if (direction != null) {
                pushOutOfBlocks = true;
            }
        }
    }

    private static boolean wouldCollideAt(BlockPos pos) {
        AABB box = mc.player.getBoundingBox();
        AABB box2 = new AABB(pos.getX(), box.minY, pos.getZ(),
                pos.getX() + 1.0, box.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
        return mc.level.getBlockCollisions(mc.player, box2).iterator().hasNext();
    }
}