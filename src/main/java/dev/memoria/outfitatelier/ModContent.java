package dev.memoria.outfitatelier;

import dev.memoria.outfitatelier.block.SewingTableBlock;
import dev.memoria.outfitatelier.item.GarmentItem;
import dev.memoria.outfitatelier.wardrobe.Wardrobe;
import dev.memoria.outfitatelier.wardrobe.WardrobeMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, OutfitAtelier.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OutfitAtelier.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, OutfitAtelier.MOD_ID);

    public static final RegistryObject<Block> SEWING_TABLE = BLOCKS.register("sewing_table",
            () -> new SewingTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Item> SEWING_TABLE_ITEM = ITEMS.register("sewing_table",
            () -> new BlockItem(SEWING_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLANK_GARMENT = ITEMS.register("blank_garment",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> GARMENT = ITEMS.register("garment",
            () -> new GarmentItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<MenuType<WardrobeMenu>> WARDROBE_MENU = MENUS.register("wardrobe",
            () -> new MenuType<>(WardrobeMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModContent::addToCreativeTab);
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(Wardrobe.class));
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SEWING_TABLE_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(BLANK_GARMENT);
        }
    }
}
