package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.network.Network;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public class ClientOutfits {
    public record Loaded(OutfitDesign design, ResourceLocation texture) {}

    private static final int MAX_LOADED = 256;
    private static final Map<UUID, Loaded> LOADED = new LinkedHashMap<>(64, 0.75F, true);
    private static final Set<UUID> DELETED = new HashSet<>();
    private static final Set<UUID> REQUESTED = new HashSet<>();

    public static Loaded getOrRequest(UUID id) {
        if (id == null || DELETED.contains(id)) {
            return null;
        }
        Loaded loaded = LOADED.get(id);
        if (loaded == null && REQUESTED.add(id)) {
            Network.sendToServer(new Network.RequestDesignMessage(id));
        }
        return loaded;
    }

    public static boolean isDeleted(UUID id) {
        return DELETED.contains(id);
    }

    public static void put(OutfitDesign design) {
        release(LOADED.remove(design.id));
        NativeImage image = new NativeImage(GarmentTemplate.TEXTURE_SIZE, GarmentTemplate.TEXTURE_SIZE, true);
        for (int y = 0; y < GarmentTemplate.TEXTURE_SIZE; y++) {
            for (int x = 0; x < GarmentTemplate.TEXTURE_SIZE; x++) {
                image.setPixelRGBA(x, y, toAbgr(design.argbAt(x, y)));
            }
        }
        ResourceLocation texture = Minecraft.getInstance().getTextureManager().register("outfitatelier_design", new DynamicTexture(image));
        LOADED.put(design.id, new Loaded(design, texture));
        evictOldest();
    }

    private static void evictOldest() {
        Iterator<Map.Entry<UUID, Loaded>> oldest = LOADED.entrySet().iterator();
        while (LOADED.size() > MAX_LOADED && oldest.hasNext()) {
            Map.Entry<UUID, Loaded> entry = oldest.next();
            release(entry.getValue());
            REQUESTED.remove(entry.getKey());
            oldest.remove();
        }
    }

    public static void markDeleted(UUID id) {
        DELETED.add(id);
        release(LOADED.remove(id));
    }

    public static void clear() {
        LOADED.values().forEach(ClientOutfits::release);
        LOADED.clear();
        DELETED.clear();
        REQUESTED.clear();
    }

    private static void release(Loaded loaded) {
        if (loaded != null) {
            Minecraft.getInstance().getTextureManager().release(loaded.texture());
        }
    }

    public static int toAbgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
    }
}
