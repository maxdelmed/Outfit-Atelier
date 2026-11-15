package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class ColorPicker {
    public static final int WHEEL_SIZE = 60;
    public static final int SLIDER_WIDTH = 8;
    private static final int SLIDER_GAP = 4;
    private static final int TEXTURE_SIZE = 64;

    private final int x;
    private final int y;
    private final ResourceLocation wheelTexture;
    private float hue;
    private float saturation;
    private float value = 1.0F;
    private boolean draggingWheel;
    private boolean draggingSlider;

    public ColorPicker(int x, int y) {
        this.x = x;
        this.y = y;
        this.wheelTexture = Minecraft.getInstance().getTextureManager().register("outfitatelier_wheel", new DynamicTexture(wheelImage()));
    }

    private static NativeImage wheelImage() {
        NativeImage image = new NativeImage(TEXTURE_SIZE, TEXTURE_SIZE, true);
        float radius = TEXTURE_SIZE / 2.0F;
        for (int py = 0; py < TEXTURE_SIZE; py++) {
            for (int px = 0; px < TEXTURE_SIZE; px++) {
                float dx = px + 0.5F - radius;
                float dy = py + 0.5F - radius;
                float distance = Mth.sqrt(dx * dx + dy * dy) / radius;
                if (distance <= 1.0F) {
                    int rgb = Mth.hsvToRgb(hueOf(dx, dy), distance, 1.0F);
                    image.setPixelRGBA(px, py, ClientOutfits.toAbgr(0xFF000000 | rgb));
                }
            }
        }
        return image;
    }

    private static float hueOf(float dx, float dy) {
        float turns = (float) (Math.atan2(dy, dx) / (Math.PI * 2.0));
        return Mth.clamp(turns < 0 ? turns + 1.0F : turns, 0.0F, 0.9999F);
    }

    public int getRgb() {
        return Mth.hsvToRgb(hue, saturation, value) & 0xFFFFFF;
    }

    public void setRgb(int rgb) {
        float red = (rgb >> 16 & 0xFF) / 255.0F;
        float green = (rgb >> 8 & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float delta = max - Math.min(red, Math.min(green, blue));
        value = max;
        saturation = max == 0 ? 0 : delta / max;
        if (delta == 0) {
            return;
        }
        float sector;
        if (max == red) {
            sector = (green - blue) / delta % 6.0F;
        } else if (max == green) {
            sector = (blue - red) / delta + 2.0F;
        } else {
            sector = (red - green) / delta + 4.0F;
        }
        hue = Mth.clamp((sector < 0 ? sector + 6.0F : sector) / 6.0F, 0.0F, 0.9999F);
    }

    public void render(GuiGraphics graphics) {
        RenderSystem.enableBlend();
        graphics.setColor(value, value, value, 1.0F);
        graphics.blit(wheelTexture, x, y, WHEEL_SIZE, WHEEL_SIZE, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        double angle = hue * Math.PI * 2.0;
        int markerX = x + WHEEL_SIZE / 2 + (int) Math.round(Math.cos(angle) * saturation * WHEEL_SIZE / 2.0);
        int markerY = y + WHEEL_SIZE / 2 + (int) Math.round(Math.sin(angle) * saturation * WHEEL_SIZE / 2.0);
        graphics.fill(markerX - 2, markerY - 2, markerX + 2, markerY + 2, 0xFF000000);
        graphics.fill(markerX - 1, markerY - 1, markerX + 1, markerY + 1, 0xFFFFFFFF);

        int sliderX = sliderX();
        int fullColor = 0xFF000000 | Mth.hsvToRgb(hue, saturation, 1.0F);
        graphics.fillGradient(sliderX, y, sliderX + SLIDER_WIDTH, y + WHEEL_SIZE, fullColor, 0xFF000000);
        int valueY = y + Math.round((1.0F - value) * (WHEEL_SIZE - 1));
        graphics.fill(sliderX - 1, valueY - 1, sliderX + SLIDER_WIDTH + 1, valueY + 1, 0xFFFFFFFF);
    }

    private int sliderX() {
        return x + WHEEL_SIZE + SLIDER_GAP;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        double dx = mouseX - (x + WHEEL_SIZE / 2.0);
        double dy = mouseY - (y + WHEEL_SIZE / 2.0);
        draggingWheel = dx * dx + dy * dy <= WHEEL_SIZE * WHEEL_SIZE / 4.0;
        draggingSlider = mouseX >= sliderX() - 1 && mouseX < sliderX() + SLIDER_WIDTH + 1 && mouseY >= y && mouseY < y + WHEEL_SIZE;
        return mouseDragged(mouseX, mouseY);
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (draggingWheel) {
            float dx = (float) (mouseX - (x + WHEEL_SIZE / 2.0));
            float dy = (float) (mouseY - (y + WHEEL_SIZE / 2.0));
            hue = hueOf(dx, dy);
            saturation = Mth.clamp(Mth.sqrt(dx * dx + dy * dy) / (WHEEL_SIZE / 2.0F), 0.0F, 1.0F);
        } else if (draggingSlider) {
            value = 1.0F - Mth.clamp((float) (mouseY - y) / (WHEEL_SIZE - 1), 0.0F, 1.0F);
        }
        return draggingWheel || draggingSlider;
    }

    public void mouseReleased() {
        draggingWheel = false;
        draggingSlider = false;
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(wheelTexture);
    }
}
