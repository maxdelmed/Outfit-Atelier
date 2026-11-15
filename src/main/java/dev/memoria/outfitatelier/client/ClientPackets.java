package dev.memoria.outfitatelier.client;

import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.network.Network;
import dev.memoria.outfitatelier.service.DesignSummary;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ClientPackets {
    public static void openAtelier(List<DesignSummary> designs) {
        Minecraft.getInstance().setScreen(new AtelierScreen(designs));
    }

    public static void openCatalog() {
        Minecraft.getInstance().setScreen(new CatalogScreen());
    }

    public static void receiveDesign(OutfitDesign design) {
        ClientOutfits.put(design);
    }

    public static void receiveDeleted(UUID designId) {
        ClientOutfits.markDeleted(designId);
    }

    public static void receiveWardrobe(Network.WardrobeMessage message) {
        ClientWardrobes.put(message.entityId(), message.designs());
    }

    public static void receiveResult(Network.ResultMessage result) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof AtelierScreen atelier) {
            atelier.showResult(result);
        } else if (screen instanceof CatalogScreen catalog) {
            catalog.showNotice(result.notice(), !result.success());
        }
    }

    public static void receiveCatalogPage(Network.CatalogPageMessage page) {
        if (Minecraft.getInstance().screen instanceof CatalogScreen catalog) {
            catalog.showPage(page);
        }
    }
}
