package com.itfollowsmod.client;

import com.itfollowsmod.client.renderer.StalkerRenderer;
import com.itfollowsmod.registry.ModEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the entity renderers for the mod.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityRenderers {
    /**
     * Registers the renderers for custom entities.
     *
     * @param event The entity renderers registration event.
     */
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STALKER.get(), StalkerRenderer::new);
    }
}
