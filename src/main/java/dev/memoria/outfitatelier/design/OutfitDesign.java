package dev.memoria.outfitatelier.design;

import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class OutfitDesign {
    public static final int MAX_COLORS = 127;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int PIXEL_COUNT = GarmentTemplate.TEXTURE_SIZE * GarmentTemplate.TEXTURE_SIZE;
    public static final int EMPTY = 0;
    private static final int MAX_PACKED_BYTES = 32768;

    public record Artwork(GarmentTemplate template, int[] palette, byte[] pixels) {}

    public final UUID id;
    public final String name;
    public final UUID authorId;
    public final String authorName;
    public final GarmentTemplate template;
    public final long createdAt;
    public final boolean deleted;
    private final int[] palette;
    private final byte[] pixels;

    public OutfitDesign(UUID id, String name, UUID authorId, String authorName, GarmentTemplate template,
                        long createdAt, boolean deleted, int[] palette, byte[] pixels) {
        this.id = id;
        this.name = name;
        this.authorId = authorId;
        this.authorName = authorName;
        this.template = template;
        this.createdAt = createdAt;
        this.deleted = deleted;
        this.palette = palette;
        this.pixels = pixels;
    }

    public static boolean isValid(String name, Artwork artwork) {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH || artwork.template() == null) {
            return false;
        }
        if (artwork.palette().length > MAX_COLORS || artwork.pixels().length != PIXEL_COUNT) {
            return false;
        }
        boolean painted = false;
        for (byte pixel : artwork.pixels()) {
            if (pixel < 0 || pixel > artwork.palette().length) {
                return false;
            }
            painted |= pixel != EMPTY;
        }
        return painted;
    }

    public OutfitDesign asDeleted() {
        return new OutfitDesign(id, name, authorId, authorName, template, createdAt, true, new int[0], new byte[0]);
    }

    public int argbAt(int x, int y) {
        return argbOf(pixels[y * GarmentTemplate.TEXTURE_SIZE + x], palette);
    }

    public static int argbOf(int index, int[] palette) {
        return index == EMPTY ? 0 : 0xFF000000 | palette[index - 1] & 0xFFFFFF;
    }

    public Artwork artwork() {
        return new Artwork(template, palette.clone(), pixels.clone());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putUUID("AuthorId", authorId);
        tag.putString("AuthorName", authorName);
        tag.putString("Template", template.name());
        tag.putLong("CreatedAt", createdAt);
        tag.putBoolean("Deleted", deleted);
        if (!deleted) {
            tag.putIntArray("Palette", palette);
            tag.putByteArray("Pixels", pack(pixels));
        }
        return tag;
    }

    public static OutfitDesign load(CompoundTag tag) {
        boolean deleted = tag.getBoolean("Deleted");
        return new OutfitDesign(tag.getUUID("Id"), tag.getString("Name"), tag.getUUID("AuthorId"), tag.getString("AuthorName"),
                GarmentTemplate.byName(tag.getString("Template")), tag.getLong("CreatedAt"), deleted,
                deleted ? new int[0] : tag.getIntArray("Palette"),
                deleted ? new byte[0] : unpack(tag.getByteArray("Pixels")));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeUtf(name, MAX_NAME_LENGTH);
        buffer.writeUUID(authorId);
        buffer.writeUtf(authorName, 64);
        buffer.writeLong(createdAt);
        writeArtwork(buffer, new Artwork(template, palette, pixels));
    }

    public static OutfitDesign read(FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(MAX_NAME_LENGTH);
        UUID authorId = buffer.readUUID();
        String authorName = buffer.readUtf(64);
        long createdAt = buffer.readLong();
        Artwork artwork = readArtwork(buffer);
        return new OutfitDesign(id, name, authorId, authorName, artwork.template(), createdAt, false, artwork.palette(), artwork.pixels());
    }

    public static void writeArtwork(FriendlyByteBuf buffer, Artwork artwork) {
        buffer.writeEnum(artwork.template());
        buffer.writeVarIntArray(artwork.palette());
        buffer.writeByteArray(pack(artwork.pixels()));
    }

    public static Artwork readArtwork(FriendlyByteBuf buffer) {
        GarmentTemplate template = buffer.readEnum(GarmentTemplate.class);
        int[] palette = buffer.readVarIntArray(MAX_COLORS);
        return new Artwork(template, palette, unpack(buffer.readByteArray(MAX_PACKED_BYTES)));
    }

    private static byte[] pack(byte[] pixels) {
        Deflater deflater = new Deflater();
        deflater.setInput(pixels);
        deflater.finish();
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        while (!deflater.finished()) {
            packed.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return packed.toByteArray();
    }

    private static byte[] unpack(byte[] packed) {
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        byte[] pixels = new byte[PIXEL_COUNT];
        try {
            int read = 0;
            while (read < PIXEL_COUNT && !inflater.finished()) {
                int count = inflater.inflate(pixels, read, PIXEL_COUNT - read);
                if (count == 0 && inflater.needsInput()) {
                    break;
                }
                read += count;
            }
            if (read != PIXEL_COUNT) {
                throw new DecoderException("Bad outfit pixel data");
            }
            return pixels;
        } catch (DataFormatException invalid) {
            throw new DecoderException("Bad outfit pixel data", invalid);
        } finally {
            inflater.end();
        }
    }
}
