package dev.memoria.outfitatelier.service;

import dev.memoria.outfitatelier.OutfitAtelier;
import dev.memoria.outfitatelier.network.Network;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OutfitAtelier.MOD_ID)
public class AtelierCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("outfitatelier")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("catalog").executes(context -> {
                    Network.sendOpenCatalog(context.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
