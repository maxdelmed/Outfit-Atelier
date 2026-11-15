package dev.memoria.outfitatelier.client;

import dev.memoria.outfitatelier.design.GarmentTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public class ClientWardrobes {
    public record Preview(GarmentTemplate template, ResourceLocation texture) {}

    private static final Map<Integer, UUID[]> WORN = new HashMap<>();
    private static Preview preview;

    public static void put(int entityId, UUID[] designs) {
        WORN.put(entityId, designs);
    }

    public static UUID[] get(int entityId) {
        return WORN.get(entityId);
    }

    public static boolean hasGarments(int entityId) {
        UUID[] designs = WORN.get(entityId);
        if (designs == null) {
            return false;
        }
        for (UUID design : designs) {
            if (design != null) {
                return true;
            }
        }
        return false;
    }

    public static void remove(int entityId) {
        WORN.remove(entityId);
    }

    public static void clear() {
        WORN.clear();
        preview = null;
    }

    public static Preview preview() {
        return preview;
    }

    public static void setPreview(Preview shown) {
        preview = shown;
    }
}
