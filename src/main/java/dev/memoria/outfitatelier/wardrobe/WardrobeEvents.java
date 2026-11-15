package dev.memoria.outfitatelier.wardrobe;

import dev.memoria.outfitatelier.OutfitAtelier;
import dev.memoria.outfitatelier.item.GarmentItem;
import dev.memoria.outfitatelier.network.Network;
import dev.memoria.outfitatelier.service.AtelierService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

@Mod.EventBusSubscriber(modid = OutfitAtelier.MOD_ID)
public class WardrobeEvents {
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player || event.getObject() instanceof ArmorStand) {
            event.addCapability(OutfitAtelier.id("wardrobe"), new Wardrobe(event.getObject()));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AtelierService.flagDeletedGarments(player);
            syncTo(player, player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resync(event.getEntity());
    }

    private static void resync(Player player) {
        Wardrobe wardrobe = Wardrobe.of(player);
        if (wardrobe != null) {
            wardrobe.sync();
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer) {
            syncTo(viewer, event.getTarget());
        }
    }

    private static void syncTo(ServerPlayer viewer, Entity target) {
        Wardrobe wardrobe = Wardrobe.of(target);
        if (wardrobe != null && !wardrobe.isEmpty()) {
            Network.sendWardrobe(viewer, target, wardrobe.visibleDesigns());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        boolean keep = !event.isWasDeath() || event.getEntity().level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
        if (!keep) {
            return;
        }
        event.getOriginal().reviveCaps();
        Wardrobe old = Wardrobe.of(event.getOriginal());
        Wardrobe fresh = Wardrobe.of(event.getEntity());
        if (old != null && fresh != null) {
            fresh.deserializeNBT(old.serializeNBT());
        }
        event.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        Wardrobe wardrobe = Wardrobe.of(player);
        for (ItemStack garment = takeLast(wardrobe); !garment.isEmpty(); garment = takeLast(wardrobe)) {
            event.getDrops().add(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), garment));
        }
    }

    private static ItemStack takeLast(Wardrobe wardrobe) {
        return wardrobe == null ? ItemStack.EMPTY : wardrobe.takeLast();
    }

    @SubscribeEvent
    public static void onArmorStandRemoved(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ArmorStand stand)
                || stand.getRemovalReason() != Entity.RemovalReason.KILLED) {
            return;
        }
        Wardrobe wardrobe = Wardrobe.of(stand);
        for (ItemStack garment = takeLast(wardrobe); !garment.isEmpty(); garment = takeLast(wardrobe)) {
            stand.spawnAtLocation(garment);
        }
    }

    @SubscribeEvent
    public static void onInteractArmorStand(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ArmorStand stand) || stand.isMarker() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        boolean dressing = GarmentItem.designId(held) != null;
        boolean undressing = held.isEmpty() && player.isShiftKeyDown() && hasGarments(stand);
        if (!dressing && !undressing) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Wardrobe wardrobe = Wardrobe.of(stand);
        if (player.level().isClientSide || wardrobe == null) {
            return;
        }
        if (dressing && wardrobe.put(held)) {
            held.shrink(1);
        } else if (undressing) {
            ItemHandlerHelper.giveItemToPlayer(player, wardrobe.takeLast());
        }
    }

    private static boolean hasGarments(ArmorStand stand) {
        if (stand.level().isClientSide) {
            return WardrobeClientBridge.hasGarments.test(stand.getId());
        }
        Wardrobe wardrobe = Wardrobe.of(stand);
        return wardrobe != null && !wardrobe.isEmpty();
    }
}
