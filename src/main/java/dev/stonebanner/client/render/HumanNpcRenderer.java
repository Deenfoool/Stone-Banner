package dev.stonebanner.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.stonebanner.entity.HumanNpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Minecraft-native Steve/Alex renderer for Human NPCs. */
public final class HumanNpcRenderer extends HumanoidMobRenderer<HumanNpcEntity, PlayerModel<HumanNpcEntity>> {
    private static final ResourceLocation STEVE_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
    private static final ResourceLocation ALEX_TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/player/slim/alex.png");

    private final PlayerModel<HumanNpcEntity> wideModel;
    private final PlayerModel<HumanNpcEntity> slimModel;

    public HumanNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        wideModel = model;
        slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public void render(HumanNpcEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        model = entity.usesSlimModel() ? slimModel : wideModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(HumanNpcEntity entity) {
        return entity.usesSlimModel() ? ALEX_TEXTURE : STEVE_TEXTURE;
    }
}
