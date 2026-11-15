package dev.memoria.outfitatelier.wardrobe;

import dev.memoria.outfitatelier.ModContent;
import dev.memoria.outfitatelier.design.WardrobeSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

public class WardrobeMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 44;
    public static final int SLOT_SPACING = 24;
    public static final int SLOT_Y = 24;
    private static final int PLAYER_START = WardrobeSlot.COUNT;
    private static final int PLAYER_END = PLAYER_START + 36;

    public WardrobeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new ItemStackHandler(WardrobeSlot.COUNT));
    }

    public WardrobeMenu(int containerId, Inventory inventory, IItemHandler garments) {
        super(ModContent.WARDROBE_MENU.get(), containerId);
        for (int slot = 0; slot < WardrobeSlot.COUNT; slot++) {
            addSlot(new SlotItemHandler(garments, slot, SLOT_X + slot * SLOT_SPACING, SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 56 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 114));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < PLAYER_START
                ? moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)
                : moveItemStackTo(stack, 0, PLAYER_START, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }
}
