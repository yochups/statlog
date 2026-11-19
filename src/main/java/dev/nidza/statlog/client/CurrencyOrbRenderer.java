package dev.nidza.statlog.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nidza.statlog.Statlog;
import dev.nidza.statlog.server.CurrencyOrb;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class CurrencyOrbRenderer extends EntityRenderer<CurrencyOrb> {
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath(
            Statlog.ID, "textures/gui/statlog_currency.png");

    public CurrencyOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.15f;
        shadowStrength = 0.75f;
    }

    @Override protected int getBlockLightLevel(CurrencyOrb entity, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(entity, pos) + 7, 0, 15);
    }

    @Override public void render(CurrencyOrb orb, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.1, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.scale(0.3f, 0.3f, 0.3f);
        int color = ClientState.pointOrbColor(orb.currency());
        VertexConsumer consumer = buffers.getBuffer(RenderType.itemEntityTranslucentCull(getTextureLocation(orb)));
        PoseStack.Pose current = pose.last();
        vertex(consumer, current, -0.5f, -0.25f, 0, 1, color, light);
        vertex(consumer, current, 0.5f, -0.25f, 1, 1, color, light);
        vertex(consumer, current, 0.5f, 0.75f, 1, 0, color, light);
        vertex(consumer, current, -0.5f, 0.75f, 0, 0, color, light);
        pose.popPose();
        super.render(orb, yaw, partialTick, pose, buffers, light);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y,
                               float u, float v, int color, int light) {
        out.addVertex(pose, x, y, 0).setColor((color >> 16) & 255, (color >> 8) & 255,
                color & 255, 220).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 1, 0);
    }

    @Override public ResourceLocation getTextureLocation(CurrencyOrb orb) {
        ResourceLocation generated = GeneratedIcons.texture(ClientState.pointOrbTexture(orb.currency()));
        return generated == null ? FALLBACK : generated;
    }
}
