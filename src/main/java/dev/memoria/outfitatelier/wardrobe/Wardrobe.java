package dev.memoria.outfitatelier.wardrobe;

import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.OutfitStore;
import dev.memoria.outfitatelier.design.WardrobeSlot;
import dev.memoria.outfitatelier.item.GarmentItem;
import dev.memoria.outfitatelier.network.Network;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;

public class Wardrobe implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<Wardrobe> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    private final Entity owner;
    private final LazyOptional<Wardrobe> self = LazyOptional.of(() -> this);
    private final ItemStackHandler items = new ItemStackHandler(WardrobeSlot.COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            GarmentTemplate template = GarmentItem.template(stack);
            return GarmentItem.designId(stack) != null && template != null && template.slot.ordinal() == slot;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            sync();
        }
    };

    public Wardrobe(Entity owner) {
        this.owner = owner;
    }

    public static Wardrobe of(Entity entity) {
        return entity.getCapability(CAPABILITY).orElse(null);
    }

    public ItemStackHandler items() {
        return items;
    }

    public boolean isEmpty() {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean wear(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        GarmentTemplate template = GarmentItem.template(held);
        if (template == null || !isWearable(held)) {
            return false;
        }
        ItemStack previous = items.getStackInSlot(template.slot.ordinal());
        player.setItemInHand(hand, previous);
        items.setStackInSlot(template.slot.ordinal(), held);
        return true;
    }

    public boolean put(ItemStack garment) {
        GarmentTemplate template = GarmentItem.template(garment);
        if (template == null || !isWearable(garment) || !items.getStackInSlot(template.slot.ordinal()).isEmpty()) {
            return false;
        }
        items.setStackInSlot(template.slot.ordinal(), garment.copyWithCount(1));
        return true;
    }

    public ItemStack takeLast() {
        for (int slot = items.getSlots() - 1; slot >= 0; slot--) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                items.setStackInSlot(slot, ItemStack.EMPTY);
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean isWearable(ItemStack garment) {
        boolean wearable = OutfitStore.get(owner.getServer()).isWearable(GarmentItem.designId(garment));
        if (!wearable) {
            GarmentItem.markDeleted(garment);
        }
        return wearable;
    }

    public UUID[] visibleDesigns() {
        UUID[] designs = new UUID[WardrobeSlot.COUNT];
        for (int slot = 0; slot < designs.length; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty() && isWearable(stack)) {
                designs[slot] = GarmentItem.designId(stack);
            }
        }
        return designs;
    }

    public void sync() {
        if (owner.level() instanceof ServerLevel && owner.isAddedToWorld()) {
            Network.sendWardrobeToTrackers(owner, visibleDesigns());
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return CAPABILITY.orEmpty(capability, self);
    }

    @Override
    public CompoundTag serializeNBT() {
        return items.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        items.deserializeNBT(tag);
    }
}
