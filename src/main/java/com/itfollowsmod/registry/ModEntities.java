package com.itfollowsmod.registry;

import com.itfollowsmod.ItFollowsMod;
import com.itfollowsmod.entity.StalkerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * This class handles the registration of custom entities for the mod.
 */
public class ModEntities {
    // Deferred register for entity types
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ItFollowsMod.MOD_ID);

    // Register the Stalker entity
    public static final RegistryObject<EntityType<StalkerEntity>> STALKER = ENTITIES.register("stalker",
            () -> EntityType.Builder.of(StalkerEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F) // Width & Height of entity
                    .build("stalker"));

    /**
     * Registers the entities with the mod event bus.
     */
    public static void register() {
        ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
