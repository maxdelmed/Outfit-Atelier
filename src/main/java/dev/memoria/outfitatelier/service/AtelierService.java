package dev.memoria.outfitatelier.service;

import dev.memoria.outfitatelier.AtelierConfig;
import dev.memoria.outfitatelier.ModContent;
import dev.memoria.outfitatelier.OutfitAtelier;
import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.design.OutfitStore;
import dev.memoria.outfitatelier.item.GarmentItem;
import dev.memoria.outfitatelier.network.Network;
import dev.memoria.outfitatelier.wardrobe.Wardrobe;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

@Mod.EventBusSubscriber(modid = OutfitAtelier.MOD_ID)
public class AtelierService {
    public record Result(boolean success, Component message) {
        static Result ok(String key, Object... args) {
            return new Result(true, Component.translatable("outfitatelier.message." + key, args));
        }

        static Result fail(String key, Object... args) {
            return new Result(false, Component.translatable("outfitatelier.message." + key, args));
        }
    }

    public record CatalogPage(List<DesignSummary> entries, int page, int pageCount, int total) {}

    public static final int CATALOG_PAGE_SIZE = 7;
    private static final int OPERATOR_LEVEL = 2;
    private static final double REACH_SQUARED = 64.0;
    private static final Map<UUID, GlobalPos> OPEN_ATELIERS = new HashMap<>();

    public static void openAtelier(ServerPlayer player, BlockPos pos) {
        OPEN_ATELIERS.put(player.getUUID(), GlobalPos.of(player.level().dimension(), pos));
        Network.sendOpenAtelier(player, summariesFor(player));
    }

    public static List<DesignSummary> summariesFor(ServerPlayer player) {
        List<DesignSummary> summaries = new ArrayList<>();
        for (OutfitDesign design : OutfitStore.get(player.server).liveDesignsBy(player.getUUID())) {
            summaries.add(DesignSummary.of(design));
        }
        return summaries;
    }

    private static boolean isAtSewingTable(ServerPlayer player) {
        GlobalPos atelier = OPEN_ATELIERS.get(player.getUUID());
        return atelier != null
                && atelier.dimension().equals(player.level().dimension())
                && player.level().getBlockState(atelier.pos()).is(ModContent.SEWING_TABLE.get())
                && player.distanceToSqr(atelier.pos().getCenter()) <= REACH_SQUARED;
    }

    public static Result finishDesign(ServerPlayer player, String rawName, OutfitDesign.Artwork artwork) {
        String name = rawName.trim();
        if (!isAtSewingTable(player)) {
            return Result.fail("not_at_table");
        }
        if (!OutfitDesign.isValid(name, artwork)) {
            return Result.fail("invalid_design");
        }
        OutfitStore store = OutfitStore.get(player.server);
        if (store.liveDesignsBy(player.getUUID()).size() >= AtelierConfig.MAX_DESIGNS_PER_PLAYER.get()) {
            return Result.fail("too_many_designs");
        }
        if (!takeBlankGarment(player)) {
            return Result.fail("need_blank");
        }
        OutfitDesign design = new OutfitDesign(UUID.randomUUID(), name, player.getUUID(), player.getGameProfile().getName(),
                artwork.template(), System.currentTimeMillis(), false, artwork.palette(), artwork.pixels());
        store.put(design);
        ItemHandlerHelper.giveItemToPlayer(player, GarmentItem.create(design));
        return Result.ok("finished", name);
    }

    public static Result copyOwnDesign(ServerPlayer player, UUID designId) {
        OutfitDesign design = OutfitStore.get(player.server).get(designId);
        if (!isAtSewingTable(player)) {
            return Result.fail("not_at_table");
        }
        if (design == null || design.deleted || !design.authorId.equals(player.getUUID())) {
            return Result.fail("not_your_design");
        }
        if (!takeBlankGarment(player)) {
            return Result.fail("need_blank");
        }
        ItemHandlerHelper.giveItemToPlayer(player, GarmentItem.create(design));
        return Result.ok("copied", design.name);
    }

    private static boolean takeBlankGarment(ServerPlayer player) {
        if (player.isCreative() || !AtelierConfig.GARMENTS_COST_BLANK.get()) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModContent.BLANK_GARMENT.get())) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return true;
            }
        }
        return false;
    }

    public static void sendDesign(ServerPlayer player, UUID designId) {
        OutfitDesign design = OutfitStore.get(player.server).get(designId);
        if (design == null || design.deleted) {
            Network.sendDesignDeleted(player, designId);
        } else {
            Network.sendDesign(player, design);
        }
    }

    public static boolean isOperator(ServerPlayer player) {
        return player.hasPermissions(OPERATOR_LEVEL);
    }

    public static CatalogPage searchCatalog(ServerPlayer player, String query, int requestedPage) {
        if (!isOperator(player)) {
            return new CatalogPage(List.of(), 0, 1, 0);
        }
        List<OutfitDesign> matches = OutfitStore.get(player.server).search(query);
        int pageCount = Math.max(1, (matches.size() + CATALOG_PAGE_SIZE - 1) / CATALOG_PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        List<DesignSummary> entries = new ArrayList<>();
        for (int i = page * CATALOG_PAGE_SIZE; i < Math.min(matches.size(), (page + 1) * CATALOG_PAGE_SIZE); i++) {
            entries.add(DesignSummary.of(matches.get(i)));
        }
        return new CatalogPage(entries, page, pageCount, matches.size());
    }

    public static Result takeCatalogCopy(ServerPlayer player, UUID designId) {
        OutfitDesign design = OutfitStore.get(player.server).get(designId);
        if (!isOperator(player)) {
            return Result.fail("operators_only");
        }
        if (design == null || design.deleted) {
            return Result.fail("design_deleted");
        }
        ItemHandlerHelper.giveItemToPlayer(player, GarmentItem.create(design));
        return Result.ok("copied", design.name);
    }

    public static Result deleteDesign(ServerPlayer player, UUID designId) {
        if (!isOperator(player)) {
            return Result.fail("operators_only");
        }
        if (!OutfitStore.get(player.server).markDeleted(designId)) {
            return Result.fail("design_deleted");
        }
        announceDeleted(player.server, designId);
        return Result.ok("deleted");
    }

    private static void announceDeleted(MinecraftServer server, UUID designId) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            Network.sendDesignDeleted(online, designId);
            flagDeletedGarments(online);
            Wardrobe wardrobe = Wardrobe.of(online);
            if (wardrobe != null) {
                wardrobe.sync();
            }
        }
    }

    public static void flagDeletedGarments(ServerPlayer player) {
        OutfitStore store = OutfitStore.get(player.server);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            UUID designId = GarmentItem.designId(stack);
            if (designId != null && !store.isWearable(designId)) {
                GarmentItem.markDeleted(stack);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN_ATELIERS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OPEN_ATELIERS.clear();
    }
}
