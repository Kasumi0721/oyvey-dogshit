package me.alpha432.oyvey.util;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.combat.PacketMine;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BlockUtil implements Util {

    public static final List<BlockPos> placedPos = new ArrayList<>();
    private static final double IE_RANGE = 6.0;

    // ==================== 放置检测 ====================

    public static boolean canPlace(BlockPos pos) {
        return canPlace(pos, 1000.0);
    }

    public static boolean canPlace(BlockPos pos, double distance) {
        if (getPlaceSide(pos, distance) == null) return false;
        return canReplace(pos) && !hasEntity(pos, false);
    }

    public static boolean canPlace(BlockPos pos, double distance, boolean ignoreCrystal) {
        if (getPlaceSide(pos, distance) == null) return false;
        return canReplace(pos) && !hasEntity(pos, ignoreCrystal);
    }

    public static boolean clientCanPlace(BlockPos pos, boolean ignoreCrystal) {
        return canReplace(pos) && !hasEntity(pos, ignoreCrystal);
    }

    // ==================== 实体查询 ====================

    public static List<Entity> getEntities(AABB box) {
        List<Entity> list = new ArrayList<>();
        if (mc.level == null) return list;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity != null && entity.getBoundingBox().intersects(box)) {
                list.add(entity);
            }
        }
        return list;
    }

    public static List<EndCrystal> getEndCrystals(AABB box) {
        List<EndCrystal> list = new ArrayList<>();
        for (Entity entity : getEntities(box)) {
            if (entity instanceof EndCrystal crystal) list.add(crystal);
        }
        return list;
    }

    public static boolean hasEntity(BlockPos pos, boolean ignoreCrystal) {
        return hasEntity(blockAABB(pos), ignoreCrystal);
    }

    /** 判断一个 AABB 内是否有"阻挡"实体（忽略物品/经验球/箭/经验瓶） */
    public static boolean hasEntity(AABB box, boolean ignoreCrystal) {
        for (Entity entity : getEntities(box)) {
            if (!entity.isAlive()) continue;
            if (entity instanceof ItemEntity) continue;
            if (entity instanceof ExperienceOrb) continue;

            String name = entity.getClass().getSimpleName();
            if (name.equals("Arrow") || name.equals("AbstractArrow")
                    || name.contains("ExperienceBottle") || name.contains("Spear")) continue;

            if (!ignoreCrystal
                    || !(entity instanceof EndCrystal)
                    || !(mc.player.getEyePosition().distanceTo(getClosestPoint(entity)) <= IE_RANGE)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasCrystal(BlockPos pos) {
        for (EndCrystal entity : getEndCrystals(blockAABB(pos))) {
            if (entity.isAlive()) return true;
        }
        return false;
    }

    public static boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal) {
        return noEntityBlockCrystal(pos, ignoreCrystal, false);
    }

    public static boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        for (Entity entity : getEntities(blockAABB(pos))) {
            if (!entity.isAlive()) continue;
            if (ignoreItem && entity instanceof ItemEntity) continue;

            String name = entity.getClass().getSimpleName();
            if (name.equals("Arrow") || name.equals("AbstractArrow")
                    || name.contains("ExperienceBottle") || name.contains("Spear")) continue;

            if (!ignoreCrystal
                    || !(entity instanceof EndCrystal)
                    || !(mc.player.getEyePosition().distanceTo(getClosestPoint(entity)) <= IE_RANGE)) {
                return false;
            }
        }
        return true;
    }

    private static Vec3 getClosestPoint(Entity entity) {
        AABB box = entity.getBoundingBox();
        Vec3 eye = mc.player.getEyePosition();
        return new Vec3(
                Math.max(box.minX, Math.min(eye.x, box.maxX)),
                Math.max(box.minY, Math.min(eye.y, box.maxY)),
                Math.max(box.minZ, Math.min(eye.z, box.maxZ)));
    }

    // ==================== 水晶放置 ====================

    public static boolean canPlaceCrystal(BlockPos pos) {
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        BlockState below = mc.level.getBlockState(obsPos);
        return (below.is(Blocks.BEDROCK) || below.is(Blocks.OBSIDIAN))
                && getClickSideStrict(obsPos) != null
                && mc.level.isEmptyBlock(boost)
                && noEntityBlockCrystal(boost, false)
                && noEntityBlockCrystal(boost.above(), false);
    }

    public static void placeCrystal(BlockPos pos, boolean rotate) {
        boolean offhand = mc.player.getOffhandItem().is(Items.END_CRYSTAL);
        BlockPos obsPos = pos.below();
        Direction facing = getClickSide(obsPos);
        Vec3 vec = obsPos.getCenter().add(
                facing.getStepX() * 0.5,
                facing.getStepY() * 0.5,
                facing.getStepZ() * 0.5);
        if (rotate) OyVey.rotationManager.lookAtVec3d(vec);
        clickBlock(obsPos, facing, false,
                offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    // ==================== 方块放置 ====================

    public static void placeBlock(BlockPos pos, boolean rotate) {
        placeBlock(pos, rotate, true);
    }

    public static void placeBlock(BlockPos pos, boolean rotate, boolean packet) {
        Direction side = getPlaceSide(pos);
        if (side != null) {
            placedPos.add(pos);
            clickBlock(pos.relative(side), side.getOpposite(), rotate,
                    InteractionHand.MAIN_HAND, packet);
        }
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate) {
        clickBlock(pos, side, rotate, InteractionHand.MAIN_HAND);
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, InteractionHand hand) {
        clickBlock(pos, side, rotate, hand, true);
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate, boolean packet) {
        clickBlock(pos, side, rotate, InteractionHand.MAIN_HAND, packet);
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate,
                                  InteractionHand hand, boolean packet) {
        Vec3 directionVec = new Vec3(
                pos.getX() + 0.5 + side.getStepX() * 0.5,
                pos.getY() + 0.5 + side.getStepY() * 0.5,
                pos.getZ() + 0.5 + side.getStepZ() * 0.5);
        if (rotate) OyVey.rotationManager.lookAtVec3d(directionVec);

        EntityUtil.swingHand(hand, SwingSide.All);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (packet) {
            mc.getConnection().send(new ServerboundUseItemOnPacket(hand, result, 0));
        } else {
            mc.gameMode.useItemOn(mc.player, hand, result);
        }
        mc.rightClickDelay = 4;
        if (rotate) OyVey.rotationManager.snapBack();
    }

    public static void clickBlock(BlockPos pos, Direction side, boolean rotate,
                                  InteractionHand hand, SwingSide swingSide) {
        Vec3 directionVec = new Vec3(
                pos.getX() + 0.5 + side.getStepX() * 0.5,
                pos.getY() + 0.5 + side.getStepY() * 0.5,
                pos.getZ() + 0.5 + side.getStepZ() * 0.5);
        if (rotate) OyVey.rotationManager.lookAtVec3d(directionVec);
        EntityUtil.swingHand(hand, swingSide);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        mc.getConnection().send(new ServerboundUseItemOnPacket(hand, result, 0));
        mc.rightClickDelay = 4;
        if (rotate) OyVey.rotationManager.snapBack();
    }

    public static void airPlace(BlockPos pos, boolean rotate) {
        airPlace(pos, rotate, InteractionHand.MAIN_HAND, true);
    }

    public static void airPlace(BlockPos pos, boolean rotate, InteractionHand hand, boolean packet) {
        Direction side = getClickSide(pos);
        Vec3 directionVec = new Vec3(
                pos.getX() + 0.5 + side.getStepX() * 0.5,
                pos.getY() + 0.5 + side.getStepY() * 0.5,
                pos.getZ() + 0.5 + side.getStepZ() * 0.5);
        if (rotate) OyVey.rotationManager.lookAtVec3d(directionVec);

        EntityUtil.swingHand(hand, SwingSide.All);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (packet) {
            mc.getConnection().send(new ServerboundUseItemOnPacket(hand, result, 0));
        } else {
            mc.gameMode.useItemOn(mc.player, hand, result);
        }
        mc.rightClickDelay = 4;
        if (rotate) OyVey.rotationManager.snapBack();
    }

    // ==================== 距离工具 ====================

    public static double distanceToXZ(double x, double z, double x2, double z2) {
        double dx = x2 - x;
        double dz = z2 - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static double distanceToXZ(double x, double z) {
        return distanceToXZ(x, z, mc.player.getX(), mc.player.getZ());
    }

    // ==================== 面选择 ====================

    public static Direction getPlaceSide(BlockPos pos) {
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (canClick(pos.relative(i))
                    && !canReplace(pos.relative(i))
                    && isStrictDirection(pos.relative(i), i.getOpposite())) {
                double vecDis = mc.player.getEyePosition().distanceToSqr(
                        pos.getCenter().add(i.getStepX() * 0.5,
                                i.getStepY() * 0.5,
                                i.getStepZ() * 0.5));
                if (vecDis < minDistance) {
                    side = i;
                    minDistance = vecDis;
                }
            }
        }
        return side;
    }

    public static Direction getBestNeighboring(BlockPos pos, Direction facing) {
        Direction bestFacing = null;
        double distance = 0.0;
        for (Direction i : Direction.values()) {
            if ((facing == null || !pos.relative(i).equals(pos.relative(facing, -1)))
                    && i != Direction.DOWN
                    && getPlaceSide(pos) != null
                    && (bestFacing == null
                    || mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter()) < distance)) {
                bestFacing = i;
                distance = mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter());
            }
        }
        return bestFacing;
    }

    public static Direction getPlaceSide(BlockPos pos, double reachDistance) {
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (canClick(pos.relative(i))
                    && !canReplace(pos.relative(i))
                    && isStrictDirection(pos.relative(i), i.getOpposite())) {
                double vecDis = mc.player.getEyePosition().distanceToSqr(
                        pos.getCenter().add(i.getStepX() * 0.5,
                                i.getStepY() * 0.5,
                                i.getStepZ() * 0.5));
                if (Math.sqrt(vecDis) <= reachDistance && vecDis < minDistance) {
                    side = i;
                    minDistance = vecDis;
                }
            }
        }
        return side;
    }

    public static Direction getClickSide(BlockPos pos) {
        Direction side = Direction.UP;
        double minDistance = Double.MAX_VALUE;
        for (Direction i : Direction.values()) {
            if (isStrictDirection(pos, i)) {
                double disSq = mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter());
                if (disSq < minDistance) {
                    side = i;
                    minDistance = disSq;
                }
            }
        }
        return side;
    }

    public static Direction getClickSideStrict(BlockPos pos) {
        Direction side = null;
        double minDistance = Double.MAX_VALUE;
        for (Direction i : Direction.values()) {
            if (isStrictDirection(pos, i)) {
                double disSq = mc.player.getEyePosition().distanceToSqr(pos.relative(i).getCenter());
                if (disSq < minDistance) {
                    side = i;
                    minDistance = disSq;
                }
            }
        }
        return side;
    }

    public static boolean isStrictDirection(BlockPos pos, Direction side, double reachDistance) {
        double vecDis = mc.player.getEyePosition().distanceToSqr(
                pos.getCenter().add(side.getStepX() * 0.5,
                        side.getStepY() * 0.5,
                        side.getStepZ() * 0.5));
        return Math.sqrt(vecDis) <= reachDistance && isStrictDirection(pos, side);
    }

    // ==================== 默认 NCP 反作弊 ====================
    public static boolean isStrictDirection(BlockPos pos, Direction side) {
        BlockState neighbor = mc.level.getBlockState(pos.relative(side));
        if (neighbor.isCollisionShapeFullBlock(mc.level, pos.relative(side))) return false;

        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 blockCenter = pos.getCenter();
        ArrayList<Direction> validAxis = new ArrayList<>();
        validAxis.addAll(checkAxis(eyePos.x - blockCenter.x, Direction.WEST, Direction.EAST, false));
        validAxis.addAll(checkAxis(eyePos.y - blockCenter.y, Direction.DOWN, Direction.UP, true));
        validAxis.addAll(checkAxis(eyePos.z - blockCenter.z, Direction.NORTH, Direction.SOUTH, false));
        return validAxis.contains(side);
    }

    public static ArrayList<Direction> checkAxis(double diff, Direction negativeSide,
                                                 Direction positiveSide, boolean vertical) {
        ArrayList<Direction> valid = new ArrayList<>();
        if (vertical) {
            if (diff < -0.5) valid.add(negativeSide);
            if (diff > -0.5) valid.add(positiveSide);
        } else {
            if (diff < -0.5) valid.add(negativeSide);
            if (diff > 0.5) valid.add(positiveSide);
        }
        return valid;
    }

    // ==================== 区块/球体查询 ====================

    public static ArrayList<BlockEntity> getTileEntities() {
        return getLoadedChunks()
                .flatMap(chunk -> chunk.getBlockEntities().values().stream())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static Stream<LevelChunk> getLoadedChunks() {
        int radius = Math.max(2, mc.options.getEffectiveRenderDistance()) + 3;
        int diameter = radius * 2 + 1;
        BlockPos center = mc.player.blockPosition();
        BlockPos min = center.offset(-radius, 0, -radius);
        BlockPos max = center.offset(radius, 0, radius);
        return Stream.iterate(min, pos -> {
                    int x = pos.getX();
                    int z = pos.getZ();
                    if (++x > max.getX()) { x = min.getX(); z++; }
                    return new BlockPos(x, pos.getY(), z);
                }).limit((long) diameter * diameter)
                .filter(c -> mc.level.hasChunk(c.getX() >> 4, c.getZ() >> 4))
                .map(c -> mc.level.getChunk(c.getX() >> 4, c.getZ() >> 4))
                .filter(Objects::nonNull);
    }

    public static ArrayList<BlockPos> getSphere(float range) {
        return getSphere(range, mc.player.getEyePosition());
    }

    public static BlockPos getBlock(Block block, float range) {
        for (BlockPos pos : getSphere(range)) {
            if (mc.level.getBlockState(pos).is(block)) return pos;
        }
        return null;
    }

    public static BlockPos getBlock(Class<?> block, float range) {
        for (BlockPos pos : getSphere(range)) {
            if (block.isInstance(mc.level.getBlockState(pos).getBlock())) return pos;
        }
        return null;
    }

    public static ArrayList<BlockPos> getSphere(float range, Vec3 pos) {
        ArrayList<BlockPos> list = new ArrayList<>();
        for (double y = pos.y + range; y > pos.y - range; y--) {
            if (y < -64.0) continue;
            for (double x = pos.x - range; x < pos.x + range; x++) {
                for (double z = pos.z - range; z < pos.z + range; z++) {
                    BlockPos curPos = BlockPos.containing(x, y, z);
                    if (curPos.getCenter().distanceTo(pos) <= range) list.add(curPos);
                }
            }
        }
        return list;
    }

    public static Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }

    public static boolean canReplace(BlockPos pos) {
        if (pos.getY() >= 320) return false;
        if (placedPos.contains(pos)) return false;
        return mc.level.getBlockState(pos).canBeReplaced();
    }

    public static boolean canClick(BlockPos pos) {
        if (placedPos.contains(pos)) return true;
        Block block = mc.level.getBlockState(pos).getBlock();
        return mc.player.isShiftKeyDown() || !isClickable(block);
    }

    public static boolean isClickable(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof BaseEntityBlock
                || block instanceof BedBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof TrapDoorBlock;
    }

    public static boolean canCollide(AABB box) {
        return canCollide(mc.player, box);
    }

    public static boolean canCollide(@Nullable Entity entity, AABB box) {
        return mc.level.getBlockCollisions(entity, box).iterator().hasNext();
    }

    public static boolean allowAirPlace() {
        return false;
    }

    // ==================== 内部工具 ====================

    public static AABB blockAABB(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }
}