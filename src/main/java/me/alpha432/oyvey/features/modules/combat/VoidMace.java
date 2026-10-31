package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.inventory.Result;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VoidMace extends Module {
    public static VoidMace INSTANCE;

    // ==================== 枚举 ====================
    public enum Sequence {
        PRE_ROTATE_VOID_ATTACK,
        VOID_ROTATE_ATTACK,
        STEPPED_VOID_ATTACK,
        VOID_RETURN_ATTACK,
        DOUBLE_VOID_ATTACK,
        VOID_PULL_ATTACK
    }

    public enum Recover {
        NONE, ORIGIN, TROLLHACK, FLY, GLIDE
    }

    public enum AttackHand {
        MAIN, OFFHAND, MAIN_THEN_OFFHAND, OFFHAND_THEN_MAIN
    }

    public enum SwapStyle {
        API, PACKET_SLOT, OFFHAND_PACKET, NONE
    }

    public enum SwapTiming {
        BEFORE_VOID, AFTER_VOID, BEFORE_ATTACK
    }

    public enum Timing {
        INSTANT, CHARGE, DELAY, CHARGE_AND_DELAY
    }

    public enum TargetMode {
        Players, Mobs, Both
    }

    // ==================== 设置 ====================
    private final Setting<Sequence> sequence = mode("Sequence", Sequence.PRE_ROTATE_VOID_ATTACK);
    private final Setting<Recover> recover = mode("Recover", Recover.ORIGIN);

    private final Setting<TargetMode> targetMode = mode("Target", TargetMode.Players);
    private final Setting<Boolean> hostileOnly = bool("Hostile Only", true,
            () -> targetMode.getValue() != TargetMode.Players);
    private final Setting<Float> targetRange = num("Target Range", 10f, 1f, 30f);
    private final Setting<Float> attackRange = num("Attack Range", 6f, 1f, 6f);
    private final Setting<Boolean> requireAttackRange = bool("Require Attack Range", true);
    private final Setting<Boolean> enforceServerReach = bool("Enforce Server Reach", true);
    private final Setting<Float> serverReach = num("Server Reach", 3f, 1f, 6f,
            () -> enforceServerReach.getValue());

    private final Setting<Float> voidY = num("Void Y", -70f, -512f, 320f);
    private final Setting<Float> lowHpThreshold = num("Low HP ", 10f, 0f, 40f);
    private final Setting<Float> lowHpDepth = num("Low HP Y", -100f, -500f, 320f,
            () -> lowHpThreshold.getValue() > 0f);

    private final Setting<Float> ascendHeight = num("Ascend Height", 6f, 0f, 10f);
    private final Setting<Integer> ascendSteps = num("Ascend Steps", 2, 1, 16,
            () -> ascendHeight.getValue() > 0f);
    private final Setting<Integer> voidSteps = num("Void Steps", 1, 1, 32);
    private final Setting<Boolean> autoSteps = bool("Auto Steps", false);
    private final Setting<Float> maxStep = num("Max Step", 8f, 0.5f, 20f,
            () -> autoSteps.getValue());
    private final Setting<Integer> packetsPerTick = num("Packets Per Tick", 8, 1, 64);
    private final Setting<Integer> cyclesPerTick = num("Cycles Per Tick", 1, 1, 5);

    private final Setting<Boolean> microAfterVoid = bool("Micro After Void", false);
    private final Setting<Float> microOffset = num("Micro Offset", 1.0E-9f, -0.01f, 0.01f,
            () -> microAfterVoid.getValue());

    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> swing = bool("Swing", true);

    private final Setting<Timing> timing = mode("Timing", Timing.CHARGE);
    private final Setting<Float> minCharge = num("Min Charge", 1f, 0.1f, 1f,
            () -> timing.getValue() == Timing.CHARGE || timing.getValue() == Timing.CHARGE_AND_DELAY);
    private final Setting<Integer> attackDelay = num("Attack Delay", 1700, 0, 4000,
            () -> timing.getValue() == Timing.DELAY || timing.getValue() == Timing.CHARGE_AND_DELAY);
    private final Setting<Boolean> resetChargeTicker = bool("Reset Charge Ticker", true,
            () -> timing.getValue() != Timing.INSTANT);

    private final Setting<Boolean> skipInvulnerable = bool("Skip Invulnerable", true);
    private final Setting<Integer> invulnerableThreshold = num("Invuln Threshold", 10, 0, 20,
            () -> skipInvulnerable.getValue());

    private final Setting<AttackHand> attackHand = mode("Attack Hand", AttackHand.MAIN);

    private final Setting<SwapStyle> swapStyle = mode("Swap Style", SwapStyle.API);
    private final Setting<SwapTiming> swapTiming = mode("Swap Timing", SwapTiming.BEFORE_VOID);
    private final Setting<Boolean> inventorySwap = bool("Inventory Swap", false);
    private final Setting<Boolean> rawSwapFallback = bool("Raw Swap Fallback", true);

    private final Setting<Boolean> onGround = bool("OnGround Packets", false,
            () -> sequence.getValue() != Sequence.STEPPED_VOID_ATTACK);

    // ==================== Ground Sync ====================
    /** 站在地面时主动向服务器同步 onGround=true。 */
    private final Setting<Boolean> groundSync = bool("Ground Sync", true);
    /** 落地包发送节流：每多少 tick 同步一次（1 = 每 tick）。 */
    private final Setting<Integer> groundSyncInterval = num("Sync Interval", 1, 1, 10,
            () -> groundSync.getValue());

    // ==================== 状态 ====================
    private int movePacketBudget;
    private int chargeTicks;
    private long lastAttackAt;
    private boolean attacking;

    private int groundSyncTimer = 0;
    private double activeVoidY = -70.0;

    private static final double VANILLA_MOVE_LIMIT = 10.0;

    public VoidMace() {
        super("VoidMace", "重锤序列攻击（全量版）", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        chargeTicks = 0;
        lastAttackAt = 0L;
        movePacketBudget = 0;
        attacking = false;
        groundSyncTimer = 0;
    }

    @Override
    public void onDisable() {
        movePacketBudget = 0;
        chargeTicks = 0;
        lastAttackAt = 0L;
        attacking = false;
        groundSyncTimer = 0;
    }

    // ==================== 主逻辑 ====================

    @Override
    public void onTick() {
        if (nullCheck()) return;

        movePacketBudget = 0;
        if (chargeTicks < Integer.MAX_VALUE) chargeTicks++;

        if (mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;

        // ★ Ground Sync
        tickGroundSync();

        Entity target = findTarget();
        if (target == null) return;

        if (requireAttackRange.getValue() && !withinAttackRange(target)) return;
        if (enforceServerReach.getValue() && !withinServerReach(target)) return;

        Result mace = findMace();
        if (mace.slot() == -1 && !canUseOffhandMace()) return;

        if (skipInvulnerable.getValue() && target.invulnerableTime > invulnerableThreshold.getValue()) return;
        if (!timingReady()) return;

        if (!executeCycles(target, mace)) return;

        lastAttackAt = System.currentTimeMillis();
        chargeTicks = 0;
        if (resetChargeTicker.getValue() && timing.getValue() != Timing.INSTANT) {
            mc.player.resetAttackStrengthTicker();
        }
    }

    // ==================== Ground Sync ====================

    private void tickGroundSync() {
        if (!groundSync.getValue()) return;
        if (attacking) return;
        if (mc.player == null) return;
        if (mc.getConnection() == null) return;

        if (!mc.player.onGround()) {
            groundSyncTimer = 0;
            return;
        }

        if (mc.options.keyJump.isDown()) {
            groundSyncTimer = 0;
            return;
        }

        int interval = Math.max(1, groundSyncInterval.getValue());
        groundSyncTimer++;
        if (groundSyncTimer < interval) return;
        groundSyncTimer = 0;

        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                true,
                mc.player.horizontalCollision));
    }

    // ==================== 虚空 Y 选择 ====================

    private double pickVoidY(Entity target) {
        if (target instanceof LivingEntity living) {
            float hp = living.getHealth() + living.getAbsorptionAmount();
            if (hp <= lowHpThreshold.getValue()) {
                return lowHpDepth.getValue();
            }
        }
        return voidY.getValue();
    }

    // ==================== 时序 ====================

    private boolean timingReady() {
        return switch (timing.getValue()) {
            case INSTANT -> true;
            case CHARGE -> chargeProgress() >= minCharge.getValue();
            case DELAY -> delayElapsed();
            case CHARGE_AND_DELAY -> chargeProgress() >= minCharge.getValue() && delayElapsed();
        };
    }

    private double chargeProgress() {
        float delay = mc.player.getCurrentItemAttackStrengthDelay();
        if (delay <= 0.0f) return 1.0;
        return Math.min(1.0, chargeTicks / (double) delay);
    }

    private boolean delayElapsed() {
        return System.currentTimeMillis() - lastAttackAt >= attackDelay.getValue();
    }

    // ==================== 目标选择 ====================

    private Entity findTarget() {
        Entity best = null;
        double bestDist = targetRange.getValue() * targetRange.getValue();
        TargetMode mode = targetMode.getValue();

        if (mode == TargetMode.Players || mode == TargetMode.Both) {
            for (Player player : mc.level.players()) {
                if (!isValidTarget(player)) continue;
                double dist = mc.player.distanceToSqr(player);
                if (dist <= bestDist) {
                    best = player;
                    bestDist = dist;
                }
            }
        }

        if (mode == TargetMode.Mobs || mode == TargetMode.Both) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity)) continue;
                if (entity instanceof Player) continue;
                if (!canHitMob(entity)) continue;
                double dist = mc.player.distanceToSqr(entity);
                if (dist <= bestDist) {
                    best = entity;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    private boolean isValidTarget(Player player) {
        if (player == null || player == mc.player) return false;
        if (!player.isAlive()) return false;
        if (player.getHealth() <= 0.0f) return false;
        if (player.isSpectator()) return false;
        try {
            if (OyVey.friendManager.isFriend(player)) return false;
        } catch (Throwable ignored) {}
        return true;
    }

    private boolean canHitMob(Entity entity) {
        if (entity == null || entity == mc.player) return false;
        if (!entity.isAlive()) return false;
        if (entity.isSpectator()) return false;

        if (hostileOnly.getValue()) {
            if (entity instanceof Monster) return true;
            String name = entity.getClass().getSimpleName().toLowerCase();
            return name.contains("slime")
                    || name.contains("magma")
                    || name.contains("ghast")
                    || name.contains("hoglin")
                    || name.contains("piglin")
                    || name.contains("shulker")
                    || name.contains("phantom")
                    || name.contains("wither")
                    || name.contains("dragon");
        }
        return true;
    }

    // ==================== 距离 ====================

    private AABB targetBox(Entity target) {
        return target.getBoundingBox();
    }

    private boolean withinAttackRange(Entity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 closest = closestPoint(eye, targetBox(target));
        return eye.distanceTo(closest) <= attackRange.getValue();
    }

    private boolean withinServerReach(Entity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 closest = closestPoint(eye, targetBox(target));
        return eye.distanceTo(closest) <= serverReach.getValue();
    }

    private static Vec3 closestPoint(Vec3 eye, AABB box) {
        double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        return new Vec3(x, y, z);
    }

    // ==================== 物品 ====================

    private Result findMace() {
        int scope = inventorySwap.getValue()
                ? InventoryUtil.FULL_SCOPE
                : InventoryUtil.HOTBAR_SCOPE;
        return InventoryUtil.find(Items.MACE, scope);
    }

    private boolean canUseOffhandMace() {
        return mc.player.getOffhandItem().is(Items.MACE)
                && (attackHand.getValue() == AttackHand.OFFHAND
                || attackHand.getValue() == AttackHand.OFFHAND_THEN_MAIN);
    }

    // ==================== 攻击核心 ====================

    private boolean executeCycles(Entity target, Result mace) {
        activeVoidY = pickVoidY(target);

        Vec3 origin = mc.player.position();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 hit = closestPoint(eye, targetBox(target));
        float yaw = yawTo(eye, hit);
        float pitch = pitchTo(eye, hit);
        int cycles = Math.max(1, cyclesPerTick.getValue());

        boolean[] success = { false };
        attacking = true;
        try {
            switch (swapStyle.getValue()) {
                case API -> InventoryUtil.withSwap(mace, () -> {
                    for (int i = 0; i < cycles; i++) {
                        sendSequence(target, origin, yaw, pitch);
                    }
                    success[0] = true;
                });
                case NONE -> {
                    for (int i = 0; i < cycles; i++) {
                        sendSequence(target, origin, yaw, pitch);
                    }
                    success[0] = true;
                }
                case PACKET_SLOT, OFFHAND_PACKET -> {
                    boolean ok = true;
                    for (int i = 0; i < cycles && ok; i++) {
                        ok = sendSequenceTimed(target, origin, yaw, pitch, mace);
                    }
                    success[0] = ok;
                }
            }
        } finally {
            attacking = false;
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                        origin.x, origin.y, origin.z, true, false));
            }
        }
        return success[0];
    }

    private boolean sendSequenceTimed(Entity target, Vec3 origin, float yaw, float pitch, Result mace) {
        if (swapTiming.getValue() == SwapTiming.BEFORE_VOID) {
            SwapState st = beginManualSwap(mace);
            if (st == null) return false;
            try {
                sendSequence(target, origin, yaw, pitch);
            } finally {
                restoreManualSwap(st);
            }
            return true;
        }

        // AFTER_VOID / BEFORE_ATTACK：Part 1 先发 void 部分
        switch (sequence.getValue()) {
            case PRE_ROTATE_VOID_ATTACK -> {
                rotateTarget(yaw, pitch);
                sendVoid(origin);
            }
            case VOID_ROTATE_ATTACK -> sendVoid(origin);
            case STEPPED_VOID_ATTACK -> sendSteppedVoid(origin);
            case VOID_RETURN_ATTACK -> {
                sendVoid(origin);
                sendPos(origin.x, origin.y, origin.z);
            }
            case DOUBLE_VOID_ATTACK -> {
                sendVoid(origin);
                sendPos(origin.x, origin.y, origin.z);
                sendVoid(origin);
            }
            case VOID_PULL_ATTACK -> {
                sendVoid(origin);
                sendPullShape(origin);
                sendVoid(origin);
            }
        }

        // Part 2 前置 swap
        SwapState st = beginManualSwap(mace);
        if (st == null) return false;
        try {
            switch (sequence.getValue()) {
                case VOID_ROTATE_ATTACK, STEPPED_VOID_ATTACK -> rotateTarget(yaw, pitch);
                default -> { }
            }
            sendAttack(target);
            sendRecover(origin);
        } finally {
            restoreManualSwap(st);
        }
        return true;
    }

    private void sendSequence(Entity target, Vec3 origin, float yaw, float pitch) {
        switch (sequence.getValue()) {
            case PRE_ROTATE_VOID_ATTACK -> {
                rotateTarget(yaw, pitch);
                sendVoid(origin);
                sendAttack(target);
                sendRecover(origin);
            }
            case VOID_ROTATE_ATTACK -> {
                sendVoid(origin);
                rotateTarget(yaw, pitch);
                sendAttack(target);
                sendRecover(origin);
            }
            case STEPPED_VOID_ATTACK -> {
                sendSteppedVoid(origin);
                rotateTarget(yaw, pitch);
                sendAttack(target);
                sendRecover(origin);
            }
            case VOID_RETURN_ATTACK -> {
                sendVoid(origin);
                sendPos(origin.x, origin.y, origin.z);
                rotateTarget(yaw, pitch);
                sendAttack(target);
                sendRecover(origin);
            }
            case DOUBLE_VOID_ATTACK -> {
                sendVoid(origin);
                sendPos(origin.x, origin.y, origin.z);
                sendVoid(origin);
                rotateTarget(yaw, pitch);
                sendAttack(target);
                sendRecover(origin);
            }
            case VOID_PULL_ATTACK -> {
                sendVoid(origin);
                sendPullShape(origin);
                sendVoid(origin);
                rotateTarget(yaw, pitch);
                sendAttack(target);
                sendRecover(origin);
            }
        }
    }

    private void rotateTarget(float yaw, float pitch) {
        if (!rotate.getValue()) return;
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, false, false));
    }

    // ==================== 下潜 / 上升 / 恢复 ====================

    private void sendVoid(Vec3 origin) {
        sendPos(origin.x, activeVoidY, origin.z);
        if (microAfterVoid.getValue()) {
            sendPos(origin.x, activeVoidY + microOffset.getValue(), origin.z);
        }
    }

    private void sendSteppedVoid(Vec3 origin) {
        double apex = sendAscent(origin);
        double total = apex - activeVoidY;
        int steps = Math.max(1, voidSteps.getValue());

        if (autoSteps.getValue() && total > 0.0) {
            steps = (int) Math.ceil(total / Math.max(0.5, maxStep.getValue()));
        }
        if (total > 0.0) {
            steps = Math.max(steps, (int) Math.ceil(total / VANILLA_MOVE_LIMIT));
        }
        int available = packetsPerTick.getValue() - movePacketBudget - recoverPacketCount();
        if (available < 1) available = 1;
        steps = Math.min(steps, available);

        for (int i = 1; i <= steps; i++) {
            double y = apex + (activeVoidY - apex) * i / steps;
            sendPos(origin.x, y, origin.z);
        }
    }

    private double sendAscent(Vec3 origin) {
        double height = ascendHeight.getValue();
        if (height <= 0.0) return origin.y;

        int steps = Math.max(1, ascendSteps.getValue());
        steps = Math.max(steps, (int) Math.ceil(height / VANILLA_MOVE_LIMIT));

        int available = packetsPerTick.getValue() - movePacketBudget - recoverPacketCount() - 1;
        if (available < 1) return origin.y;
        steps = Math.min(steps, available);

        double apex = origin.y;
        for (int i = 1; i <= steps; i++) {
            apex = origin.y + height * i / steps;
            sendPos(origin.x, apex, origin.z);
        }
        return apex;
    }

    private int recoverPacketCount() {
        return switch (recover.getValue()) {
            case NONE -> 0;
            case ORIGIN -> 1;
            case TROLLHACK -> 2;
            case FLY -> 6;
            case GLIDE -> 5;
        };
    }

    private void sendRecover(Vec3 origin) {
        switch (recover.getValue()) {
            case NONE -> {}
            case ORIGIN -> sendPos(origin.x, origin.y, origin.z);
            case TROLLHACK -> {
                sendPos(origin.x, origin.y + 2.3400880035762786, origin.z);
                sendPos(origin.x, origin.y, origin.z);
            }
            case FLY -> {
                sendPos(origin.x, origin.y + 1.16610926093821, origin.z);
                sendPos(origin.x, origin.y + 1.170005801788139, origin.z);
                sendPos(origin.x, origin.y + 1.2426308013947485, origin.z);
                sendPos(origin.x, origin.y + 2.3400880035762786, origin.z);
                sendPos(origin.x, origin.y + 2.640088003576279, origin.z);
                sendPos(origin.x, origin.y, origin.z);
            }
            case GLIDE -> {
                sendPos(origin.x, origin.y + 1.0001, origin.z);
                sendPos(origin.x, origin.y + 1.0405, origin.z);
                sendPos(origin.x, origin.y + 1.0802, origin.z);
                sendPos(origin.x, origin.y + 1.1027, origin.z);
                sendPos(origin.x, origin.y, origin.z);
            }
        }
    }

    private void sendPullShape(Vec3 origin) {
        sendPos(origin.x, origin.y + 2.3400880035762786, origin.z);
    }

    // ==================== 攻击 ====================

    private void sendAttack(Entity target) {
        switch (attackHand.getValue()) {
            case MAIN -> sendAttack(target, InteractionHand.MAIN_HAND);
            case OFFHAND -> sendAttack(target, InteractionHand.OFF_HAND);
            case MAIN_THEN_OFFHAND -> {
                sendAttack(target, InteractionHand.MAIN_HAND);
                sendAttack(target, InteractionHand.OFF_HAND);
            }
            case OFFHAND_THEN_MAIN -> {
                sendAttack(target, InteractionHand.OFF_HAND);
                sendAttack(target, InteractionHand.MAIN_HAND);
            }
        }
    }

    private void sendAttack(Entity target, InteractionHand hand) {
        mc.getConnection().send(
                ServerboundInteractPacket.createAttackPacket(target, mc.player.isShiftKeyDown()));
        if (swing.getValue()) {
            mc.player.swing(hand);
        }
    }

    private void sendPos(double x, double y, double z) {
        if (movePacketBudget >= packetsPerTick.getValue()) return;
        movePacketBudget++;
        boolean ground = sequence.getValue() == Sequence.STEPPED_VOID_ATTACK
                ? false
                : onGround.getValue();
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, ground, false));
    }

    // ==================== 手动交换 ====================

    private void sendOffhandSwap() {
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ZERO,
                Direction.DOWN));
    }

    private void sendPacketSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private SwapState beginManualSwap(Result mace) {
        int selected = mc.player.getInventory().getSelectedSlot();
        return switch (swapStyle.getValue()) {
            case NONE -> SwapState.NONE;
            case PACKET_SLOT -> {
                if (mace.slot() < 0 || mace.slot() > 8) yield null;
                sendPacketSlot(mace.slot());
                yield new SwapState(selected, false, true);
            }
            case OFFHAND_PACKET -> {
                if (mc.player.getOffhandItem().is(Items.MACE)) {
                    sendOffhandSwap();
                    yield new SwapState(selected, true, false);
                }
                if (mace.slot() >= 0 && mace.slot() <= 8) {
                    sendPacketSlot(mace.slot());
                    sendOffhandSwap();
                    sendOffhandSwap();
                    yield new SwapState(selected, false, true);
                }
                yield null;
            }
            default -> SwapState.NONE;
        };
    }

    private void restoreManualSwap(SwapState st) {
        if (st == null) return;
        if (st.offhandSwapped()) sendOffhandSwap();
        if (st.slotSwapped()) sendPacketSlot(st.selectedSlot());
    }

    private record SwapState(int selectedSlot, boolean offhandSwapped, boolean slotSwapped) {
        private static final SwapState NONE = new SwapState(-1, false, false);
    }

    // ==================== 数学 ====================

    private static float yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        return (float) -Math.toDegrees(Math.atan2(dy, h));
    }

    // ==================== 包拦截 ====================

    @Subscribe(priority = 999)
    public void onPacketSend(PacketEvent.Send event) {
        if (!attacking) return;
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket packet)) return;
        if (!packet.isOnGround()) return;

        event.cancel();

        ServerboundMovePlayerPacket out;
        if (packet instanceof ServerboundMovePlayerPacket.PosRot p) {
            out = new ServerboundMovePlayerPacket.PosRot(
                    p.getX(mc.player.getX()), p.getY(mc.player.getY()), p.getZ(mc.player.getZ()),
                    p.getYRot(mc.player.getYRot()), p.getXRot(mc.player.getXRot()),
                    false, p.horizontalCollision());
        } else if (packet instanceof ServerboundMovePlayerPacket.Pos p) {
            out = new ServerboundMovePlayerPacket.Pos(
                    p.getX(mc.player.getX()), p.getY(mc.player.getY()), p.getZ(mc.player.getZ()),
                    false, p.horizontalCollision());
        } else if (packet instanceof ServerboundMovePlayerPacket.Rot p) {
            out = new ServerboundMovePlayerPacket.Rot(
                    p.getYRot(mc.player.getYRot()), p.getXRot(mc.player.getXRot()),
                    false, p.horizontalCollision());
        } else if (packet instanceof ServerboundMovePlayerPacket.StatusOnly s) {
            out = new ServerboundMovePlayerPacket.StatusOnly(false, s.horizontalCollision());
        } else {
            return;
        }
        mc.getConnection().send(out);
    }
}