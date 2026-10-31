package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.inventory.Result;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class VoidMaceSmart extends Module {
    public static VoidMaceSmart INSTANCE;

    public enum HitPhase { AT_DEPTH, AFTER_RETURN }
    public enum Recovery { NONE, ORIGIN, TROLLHACK }
    public enum TargetMode { Players, Mobs, Both }

    private static final int PACKET_LIMIT = 128;

    // ==================== 目标 ====================
    private final Setting<TargetMode> targetMode = mode("Target", TargetMode.Players);

    // ==================== VL Counter ====================
    /** 开启后，下潜高度受 VL 预算限制，不再固定用 Void Y。 */
    private final Setting<Boolean> vlCounter = bool("VL Counter", false);

    /** VL 总预算。 */
    private final Setting<Float> maxVl = num("Max VL", 130f, 0f, 10000f,
            () -> vlCounter.getValue());

    /** 每下潜 1 格消耗的 VL。 */
    private final Setting<Float> vlPerBlock = num("VL per Block", 1f, 0f, 100f,
            () -> vlCounter.getValue());

    /** 固定额外消耗。 */
    private final Setting<Float> vlBase = num("VL Base", 0f, 0f, 100f,
            () -> vlCounter.getValue());

    /** 多久没消耗 VL 就重置（毫秒）。 */
    private final Setting<Integer> vlResetMs = num("VL Reset (ms)", 1700, 0, 10000,
            () -> vlCounter.getValue());

    /** 保留百分比，防止一次性打空 VL。 */
    private final Setting<Integer> vlReserve = num("VL Reserve %", 20, 0, 90,
            () -> vlCounter.getValue());

    /** VL 模式下的最大下潜高度。 */
    private final Setting<Float> maxHeight = num("Max Height", 130f, 1.5f, 500f,
            () -> vlCounter.getValue());

    /** 预测伤害时额外要求多打的血量。 */
    private final Setting<Float> healthMargin = num("Health Margin", 2f, 0f, 40f,
            () -> vlCounter.getValue());

    /** 伤害缩放百分比，用来修正预测偏差。 */
    private final Setting<Float> damageScale = num("Damage Scale %", 100f, 5f, 300f,
            () -> vlCounter.getValue());

    // ==================== 虚空 Y ====================
    private final Setting<Float> lowHpThreshold = num("Low HP ", 10f, 0f, 40f);
    private final Setting<Float> depth = num("Void Y", -266.71f, -500f, 320f,
            () -> !vlCounter.getValue());
    private final Setting<Float> lowHpDepth = num("Low HP  Y", -100f, -500f, 320f,
            () -> !vlCounter.getValue() && lowHpThreshold.getValue() > 0f);

    // ==================== 距离 ====================
    private final Setting<Float> range = num("Target Range", 10f, 1f, 30f);
    private final Setting<Float> reach = num("Attack Range", 6f, 0f, 6f);

    // ==================== 其他 ====================
    private final Setting<Integer> packets = num("Dive Packets", 27, 1, 64);
    private final Setting<Float> lift = num("Ascend Height", 0f, 0f, 10f);
    private final Setting<HitPhase> phase = mode("Attack At", HitPhase.AT_DEPTH);
    private final Setting<Recovery> recovery = mode("Recover", Recovery.TROLLHACK);

    private final Setting<Integer> delay = num("Attack Delay", 1889, 0, 5000);
    private final Setting<Integer> cycles = num("Cycles", 3, 1, 5);

    private final Setting<Boolean> rotate = bool("Rotate", false);
    private final Setting<Boolean> invSwap = bool("Inventory Swap", true);

    private final Setting<Boolean> hostileOnly = bool("Hostile Only", true,
            () -> targetMode.getValue() != TargetMode.Players);

    private final Setting<Boolean> ignoreIframes = bool("Ignore Iframes", false);

    // ==================== Ground Sync ====================
    private final Setting<Boolean> groundSync = bool("Ground Sync", true);
    private final Setting<Integer> groundSyncInterval = num("Sync Interval", 1, 1, 10,
            () -> groundSync.getValue());

    // ==================== 状态 ====================
    private int charge;
    private long lastHit;
    private int sentPackets;

    /** 只在攻击流程中拦截 onGround 包。 */
    private boolean attacking = false;

    /** Ground Sync 计数器。 */
    private int groundSyncTimer = 0;

    /** VL 已消耗量。 */
    private double vlSpent;
    /** 上次消耗 VL 的时间戳。 */
    private long lastVlSpendAt;

    public VoidMaceSmart() {
        super("VoidMaceSmart", "重锤虚空攻击", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        charge = 0;
        lastHit = 0L;
        sentPackets = 0;
        attacking = false;
        groundSyncTimer = 0;
        vlSpent = 0.0;
        lastVlSpendAt = 0L;
    }

    @Override
    public void onDisable() {
        attacking = false;
        groundSyncTimer = 0;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        sentPackets = 0;
        if (charge < Integer.MAX_VALUE) charge++;

        // ★ Ground Sync（不参与攻击逻辑，只做状态同步）
        tickGroundSync();

        // ★★ 核心规则：玩家不在地面 → 不攻击 ★★
        if (!mc.player.onGround()) return;

        Entity target = pickTarget();
        if (target == null) return;

        if (reach.getValue() > 0.0 && !inReach(target, reach.getValue())) return;
        if (!ignoreIframes.getValue() && target.invulnerableTime > 10) return;
        if (!offCooldown()) return;

        int scope = invSwap.getValue()
                ? InventoryUtil.FULL_SCOPE
                : InventoryUtil.HOTBAR_SCOPE;
        Result result = InventoryUtil.find(Items.MACE, scope);
        if (result.slot() == -1) return;

        double y;
        int count;

        if (vlCounter.getValue()) {
            // VL 模式：动态计算下潜高度
            double height = nextVlHeight(target);
            if (height <= 0.0) return; // 预算不够 / 伤害不够 / 低于 smash 阈值
            y = mc.player.getY() - height;
            count = 1;
        } else {
            y = pickVoidY(target);
            count = Math.max(1, packets.getValue());
        }

        if (strike(target, result, y, count)) {
            if (vlCounter.getValue()) {
                int rounds = Math.max(1, cycles.getValue());
                spendVl((mc.player.getY() - y) * rounds);
            }
            lastHit = System.currentTimeMillis();
            charge = 0;
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

    private double pickVoidY(Entity target) {
        if (target instanceof LivingEntity living) {
            float hp = living.getHealth() + living.getAbsorptionAmount();
            if (hp <= lowHpThreshold.getValue()) {
                return lowHpDepth.getValue();
            }
        }
        return depth.getValue();
    }

    private boolean offCooldown() {
        return delayDone() && (lastHit == 0L || charge >= fullCharge());
    }

    private boolean delayDone() {
        return System.currentTimeMillis() - lastHit >= delay.getValue();
    }

    private int fullCharge() {
        ItemStack mace = maceStack();
        if (!mace.isEmpty()) {
            float speed = (float) mc.player.getAttributeValue(Attributes.ATTACK_SPEED);
            speed -= itemAttackSpeed(mc.player.getMainHandItem());
            speed += itemAttackSpeed(mace);
            if (speed > 0.0f) {
                return Math.max(1, (int) Math.ceil(20.0f / speed));
            }
        }
        return 34;
    }

    // ==================== VL Counter 数学 ====================

    static double vlAfterWindow(double spent, long sinceLastMs, long resetMs) {
        return sinceLastMs >= resetMs ? 0.0 : spent;
    }

    static double affordableHeight(double usableVl, double vlBase, double vlPerBlock) {
        if (vlPerBlock <= 0.0) return Double.MAX_VALUE;
        return (usableVl - vlBase) / vlPerBlock;
    }

    static double planHeight(double needed, double affordable, double maxHeight) {
        double height = Math.min(Math.min(needed, affordable), maxHeight);
        return height < MaceItem.SMASH_ATTACK_FALL_THRESHOLD ? 0.0 : height;
    }

    private void refreshVlWindow() {
        vlSpent = vlAfterWindow(vlSpent,
                System.currentTimeMillis() - lastVlSpendAt,
                vlResetMs.getValue());
    }

    private double remainingVl() {
        refreshVlWindow();
        return Math.max(0.0, maxVl.getValue() - vlSpent);
    }

    private double usableVl() {
        return remainingVl() * (1.0 - vlReserve.getValue() / 100.0);
    }

    private double vlCost(double height) {
        return vlBase.getValue() + vlPerBlock.getValue() * height;
    }

    private void spendVl(double height) {
        vlSpent += vlCost(height);
        lastVlSpendAt = System.currentTimeMillis();
    }

    private double nextVlHeight(Entity target) {
        refreshVlWindow();
        ItemStack mace = maceStack();
        double needed = heightForDamage(target, requiredDamage(target), mace);
        double affordable = affordableHeight(usableVl(), vlBase.getValue(), vlPerBlock.getValue());
        return planHeight(needed, affordable, maxHeight.getValue());
    }

    private double heightForDamage(Entity target, double required, ItemStack mace) {
        if (required <= 0.0) return 0.0;
        double lo = MaceItem.SMASH_ATTACK_FALL_THRESHOLD;
        double hi = maxHeight.getValue();
        if (hi <= lo || predictedDamage(target, hi, mace) < required) {
            return hi;
        }
        for (int i = 0; i < 20; i++) {
            double mid = (lo + hi) / 2.0;
            if (predictedDamage(target, mid, mace) >= required) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return hi;
    }

    private double requiredDamage(Entity target) {
        if (target instanceof LivingEntity living) {
            return living.getHealth() + living.getAbsorptionAmount() + healthMargin.getValue();
        }
        return healthMargin.getValue();
    }

    private double predictedDamage(Entity target, double height, ItemStack mace) {
        if (height <= MaceItem.SMASH_ATTACK_FALL_THRESHOLD) return 0.0;

        float attack = maceAttackDamage(mace);
        float smash = (float) smashBonus(height);
        float density = 0.5f * enchLevel(mace, Enchantments.DENSITY) * (float) height;
        boolean crit = !mc.player.isSprinting() && charge >= fullCharge();
        float raw = (attack + smash + density) * (crit ? 1.5f : 1.0f);

        int sharpness = enchLevel(mace, Enchantments.SHARPNESS);
        if (sharpness > 0) {
            raw += 0.5f * sharpness + 0.5f;
        }
        return raw * (damageScale.getValue() / 100.0);
    }

    private static double smashBonus(double fall) {
        if (fall <= MaceItem.SMASH_ATTACK_FALL_THRESHOLD) return 0.0;
        if (fall <= 3.0) return 4.0 * fall;
        if (fall <= 8.0) return 12.0 + 2.0 * (fall - 3.0);
        return 22.0 + (fall - 8.0);
    }

    private int enchLevel(ItemStack stack,
                          net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> ench) {
        if (stack == null || stack.isEmpty() || mc.level == null) return 0;
        var registry = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        var holder = registry.getOrThrow(ench);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    private ItemStack maceStack() {
        ItemStack held = mc.player.getMainHandItem();
        if (held.is(Items.MACE)) return held;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(Items.MACE)) return s;
        }
        return ItemStack.EMPTY;
    }

    private float maceAttackDamage(ItemStack mace) {
        float damage = (float) mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        damage -= itemAttackDamage(mc.player.getMainHandItem());
        damage += itemAttackDamage(mace);
        return damage;
    }

    private static float itemAttackDamage(ItemStack stack) {
        float damage = 0.0f;
        if (stack == null || stack.isEmpty()) return 0.0f;
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0.0f;
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_DAMAGE)) {
                damage += (float) entry.modifier().amount();
            }
        }
        return damage;
    }

    private static float itemAttackSpeed(ItemStack stack) {
        float speed = 0.0f;
        if (stack == null || stack.isEmpty()) return 0.0f;
        var modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0.0f;
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().is(Attributes.ATTACK_SPEED)) {
                speed += (float) entry.modifier().amount();
            }
        }
        return speed;
    }

    // ==================== 攻击核心 ====================

    private boolean strike(Entity target, Result maceResult, double y, int pk) {
        Vec3 start = mc.player.position();
        Vec3 eye = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 point = closestPoint(eye, box);
        float yaw = yawTo(eye, point);
        float pitch = pitchTo(eye, point);
        int loops = Math.max(1, cycles.getValue());

        boolean[] done = { false };

        attacking = true;

        try {
            InventoryUtil.withSwap(maceResult, () -> {
                if (!mc.player.getMainHandItem().is(Items.MACE)) return;

                for (int i = 0; i < loops; i++) {
                    dive(start, y, pk);
                    if (phase.getValue() == HitPhase.AFTER_RETURN) {
                        pos(start.x, start.y, start.z);
                    }
                    if (rotate.getValue()) {
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, false, false));
                    }
                    attack(target);
                    recover(start);
                }
                done[0] = true;
            });
        } finally {
            attacking = false;
            sendGroundConfirm(start);
        }

        return done[0];
    }

    private void sendGroundConfirm(Vec3 start) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                start.x, start.y, start.z, true, false));
    }

    private void dive(Vec3 start, double y, int pk) {
        double from = start.y;
        double up = Math.max(0.0, lift.getValue());
        if (up > 0.0) {
            for (int i = 1; i <= 4; i++) {
                pos(start.x, start.y + up * i / 4.0, start.z);
            }
            from = start.y + up;
        }
        for (int i = 1; i <= pk; i++) {
            pos(start.x, from + (y - from) * i / pk, start.z);
        }
        pos(start.x, y - 0.01, start.z);
    }

    private void recover(Vec3 start) {
        switch (recovery.getValue()) {
            case NONE -> {}
            case ORIGIN -> pos(start.x, start.y, start.z);
            case TROLLHACK -> {
                pos(start.x, start.y + 2.3400880035762786, start.z);
                pos(start.x, start.y, start.z);
            }
        }
    }

    private void attack(Entity target) {
        mc.getConnection().send(
                ServerboundInteractPacket.createAttackPacket(target, mc.player.isShiftKeyDown()));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void pos(double x, double y, double z) {
        if (sentPackets >= PACKET_LIMIT) return;
        sentPackets++;
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
    }

    // ==================== 目标选择 ====================

    private Entity pickTarget() {
        Entity best = null;
        double bestDist = range.getValue() * range.getValue();
        TargetMode mode = targetMode.getValue();

        if (mode == TargetMode.Players || mode == TargetMode.Both) {
            for (Player player : mc.level.players()) {
                if (!canHit(player)) continue;
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

    private boolean canHit(Player player) {
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

    private boolean inReach(Entity target, double dist) {
        Vec3 eye = mc.player.getEyePosition();
        return eye.distanceTo(closestPoint(eye, target.getBoundingBox())) <= dist;
    }

    // ==================== 数学 ====================

    private static Vec3 closestPoint(Vec3 eye, AABB box) {
        double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        return new Vec3(x, y, z);
    }

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

    // ==================== onGround 包拦截 ====================

    @Subscribe(priority = 999)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;

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
        } else if (packet instanceof ServerboundMovePlayerPacket.StatusOnly s) {
            out = new ServerboundMovePlayerPacket.StatusOnly(false, s.horizontalCollision());
        } else {
            return;
        }
        mc.getConnection().send(out);
    }
}