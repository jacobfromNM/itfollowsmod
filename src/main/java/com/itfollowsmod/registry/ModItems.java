package com.itfollowsmod.registry;

import com.itfollowsmod.ItFollowsMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * This class handles the registration of custom items for the mod.
 */
public class ModItems {
    // Deferred register for items
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ItFollowsMod.MOD_ID);

    /**
     * Registers the items with the mod event bus.
     */
    public static void register() {
        ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
