package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.BlockUtil;
import me.alpha432.oyvey.util.CombatUtil;
import me.alpha432.oyvey.util.EntityUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Breaker extends Module {
    public static Breaker INSTANCE;

    private final Setting<Float> targetRange = num("TargetRange", 6f, 0f, 8f, () -> true);
    private final Setting<Float> range = num("Range", 6f, 0f, 8f, () -> true);
    private final Setting<Boolean> burrow = bool("Burrow", true, () -> true);
    private final Setting<Boolean> head = bool("Head", true, () -> true);
    private final Setting<Boolean> face = bool("Face", true, () -> true);
    private final Setting<Boolean> down = bool("Down", false, () -> true);
    private final Setting<Boolean> surround = bool("Surround", true, () -> true);
    private final Setting<Boolean> cevPause = bool("CevPause", true, () -> true);
    private final Setting<Boolean> forceDouble = bool("ForceDouble", false, () -> true);

    public static final List<Block> hard = Arrays.asList(
            Blocks.OBSIDIAN,
            Blocks.ENDER_CHEST,
            Blocks.NETHERITE_BLOCK,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.ANCIENT_DEBRIS,
            Blocks.ANVIL
    );

    public Breaker() {
        super("Breaker", "自动挖掘", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;

        Player player = CombatUtil.getClosestEnemy(targetRange.getValue());
        if (player != null) {
            doBreak(player);
        }
    }

    private void doBreak(Player player) {
        BlockPos pos = EntityUtil.getEntityPos(player, true);

        if (PacketMine.getBreakPos() == null
                || PacketMine.getBreakPos().equals(PacketMine.secondPos)
                || PacketMine.secondPos == null
                || mc.level.isEmptyBlock(PacketMine.secondPos)
                || !forceDouble.getValue()) {

            double[] yOffset = new double[]{-0.8, 0.3, 1.1};
            double[] xzOffset = new double[]{0.3, -0.3};

            // 检查敌人周围是否已经有在挖的方块
            for (Player entity : CombatUtil.getEnemies(targetRange.getValue())) {
                for (double y : yOffset) {
                    for (double x : xzOffset) {
                        for (double z : xzOffset) {
                            BlockPos offsetPos = BlockPos.containing(
                                    entity.getX() + x, entity.getY() + y, entity.getZ() + z);
                            if (canBreak(offsetPos) && offsetPos.equals(PacketMine.getBreakPos())) {
                                return;
                            }
                        }
                    }
                }
            }

            List<Float> yList = new ArrayList<>();
            if (down.getValue()) yList.add(-0.8f);
            if (head.getValue()) yList.add(2.3f);
            if (burrow.getValue()) yList.add(0.3f);
            if (face.getValue()) yList.add(1.1f);

            // 第一轮：单轴偏移
            for (float y : yList) {
                for (double offset : xzOffset) {
                    BlockPos offsetPos = BlockPos.containing(
                            player.getX() + offset, player.getY() + y, player.getZ() + offset);
                    if (canBreak(offsetPos)) {
                        PacketMine.INSTANCE.mine(offsetPos);
                        return;
                    }
                }
            }

            // 第二轮：双轴组合
            for (float y : yList) {
                for (double offsetX : xzOffset) {
                    for (double offsetZ : xzOffset) {
                        BlockPos offsetPos = BlockPos.containing(
                                player.getX() + offsetZ, player.getY() + y, player.getZ() + offsetX);
                        if (canBreak(offsetPos)) {
                            PacketMine.INSTANCE.mine(offsetPos);
                            return;
                        }
                    }
                }
            }

            if (surround.getValue()) {
                // 检查敌人周围是否有正在挖的
                for (Direction i : Direction.values()) {
                    if (i != Direction.UP && i != Direction.DOWN
                            && Math.sqrt(mc.player.getEyePosition()
                            .distanceToSqr(pos.relative(i).getCenter())) <= range.getValue()
                            && (mc.level.isEmptyBlock(pos.relative(i))
                            || pos.relative(i).equals(PacketMine.getBreakPos()))
                            && canPlaceCrystal(pos.relative(i), false)
                            && !pos.relative(i).equals(PacketMine.secondPos)) {
                        return;
                    }
                }

                // 找能挖的方块
                ArrayList<BlockPos> list = new ArrayList<>();

                for (Direction i : Direction.values()) {
                    if (i != Direction.UP && i != Direction.DOWN
                            && Math.sqrt(mc.player.getEyePosition()
                            .distanceToSqr(pos.relative(i).getCenter())) <= range.getValue()
                            && canBreak(pos.relative(i))
                            && canPlaceCrystal(pos.relative(i), true)
                            && !isSurroundPos(pos.relative(i))) {
                        list.add(pos.relative(i));
                    }
                }

                if (!list.isEmpty()) {
                    PacketMine.INSTANCE.mine(list.stream()
                            .min(Comparator.comparingDouble(e -> e.distToCenterSqr(mc.player.getEyePosition())))
                            .get());
                } else {
                    for (Direction i : Direction.values()) {
                        if (i != Direction.UP && i != Direction.DOWN
                                && Math.sqrt(mc.player.getEyePosition()
                                .distanceToSqr(pos.relative(i).getCenter())) <= range.getValue()
                                && canBreak(pos.relative(i))
                                && canPlaceCrystal(pos.relative(i), false)) {
                            list.add(pos.relative(i));
                        }
                    }

                    if (!list.isEmpty()) {
                        PacketMine.INSTANCE.mine(list.stream()
                                .min(Comparator.comparingDouble(e -> e.distToCenterSqr(mc.player.getEyePosition())))
                                .get());
                    }
                }
            }
        }
    }

    private boolean isSurroundPos(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (i != Direction.UP && i != Direction.DOWN) {
                BlockPos self = EntityUtil.getPlayerPos(true);
                if (self.relative(i).equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canPlaceCrystal(BlockPos pos, boolean block) {
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        return (BlockUtil.getBlock(obsPos) == Blocks.BEDROCK
                || BlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN
                || !block)
                && BlockUtil.noEntityBlockCrystal(boost, true, true)
                && BlockUtil.noEntityBlockCrystal(boost.above(), true, true);
    }

    private boolean isObsidian(BlockPos pos) {
        return mc.player.getEyePosition().distanceTo(pos.getCenter()) <= PacketMine.INSTANCE.range.getValue()
                && hard.contains(BlockUtil.getBlock(pos))
                && BlockUtil.getClickSideStrict(pos) != null;
    }

    private boolean canBreak(BlockPos pos) {
        return isObsidian(pos)
                && (BlockUtil.getClickSideStrict(pos) != null || pos.equals(PacketMine.getBreakPos()))
                && (!pos.equals(PacketMine.secondPos)
                || (!mc.player.getMainHandItem().is(ItemTags.PICKAXES)
                && !PacketMine.INSTANCE.autoSwitch.getValue()
                && !PacketMine.INSTANCE.noGhostHand.getValue()));
    }
}