package me.alpha432.oyvey.features.modules.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.entity.RotationEvent;
import me.alpha432.oyvey.event.impl.network.PacketEvent;
import me.alpha432.oyvey.event.impl.render.ClickBlockEvent;
import me.alpha432.oyvey.event.impl.render.Render3DEvent;
import me.alpha432.oyvey.event.system.Subscribe;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.*;
import me.alpha432.oyvey.util.inventory.InventoryUtil;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.glfw.GLFW.*;

public class PacketMine extends Module {
    public static PacketMine INSTANCE;
    public static BlockPos secondPos;
    public static double progress = 0.0;

    private final FadeUtils animationTime = new FadeUtils(1000L);
    private final FadeUtils secondAnim = new FadeUtils(1000L);
    private final DecimalFormat df = new DecimalFormat("0.0");

    // ==================== General ====================
    private final Setting<Page> page = mode("Page", Page.General);
    private final Setting<Float> stopDelay = num("StopDelay", 50f, 0f, 500f,
            () -> page.getValue() == Page.General);
    private final Setting<Float> startDelay = num("StartDelay", 200f, 0f, 500f,
            () -> page.getValue() == Page.General);
    private final Setting<Float> damage = num("Damage", 0.7f, 0f, 2f,
            () -> page.getValue() == Page.General);
    private final Setting<Float> maxBreak = num("MaxBreak", 3f, 0f, 20f,
            () -> page.getValue() == Page.General);

    public final Setting<Boolean> noGhostHand = bool("1.21", false,
            () -> page.getValue() == Page.General);
    public final Setting<Boolean> noCollide = bool("NoCollide", true,
            () -> page.getValue() == Page.General);

    private final Setting<Timing> timing = mode("Timing", Timing.All,
            () -> page.getValue() == Page.General);

    private final Setting<Boolean> grimDisabler = bool("GrimDisabler", false,
            () -> page.getValue() == Page.General);
    private final Setting<Boolean> instant = bool("Instant", false,
            () -> page.getValue() == Page.General);
    private final Setting<Boolean> wait = bool("Wait", true,
            () -> !instant.getValue() && page.getValue() == Page.General);
    private final Setting<Boolean> mineAir = bool("MineAir", true,
            () -> wait.getValue() && !instant.getValue() && page.getValue() == Page.General);
    private final Setting<Boolean> hotBar = bool("HotbarSwap", false,
            () -> page.getValue() == Page.General);

    private final Setting<Boolean> doubleBreak = bool("DoubleBreak", true,
            () -> page.getValue() == Page.General).setParent();
    public final Setting<Boolean> autoSwitch = bool("AutoSwitch", true,
            () -> page.getValue() == Page.General && doubleBreak.isOpen());
    private final Setting<Float> start = num("Start", 0.9f, 0f, 2f,
            () -> page.getValue() == Page.General && doubleBreak.isOpen());
    private final Setting<Float> timeOut = num("TimeOut", 1.2f, 0f, 2f,
            () -> page.getValue() == Page.General && doubleBreak.isOpen());

    private final Setting<Boolean> setAir = bool("SetAir", false, () -> page.getValue() == Page.General);
    private final Setting<Boolean> swing = bool("Swing", true, () -> page.getValue() == Page.General);
    private final Setting<Boolean> endSwing = bool("EndSwing", false, () -> page.getValue() == Page.General);
    public final Setting<Float> range = num("Range", 6f, 3f, 10f,
            () -> page.getValue() == Page.General);

    private final Setting<SwingSide> swingMode = mode("SwingSide", SwingSide.All,
            () -> page.getValue() == Page.General);

    // ==================== Check ====================
    private final Setting<Boolean> unbreakableCancel = bool("UnbreakableCancel", true, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> switchReset = bool("SwitchReset", false, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> preferWeb = bool("PreferWeb", true, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> preferHead = bool("PreferHead", true, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> farCancel = bool("FarCancel", false, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> onlyGround = bool("OnlyGround", true, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> checkWeb = bool("CheckWeb", true, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> checkGround = bool("CheckGround", true, () -> page.getValue() == Page.Check);
    private final Setting<Boolean> smart = bool("Smart", true,
            () -> page.getValue() == Page.Check && checkGround.getValue());

    private final Setting<Boolean> usingPause = bool("UsingPause", false,
            () -> page.getValue() == Page.Check).setParent();
    private final Setting<Boolean> allowOffhand = bool("AllowOffhand", true,
            () -> page.getValue() == Page.Check && usingPause.isOpen());
    private final Setting<Boolean> bypassGround = bool("BypassGround", true,
            () -> page.getValue() == Page.Check);
    private final Setting<Float> bypassTime = num("BypassTime", 400f, 0f, 2000f,
            () -> bypassGround.getValue() && page.getValue() == Page.Check);
    private final Setting<Integer> pause = bind("Pause", -1,
            () -> page.getValue() == Page.Check);

    // ==================== Rotation ====================
    private final Setting<Boolean> rotate = bool("StartRotate", true, () -> page.getValue() == Page.Rotation);
    private final Setting<Boolean> endRotate = bool("EndRotate", false, () -> page.getValue() == Page.Rotation);
    private final Setting<Float> syncTime = num("Sync", 300f, 0f, 1000f,
            () -> page.getValue() == Page.Rotation);
    private final Setting<Boolean> yawStep = bool("YawStep", false,
            () -> page.getValue() == Page.Rotation).setParent();
    private final Setting<Boolean> whenElytra = bool("FallFlying", true,
            () -> page.getValue() == Page.Rotation && yawStep.isOpen());
    private final Setting<Float> steps = num("Steps", 0.05f, 0f, 1f,
            () -> page.getValue() == Page.Rotation && yawStep.isOpen());
    private final Setting<Boolean> checkFov = bool("OnlyLooking", true,
            () -> page.getValue() == Page.Rotation && yawStep.isOpen());
    private final Setting<Float> fov = num("Fov", 20f, 0f, 360f,
            () -> page.getValue() == Page.Rotation && yawStep.isOpen());
    private final Setting<Float> priority = num("Priority", 10f, 0f, 100f,
            () -> page.getValue() == Page.Rotation && yawStep.isOpen());

    // ==================== Place ====================
    public final Setting<Boolean> crystal = bool("Crystal", false,
            () -> page.getValue() == Page.Place).setParent();
    private final Setting<Boolean> onlyHeadBomber = bool("OnlyCev", true,
            () -> page.getValue() == Page.Place && crystal.isOpen());
    private final Setting<Boolean> waitPlace = bool("WaitPlace", true,
            () -> page.getValue() == Page.Place && crystal.isOpen());
    private final Setting<Boolean> spamPlace = bool("SpamPlace", false,
            () -> page.getValue() == Page.Place && crystal.isOpen());
    private final Setting<Boolean> afterBreak = bool("AfterBreak", true,
            () -> page.getValue() == Page.Place && crystal.isOpen());
    private final Setting<Boolean> checkDamage = bool("DetectProgress", true,
            () -> page.getValue() == Page.Place && crystal.isOpen());
    private final Setting<Float> crystalDamage = num("Progress", 0.9f, 0f, 1f,
            () -> page.getValue() == Page.Place && crystal.isOpen() && checkDamage.getValue());
    public final Setting<Integer> obsidian = bind("Obsidian", -1, () -> page.getValue() == Page.Place);
    private final Setting<Integer> enderChest = bind("EnderChest", -1, () -> page.getValue() == Page.Place);
    private final Setting<Boolean> placeRotate = bool("PlaceRotate", true,
            () -> page.getValue() == Page.Place);
    private final Setting<Boolean> inventory = bool("InventorySwap", true,
            () -> page.getValue() == Page.Place);
    private final Setting<Float> placeDelay = num("PlaceDelay", 100f, 0f, 1000f,
            () -> page.getValue() == Page.Place);

    // ==================== Render ====================
    private final Setting<Boolean> checkDouble = bool("CheckDouble", false, () -> page.getValue() == Page.Render);
    private final Setting<Animation> animation = mode("Animation", Animation.Up, () -> page.getValue() == Page.Render);
    private final Setting<Easing> ease = mode("Ease", Easing.CubicInOut, () -> page.getValue() == Page.Render);
    private final Setting<Easing> fadeEase = mode("FadeEase", Easing.CubicInOut, () -> page.getValue() == Page.Render);
    private final Setting<Float> expandLine = num("ExpandLine", 0f, 0f, 1f, () -> page.getValue() == Page.Render);

    private final Setting<Color> startColor = color("StartFill", new Color(255, 255, 255, 100),
            () -> page.getValue() == Page.Render);
    private final Setting<Color> startOutlineColor = color("StartOutline", new Color(255, 255, 255, 255),
            () -> page.getValue() == Page.Render);
    private final Setting<Color> doubleColor = color("DoubleFill", new Color(88, 94, 255, 100),
            () -> doubleBreak.getValue() && page.getValue() == Page.Render);
    private final Setting<Color> doubleOutlineColor = color("DoubleOutline", new Color(88, 94, 255, 255),
            () -> doubleBreak.getValue() && page.getValue() == Page.Render);

    private final Setting<Boolean> text = bool("Text", true, () -> page.getValue() == Page.Render);
    private final Setting<Boolean> box = bool("Box", true, () -> page.getValue() == Page.Render);
    private final Setting<Boolean> outline = bool("Outline", true, () -> page.getValue() == Page.Render);
    private final Setting<Boolean> fill = bool("Fill", true, () -> page.getValue() == Page.Render);

    // ==================== 状态 ====================
    private final Timer mineTimer = new Timer();
    private final Timer sync = new Timer();
    private final Timer secondTimer = new Timer();
    private final Timer delayTimer = new Timer();
    private final Timer placeTimer = new Timer();
    private final Timer startTime = new Timer();

    public static boolean ghost = false;
    public static boolean complete = false;

    int lastSlot = -1;
    Vec3 directionVec = null;
    Runnable switchBack;
    BlockPos breakPos;
    boolean startPacket = false;
    int breakNumber = 0;
    double breakFinalTime;
    double secondFinalTime;
    boolean swapped = false;
    int mainSlot = 0;

    public PacketMine() {
        super("PacketMine", "1", Category.COMBAT);
        INSTANCE = this;
    }

    public static BlockPos getBreakPos() {
        return INSTANCE.isEnabled() ? INSTANCE.breakPos : null;
    }

    @Override
    public String getInfo() {
        return progress >= 1.0 ? "Done" : df.format(progress * 100.0) + "%";
    }

    // ==================== 工具方法 ====================
    private int getEfficiencyLevel(ItemStack stack) {
        if (stack.isEmpty() || mc.level == null) return 0;
        Holder<Enchantment> holder = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    private boolean isBindPressed(Setting<Integer> bind) {
        int key = bind.getValue();
        if (key == -1) return false;
        long handle = glfwGetCurrentContext();
        return handle != 0L && glfwGetKey(handle, key) == GLFW_PRESS;
    }

    private static AABB blockAABB(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    // ==================== 事件 ====================
    @Subscribe
    public void onRotate(RotationEvent event) {
        if (rotate.getValue() && shouldYawStep() && directionVec != null
                && !sync.passedMs(syncTime.getValue())) {
            event.setTarget(directionVec, steps.getValueFloat(), priority.getValueFloat());
        }
    }

    public void onLogin() {
        startPacket = false;
        ghost = false;
        complete = false;
        breakPos = null;
        secondPos = null;
    }

    @Override
    public void onDisable() {
        startPacket = false;
        ghost = false;
        complete = false;
        breakPos = null;
    }

    private void autoSwitch() {
        if (autoSwitch.getValue() && doubleBreak.getValue()) {
            int index = -1;
            if (secondPos != null) {
                float currentFastest = 1.0F;
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (!stack.isEmpty()) {
                        float digSpeed = getEfficiencyLevel(stack);
                        float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(secondPos));
                        if (digSpeed + destroySpeed > currentFastest) {
                            currentFastest = digSpeed + destroySpeed;
                            index = i;
                        }
                    }
                }
            }

            if (index != -1
                    && !mc.options.keyUse.isDown()
                    && !mc.options.keyAttack.isDown()
                    && !mc.player.isUsingItem()
                    && secondTimer.passedMs((long) getBreakTime(secondPos, index, start.getValue()))) {
                if (index != mc.player.getInventory().getSelectedSlot()) {
                    mainSlot = mc.player.getInventory().getSelectedSlot();
                    InventoryUtil.switchToSlot(index);
                    swapped = true;
                }
            } else if (swapped) {
                InventoryUtil.switchToSlot(mainSlot);
                swapped = false;
            }
        }
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        if (breakPos != null && mc.level.isEmptyBlock(breakPos)) {
            complete = true;
        }

        if (secondPos != null) {
            int secondSlot = getTool(secondPos);
            if (secondSlot == -1) secondSlot = mc.player.getInventory().getSelectedSlot();

            secondFinalTime = getBreakTime(secondPos, secondSlot, 1.0);
            if (!isAir(secondPos) && !unbreakable(secondPos)) {
                double time = getBreakTime(secondPos, mc.player.getInventory().getSelectedSlot(), 1.0);
                if (secondTimer.passedMs((long) (time * timeOut.getValue()))) {
                    secondPos = null;
                }
            } else {
                secondPos = null;
            }
        }

        if (switchBack != null) {
            switchBack.run();
            switchBack = null;
        }

        if (timing.getValue() != Timing.Post) {

            if (mc.player.isDeadOrDying()) {
                secondPos = null;
            }

            autoSwitch();

            if (mc.player.isCreative()) {
                startPacket = false;
                ghost = false;
                complete = false;
                breakNumber = 0;
                breakPos = null;
                progress = 0.0;
            } else if (breakPos == null) {
                breakNumber = 0;
                startPacket = false;
                ghost = false;
                complete = false;
                progress = 0.0;
            } else {
                int slot = getTool(breakPos);
                if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();

                breakFinalTime = getBreakTime(breakPos, slot);
                progress = mineTimer.getMs() / breakFinalTime;

                if (isAir(breakPos)) breakNumber = 0;

                if ((!(breakNumber > maxBreak.getValue() - 1.0) || !(maxBreak.getValue() > 0.0) || complete)
                        && (wait.getValue() || !isAir(breakPos) || instant.getValue())) {

                    if (unbreakable(breakPos)) {
                        if (unbreakableCancel.getValue()) {
                            breakPos = null;
                            startPacket = false;
                            ghost = false;
                            complete = false;
                        }
                        breakNumber = 0;
                    } else if (Math.sqrt(mc.player.getEyePosition().distanceToSqr(breakPos.getCenter())) > range.getValue()) {
                        if (farCancel.getValue()) {
                            startPacket = false;
                            ghost = false;
                            complete = false;
                            breakNumber = 0;
                            breakPos = null;
                        }
                    } else if (!usingPause.getValue()
                            || !mc.player.isUsingItem()
                            || (allowOffhand.getValue() && mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND)) {

                        if (!isBindPressed(pause)) {

                            if (hotBar.getValue() || EntityUtil.inInventory()) {

                                if (isAir(breakPos)) {
                                    if (shouldCrystal()) {
                                        for (Direction facing : Direction.values()) {
                                            CombatUtil.attackCrystal(breakPos.relative(facing), placeRotate.getValue(), true);
                                        }
                                    }

                                    if (placeTimer.passedMs((long) placeDelay.getValue().floatValue())
                                            && BlockUtil.canPlace(breakPos) && mc.screen == null) {
                                        if (isBindPressed(enderChest)) {
                                            int eChest = findBlock(Blocks.ENDER_CHEST);
                                            if (eChest != -1) {
                                                int oldSlot = mc.player.getInventory().getSelectedSlot();
                                                doSwap(eChest, eChest);
                                                BlockUtil.placeBlock(breakPos, placeRotate.getValue(), true);
                                                doSwap(oldSlot, eChest);
                                                placeTimer.reset();
                                            }
                                        } else if (isBindPressed(obsidian)) {
                                            int obsidian = findBlock(Blocks.OBSIDIAN);
                                            if (obsidian != -1) {
                                                boolean hasCrystal = false;
                                                if (shouldCrystal()) {
                                                    for (Entity entity : BlockUtil.getEntities(blockAABB(breakPos.above()))) {
                                                        if (entity instanceof EndCrystal) {
                                                            hasCrystal = true;
                                                            break;
                                                        }
                                                    }
                                                }

                                                if (!hasCrystal || spamPlace.getValue()) {
                                                    int oldSlot = mc.player.getInventory().getSelectedSlot();
                                                    doSwap(obsidian, obsidian);
                                                    BlockUtil.placeBlock(breakPos, placeRotate.getValue(), true);
                                                    doSwap(oldSlot, obsidian);
                                                    placeTimer.reset();
                                                }
                                            }
                                        }
                                    }

                                    breakNumber = 0;
                                } else if (canPlaceCrystal(breakPos.above()) && shouldCrystal()) {
                                    if (placeTimer.passedMs((long) placeDelay.getValue().floatValue())) {
                                        if (checkDamage.getValue()) {
                                            if (mineTimer.getMs() / breakFinalTime >= crystalDamage.getValue() && !placeCrystal()) {
                                                return;
                                            }
                                        } else if (!placeCrystal()) {
                                            return;
                                        }
                                    } else if (startPacket) {
                                        return;
                                    }
                                }

                                if (delayTimer.passed(stopDelay.getValue().longValue())) {
                                    if (startPacket) {
                                        if (isAir(breakPos)) return;
                                        if (onlyGround.getValue() && !mc.player.onGround()) return;

                                        if (mineTimer.passed((long) breakFinalTime)) {
                                            if (endRotate.getValue() && shouldYawStep()
                                                    && !faceVector(breakPos.getCenter().add(
                                                    sideVec(BlockUtil.getClickSide(breakPos), 0.5)))) {
                                                return;
                                            }

                                            int old = mc.player.getInventory().getSelectedSlot();
                                            boolean shouldSwitch;
                                            if (hotBar.getValue()) {
                                                shouldSwitch = slot != old;
                                            } else {
                                                if (slot < 9) slot += 36;
                                                shouldSwitch = old + 36 != slot;
                                            }

                                            if (shouldSwitch) {
                                                if (hotBar.getValue()) {
                                                    InventoryUtil.switchToSlot(slot);
                                                } else {
                                                    mc.gameMode.handleInventoryMouseClick(
                                                            mc.player.containerMenu.containerId, slot, old,
                                                            ClickType.SWAP, mc.player);
                                                }
                                            }

                                            int finalSlot = slot;
                                            switchBack = () -> {
                                                if (endRotate.getValue()
                                                        && !faceVector(breakPos.getCenter().add(
                                                        sideVec(BlockUtil.getClickSide(breakPos), 0.5)))) {
                                                    if (shouldSwitch) {
                                                        if (hotBar.getValue()) {
                                                            InventoryUtil.switchToSlot(old);
                                                        } else {
                                                            mc.gameMode.handleInventoryMouseClick(
                                                                    mc.player.containerMenu.containerId, finalSlot, old,
                                                                    ClickType.SWAP, mc.player);
                                                            EntityUtil.syncInventory();
                                                        }
                                                    }
                                                } else {
                                                    sendSequencedPacket(id -> new ServerboundPlayerActionPacket(
                                                            Action.STOP_DESTROY_BLOCK, breakPos,
                                                            BlockUtil.getClickSide(breakPos), id));
                                                    if (endSwing.getValue()) {
                                                        EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
                                                    }
                                                    if (shouldSwitch) {
                                                        if (hotBar.getValue()) {
                                                            InventoryUtil.switchToSlot(old);
                                                        } else {
                                                            mc.gameMode.handleInventoryMouseClick(
                                                                    mc.player.containerMenu.containerId, finalSlot, old,
                                                                    ClickType.SWAP, mc.player);
                                                            EntityUtil.syncInventory();
                                                        }
                                                    }

                                                    breakNumber++;
                                                    delayTimer.reset();
                                                    startTime.reset();

                                                    if (afterBreak.getValue() && shouldCrystal()) {
                                                        for (Direction facing : Direction.values()) {
                                                            CombatUtil.attackCrystal(breakPos.relative(facing), placeRotate.getValue(), true);
                                                        }
                                                    }

                                                    if (setAir.getValue()) {
                                                        mc.level.setBlock(breakPos, Blocks.AIR.defaultBlockState(), 3);
                                                    }

                                                    if (endRotate.getValue() && !shouldYawStep()) {
                                                        OyVey.rotationManager.snapBack();
                                                    }

                                                    ghost = true;
                                                }
                                            };
                                            if (!noGhostHand.getValue()) {
                                                switchBack.run();
                                                switchBack = null;
                                            }
                                        }
                                    } else {
                                        if (!startTime.passed(startDelay.getValueInt())) return;
                                        if (!mineAir.getValue() && isAir(breakPos)) return;

                                        Direction side = BlockUtil.getClickSide(breakPos);
                                        if (rotate.getValue()) {
                                            if (!faceVector(breakPos.getCenter().add(sideVec(side, 0.5)))) {
                                                return;
                                            }
                                        }

                                        mineTimer.reset();
                                        animationTime.reset();

                                        if (swing.getValue()) {
                                            EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
                                        }

                                        if (doubleBreak.getValue()) {
                                            if (secondPos == null || isAir(secondPos)) {
                                                double breakTime = getBreakTime(breakPos, slot, 1.0);
                                                secondAnim.reset();
                                                secondAnim.setLength((long) breakTime);
                                                secondTimer.reset();
                                                secondPos = breakPos;
                                            }
                                            doDoubleBreak(side);
                                        }

                                        sendSequencedPacket(id -> new ServerboundPlayerActionPacket(
                                                Action.START_DESTROY_BLOCK, breakPos, side, id));
                                        if (rotate.getValue() && !shouldYawStep()) {
                                            OyVey.rotationManager.snapBack();
                                        }
                                        startTime.reset();
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (breakPos.equals(secondPos)) secondPos = null;
                    startPacket = false;
                    ghost = false;
                    complete = false;
                    breakNumber = 0;
                    breakPos = null;
                }
            }
        }
    }

    private static Vec3 sideVec(Direction dir, double scale) {
        return new Vec3(dir.getStepX() * scale,
                dir.getStepY() * scale,
                dir.getStepZ() * scale);
    }

    void doDoubleBreak(Direction side) {
        sendSequencedPacket(id -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, breakPos, side, id));
        sendSequencedPacket(id -> new ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, breakPos, side, id));
    }

    boolean placeCrystal() {
        int crystal = findCrystal();
        if (crystal != -1) {
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            doSwap(crystal, crystal);
            BlockUtil.placeCrystal(breakPos.above(), placeRotate.getValue());
            doSwap(oldSlot, crystal);
            placeTimer.reset();
            return !waitPlace.getValue();
        }
        return true;
    }

    @Subscribe
    public void onAttackBlock(ClickBlockEvent event) {
        if (nullCheck()) return;
        if (mc.player.isCreative()) return;

        event.cancel();
        BlockPos pos = event.getPos();
        if (pos.equals(breakPos)) return;
        if (unbreakable(pos)) return;

        if (breakPos != null && preferWeb.getValue() && BlockUtil.getBlock(breakPos) == Blocks.COBWEB) return;

        if (breakPos != null && preferHead.getValue()
                && mc.player.isVisuallyCrawling()
                && EntityUtil.getPlayerPos(true).above().equals(breakPos)) return;

        if (BlockUtil.getClickSideStrict(pos) == null) return;
        if (Math.sqrt(mc.player.getEyePosition().distanceToSqr(pos.getCenter())) > range.getValue()) return;

        breakPos = pos;
        breakNumber = 0;
        startPacket = false;
        ghost = false;
        complete = false;
        mineTimer.reset();
        animationTime.reset();

        Direction side = BlockUtil.getClickSide(breakPos);
        if (rotate.getValue()) {
            if (!faceVector(breakPos.getCenter().add(sideVec(side, 0.5)))) return;
        }

        if (startTime.passed(startDelay.getValueInt())) {
            if (swing.getValue()) {
                EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
            }

            // Alien 原版：先发 START+STOP 秒破
            if (doubleBreak.getValue()) {
                if (secondPos == null || isAir(secondPos)) {
                    int slot = getTool(breakPos);
                    if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();
                    secondFinalTime = getBreakTime(breakPos, slot, 1.0);
                    secondAnim.reset();
                    secondAnim.setLength((long) secondFinalTime);
                    secondTimer.reset();
                    secondPos = breakPos;
                }
                doDoubleBreak(side);
            }

            int slot = getTool(breakPos);
            if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();
            breakFinalTime = getBreakTime(breakPos, slot);

            sendSequencedPacket(id -> new ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, breakPos, side, id));
            if (rotate.getValue() && !shouldYawStep()) {
                OyVey.rotationManager.snapBack();
            }
            startTime.reset();
        }
    }

    public void mine(BlockPos pos) {
        if (nullCheck()) return;
        if (mc.player.isCreative()) {
            mc.gameMode.startDestroyBlock(pos, BlockUtil.getClickSide(pos));
            return;
        }
        if (!isEnabled()) {
            mc.gameMode.startDestroyBlock(pos, BlockUtil.getClickSide(pos));
            return;
        }

        if (pos.equals(breakPos)) return;
        if (unbreakable(pos)) return;
        if (breakPos != null && preferWeb.getValue() && BlockUtil.getBlock(breakPos) == Blocks.COBWEB) return;
        if (breakPos != null && preferHead.getValue()
                && EntityUtil.getPlayerPos(true).above().equals(breakPos)) return;
        if (BlockUtil.getClickSideStrict(pos) == null) return;
        if (Math.sqrt(mc.player.getEyePosition().distanceToSqr(pos.getCenter())) > range.getValue()) return;

        breakPos = pos;
        breakNumber = 0;
        startPacket = false;
        ghost = false;
        complete = false;
        mineTimer.reset();
        animationTime.reset();
    }

    boolean faceVector(Vec3 directionVec) {
        if (!shouldYawStep()) {
            OyVey.rotationManager.lookAtVec3d(directionVec);
            return true;
        }
        sync.reset();
        this.directionVec = directionVec;
        return OyVey.rotationManager.inFov(directionVec, fov.getValueFloat()) || !checkFov.getValue();
    }

    // ==================== 渲染（改这里）====================
    @Override
    public void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;
        if (mc.level == null) return;

        PoseStack poseStack = event.getMatrix();

        // secondPos：蓝色，不管方块有没有被挖掉
        if (secondPos != null && !secondPos.equals(breakPos)) {
            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(poseStack, secondPos, doubleColor.getValue());
            }
            if (outline.getValue()) {
                RenderUtil.drawBox(poseStack, secondPos, doubleOutlineColor.getValue(), 2.0f);
            }
        }

        // breakPos：白色，不管方块有没有被挖掉
        if (breakPos != null) {
            if (fill.getValue()) {
                RenderUtil.drawBoxFilled(poseStack, breakPos, startColor.getValue());
            }
            if (outline.getValue()) {
                RenderUtil.drawBox(poseStack, breakPos, startOutlineColor.getValue(), 2.0f);
            }
        }
    }

    // ==================== 包处理 ====================
    @Subscribe(priority = -200)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck() || mc.player.isCreative()) return;

        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            // bypassGround 暂时关闭
        } else if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
            if (packet.getSlot() != lastSlot) {
                lastSlot = packet.getSlot();
                if (switchReset.getValue()) {
                    startPacket = false;
                    ghost = false;
                    complete = false;
                    mineTimer.reset();
                    animationTime.reset();
                }
            }
        } else if (event.getPacket() instanceof ServerboundPlayerActionPacket packet) {
            if (packet.getAction() == Action.START_DESTROY_BLOCK) {
                if (breakPos == null || !packet.getPos().equals(breakPos)) return;
                if (grimDisabler.getValue()) {
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            Action.STOP_DESTROY_BLOCK, packet.getPos(), packet.getDirection()));
                }
                startPacket = true;
            } else if (packet.getAction() == Action.STOP_DESTROY_BLOCK) {
                if (breakPos == null || !packet.getPos().equals(breakPos)) return;
                if (!instant.getValue()) {
                    startPacket = false;
                    ghost = false;
                    complete = false;
                }
            }
        }
    }

    // ==================== 辅助逻辑 ====================
    boolean canPlaceCrystal(BlockPos pos) {
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        return (BlockUtil.getBlock(obsPos) == Blocks.BEDROCK || BlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN)
                && BlockUtil.getClickSideStrict(obsPos) != null
                && noEntity(boost)
                && noEntity(boost.above())
                && mc.level.isEmptyBlock(boost.above());
    }

    boolean noEntity(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(blockAABB(pos))) {
            if (!(entity instanceof ItemEntity) && (!(entity instanceof ArmorStand))) {
                return false;
            }
        }
        return true;
    }

    void doSwap(int slot, int inv) {
        if (!inventory.getValue()) {
            InventoryUtil.switchToSlot(slot);
        } else {
            InventoryUtil.inventorySwap(inv, mc.player.getInventory().getSelectedSlot());
        }
    }

    int findCrystal() {
        return inventory.getValue()
                ? InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL)
                : InventoryUtil.findItem(Items.END_CRYSTAL);
    }

    int findBlock(Block block) {
        return inventory.getValue()
                ? InventoryUtil.findBlockInventorySlot(block)
                : InventoryUtil.findBlock(block);
    }

    boolean shouldCrystal() {
        return crystal.getValue() && (!onlyHeadBomber.getValue() || isBindPressed(obsidian));
    }

    public static double getBreakTime(BlockPos pos) {
        int slot = INSTANCE.getTool(pos);
        if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();
        return INSTANCE.getBreakTime(pos, slot);
    }

    double getBreakTime(BlockPos pos, int slot) {
        return getBreakTime(pos, slot, damage.getValue());
    }

    double getBreakTime(BlockPos pos, int slot, double damageMultiplier) {
        return 1.0F / getBlockStrength(pos, mc.player.getInventory().getItem(slot)) / 20.0F * 1000.0F * damageMultiplier;
    }

    float getBlockStrength(BlockPos position, ItemStack itemStack) {
        BlockState state = mc.level.getBlockState(position);
        float hardness = state.getDestroySpeed(mc.level, position);
        if (hardness < 0.0F) return 0.0F;
        float i = state.requiresCorrectToolForDrops() && !itemStack.isCorrectToolForDrops(state) ? 100.0F : 30.0F;
        return getDigSpeed(state, itemStack) / hardness / i;
    }

    float getDigSpeed(BlockState state, ItemStack itemStack) {
        float digSpeed = getDestroySpeed(state, itemStack);
        if (digSpeed > 1.0F) {
            int efficiencyModifier = getEfficiencyLevel(itemStack);
            if (efficiencyModifier > 0 && !itemStack.isEmpty()) {
                digSpeed += (float) (StrictMath.pow(efficiencyModifier, 2.0) + 1.0);
            }
        }

        if (mc.player.isUnderWater()) {
            var attr = mc.player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
            if (attr != null) digSpeed *= (float) attr.getValue();
        }

        boolean inWeb = checkWeb.getValue()
                && mc.level.getBlockState(mc.player.blockPosition()).is(Blocks.COBWEB)
                && mc.level.getBlockState(breakPos).getBlock() == Blocks.COBWEB;

        if ((!mc.player.onGround() || inWeb)
                && checkGround.getValue()
                && (!smart.getValue() || mc.player.isFallFlying() || inWeb)) {
            digSpeed /= 5.0F;
        }

        return Math.max(digSpeed, 0.0F);
    }

    float getDestroySpeed(BlockState state, ItemStack itemStack) {
        float destroySpeed = 1.0F;
        if (itemStack != null && !itemStack.isEmpty()) {
            destroySpeed *= itemStack.getDestroySpeed(state);
        }
        return destroySpeed;
    }

    int getTool(BlockPos pos) {
        if (hotBar.getValue()) {
            int index = -1;
            float currentFastest = 1.0F;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    float digSpeed = getEfficiencyLevel(stack);
                    float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));
                    if (digSpeed + destroySpeed > currentFastest) {
                        currentFastest = digSpeed + destroySpeed;
                        index = i;
                    }
                }
            }
            return index;
        } else {
            AtomicInteger slot = new AtomicInteger(-1);
            float currentFastest = 1.0F;
            for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    float digSpeed = getEfficiencyLevel(entry.getValue());
                    float destroySpeed = entry.getValue().getDestroySpeed(mc.level.getBlockState(pos));
                    if (digSpeed + destroySpeed > currentFastest) {
                        currentFastest = digSpeed + destroySpeed;
                        slot.set(entry.getKey());
                    }
                }
            }
            return slot.get();
        }
    }

    private boolean shouldYawStep() {
        return false;
    }

    boolean isAir(BlockPos breakPos) {
        return mc.level.isEmptyBlock(breakPos)
                || (BlockUtil.getBlock(breakPos) == Blocks.FIRE && BlockUtil.hasCrystal(breakPos));
    }

    public static boolean unbreakable(BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        if (state.getBlock() instanceof AirBlock) return false;
        float hardness = state.getDestroySpeed(mc.level, blockPos);
        return hardness == -1.0F || hardness == 100.0F;
    }

    public enum Animation {
        Center, Grow, Up, Down, Oscillation, None
    }

    public enum Page {
        General, Check, Place, Rotation, Render
    }
}