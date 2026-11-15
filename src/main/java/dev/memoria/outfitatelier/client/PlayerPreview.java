package dev.memoria.outfitatelier.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;

public class PlayerPreview {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private float angle = 20.0F;
    private boolean dragging;

    public PlayerPreview(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(GuiGraphics graphics) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF202020);
        graphics.fill(x, y, x + width, y + height, 0xFF55606C);
        LocalPlayer player = Minecraft.getInstance().player;
        float bodyRot = player.yBodyRot;
        float bodyRotOld = player.yBodyRotO;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float headRot = player.yHeadRot;
        float headRotOld = player.yHeadRotO;
        player.yBodyRot = 180.0F + angle;
        player.yBodyRotO = player.yBodyRot;
        player.setYRot(player.yBodyRot);
        player.setXRot(0);
        player.yHeadRot = player.yBodyRot;
        player.yHeadRotO = player.yBodyRot;
        InventoryScreen.renderEntityInInventory(graphics, x + width / 2, y + height - 12, height * 2 / 5,
                new Quaternionf().rotateZ((float) Math.PI), null, player);
        player.yBodyRot = bodyRot;
        player.yBodyRotO = bodyRotOld;
        player.setYRot(yRot);
        player.setXRot(xRot);
        player.yHeadRot = headRot;
        player.yHeadRotO = headRotOld;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        dragging = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        return dragging;
    }

    public boolean mouseDragged(double dragX) {
        if (dragging) {
            angle += (float) dragX * 2.0F;
        }
        return dragging;
    }

    public void mouseReleased() {
        dragging = false;
    }
}
