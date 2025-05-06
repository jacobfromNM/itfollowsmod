package com.itfollowsmod;

import com.itfollowsmod.entity.StalkerEntity;
import com.itfollowsmod.registry.ModEntities;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;

import java.util.Random;
import java.util.List;
import java.util.Comparator;

@Mod.EventBusSubscriber(modid = "itfollowsmod")
public class ModEvents {

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.level.isClientSide && player.level instanceof ServerLevel serverLevel) {
            cleanUpStalkers(serverLevel); // Hopefully this works...

            boolean stalkerExists = !serverLevel.getEntitiesOfClass(
                    StalkerEntity.class,
                    new AABB(-30000000, serverLevel.getMinBuildHeight(), -30000000,
                            30000000, serverLevel.getMaxBuildHeight(), 30000000))
                    .isEmpty();

            if (!stalkerExists) {
                ItFollowsMod.LOGGER.info("[It Follows] No Stalker found. Spawning one near player {}",
                        player.getName().getString());
                spawnNearPlayer(serverLevel, player);
            } else {
                ItFollowsMod.LOGGER.info("[It Follows] Existing Stalker found — no spawn triggered.");
            }
        }
    }

    public static void spawnNearPlayer(ServerLevel world, Player player) {
        Random random = new Random();
        double angle = random.nextDouble() * Math.PI * 2; // Random direction

        double distance = 48 + random.nextDouble() * 32; // 3-5 chunks (48-80 blocks)
        double spawnX = player.getX() + Math.cos(angle) * distance;
        double spawnZ = player.getZ() + Math.sin(angle) * distance;
        double spawnY = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(spawnX, 0, spawnZ)).getY();

        StalkerEntity stalker = new StalkerEntity(ModEntities.STALKER.get(), world);
        stalker.setPos(spawnX, spawnY, spawnZ);
        world.addFreshEntity(stalker);
    }

    public static void cleanUpStalkers(ServerLevel world) {
        List<StalkerEntity> stalkers = world.getEntitiesOfClass(
                StalkerEntity.class,
                new AABB(
                        -30000000, world.getMinBuildHeight(), -30000000,
                        30000000, world.getMaxBuildHeight(), 30000000));

        if (stalkers.size() <= 1) {
            ItFollowsMod.LOGGER.info("[It Follows] No extra stalkers found.");
            return;
        }

        stalkers.sort(Comparator.comparingInt(Entity::getId));
        StalkerEntity oldest = stalkers.get(0);

        ItFollowsMod.LOGGER.info("[It Follows] Found {} Stalkers. Keeping oldest (ID {}). Removing the rest.",
                stalkers.size(), oldest.getId());

        for (int i = 1; i < stalkers.size(); i++) {
            stalkers.get(i).discard();
        }
    }

}
