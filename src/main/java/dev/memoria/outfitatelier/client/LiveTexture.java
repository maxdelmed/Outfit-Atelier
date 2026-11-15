package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.OutfitCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public class LiveTexture {
    private final DynamicTexture texture = new DynamicTexture(
            new NativeImage(GarmentTemplate.TEXTURE_SIZE, GarmentTemplate.TEXTURE_SIZE, true));
    private final ResourceLocation id = Minecraft.getInstance().getTextureManager().register("outfitatelier_canvas", texture);

    public ResourceLocation id() {
        return id;
    }

    public void refresh(OutfitCanvas canvas) {
        NativeImage image = texture.getPixels();
        for (int y = 0; y < GarmentTemplate.TEXTURE_SIZE; y++) {
            for (int x = 0; x < GarmentTemplate.TEXTURE_SIZE; x++) {
                image.setPixelRGBA(x, y, ClientOutfits.toAbgr(canvas.argbAt(x, y)));
            }
        }
        texture.upload();
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }
}
