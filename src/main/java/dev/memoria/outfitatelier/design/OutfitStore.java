package dev.memoria.outfitatelier.design;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class OutfitStore extends SavedData {
    private static final String FILE_NAME = "outfitatelier_designs";

    private final Map<UUID, OutfitDesign> designs = new LinkedHashMap<>();

    public static OutfitStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(OutfitStore::load, OutfitStore::new, FILE_NAME);
    }

    public OutfitDesign get(UUID id) {
        return id == null ? null : designs.get(id);
    }

    public boolean isWearable(UUID id) {
        OutfitDesign design = get(id);
        return design != null && !design.deleted;
    }

    public void put(OutfitDesign design) {
        designs.put(design.id, design);
        setDirty();
    }

    public boolean markDeleted(UUID id) {
        OutfitDesign design = get(id);
        if (design == null || design.deleted) {
            return false;
        }
        put(design.asDeleted());
        return true;
    }

    public List<OutfitDesign> liveDesignsBy(UUID authorId) {
        List<OutfitDesign> owned = new ArrayList<>();
        for (OutfitDesign design : designs.values()) {
            if (!design.deleted && design.authorId.equals(authorId)) {
                owned.add(design);
            }
        }
        return owned;
    }

    public List<OutfitDesign> search(String query) {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<OutfitDesign> matches = new ArrayList<>();
        for (OutfitDesign design : designs.values()) {
            if (needle.isEmpty()
                    || design.name.toLowerCase(Locale.ROOT).contains(needle)
                    || design.authorName.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(design);
            }
        }
        matches.sort(Comparator.comparingLong((OutfitDesign design) -> design.createdAt).reversed());
        return matches;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (OutfitDesign design : designs.values()) {
            list.add(design.save());
        }
        tag.put("Designs", list);
        return tag;
    }

    public static OutfitStore load(CompoundTag tag) {
        OutfitStore store = new OutfitStore();
        ListTag list = tag.getList("Designs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            OutfitDesign design = OutfitDesign.load(list.getCompound(i));
            if (design.template != null) {
                store.designs.put(design.id, design);
            }
        }
        return store;
    }
}
