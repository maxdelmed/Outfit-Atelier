package dev.memoria.outfitatelier.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.memoria.outfitatelier.design.GarmentTemplate;
import dev.memoria.outfitatelier.design.GarmentTemplate.Anchor;
import dev.memoria.outfitatelier.design.GarmentTemplate.Part;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class GarmentMesh {
    private static final float MODEL_UNIT = 1.0F / 16.0F;
    private static final float TEXTURE_UNITS = 64.0F;

    private record Corner(float x, float y, float z) {}

    public static void render(PoseStack poseStack, VertexConsumer consumer, GarmentTemplate template,
                              HumanoidModel<?> model, boolean slimArms, int light) {
        for (Part part : template.parts) {
            poseStack.pushPose();
            anchorOf(model, part.anchor()).translateAndRotate(poseStack);
            if (part.tiltDegrees() != 0) {
                poseStack.translate(0, part.y0() * MODEL_UNIT, part.z0() * MODEL_UNIT);
                poseStack.mulPose(Axis.XP.rotationDegrees(part.tiltDegrees()));
                poseStack.translate(0, -part.y0() * MODEL_UNIT, -part.z0() * MODEL_UNIT);
            }
            renderPart(poseStack.last(), consumer, part, slimArms, light);
            poseStack.popPose();
        }
    }

    private static ModelPart anchorOf(HumanoidModel<?> model, Anchor anchor) {
        return switch (anchor) {
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
        };
    }

    private static void renderPart(PoseStack.Pose pose, VertexConsumer consumer, Part part, boolean slimArms, int light) {
        float x0 = part.x0() + (slimArms && part.anchor() == Anchor.RIGHT_ARM ? 1 : 0);
        float x1 = part.x1() - (slimArms && part.anchor() == Anchor.LEFT_ARM ? 1 : 0);
        float topX0 = x0 - part.inflate();
        float topX1 = x1 + part.inflate();
        float topZ0 = part.z0() - part.inflate();
        float topZ1 = part.z1() + part.inflate();
        float topY = part.y0() - part.inflate();
        float bottomY = part.y1() + part.inflate();
        float bottomX0 = topX0 - part.flare();
        float bottomX1 = topX1 + part.flare();
        float bottomZ0 = topZ0 - part.flare();
        float bottomZ1 = topZ1 + part.flare();

        Corner topNorthWest = new Corner(topX0, topY, topZ0);
        Corner topNorthEast = new Corner(topX1, topY, topZ0);
        Corner topSouthEast = new Corner(topX1, topY, topZ1);
        Corner topSouthWest = new Corner(topX0, topY, topZ1);
        Corner bottomNorthWest = new Corner(bottomX0, bottomY, bottomZ0);
        Corner bottomNorthEast = new Corner(bottomX1, bottomY, bottomZ0);
        Corner bottomSouthEast = new Corner(bottomX1, bottomY, bottomZ1);
        Corner bottomSouthWest = new Corner(bottomX0, bottomY, bottomZ1);

        int width = part.width();
        int height = part.height();
        int depth = part.depth();
        int u = part.u();
        int sideV = part.v() + depth;

        face(pose, consumer, light, topSouthWest, topNorthWest, bottomNorthWest, bottomSouthWest, u, sideV, depth, height, -1, 0, 0);
        face(pose, consumer, light, topNorthWest, topNorthEast, bottomNorthEast, bottomNorthWest, u + depth, sideV, width, height, 0, 0, -1);
        face(pose, consumer, light, topNorthEast, topSouthEast, bottomSouthEast, bottomNorthEast, u + depth + width, sideV, depth, height, 1, 0, 0);
        face(pose, consumer, light, topSouthEast, topSouthWest, bottomSouthWest, bottomSouthEast, u + 2 * depth + width, sideV, width, height, 0, 0, 1);
        if (part.hasTop()) {
            face(pose, consumer, light, topSouthWest, topSouthEast, topNorthEast, topNorthWest, u + depth, part.v(), width, depth, 0, -1, 0);
        }
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, int light,
                             Corner topLeft, Corner topRight, Corner bottomRight, Corner bottomLeft,
                             int u, int v, int width, int height, float normalX, float normalY, float normalZ) {
        float u0 = u / TEXTURE_UNITS;
        float v0 = v / TEXTURE_UNITS;
        float u1 = (u + width) / TEXTURE_UNITS;
        float v1 = (v + height) / TEXTURE_UNITS;
        vertex(pose, consumer, light, topLeft, u0, v0, normalX, normalY, normalZ);
        vertex(pose, consumer, light, topRight, u1, v0, normalX, normalY, normalZ);
        vertex(pose, consumer, light, bottomRight, u1, v1, normalX, normalY, normalZ);
        vertex(pose, consumer, light, bottomLeft, u0, v1, normalX, normalY, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, int light, Corner corner,
                               float u, float v, float normalX, float normalY, float normalZ) {
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        consumer.vertex(position, corner.x() * MODEL_UNIT, corner.y() * MODEL_UNIT, corner.z() * MODEL_UNIT)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, normalX, normalY, normalZ)
                .endVertex();
    }
}
