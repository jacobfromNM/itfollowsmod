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
import java.util.Random;

@Mod.EventBusSubscriber(modid = "itfollowsmod")
public class ModEvents {
    
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!player.level.isClientSide && player.level instanceof ServerLevel serverLevel) {
            spawnNearPlayer(serverLevel, player);
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
}
