package dev.memoria.outfitatelier.test;

import com.mojang.authlib.GameProfile;
import dev.memoria.outfitatelier.ModContent;
import dev.memoria.outfitatelier.OutfitAtelier;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.GarmentTemplate.Panel;
import dev.memoria.outfitatelier.design.OutfitCanvas;
import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.design.OutfitStore;
import dev.memoria.outfitatelier.design.WardrobeSlot;
import dev.memoria.outfitatelier.item.GarmentItem;
import dev.memoria.outfitatelier.service.AtelierService;
import dev.memoria.outfitatelier.wardrobe.Wardrobe;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(OutfitAtelier.MOD_ID)
@PrefixGameTestTemplate(false)
public class AtelierGameTests {
    private static final BlockPos ORIGIN = new BlockPos(0, 0, 0);

    @GameTest(template = "empty")
    public static void canvasPaintsInsideThePanelOnly(GameTestHelper helper) {
        OutfitCanvas canvas = new OutfitCanvas(GarmentTemplate.SHIRT);
        Panel front = panel(GarmentTemplate.SHIRT, "front");
        Panel rightSleeve = panel(GarmentTemplate.SHIRT, "right_sleeve");
        Panel leftSleeve = panel(GarmentTemplate.SHIRT, "left_sleeve");

        canvas.fill(front, front.left(), front.top(), 0xAA2244);
        helper.assertTrue(canvas.paintAt(front.left() + 15, front.top() + 23) == 0xAA2244, "fill missed the panel corner");
        helper.assertTrue(canvas.paintAt(front.left() + 16, front.top()) == OutfitCanvas.PAINT_EMPTY, "fill left the panel");
        canvas.drawLine(front, front.left() - 5, front.top(), front.left() + 40, front.top(), 0x112233);
        helper.assertTrue(canvas.paintAt(front.left() - 1, front.top()) == OutfitCanvas.PAINT_EMPTY, "line left the panel");

        canvas.setPixel(rightSleeve, rightSleeve.left(), rightSleeve.top() + 10, 0x00FF00);
        canvas.mirrorSleeve(rightSleeve, leftSleeve);
        int outerFaceOfLeftSleeve = leftSleeve.left() + 2 * 8 + 7;
        helper.assertTrue(canvas.paintAt(outerFaceOfLeftSleeve, leftSleeve.top() + 10) == 0x00FF00, "mirror put the outer sleeve face in the wrong place");

        helper.assertTrue(OutfitDesign.isValid("Shirt", canvas.toArtwork()), "painted artwork is not valid");
        helper.assertTrue(!OutfitDesign.isValid("Shirt", new OutfitCanvas(GarmentTemplate.SHIRT).toArtwork()), "empty artwork was accepted");
        canvas.undo();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void designSurvivesPacketAndSave(GameTestHelper helper) {
        OutfitDesign design = sampleDesign(UUID.randomUUID(), "Author");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        design.write(buffer);
        helper.assertTrue(buffer.readableBytes() < 600, "design packet is not compact: " + buffer.readableBytes());
        OutfitDesign received = OutfitDesign.read(buffer);
        helper.assertTrue(received.template == GarmentTemplate.SHIRT && sameArtwork(design, received), "packet changed the design");

        OutfitStore store = new OutfitStore();
        store.put(design);
        OutfitDesign kept = sampleDesign(UUID.randomUUID(), "Other");
        store.put(kept);
        store.markDeleted(design.id);
        OutfitStore restored = OutfitStore.load(store.save(new CompoundTag()));
        helper.assertTrue(restored.get(design.id).deleted && !restored.isWearable(design.id), "deleted design came back after reload");
        helper.assertTrue(sameArtwork(kept, restored.get(kept.id)), "save changed a live design");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void finishingConsumesOneBlankAndGivesAGarment(GameTestHelper helper) {
        helper.setBlock(ORIGIN, ModContent.SEWING_TABLE.get());
        ServerPlayer author = playerAt(helper, "Author");
        ServerPlayer visitor = playerAt(helper, "Visitor");
        OutfitDesign.Artwork artwork = sampleDesign(author.getUUID(), "Author").artwork();

        helper.assertTrue(!AtelierService.finishDesign(author, "Rose shirt", artwork).success(), "finished without a sewing table");
        AtelierService.openAtelier(author, helper.absolutePos(ORIGIN));
        AtelierService.openAtelier(visitor, helper.absolutePos(ORIGIN));
        helper.assertTrue(!AtelierService.finishDesign(author, " ", artwork).success(), "finished without a name");
        helper.assertTrue(!AtelierService.finishDesign(author, "Rose shirt", artwork).success(), "finished without a blank garment");
        helper.assertTrue(AtelierService.summariesFor(author).isEmpty(), "rejected finish stored a design");

        author.getInventory().add(new ItemStack(ModContent.BLANK_GARMENT.get(), 2));
        helper.assertTrue(AtelierService.finishDesign(author, "Rose shirt", artwork).success(), "finish failed with a blank garment");
        helper.assertTrue(author.getInventory().countItem(ModContent.BLANK_GARMENT.get()) == 1, "finish did not take exactly one blank");
        ItemStack garment = garmentOf(author);
        UUID designId = GarmentItem.designId(garment);
        helper.assertTrue(designId != null && GarmentItem.template(garment) == GarmentTemplate.SHIRT, "garment item is missing its design");

        visitor.getInventory().add(new ItemStack(ModContent.BLANK_GARMENT.get()));
        helper.assertTrue(!AtelierService.copyOwnDesign(visitor, designId).success(), "another player copied the design at the table");
        helper.assertTrue(AtelierService.copyOwnDesign(author, designId).success(), "author could not copy the design");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyOperatorsModerateAndDeletionSticks(GameTestHelper helper) {
        OutfitStore store = OutfitStore.get(helper.getLevel().getServer());
        ServerPlayer wearer = playerAt(helper, "Wearer");
        ServerPlayer operator = playerAt(helper, "Operator");
        helper.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 4, false));
        OutfitDesign design = sampleDesign(wearer.getUUID(), "Wearer");
        store.put(design);

        wearer.setItemInHand(InteractionHand.MAIN_HAND, GarmentItem.create(design));
        Wardrobe wardrobe = Wardrobe.of(wearer);
        helper.assertTrue(wardrobe.wear(wearer, InteractionHand.MAIN_HAND), "could not wear the garment");
        helper.assertTrue(wearer.getMainHandItem().isEmpty(), "worn garment stayed in hand");
        helper.assertTrue(design.id.equals(wardrobe.visibleDesigns()[WardrobeSlot.TOP.ordinal()]), "worn design is not visible");
        wearer.getInventory().add(GarmentItem.create(design));

        helper.assertTrue(!AtelierService.deleteDesign(wearer, design.id).success(), "non-operator deleted a design");
        helper.assertTrue(!AtelierService.takeCatalogCopy(wearer, design.id).success(), "non-operator took a free copy");
        helper.assertTrue(AtelierService.searchCatalog(wearer, "", 0).entries().isEmpty(), "non-operator browsed the catalog");

        helper.assertTrue(AtelierService.searchCatalog(operator, "wearer", 0).total() >= 1, "operator search by creator found nothing");
        helper.assertTrue(AtelierService.takeCatalogCopy(operator, design.id).success(), "operator could not take a copy");
        helper.assertTrue("Wearer".equals(garmentOf(operator).getTag().getString("Author")), "free copy lost its author");

        helper.assertTrue(AtelierService.deleteDesign(operator, design.id).success(), "operator could not delete");
        helper.assertTrue(!AtelierService.deleteDesign(operator, design.id).success(), "deleted twice");
        helper.assertTrue(!AtelierService.takeCatalogCopy(operator, design.id).success(), "copied a deleted design");
        helper.assertTrue(wardrobe.visibleDesigns()[WardrobeSlot.TOP.ordinal()] == null, "deleted design still visible when worn");
        AtelierService.flagDeletedGarments(wearer);
        helper.assertTrue(GarmentItem.isMarkedDeleted(garmentOf(wearer)), "carried copy not marked deleted");
        wearer.setItemInHand(InteractionHand.MAIN_HAND, GarmentItem.create(design));
        helper.assertTrue(!wardrobe.wear(wearer, InteractionHand.MAIN_HAND), "wore a deleted design");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void armorStandDisplaysOneGarmentPerSlot(GameTestHelper helper) {
        OutfitStore store = OutfitStore.get(helper.getLevel().getServer());
        OutfitDesign design = sampleDesign(UUID.randomUUID(), "Author");
        store.put(design);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, ORIGIN);
        Wardrobe wardrobe = Wardrobe.of(stand);

        helper.assertTrue(wardrobe != null && wardrobe.put(GarmentItem.create(design)), "could not dress the armor stand");
        helper.assertTrue(!wardrobe.put(GarmentItem.create(design)), "dressed the same slot twice");
        Wardrobe reloaded = new Wardrobe(stand);
        reloaded.deserializeNBT(wardrobe.serializeNBT());
        helper.assertTrue(design.id.equals(reloaded.visibleDesigns()[WardrobeSlot.TOP.ordinal()]), "garment lost when the stand is saved");
        helper.assertTrue(design.id.equals(GarmentItem.designId(wardrobe.takeLast())), "took a different garment back");
        helper.assertTrue(wardrobe.isEmpty(), "stand still dressed after undressing");
        helper.succeed();
    }

    private static Panel panel(GarmentTemplate template, String key) {
        return template.panels.stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow();
    }

    private static OutfitDesign sampleDesign(UUID authorId, String authorName) {
        OutfitCanvas canvas = new OutfitCanvas(GarmentTemplate.SHIRT);
        Panel front = panel(GarmentTemplate.SHIRT, "front");
        canvas.fill(front, front.left(), front.top(), 0xC02040);
        canvas.setPixel(front, front.left() + 3, front.top() + 3, 0xFFFFFF);
        OutfitDesign.Artwork artwork = canvas.toArtwork();
        return new OutfitDesign(UUID.randomUUID(), "Rose shirt", authorId, authorName, GarmentTemplate.SHIRT,
                System.currentTimeMillis(), false, artwork.palette(), artwork.pixels());
    }

    private static boolean sameArtwork(OutfitDesign expected, OutfitDesign actual) {
        for (int y = 0; y < GarmentTemplate.TEXTURE_SIZE; y++) {
            for (int x = 0; x < GarmentTemplate.TEXTURE_SIZE; x++) {
                if (expected.argbAt(x, y) != actual.argbAt(x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ServerPlayer playerAt(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(ORIGIN);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack garmentOf(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (GarmentItem.designId(stack) != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
