package dev.memoria.outfitatelier.service;

import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.OutfitDesign;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

public record DesignSummary(UUID id, String name, String authorName, GarmentTemplate template, long createdAt, boolean deleted) {
    public static DesignSummary of(OutfitDesign design) {
        return new DesignSummary(design.id, design.name, design.authorName, design.template, design.createdAt, design.deleted);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeUtf(name, OutfitDesign.MAX_NAME_LENGTH);
        buffer.writeUtf(authorName, 64);
        buffer.writeEnum(template);
        buffer.writeLong(createdAt);
        buffer.writeBoolean(deleted);
    }

    public static DesignSummary read(FriendlyByteBuf buffer) {
        return new DesignSummary(buffer.readUUID(), buffer.readUtf(OutfitDesign.MAX_NAME_LENGTH), buffer.readUtf(64),
                buffer.readEnum(GarmentTemplate.class), buffer.readLong(), buffer.readBoolean());
    }
}
