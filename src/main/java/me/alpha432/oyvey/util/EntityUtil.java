package me.alpha432.oyvey.util;

import me.alpha432.oyvey.features.modules.combat.PacketMine;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EntityUtil implements Util {

    public static boolean inInventory() {
        return mc.screen == null
                || mc.screen instanceof OptionsScreen
                || mc.screen instanceof ChatScreen
                || mc.screen instanceof InventoryScreen
                || mc.screen instanceof PauseScreen;
    }
    public static boolean isInsideBlock() {
        return isInsideBlock(mc.player);
    }

    public static boolean isInsideBlock(Player player) {
        if (mc.level == null || player == null) return false;
        return mc.level.getBlockCollisions(player, player.getBoundingBox()).iterator().hasNext();
    }
    public static boolean isHoldingWeapon(Player player) {
        ItemStack main = player.getMainHandItem();
        return main.is(ItemTags.SWORDS) || main.is(ItemTags.AXES);
    }

    public static int getDamagePercent(ItemStack stack) {
        return stack.getDamageValue() == stack.getMaxDamage()
                ? 100
                : (int) ((stack.getMaxDamage() - stack.getDamageValue())
                / Math.max(0.1, stack.getMaxDamage()) * 100.0);
    }

    public static boolean isArmorLow(Player player, int durability) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece.isEmpty()) return true;
            if (getDamagePercent(piece) < durability) return true;
        }
        return false;
    }

    public static float getHealth(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.getHealth() + living.getAbsorptionAmount();
        }
        return 0.0F;
    }

    public static BlockPos getEntityPos(Entity entity) {
        return BlockPos.containing(entity.position());
    }

    public static BlockPos getPlayerPos(boolean fix) {
        Vec3 pos = mc.player.position();
        return fix ? BlockPos.containing(pos.x, pos.y + 0.5, pos.z)
                : BlockPos.containing(pos);
    }

    public static BlockPos getEntityPos(Entity entity, boolean fix) {
        Vec3 pos = entity.position();
        return fix ? BlockPos.containing(pos.x, pos.y + 0.5, pos.z)
                : BlockPos.containing(pos);
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3 n = new Vec3(side.getStepX(), side.getStepY(), side.getStepZ());
        Vec3 testVec = pos.getCenter().add(n.x * 0.5, n.y * 0.5, n.z * 0.5);
        BlockHitResult result = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(),
                testVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    public static void swingHand(InteractionHand hand, SwingSide side) {
        switch (side) {
            case All -> mc.player.swing(hand);
            case Client -> mc.player.swing(hand, false);
            case Server -> mc.getConnection().send(new ServerboundSwingPacket(hand));
        }
    }

    public static void syncInventory() {
        mc.getConnection().send(new ServerboundContainerClosePacket(
                mc.player.containerMenu.containerId));
    }
}