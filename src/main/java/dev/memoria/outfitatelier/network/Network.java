package dev.memoria.outfitatelier.network;

import dev.memoria.outfitatelier.OutfitAtelier;
import dev.memoria.outfitatelier.client.ClientPackets;
import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.design.WardrobeSlot;
import dev.memoria.outfitatelier.service.AtelierService;
import dev.memoria.outfitatelier.service.DesignSummary;
import dev.memoria.outfitatelier.wardrobe.Wardrobe;
import dev.memoria.outfitatelier.wardrobe.WardrobeMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class Network {
    private static final String PROTOCOL = "1";
    private static final int MAX_QUERY_LENGTH = 32;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            OutfitAtelier.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public enum CatalogAction { TAKE_COPY, DELETE }

    public record OpenAtelierMessage(List<DesignSummary> designs) {}

    public record OpenCatalogMessage() {}

    public record DesignMessage(OutfitDesign design) {}

    public record DesignDeletedMessage(UUID designId) {}

    public record ResultMessage(boolean success, Component notice, List<DesignSummary> designs) {}

    public record WardrobeMessage(int entityId, UUID[] designs) {}

    public record CatalogPageMessage(List<DesignSummary> entries, int page, int pageCount, int total) {}

    public record FinishMessage(String name, OutfitDesign.Artwork artwork) {}

    public record CopyMessage(UUID designId) {}

    public record RequestDesignMessage(UUID designId) {}

    public record CatalogQueryMessage(String query, int page) {}

    public record CatalogActionMessage(CatalogAction action, UUID designId, String query, int page) {}

    public record OpenWardrobeMessage() {}

    public static void register() {
        registerClientbound(0, OpenAtelierMessage.class,
                (message, buffer) -> writeSummaries(buffer, message.designs()),
                buffer -> new OpenAtelierMessage(readSummaries(buffer)),
                message -> ClientPackets.openAtelier(message.designs()));
        registerClientbound(1, OpenCatalogMessage.class, (message, buffer) -> { }, buffer -> new OpenCatalogMessage(),
                message -> ClientPackets.openCatalog());
        registerClientbound(2, DesignMessage.class,
                (message, buffer) -> message.design().write(buffer),
                buffer -> new DesignMessage(OutfitDesign.read(buffer)),
                message -> ClientPackets.receiveDesign(message.design()));
        registerClientbound(3, DesignDeletedMessage.class,
                (message, buffer) -> buffer.writeUUID(message.designId()),
                buffer -> new DesignDeletedMessage(buffer.readUUID()),
                message -> ClientPackets.receiveDeleted(message.designId()));
        registerClientbound(4, ResultMessage.class, Network::writeResult, Network::readResult,
                message -> ClientPackets.receiveResult(message));
        registerClientbound(5, WardrobeMessage.class, Network::writeWardrobe, Network::readWardrobe,
                message -> ClientPackets.receiveWardrobe(message));
        registerClientbound(6, CatalogPageMessage.class, Network::writeCatalogPage, Network::readCatalogPage,
                message -> ClientPackets.receiveCatalogPage(message));

        registerServerbound(10, FinishMessage.class,
                (message, buffer) -> {
                    buffer.writeUtf(message.name(), OutfitDesign.MAX_NAME_LENGTH);
                    OutfitDesign.writeArtwork(buffer, message.artwork());
                },
                buffer -> new FinishMessage(buffer.readUtf(OutfitDesign.MAX_NAME_LENGTH), OutfitDesign.readArtwork(buffer)),
                (message, player) -> sendResult(player, AtelierService.finishDesign(player, message.name(), message.artwork())));
        registerServerbound(11, CopyMessage.class,
                (message, buffer) -> buffer.writeUUID(message.designId()),
                buffer -> new CopyMessage(buffer.readUUID()),
                (message, player) -> sendResult(player, AtelierService.copyOwnDesign(player, message.designId())));
        registerServerbound(12, RequestDesignMessage.class,
                (message, buffer) -> buffer.writeUUID(message.designId()),
                buffer -> new RequestDesignMessage(buffer.readUUID()),
                (message, player) -> AtelierService.sendDesign(player, message.designId()));
        registerServerbound(13, CatalogQueryMessage.class,
                (message, buffer) -> buffer.writeUtf(message.query(), MAX_QUERY_LENGTH).writeVarInt(message.page()),
                buffer -> new CatalogQueryMessage(buffer.readUtf(MAX_QUERY_LENGTH), buffer.readVarInt()),
                (message, player) -> sendCatalogPage(player, message.query(), message.page()));
        registerServerbound(14, CatalogActionMessage.class,
                (message, buffer) -> buffer.writeEnum(message.action()).writeUUID(message.designId())
                        .writeUtf(message.query(), MAX_QUERY_LENGTH).writeVarInt(message.page()),
                buffer -> new CatalogActionMessage(buffer.readEnum(CatalogAction.class), buffer.readUUID(),
                        buffer.readUtf(MAX_QUERY_LENGTH), buffer.readVarInt()),
                Network::handleCatalogAction);
        registerServerbound(15, OpenWardrobeMessage.class, (message, buffer) -> { }, buffer -> new OpenWardrobeMessage(),
                (message, player) -> openWardrobe(player));
    }

    private static void writeSummaries(FriendlyByteBuf buffer, List<DesignSummary> summaries) {
        buffer.writeCollection(summaries, (out, summary) -> summary.write(out));
    }

    private static List<DesignSummary> readSummaries(FriendlyByteBuf buffer) {
        return buffer.readList(DesignSummary::read);
    }

    private static void writeResult(ResultMessage message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success());
        buffer.writeComponent(message.notice());
        writeSummaries(buffer, message.designs());
    }

    private static ResultMessage readResult(FriendlyByteBuf buffer) {
        return new ResultMessage(buffer.readBoolean(), buffer.readComponent(), readSummaries(buffer));
    }

    private static void writeWardrobe(WardrobeMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId());
        for (UUID design : message.designs()) {
            buffer.writeNullable(design, FriendlyByteBuf::writeUUID);
        }
    }

    private static WardrobeMessage readWardrobe(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        UUID[] designs = new UUID[WardrobeSlot.COUNT];
        for (int slot = 0; slot < designs.length; slot++) {
            designs[slot] = buffer.readNullable(FriendlyByteBuf::readUUID);
        }
        return new WardrobeMessage(entityId, designs);
    }

    private static void writeCatalogPage(CatalogPageMessage message, FriendlyByteBuf buffer) {
        writeSummaries(buffer, message.entries());
        buffer.writeVarInt(message.page());
        buffer.writeVarInt(message.pageCount());
        buffer.writeVarInt(message.total());
    }

    private static CatalogPageMessage readCatalogPage(FriendlyByteBuf buffer) {
        return new CatalogPageMessage(readSummaries(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private static <T> void registerClientbound(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                                Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        CHANNEL.registerMessage(id, type, encoder, decoder, (message, context) -> {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handler.accept(message)));
            context.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <T> void registerServerbound(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                                Function<FriendlyByteBuf, T> decoder, BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.registerMessage(id, type, encoder, decoder, (message, context) -> {
            context.get().enqueueWork(() -> {
                ServerPlayer player = context.get().getSender();
                if (player != null) {
                    handler.accept(message, player);
                }
            });
            context.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static void handleCatalogAction(CatalogActionMessage message, ServerPlayer player) {
        AtelierService.Result result = message.action() == CatalogAction.DELETE
                ? AtelierService.deleteDesign(player, message.designId())
                : AtelierService.takeCatalogCopy(player, message.designId());
        sendResult(player, result);
        sendCatalogPage(player, message.query(), message.page());
    }

    private static void sendResult(ServerPlayer player, AtelierService.Result result) {
        send(player, new ResultMessage(result.success(), result.message(), AtelierService.summariesFor(player)));
    }

    private static void sendCatalogPage(ServerPlayer player, String query, int page) {
        AtelierService.CatalogPage found = AtelierService.searchCatalog(player, query, page);
        send(player, new CatalogPageMessage(found.entries(), found.page(), found.pageCount(), found.total()));
    }

    private static void openWardrobe(ServerPlayer player) {
        Wardrobe wardrobe = Wardrobe.of(player);
        if (wardrobe != null) {
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (containerId, inventory, opener) -> new WardrobeMenu(containerId, inventory, wardrobe.items()),
                    Component.translatable("outfitatelier.gui.wardrobe")));
        }
    }

    private static void send(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendOpenAtelier(ServerPlayer player, List<DesignSummary> designs) {
        send(player, new OpenAtelierMessage(designs));
    }

    public static void sendOpenCatalog(ServerPlayer player) {
        send(player, new OpenCatalogMessage());
    }

    public static void sendDesign(ServerPlayer player, OutfitDesign design) {
        send(player, new DesignMessage(design));
    }

    public static void sendDesignDeleted(ServerPlayer player, UUID designId) {
        send(player, new DesignDeletedMessage(designId));
    }

    public static void sendWardrobe(ServerPlayer viewer, Entity owner, UUID[] designs) {
        send(viewer, new WardrobeMessage(owner.getId(), designs));
    }

    public static void sendWardrobeToTrackers(Entity owner, UUID[] designs) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner), new WardrobeMessage(owner.getId(), designs));
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }
}
