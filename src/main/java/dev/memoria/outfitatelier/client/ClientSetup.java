package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.memoria.outfitatelier.ModContent;
import dev.memoria.outfitatelier.OutfitAtelier;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.item.GarmentItem;
import dev.memoria.outfitatelier.network.Network;
import dev.memoria.outfitatelier.wardrobe.WardrobeClientBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = OutfitAtelier.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final KeyMapping WARDROBE_KEY = new KeyMapping("key.outfitatelier.wardrobe",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.outfitatelier");

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModContent.WARDROBE_MENU.get(), WardrobeScreen::new);
            WardrobeClientBridge.hasGarments = ClientWardrobes::hasGarments;
            ItemProperties.register(ModContent.GARMENT.get(), OutfitAtelier.id("template"), (stack, level, entity, seed) -> {
                GarmentTemplate template = GarmentItem.template(stack);
                return template == null ? 0.0F : template.ordinal() / 10.0F;
            });
        });
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(WARDROBE_KEY);
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new GarmentLayer(renderer));
            }
        }
        LivingEntityRenderer armorStand = event.getRenderer(EntityType.ARMOR_STAND);
        if (armorStand != null) {
            armorStand.addLayer(new GarmentLayer(armorStand));
        }
    }

    @Mod.EventBusSubscriber(modid = OutfitAtelier.MOD_ID, value = Dist.CLIENT)
    public static class GameEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().player != null) {
                while (WARDROBE_KEY.consumeClick()) {
                    Network.sendToServer(new Network.OpenWardrobeMessage());
                }
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientOutfits.clear();
            ClientWardrobes.clear();
        }

        @SubscribeEvent
        public static void onEntityLeave(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide) {
                ClientWardrobes.remove(event.getEntity().getId());
            }
        }
    }
}
