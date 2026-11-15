package dev.memoria.outfitatelier.client;

import dev.memoria.outfitatelier.design.WardrobeSlot;
import dev.memoria.outfitatelier.wardrobe.WardrobeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class WardrobeScreen extends AbstractContainerScreen<WardrobeMenu> {
    public WardrobeScreen(WardrobeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 138;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF2B2530);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFCDBFD6);
        for (Slot slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF4E4358);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFFA899B4);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 5, 0x3A3040, false);
        for (WardrobeSlot slot : WardrobeSlot.values()) {
            Component label = Component.translatable("outfitatelier.gui.slot." + slot.name().toLowerCase());
            int center = WardrobeMenu.SLOT_X + slot.ordinal() * WardrobeMenu.SLOT_SPACING + 8;
            graphics.drawString(font, label, center - font.width(label) / 2, WardrobeMenu.SLOT_Y + 19, 0x3A3040, false);
        }
    }
}
