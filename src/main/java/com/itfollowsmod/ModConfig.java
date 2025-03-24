package com.itfollowsmod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.ModLoadingContext;

@Mod.EventBusSubscriber(modid = ItFollowsMod.MOD_ID)
public class ModConfig {
    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    
    public static final ForgeConfigSpec COMMON_CONFIG;
    
    // Entity Stats
    public static ForgeConfigSpec.DoubleValue STALKER_MOVEMENT_SPEED;
    public static ForgeConfigSpec.DoubleValue STALKER_ATTACK_DAMAGE;
    public static ForgeConfigSpec.BooleanValue STALKER_IS_INVINCIBLE;
    public static ForgeConfigSpec.DoubleValue STALKER_MAX_HEALTH;
    public static ForgeConfigSpec.DoubleValue STALKER_WAKING_DISTANCE;
    public static ForgeConfigSpec.DoubleValue BREAKABLE_BLOCK_HARDNESS;
    public static ForgeConfigSpec.BooleanValue STALKER_PREVENT_SLEEP;
    public static ForgeConfigSpec.IntValue MINIMUM_SPAWN_DISTANCE;
    public static ForgeConfigSpec.IntValue MAXIMUM_SPAWN_DISTANCE;
    
    // Sound Settings
    public static ForgeConfigSpec.BooleanValue ENABLE_PROXIMITY_SOUNDS;
    public static ForgeConfigSpec.BooleanValue ENABLE_ATTACK_SOUNDS;
    public static ForgeConfigSpec.BooleanValue ENABLE_DAMAGE_SOUNDS;

    
    static {
        COMMON_BUILDER.comment("It Follows Mod Configuration").push("general");
        
        // Entity Stats Section
        COMMON_BUILDER.comment("Entity Stats").push("stats");
        
        STALKER_MOVEMENT_SPEED = COMMON_BUILDER
                .comment("Movement speed of the Stalker entity (default: 0.35)")
                .defineInRange("stalkerMovementSpeed", 0.35D, 0.1D, 1.0D);
        
        STALKER_ATTACK_DAMAGE = COMMON_BUILDER
                .comment("Attack damage dealt by the Stalker entity (default: 15.0)")
                .defineInRange("stalkerAttackDamage", 15.0D, 1.0D, 100.0D);
                
        
        STALKER_IS_INVINCIBLE = COMMON_BUILDER
                .comment("Whether the Stalker entity is invincible (default: true)")
                .define("stalkerIsInvincible", true);
        
        STALKER_MAX_HEALTH = COMMON_BUILDER
                .comment("Maximum health of the Stalker entity when not invincible (default: 800.0)")
                .defineInRange("stalkerMaxHealth", 800.0D, 1.0D, 1000.0D);

        STALKER_WAKING_DISTANCE = COMMON_BUILDER
                .comment("Distance that the entity can wake the player from (default: 15.0)")
                .defineInRange("stalkerWakingDistance", 15.0D, 1.0D, 30.0D);

        BREAKABLE_BLOCK_HARDNESS = COMMON_BUILDER
                .comment("Softness threshold for blocks the entity can break (default: 1.0)")
                .defineInRange("breakableBlockHardness", 1.0D, 0.0D, 100.0D);

        STALKER_PREVENT_SLEEP = COMMON_BUILDER
                .comment("Prevent the player from sleeping while the entity is nearby (default: true)")
                .define("stalkerPreventSleep", true);

        MINIMUM_SPAWN_DISTANCE = COMMON_BUILDER
                .comment("Minimum distance from the player the entity can spawn (default: 160)")
                .defineInRange("minimumSpawnDistance", 160, 32, 200);

        MAXIMUM_SPAWN_DISTANCE = COMMON_BUILDER
                .comment("Maximum distance from the player the entity can spawn (default: 240.0)")
                .defineInRange("maximumSpawnDistance", 240, 201, 480);
        
        COMMON_BUILDER.pop();
        
        // Sound Settings Section
        COMMON_BUILDER.comment("Sound Settings").push("sounds");
        
        ENABLE_PROXIMITY_SOUNDS = COMMON_BUILDER
                .comment("Enable proximity sounds when the Stalker is near (default: true)")
                .define("enableProximitySounds", true);
        
        ENABLE_ATTACK_SOUNDS = COMMON_BUILDER
                .comment("Enable sounds when the Stalker attacks (default: true)")
                .define("enableAttackSounds", true);
        
        ENABLE_DAMAGE_SOUNDS = COMMON_BUILDER
                .comment("Enable sounds when the Stalker takes damage (default: false)")
                .define("enableDamageSounds", false);
        
        COMMON_BUILDER.pop();
        
        COMMON_BUILDER.pop();
        COMMON_CONFIG = COMMON_BUILDER.build();
    }
    
    public static void register() {
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_CONFIG);
        ItFollowsMod.LOGGER.info("Registered It Follows Mod configuration");
    }
}