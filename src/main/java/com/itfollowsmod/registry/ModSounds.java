package com.itfollowsmod.registry;

import com.itfollowsmod.ItFollowsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * This class handles the registration of custom sounds for the mod.
 */
public class ModSounds {
    // Create a DeferredRegister for sound events
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ItFollowsMod.MOD_ID);

    // Register sound events
    public static final RegistryObject<SoundEvent> WHISPERS_001 = registerSound("whispers_001");
    public static final RegistryObject<SoundEvent> ELECTRIC_ROAR = registerSound("electric_roar");
    public static final RegistryObject<SoundEvent> VIOLINS = registerSound("violins");


    // Helper method to register a sound
    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> new SoundEvent(new ResourceLocation(ItFollowsMod.MOD_ID, name)));
    }

    // Register sounds to the event bus
    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
