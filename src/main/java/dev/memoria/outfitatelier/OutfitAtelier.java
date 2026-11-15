package dev.memoria.outfitatelier;

import dev.memoria.outfitatelier.network.Network;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(OutfitAtelier.MOD_ID)
public class OutfitAtelier {
    public static final String MOD_ID = "outfitatelier";

    public OutfitAtelier() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContent.register(modBus);
        Network.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AtelierConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
