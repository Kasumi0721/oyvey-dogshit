package me.alpha432.oyvey.util.inventory;

import me.alpha432.oyvey.util.BlockUtil;
import me.alpha432.oyvey.util.EntityUtil;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public class InventoryUtil implements Util {

    static int lastSlot = -1;
    static int lastSelect = -1;
    public static void click(int slot, int button, ClickType type) {
        int id = mc.player.containerMenu.containerId;
        mc.gameMode.handleInventoryMouseClick(id, slot, button, type, mc.player);
    }
    /** 硬编码：原 AntiCheat.invSwapBypass */
    private static final boolean INV_SWAP_BYPASS = false;
    /** 硬编码：原 AntiCheat.priorHotbar */
    private static final boolean PRIOR_HOTBAR = false;
    public static final int FULL_SCOPE = 0;
    public static final int HOTBAR_SCOPE = 1;

    public static Result find(Item item, int scope) {
        if (scope == HOTBAR_SCOPE) {
            // 只搜快捷栏 0-8
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (s.is(item)) return new Result(i, s, ResultType.HOTBAR);
            }
        } else {
            // 先搜背包 9-35
            for (int i = 9; i < 36; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (s.is(item)) return new Result(i, s, ResultType.INVENTORY);
            }
            // 再搜快捷栏 0-8
            for (int i = 0; i < 9; i++) {
                ItemStack s = mc.player.getInventory().getItem(i);
                if (s.is(item)) return new Result(i, s, ResultType.HOTBAR);
            }
        }
        // 最后副手
        ItemStack off = mc.player.getOffhandItem();
        if (off.is(item)) return Result.fromOffhand(off);

        return new Result(-1, ItemStack.EMPTY, ResultType.NONE);
    }



// ==================== 临时切换并执行 ====================
public static int selected() {
    return mc.player.getInventory().getSelectedSlot();
}
    public static void withSwap(Result result, Runnable action) {
        if (result == null) return;

        if (result.hand() == InteractionHand.OFF_HAND) {
            action.run();
            return;
        }

        int old = mc.player.getInventory().getSelectedSlot();
        int target = result.slot();

        if (target >= 9) {
            // 背包物品：换到当前快捷栏槽位
            int hotbarSlot = old;
            mc.gameMode.handleInventoryMouseClick(
                    mc.player.containerMenu.containerId,
                    target, hotbarSlot, ClickType.SWAP, mc.player);
            switchToSlot(hotbarSlot);
            action.run();
            switchToSlot(old);
            // 换回背包
            mc.gameMode.handleInventoryMouseClick(
                    mc.player.containerMenu.containerId,
                    target, hotbarSlot, ClickType.SWAP, mc.player);
        } else {
            switchToSlot(target);
            action.run();
            switchToSlot(old);
        }
    }
    // ==================== 交换 ====================

    public static void inventorySwap(int slot, int selectedSlot) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect);
            lastSlot = -1;
            lastSelect = -1;
            return;
        }
        if (slot - 36 == selectedSlot) return;
        if (!EntityUtil.inInventory()) return;

        mc.gameMode.handleInventoryMouseClick(
                mc.player.containerMenu.containerId,
                slot, selectedSlot, ClickType.SWAP, mc.player);
    }

    public static void switchToSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    // ==================== 查找物品 ====================

    public static int findItem(Item input) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == input) return i;
        }
        return -1;
    }

    public static int getFood() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).has(DataComponents.FOOD)) return i;
        }
        return -1;
    }

    public static int getPotionCount(MobEffect targetEffect) {
        int count = 0;
        for (int i = 35; i >= 0; i--) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() != Items.SPLASH_POTION) continue;

            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (MobEffectInstance effect : contents.getAllEffects()) {
                if (effect.getEffect().value() == targetEffect) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    public static int getItemCount(Class<?> clazz) {
        int count = 0;
        for (Map.Entry<Integer, ItemStack> entry : getInventoryAndHotbarSlots().entrySet()) {
            Item it = entry.getValue().getItem();
            if (it instanceof BlockItem bi && clazz.isInstance(bi.getBlock())) {
                count += entry.getValue().getCount();
            }
        }
        return count;
    }

    public static int getItemCount(Item item) {
        int count = 0;
        for (Map.Entry<Integer, ItemStack> entry : getInventoryAndHotbarSlots().entrySet()) {
            if (entry.getValue().getItem() == item) count += entry.getValue().getCount();
        }
        if (mc.player.getOffhandItem().getItem() == item) {
            count += mc.player.getOffhandItem().getCount();
        }
        return count;
    }

    public static int findClass(Class<?> clazz) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (clazz.isInstance(stack.getItem())) return i;
            if (stack.getItem() instanceof BlockItem bi && clazz.isInstance(bi.getBlock())) return i;
        }
        return -1;
    }

    public static int findClassInventorySlot(Class<?> clazz) {
        if (PRIOR_HOTBAR) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (clazz.isInstance(stack.getItem())
                        || (stack.getItem() instanceof BlockItem bi && clazz.isInstance(bi.getBlock()))) {
                    return i < 9 ? i + 36 : i;
                }
            }
        } else {
            for (int i = 35; i >= 0; i--) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (clazz.isInstance(stack.getItem())
                        || (stack.getItem() instanceof BlockItem bi && clazz.isInstance(bi.getBlock()))) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }
        return -1;
    }

    public static int findBlock(Block blockIn) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()
                    && stack.getItem() instanceof BlockItem bi
                    && bi.getBlock() == blockIn) {
                return i;
            }
        }
        return -1;
    }

    public static int findUnBlock() {
        for (int i = 0; i < 9; i++) {
            if (!(mc.player.getInventory().getItem(i).getItem() instanceof BlockItem)) return i;
        }
        return -1;
    }

    public static int findBlock() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem bi
                    && !BlockUtil.isClickable(bi.getBlock())
                    && bi.getBlock() != Blocks.COBWEB) {
                return i;
            }
        }
        return -1;
    }

    public static int findBlockInventorySlot(Block block) {
        return findItemInventorySlot(block.asItem());
    }

    public static int findItemInventorySlot(Item item) {
        if (PRIOR_HOTBAR) {
            return findItemInventorySlotFromZero(item);
        }
        for (int i = 35; i >= 0; i--) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findItemInventorySlotFromZero(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == item) {
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    // ==================== 全背包 ====================

    public static Map<Integer, ItemStack> getInventoryAndHotbarSlots() {
        HashMap<Integer, ItemStack> map = new HashMap<>();
        for (int i = 0; i <= 35; i++) {
            map.put(i, mc.player.getInventory().getItem(i));
        }
        return map;
    }
}