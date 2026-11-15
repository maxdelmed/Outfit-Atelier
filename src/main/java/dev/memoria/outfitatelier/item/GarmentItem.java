package dev.memoria.outfitatelier.item;

import dev.memoria.outfitatelier.ModContent;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.OutfitDesign;
import dev.memoria.outfitatelier.wardrobe.Wardrobe;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class GarmentItem extends Item {
    private static final String DESIGN_ID = "DesignId";
    private static final String NAME = "DesignName";
    private static final String AUTHOR = "Author";
    private static final String TEMPLATE = "Template";
    private static final String DELETED = "Deleted";

    public GarmentItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(OutfitDesign design) {
        ItemStack stack = new ItemStack(ModContent.GARMENT.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(DESIGN_ID, design.id);
        tag.putString(NAME, design.name);
        tag.putString(AUTHOR, design.authorName);
        tag.putString(TEMPLATE, design.template.name());
        return stack;
    }

    public static UUID designId(ItemStack stack) {
        return stack.is(ModContent.GARMENT.get()) && stack.hasTag() && stack.getTag().hasUUID(DESIGN_ID)
                ? stack.getTag().getUUID(DESIGN_ID) : null;
    }

    public static GarmentTemplate template(ItemStack stack) {
        return stack.hasTag() ? GarmentTemplate.byName(stack.getTag().getString(TEMPLATE)) : null;
    }

    public static boolean isMarkedDeleted(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(DELETED);
    }

    public static void markDeleted(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(DELETED, true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            Wardrobe wardrobe = Wardrobe.of(serverPlayer);
            if (wardrobe == null || !wardrobe.wear(serverPlayer, hand)) {
                serverPlayer.displayClientMessage(Component.translatable("outfitatelier.message.design_deleted").withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return designId(stack) == null ? super.getName(stack) : Component.literal(stack.getTag().getString(NAME));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        GarmentTemplate template = template(stack);
        if (designId(stack) == null || template == null) {
            return;
        }
        lines.add(Component.translatable(template.translationKey()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("outfitatelier.garment.author", stack.getTag().getString(AUTHOR)).withStyle(ChatFormatting.GRAY));
        if (isMarkedDeleted(stack)) {
            lines.add(Component.translatable("outfitatelier.message.design_deleted").withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable("outfitatelier.garment.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
