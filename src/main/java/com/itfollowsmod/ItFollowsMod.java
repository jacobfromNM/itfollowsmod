package com.itfollowsmod;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.itfollowsmod.registry.ModEntities;
import com.itfollowsmod.registry.ModSounds;
import com.itfollowsmod.registry.ModItems;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Main class for the It Follows mod.
 */
@Mod(ItFollowsMod.MOD_ID)
public class ItFollowsMod {
    public static ItFollowsMod INSTANCE;
    public static final String MOD_ID = "itfollowsmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Constructor for the It Follows mod.
     */
    public ItFollowsMod() {
        // Register the configs
        ModConfig.register();

        INSTANCE = this; // Store instance reference
        LOGGER.info("Initializing the It Follows mod");
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // Register mod components
        ModItems.register();
        ModSounds.register(modEventBus);
        ModEntities.ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());

        // Register mod events
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    
        // Register event bus
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Common setup method.
     * @param event
     */
    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("It Follows Mod initialization");
    }

    /**
     * Client setup method.
     * @param event
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("It Follows Mod client initialization");
    }

    /**
     * Registers entity attributes.
     */
    @SubscribeEvent
    public void onRegisterAttributes(EntityAttributeCreationEvent event) {
        ItFollowsMod.LOGGER.info("Registering attributes for StalkerEntity");
        
        // Use hardcoded fallback values here to avoid accessing ModConfig too early
        event.put(ModEntities.STALKER.get(), 
            Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0) // Safe default
                .add(Attributes.MOVEMENT_SPEED, 0.35) // Safe default
                .add(Attributes.ATTACK_DAMAGE, 15.0) // Safe default
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ATTACK_KNOCKBACK, 2.5)
                .add(Attributes.ATTACK_SPEED, 1.2)
                .add(Attributes.FOLLOW_RANGE, 200.0)
            .build()
        );
    }
    
}

