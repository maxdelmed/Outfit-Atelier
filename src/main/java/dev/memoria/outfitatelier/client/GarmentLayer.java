package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class GarmentLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public GarmentLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        boolean slimArms = entity instanceof AbstractClientPlayer player && "slim".equals(player.getModelName());
        ClientWardrobes.Preview preview = ClientWardrobes.preview();
        if (preview != null && entity == Minecraft.getInstance().player) {
            renderGarment(poseStack, buffers, light, preview.template(), preview.texture(), slimArms);
            return;
        }
        UUID[] worn = ClientWardrobes.get(entity.getId());
        if (worn == null) {
            return;
        }
        for (UUID designId : worn) {
            ClientOutfits.Loaded loaded = ClientOutfits.getOrRequest(designId);
            if (loaded != null) {
                renderGarment(poseStack, buffers, light, loaded.design().template, loaded.texture(), slimArms);
            }
        }
    }

    private void renderGarment(PoseStack poseStack, MultiBufferSource buffers, int light, GarmentTemplate template,
                               ResourceLocation texture, boolean slimArms) {
        GarmentMesh.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(texture)), template,
                getParentModel(), slimArms, light);
    }
}
