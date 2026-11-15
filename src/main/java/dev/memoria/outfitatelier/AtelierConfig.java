package dev.memoria.outfitatelier;

import net.minecraftforge.common.ForgeConfigSpec;

public class AtelierConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_DESIGNS_PER_PLAYER;
    public static final ForgeConfigSpec.BooleanValue GARMENTS_COST_BLANK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        MAX_DESIGNS_PER_PLAYER = builder
                .comment("Finished outfit designs one player can own.")
                .defineInRange("maxDesignsPerPlayer", 100, 1, 10000);
        GARMENTS_COST_BLANK = builder
                .comment("Finishing or copying a garment takes one Blank Garment from survival players.")
                .define("garmentsCostBlank", true);
        SPEC = builder.build();
    }
}
