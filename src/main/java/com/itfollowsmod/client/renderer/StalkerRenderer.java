package com.itfollowsmod.client.renderer;

// It Follows Mod Imports...
import com.itfollowsmod.entity.StalkerEntity;
import com.mojang.blaze3d.vertex.PoseStack;

// Minecraft Imports...
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

/**
 * Renderer for the Stalker entity. Adjusts visibility based on light levels and
 * rotates between different skins.
 * 
 * @see HumanoidMobRenderer
 */
public class StalkerRenderer extends HumanoidMobRenderer<StalkerEntity, HumanoidModel<StalkerEntity>> {
    private static final ResourceLocation STEVE_TEXTURE = new ResourceLocation("itfollowsmod",
            "textures/entity/steve.png");
    private static final ResourceLocation ALEX_TEXTURE = new ResourceLocation("itfollowsmod",
            "textures/entity/alex.png");
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation("itfollowsmod",
            "textures/entity/villager.png");
    // private static final ResourceLocation INVISIBLE_TEXTURE = new
    // ResourceLocation("itfollowsmod", "textures/entity/invisible.png");

    public StalkerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    /**
     * Renders the Stalker entity based on the current light levels
     * 
     * @param entity       The Stalker entity
     * @param entityYaw    The entity's yaw rotation
     * @param partialTicks The partial tick time
     */
    @Override
    public void render(StalkerEntity entity, float entityYaw, float partialTicks, PoseStack matrixStack,
            MultiBufferSource buffer, int packedLight) {
        // Check if we should render the entity at all
        if (shouldRenderEntity(entity)) {
            super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
        }
        // If not, we simply don't render it (making it invisible)
    }

    /**
     * Gets the texture location for the Stalker entity based on the current skin
     * index
     * 
     * @param entity The Stalker entity
     * @return The texture location for the entity
     */
    @Override
    public ResourceLocation getTextureLocation(StalkerEntity entity) {
        // Get the current skin index (0-3) from the entity
        int skinIndex = getSkinIndex(entity);

        // Return the appropriate texture based on the skin index
        switch (skinIndex) {
            case 0:
                return STEVE_TEXTURE;
            case 1:
                return ALEX_TEXTURE;
            case 2:
                return VILLAGER_TEXTURE;
            case 3:
                // Try to get the nearest player's skin
                return getNearestPlayerSkin(entity);
            default:
                return STEVE_TEXTURE;
        }
    }

    /**
     *
     * @param entity The Stalker entity
     * @return The skin index for the entity
     */
    private int getSkinIndex(StalkerEntity entity) {
        // Time-based rotation (changes every few seconds)
        long worldTime = entity.level.getGameTime();
        return (int) (worldTime / 3600) % 4; // Changes every 3600 ticks (3 minutes)
    }

    /**
     * Gets the skin of the nearest player, or defaults to Steve if none found
     * 
     * @param entity The Stalker entity
     * @return The texture location for the nearest player's skin
     */
    private ResourceLocation getNearestPlayerSkin(StalkerEntity entity) {
        // Find the nearest player
        Player nearestPlayer = entity.level.getNearestPlayer(entity, 64.0); // 64 block radius

        if (nearestPlayer != null && nearestPlayer instanceof AbstractClientPlayer) {
            // Return the player's skin
            return ((AbstractClientPlayer) nearestPlayer).getSkinTextureLocation();
        } else {
            // Try to get the client player's skin as fallback
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null && clientPlayer instanceof AbstractClientPlayer) {
                return ((AbstractClientPlayer) clientPlayer).getSkinTextureLocation();
            }
        }

        // Default to Steve if no player is found
        return STEVE_TEXTURE;
    }

    /**
     * Determines if the entity should be rendered based on light conditions
     * 
     * @param entity The Stalker entity
     * @return True if the entity should be rendered, false otherwise
     */
    private boolean shouldRenderEntity(StalkerEntity entity) {
        if (entity == null || entity.level == null)
            return false;

        // Get block light (artificial light sources)
        int blockLight = entity.level.getBrightness(LightLayer.BLOCK, entity.blockPosition());

        // Check if it's day or night
        long dayTime = entity.level.getDayTime() % 24000;
        boolean isNightTime = dayTime > 13000 && dayTime < 23000;

        // Get sky light (affected by time of day and blocks above)
        int skyLight = entity.level.getBrightness(LightLayer.SKY, entity.blockPosition());

        // Calculate effective light level
        // Block light is effective regardless of time of day
        // Sky light is only effective during day time
        int effectiveLight = blockLight;

        // Only count sky light if it's daytime
        if (!isNightTime) {
            effectiveLight = Math.max(blockLight, skyLight);
        }

        // Return true if there's enough light to see the entity
        return effectiveLight >= 7;
    }
}