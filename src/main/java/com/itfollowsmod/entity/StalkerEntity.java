package com.itfollowsmod.entity;

// It Follows Mod imports
import com.itfollowsmod.ItFollowsMod;
import com.itfollowsmod.ModConfig;
import com.itfollowsmod.registry.ModSounds;

// Minecraft imports
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

// Forge Imports...
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

// Java imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * StalkerEntity Class: A creepy entity that follows the player and respawns
 * nearby, based on the
 * creature from the movie "It Follows" (2014). Spooky!
 */
public class StalkerEntity extends PathfinderMob {
    // Other Constants
    private static final int MAX_DISTRACTION_TIME = 20; // 1 second = 20 ticks
    private static final double COLLISION_ATTACK_RADIUS = 2.0; // Radius to attack entities that get in the way
    private static final int PLAYER_FOCUS_PRIORITY = 1; // Highest priority
    private static final int COLLISION_ENTITY_PRIORITY = 8; // Lower priority than player targeting
    private static final long BLOCK_BREAK_COOLDOWN = 20; // 1 second (about 20 ticks)
    private static final long SOUND_COOLDOWN = 100; // Prevents frequent sound playback
    private static final double SOUND_TRIGGER_DISTANCE = 15.0; // Distance to trigger sound
    private final Random random = new Random();
    private long lastRespawnTime = 0;
    private long lastBlockBreakTime = 0;
    private long lastSoundPlayTime = 0; // Prevents repeated sound spam
    private int stuckCounter = 0;
    private long targetResetTime = 0;
    private long lastDistanceCheckTime = 0; // Prevents frequent distance checks
    private Player primaryTarget = null; // The player that is being followed
    private int distractionCounter = 0;
    private BlockPos lastRecordedPosition = null;
    private int stuckTicks = 0;
    private boolean lastReportedStuck = false;

    /**
     * Constructor for the StalkerEntity.
     *
     * @param type  The entity type.
     * @param world The world.
     */
    public StalkerEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        this.setPersistenceRequired(); // Prevent despawning
        this.maxUpStep = 1.0F; // Can step up full blocks like a player
    }

    /**
     * Creates and returns the attribute supplier for the entity.
     *
     * @return The attribute supplier builder.
     */
    public static AttributeSupplier.Builder createAttributes() {
        // ItFollowsMod.LOGGER.info("[It Follows] Setting Mob attributes...");
        // ItFollowsMod.LOGGER.info("[It Follows] Loading attributes from ModConfig...");
        // ItFollowsMod.LOGGER.info("MAX_HEALTH: {}",
        // ModConfig.STALKER_MAX_HEALTH.get());
        // ItFollowsMod.LOGGER.info("MOVEMENT_SPEED: {}",
        // ModConfig.STALKER_MOVEMENT_SPEED.get());
        // ItFollowsMod.LOGGER.info("ATTACK_DAMAGE: {}",
        // ModConfig.STALKER_ATTACK_DAMAGE.get());

        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ModConfig.STALKER_MAX_HEALTH.get())
                .add(Attributes.MOVEMENT_SPEED, ModConfig.STALKER_MOVEMENT_SPEED.get())
                .add(Attributes.ATTACK_DAMAGE, ModConfig.STALKER_ATTACK_DAMAGE.get())
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ATTACK_KNOCKBACK, 2.5)
                .add(Attributes.ATTACK_SPEED, 1.2)
                .add(Attributes.FOLLOW_RANGE, 200.0);
    }

    /**
     * Initializes the entity attributes when added to the world.
     */
    @Override
    public void onAddedToWorld() {
        if (!this.level.isClientSide) {
            // Remove DuplicateEntities:
            removeDuplicateEntities();

            // Apply config values to attributes
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(ModConfig.STALKER_MAX_HEALTH.get());
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ModConfig.STALKER_ATTACK_DAMAGE.get());
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ModConfig.STALKER_MOVEMENT_SPEED.get());

            // Debug log
            // ItFollowsMod.LOGGER.info("StalkerEntity Speed Set: {}",
            // this.getAttributeValue(Attributes.MOVEMENT_SPEED));

            // **Force AI to recalculate path with new speed**
            this.getNavigation().stop();
            this.getNavigation().moveTo(this.getX(), this.getY(), this.getZ(), 1.0);
        }
    }

    /**
     * Registers the goals for the entity.
     */
    protected void registerGoals() {
        // Primary focus: ALWAYS follow the target
        this.goalSelector.addGoal(1, new StalkerFollowGoal(this));

        // Secondary focus: Deal with obstacles
        this.goalSelector.addGoal(2, new StalkerBreakDoorGoal(this));
        this.goalSelector.addGoal(3, new StalkerBreakGateGoal(this));

        // Opening doors is almost an afterthought - it will break through if that's
        // faster
        this.goalSelector.addGoal(4, new OpenDoorGoal(this));
        this.goalSelector.addGoal(5, new StalkerOpenGateGoal(this));

        // Attack when in range - this happens automatically as it follows
        this.goalSelector.addGoal(6,
                new MeleeAttackGoal(this, this.getAttributeValue(Attributes.MOVEMENT_SPEED), true));

        // Basic survival is lowest priority - it's supernatural after all
        this.goalSelector.addGoal(7, new FloatGoal(this));

        // Targeting remains focused entirely on the player
        this.targetSelector.addGoal(PLAYER_FOCUS_PRIORITY, new NearestAttackableTargetGoal<>(this, Player.class, true));

        // Added a custom collision entity goal with lower priority
        this.targetSelector.addGoal(COLLISION_ENTITY_PRIORITY,
                new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false,
                        (livingEntity) -> {
                            // Only target non-players that are in direct path to the player
                            return !(livingEntity instanceof Player) &&
                                    isEntityInPathToPlayer(livingEntity) &&
                                    this.distanceTo(livingEntity) < COLLISION_ATTACK_RADIUS;
                        }));
    }

    /**
     * Determines if an entity is in the direct path to the player.
     * 
     * @param entity The entity to check
     * @return True if the entity is in the path to the player
     */
    private boolean isEntityInPathToPlayer(LivingEntity entity) {
        if (primaryTarget == null)
            return false;

        // Get vector from stalker to player
        Vec3 stalkerToPlayer = new Vec3(
                primaryTarget.getX() - this.getX(),
                primaryTarget.getY() - this.getY(),
                primaryTarget.getZ() - this.getZ());

        // Get vector from stalker to entity
        Vec3 stalkerToEntity = new Vec3(
                entity.getX() - this.getX(),
                entity.getY() - this.getY(),
                entity.getZ() - this.getZ());

        // Normalize vectors
        Vec3 stalkerToPlayerNormalized = stalkerToPlayer.normalize();
        Vec3 stalkerToEntityNormalized = stalkerToEntity.normalize();

        // Calculate dot product to determine angle
        double dotProduct = stalkerToPlayerNormalized.x * stalkerToEntityNormalized.x +
                stalkerToPlayerNormalized.y * stalkerToEntityNormalized.y +
                stalkerToPlayerNormalized.z * stalkerToEntityNormalized.z;

        // Entity is in path if dot product is high (angle is small) and distance is
        // small
        return dotProduct > 0.7 && this.distanceTo(entity) < 5.0;
    }

    /**
     * aiStep method with respawn logic and cooldowns, called once per tick.
     */
    @Override
    public void aiStep() {
        super.aiStep();

        long worldTime = this.level.getGameTime();

        // Update the primary target (player) more frequently
        if (primaryTarget == null || !primaryTarget.isAlive() || worldTime % 40 == 0) {
            updatePrimaryTarget();
            // Immediately set as target to maintain focus
            if (primaryTarget != null) {
                this.setTarget(primaryTarget);
            }
        }

        // Check if we're stuck with cooldown (once per 3 seconds)
        if (worldTime % 60 == 0) {
            boolean currentlyStuck = isStuck();

            // If we've been stuck for several checks, trigger respawn  
            if (currentlyStuck) {
                stuckCounter++;
                if (stuckCounter >= 3 && worldTime - lastRespawnTime >= 100) { // 5 seconds between respawn attempts
                    if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Stuck for too long, respawning");
                    this.respawnNearby();
                    lastRespawnTime = worldTime;
                    stuckCounter = 0;
                }
            } else {
                stuckCounter = 0;
            }
        }

        // Day cycle respawn (every 20 minutes) - in other words, if I'm not stuck,
        // respawn every 20 minutes.
        if (worldTime - lastRespawnTime >= 24000) {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Day cycle respawn triggered");
            this.respawnNearby();
            lastRespawnTime = worldTime;
        }

        this.breakBlocksInPath();
        if (ModConfig.STALKER_PREVENT_SLEEP.get())
            this.wakeSleepingPlayers();
        if (ModConfig.ENABLE_PROXIMITY_SOUNDS.get())
            this.playSound();
        this.checkPlayerDistanceAndRespawn();

        // Attack entities in the path to player without changing primary target
        this.attackEntitiesInPath();

        // Always ensure player is the primary target unless temporarily distracted
        if (this.getTarget() != primaryTarget && primaryTarget != null && primaryTarget.isAlive()) {
            // Check if we should return to targeting the player
            if (worldTime % 20 == 0 || this.distanceTo(primaryTarget) < 5.0) {
                this.setTarget(primaryTarget);
            }
        }
    }

    /**
     * Check if the entity is in water/lava and adjust the speed accordingly (move
     * faster). Also adds a minor speed booost if the player is close.
     * 
     * @param travelVector The travel vector.
     */
    @Override
    public void travel(Vec3 travelVector) {
        boolean inLiquid = this.isInWater() || this.isInLava();
        double baseSpeed = ModConfig.STALKER_MOVEMENT_SPEED.get();
        double adjustedSpeed = baseSpeed;

        if (inLiquid) {
            // Double speed when stalking through water or lava—because nothing is more
            // terrifying than a wet Stalker
            adjustedSpeed = baseSpeed * 2.0;
        } else {
            // Adjust speed based on proximity to the player
            if (primaryTarget != null && primaryTarget.isAlive()) {
                double distance = this.distanceTo(primaryTarget);

                if (distance <= 15.0) {
                    adjustedSpeed = baseSpeed * 1.3; // 30% faster when right behind you—boo!
                } else if (distance <= 30.0) {
                    adjustedSpeed = baseSpeed * 1.2; // 20% faster when getting closer
                }
                // Else, keep base speed—no rush, it has eternity
            }
        }

        // Apply the speed value to the entity
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(adjustedSpeed);

        // Proceed with normal travel logic
        super.travel(travelVector);
    }

    /**
     * Method to attack entities in the path without changing target
     */
    private void attackEntitiesInPath() {
        if (primaryTarget == null)
            return;

        // Find entities in a narrow cone in front of the stalker toward the player
        List<LivingEntity> entitiesInPath = this.level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(COLLISION_ATTACK_RADIUS, COLLISION_ATTACK_RADIUS,
                        COLLISION_ATTACK_RADIUS),
                entity -> entity != this &&
                        entity != primaryTarget &&
                        entity.isAlive() &&
                        isEntityInPathToPlayer(entity));

        // Attack the closest entity in path without changing primary target
        if (!entitiesInPath.isEmpty()) {
            LivingEntity closest = null;
            double closestDist = Double.MAX_VALUE;

            for (LivingEntity entity : entitiesInPath) {
                double dist = this.distanceToSqr(entity);
                if (dist < closestDist) {
                    closest = entity;
                    closestDist = dist;
                }
            }

            if (closest != null && this.distanceTo(closest) < COLLISION_ATTACK_RADIUS) {
                // Attack without changing target
                this.doHurtTarget(closest);

                // Very briefly change target but quickly return to player
                if (random.nextInt(5) == 0) { // Only 20% chance to temporarily switch targets
                    // LivingEntity previousTarget = this.getTarget();
                    this.setTarget(closest);
                    // Reset back to previous target soon
                    this.targetResetTime = this.level.getGameTime() + 20; // Reset after 1 second
                }
            }
        }
    }

    /**
     * Update the primary target (always a player)
     */
    private void updatePrimaryTarget() {
        Player nearest = this.level.getNearestPlayer(this, 512.0); // Get the nearest player within 512 blocks
        if (nearest != null) {
            primaryTarget = nearest;
        }
    }

    /**
     * Remove duplicate entities from the world.
     */
    private void removeDuplicateEntities() {
        if (this.level.isClientSide) return; // Only run on server side
    
        ServerLevel serverLevel = (ServerLevel) this.level;
    
        List<StalkerEntity> allStalkers = serverLevel.getEntitiesOfClass(
            StalkerEntity.class,
            new AABB(
                serverLevel.getMinBuildHeight(), 0, serverLevel.getMinBuildHeight(),
                serverLevel.getMaxBuildHeight(), 256, serverLevel.getMaxBuildHeight())
        );
    
        if (allStalkers.size() <= 1) return;
    
        allStalkers.sort(Comparator.comparingInt(Entity::getId));
        StalkerEntity oldest = allStalkers.get(0);
    
        for (int i = 1; i < allStalkers.size(); i++) {
            StalkerEntity duplicate = allStalkers.get(i);
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It FOllows] Removing duplicate Stalker with ID: {}", duplicate.getId());
            duplicate.discard();
        }
    
        if (this != oldest) {
            this.discard();
        }
    }
    

    /**
     * Prevents the entity from despawning when far away.
     * 
     * @param distance The distance from the player.
     * @return True if the entity should despawn, false otherwise. Hint: Always
     *         return false.
     */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false; // Prevent despawning when far away
    }

    /**
     * Returns if the entity is invincible.
     *
     * @return The entity's invincibility status.
     */
    public boolean isInvincible() {
        return ModConfig.STALKER_IS_INVINCIBLE.get();
    }

    /**
     * Remove the entity's health bar
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Return true if the entity is invincible
        return this.isInvincible();
    }

    /**
     * Handles damage taken by the entity.
     *
     * @param source The source of the damage.
     * @param amount The amount of damage.
     * @return True if the entity was hurt, false otherwise.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // If the entity is invincible, play sound but don't apply damage
        if (this.isInvincible()) {

            // Play sound regardless of damage source - if configured to play sounds.
            if (ModConfig.ENABLE_DAMAGE_SOUNDS.get()) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.VIOLINS.get(),
                        this.getSoundSource(), // Entity sound source
                        1.0F, // Volume
                        1.0F); // Pitch
            }
            // Handle targeting logic even if invincible
            if (source.getEntity() instanceof LivingEntity && !(source.getEntity() instanceof Player)) {
                this.setTarget((LivingEntity) source.getEntity());
                this.targetResetTime = this.level.getGameTime() + 20; // Reset after 1 second
            }

            // Return false to prevent damage
            return false;
        }

        // If not invincible, process damage normally
        boolean hurt = super.hurt(source, amount);

        if (hurt) {
            // Play sound
            if (ModConfig.ENABLE_DAMAGE_SOUNDS.get()) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.VIOLINS.get(),
                        this.getSoundSource(),
                        1.0F,
                        1.0F);
            }

            // Handle targeting logic
            if (source.getEntity() instanceof LivingEntity && !(source.getEntity() instanceof Player)) {
                this.setTarget((LivingEntity) source.getEntity());
                this.targetResetTime = this.level.getGameTime() + 20;
            }
        }

        return hurt;
    }

    /**
     * Handles the entity's attack on a target, whether it's the player, or another
     * entity.
     *
     * @param target The target entity.
     * @return True if the attack was successful, false otherwise.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (target instanceof LivingEntity) {
            double damage = ModConfig.STALKER_ATTACK_DAMAGE.get();
            boolean success = target.hurt(DamageSource.mobAttack(this), (float) damage);
            if (success) {
                // Play attack sound
                if (ModConfig.ENABLE_ATTACK_SOUNDS.get()) {
                    this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                            ModSounds.ELECTRIC_ROAR.get(),
                            this.getSoundSource(), // Entity sound source
                            0.7F, // Volume
                            1.0F); // Pitch
                }
                // If it's not the primary target, only briefly target it
                if (target != primaryTarget && target instanceof LivingEntity) {
                    this.targetResetTime = this.level.getGameTime() + 20; // Reset after 1 second
                }
            }
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Attacked target for {} damage.", damage);
            return success;
        }
        return super.doHurtTarget(target);
    }

    /**
     * Do nothing, as the entity should not die.
     *
     * @param cause The cause of death.
     */
    @Override
    public void die(DamageSource cause) {
        // If invincible, do nothing
        if (this.isInvincible()) {
            return;
        }
        // Otherwise, call the parent method
        super.die(cause);
    }

    /**
     * StalkerFollowGoal: Follows the nearest player while attacking anything in its
     * path.
     */
    private class StalkerFollowGoal extends Goal {
        private final StalkerEntity stalker;
        private int pathRecalculationDelay = 0;

        public StalkerFollowGoal(StalkerEntity stalker) {
            this.stalker = stalker;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return primaryTarget != null && primaryTarget.isAlive();
        }

        @Override
        public void start() {
            // Set the primary target (player)
            stalker.setTarget(primaryTarget);
            pathRecalculationDelay = 0;
        }

        @Override
        public void tick() {
            if (primaryTarget == null || !primaryTarget.isAlive())
                return;

            // Focus on player by looking at them
            stalker.getLookControl().setLookAt(primaryTarget, 30.0F, 30.0F);

            // If we're targeting something other than the player, track distraction time
            if (stalker.getTarget() != null && stalker.getTarget() != primaryTarget) {
                distractionCounter++;

                // Return to targeting player after MAX_DISTRACTION_TIME or if player is close
                if (distractionCounter >= MAX_DISTRACTION_TIME ||
                        stalker.distanceTo(primaryTarget) < 5.0) {
                    stalker.setTarget(primaryTarget);
                    distractionCounter = 0;
                }
            } else {
                distractionCounter = 0;
            }

            // Recalculate path to player more frequently to improve navigation
            if (--pathRecalculationDelay <= 0) {
                pathRecalculationDelay = 10; // Recalculate every half second
                stalker.getNavigation().moveTo(primaryTarget, stalker.getAttributeValue(Attributes.MOVEMENT_SPEED));
            }

            // Attack if close to player
            if (stalker.distanceTo(primaryTarget) <= 2.0) {
                stalker.doHurtTarget(primaryTarget);
            }
        }
    }

    /**
     * Goal to open gates.
     */
    static class StalkerOpenGateGoal extends Goal {
        private final StalkerEntity stalker;
        private BlockPos targetGatePos = null;

        public StalkerOpenGateGoal(StalkerEntity stalker) {
            this.stalker = stalker;
        }

        @Override
        public boolean canUse() {
            if (stalker.getNavigation().isDone()) {
                return false; // Only open gates if the stalker is actively moving
            }

            BlockPos entityPos = stalker.blockPosition();

            // Check nearby blocks for a closed fence gate
            for (BlockPos pos : BlockPos.betweenClosed(entityPos.offset(-1, 0, -1), entityPos.offset(1, 1, 1))) {
                BlockState state = stalker.level.getBlockState(pos);
                if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                    targetGatePos = pos;
                    return true;
                }
            }

            return false;
        }

        @Override
        public void start() {
            if (targetGatePos != null) {
                BlockState state = stalker.level.getBlockState(targetGatePos);
                if (state.getBlock() instanceof FenceGateBlock) {
                    stalker.level.setBlock(targetGatePos, state.setValue(FenceGateBlock.OPEN, true), 10);
                    stalker.playSound(SoundEvents.FENCE_GATE_OPEN, 1.0F, 1.0F);
                }
            }
        }
    }

    /**
     * Goal to break gates.
     */
    static class StalkerBreakGateGoal extends Goal {
        private final StalkerEntity stalker;

        public StalkerBreakGateGoal(StalkerEntity stalker) {
            this.stalker = stalker;
        }

        @Override
        public boolean canUse() {
            BlockPos pos = stalker.blockPosition();
            BlockState state = stalker.level.getBlockState(pos);
            return state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN) == false;
        }

        @Override
        public void start() {
            BlockPos pos = stalker.blockPosition();
            BlockState state = stalker.level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock) {
                stalker.level.destroyBlock(pos, true);
                stalker.playSound(SoundEvents.WOOD_BREAK, 1.0F, 1.0F);
                if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Broke a fence gate at {}", pos);
            }
        }
    }

    /**
     * Goal to open doors.
     */
    static class OpenDoorGoal extends Goal {
        private final StalkerEntity stalker;
        private BlockPos targetDoorPos = null;

        public OpenDoorGoal(StalkerEntity stalker) {
            this.stalker = stalker;
        }

        @Override
        public boolean canUse() {
            if (stalker.getNavigation().isDone()) {
                return false; // Only open doors if the stalker is actively moving
            }

            BlockPos entityPos = stalker.blockPosition();

            // Check nearby blocks for a closed door
            for (BlockPos pos : BlockPos.betweenClosed(entityPos.offset(-1, 0, -1), entityPos.offset(1, 1, 1))) {
                BlockState state = stalker.level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                        && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                    targetDoorPos = pos;
                    return true;
                }
            }

            return false;
        }

        @Override
        public void start() {
            if (targetDoorPos != null) {
                BlockState state = stalker.level.getBlockState(targetDoorPos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                    stalker.level.setBlock(targetDoorPos,
                            state.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true), 10);
                    stalker.playSound(SoundEvents.FENCE_GATE_OPEN, 1.0F, 1.0F);
                }
            }
        }
    }

    /**
     * Goal to break doors.
     */
    static class StalkerBreakDoorGoal extends Goal {
        private final StalkerEntity stalker;
        private long lastBreakAttempt = 0;
        private static final long BREAK_COOLDOWN = 60; // 3 seconds in ticks

        public StalkerBreakDoorGoal(StalkerEntity stalker) {
            this.stalker = stalker;
        }

        @Override
        public boolean canUse() {
            BlockPos entityPos = stalker.blockPosition();
            long currentTime = stalker.level.getGameTime();

            // Check the block the entity is in and adjacent blocks
            for (BlockPos pos : BlockPos.betweenClosed(entityPos.offset(-1, 0, -1), entityPos.offset(1, 1, 1))) {
                BlockState state = stalker.level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock &&
                        !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) &&
                        currentTime - lastBreakAttempt > BREAK_COOLDOWN) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void start() {
            BlockPos entityPos = stalker.blockPosition();

            // Check the block the entity is in and adjacent blocks
            for (BlockPos pos : BlockPos.betweenClosed(entityPos.offset(-1, 0, -1), entityPos.offset(1, 1, 1))) {
                BlockState state = stalker.level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock &&
                        !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) {
                    stalker.level.destroyBlock(pos, true);
                    stalker.playSound(SoundEvents.WOOD_BREAK, 1.0F, 1.0F);
                    if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Broke a door at {}", pos);
                    lastBreakAttempt = stalker.level.getGameTime();
                    break;
                }
            }
        }
    }

    /**
     * Plays a creepy sound near the entity.
     */
    private void playSound() {
        if (!ModConfig.ENABLE_PROXIMITY_SOUNDS.get())
            return;

        long currentTime = this.level.getGameTime();
        if (currentTime - lastSoundPlayTime < SOUND_COOLDOWN + random.nextInt(200))
            return; // Randomized delay

        for (Player player : this.level.players()) {
            double distance = this.distanceTo(player);

            if (distance < SOUND_TRIGGER_DISTANCE) {
                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.VIOLINS.get(),
                        this.getSoundSource(), // Entity sound source
                        0.5F, // Volume
                        1.0F); // Pitch

                this.level.playSound(null, this.getX(), this.getY(), this.getZ(),
                        ModSounds.WHISPERS_001.get(),
                        this.getSoundSource(), // Entity sound source
                        1.0F, // Volume
                        1.0F); // Pitch

                if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Playing sound");
                break; // Play sound for only one player at a time
            }
        }

        lastSoundPlayTime = currentTime;
    }

    /**
     * Wakes up sleeping players near the entity.
     */
    private void wakeSleepingPlayers() {
        if (!(this.level instanceof ServerLevel)) {
            return; // Exit early if not on the server
        }

        ServerLevel serverWorld = (ServerLevel) this.level;

        for (Player player : this.level.players()) {
            if (player.isSleeping() && this.distanceTo(player) < ModConfig.STALKER_WAKING_DISTANCE.get()) {
                if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] wakeSleepingPlayers: Waking player.");
                player.stopSleeping();
                player.displayClientMessage(Component.literal("You can't sleep, something approaches..."), true);
                attemptTeleportNearPlayer(serverWorld);
            }
        }
    }

    /**
     * Checks the distance to the player and respawns the entity if too far away,
     * called specifically from wakeSleepingPlayers()
     *
     * @param serverWorld The server world.
     */
    private void attemptTeleportNearPlayer(ServerLevel serverWorld) {
        Player nearestPlayer = serverWorld.getNearestPlayer(this, 512.0);

        if (nearestPlayer == null) {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] No nearby player found for teleportation.");
            return;
        }

        // boolean shouldTeleport = (this.distanceToSqr(nearestPlayer) >
        // ModConfig.MINIMUM_SPAWN_DISTANCE.get()) && (random.nextDouble() < 0.20); //
        // 20% chance
        boolean shouldTeleport = this.distanceToSqr(nearestPlayer) > ModConfig.MINIMUM_SPAWN_DISTANCE.get();


        if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Teleport check: Distance={}, MinDistance={}, ChanceRoll={}", 
            this.distanceToSqr(nearestPlayer),
            ModConfig.MINIMUM_SPAWN_DISTANCE.get(),
            shouldTeleport);


        if (!shouldTeleport)
            return;

        // Try to find a valid teleport position
        BlockPos spawnPos = trySpawnAdjacentToPlayer(serverWorld, nearestPlayer);

        if (spawnPos != null) {
            this.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        } else {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] No valid spawn positions found near player.");
        }
    }

    /**
     * Breaks blocks in the entity's path.
     */
    private void breakBlocksInPath() {
        if (this.level.getGameTime() - lastBlockBreakTime < BLOCK_BREAK_COOLDOWN) {
            return;
        }

        BlockPos frontPos = this.blockPosition().relative(this.getDirection());
        BlockState blockState = this.level.getBlockState(frontPos);

        if (isBreakable(blockState)) {
            this.level.destroyBlock(frontPos, true);
            lastBlockBreakTime = this.level.getGameTime();
        }
    }

    /**
     * Checks if a block state is breakable by the entity.
     *
     * @param state The block state.
     * @return True if the block is breakable, false otherwise.
     */
    private boolean isBreakable(BlockState state) {
        Block block = state.getBlock();
        float hardness = state.getDestroySpeed(level, this.blockPosition());

        // First, check if the block is breakable by the entity based on the modconfig value.
        if (hardness > 0 && hardness < ModConfig.BREAKABLE_BLOCK_HARDNESS.get()) return true;

        // Allow specific block types (thematic breaking)
        // Always break these thematic "barrier" types
        if (block instanceof FenceGateBlock ||
                block instanceof net.minecraft.world.level.block.DoorBlock ||
                block instanceof net.minecraft.world.level.block.TrapDoorBlock ||
                block instanceof net.minecraft.world.level.block.WallBlock) {
            return true;
        }

        // Use ForgeRegistries to get the block's registry path
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null) {
            String path = id.getPath();
            if (path.contains("barrier") || path.contains("torch") || path.contains("candle")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Respawns the entity nearby a player.
     * Uses more direct Minecraft mechanics for reliable spawning.
     */
    private void respawnNearby() {
        if (this.level.isClientSide)
            return;

        ServerLevel serverWorld = (ServerLevel) this.level;
        Player nearestPlayer = serverWorld.getNearestPlayer(this, 512.0);

        if (nearestPlayer == null) {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.warn("[It Follows] respawnNearby: No player found nearby. Leaving entity at current location.");
            return;
        }

        // Check for the rare chance to spawn right next to player
        boolean spawnNearPlayer = (this.distanceToSqr(nearestPlayer) > ModConfig.MINIMUM_SPAWN_DISTANCE.get())
                && (random.nextDouble() < 0.05); // 5% chance to spawn directly next to the player.
        BlockPos spawnPos = null;

        if (spawnNearPlayer) {
            spawnPos = trySpawnAdjacentToPlayer(serverWorld, nearestPlayer);
            if (spawnPos != null) {
                this.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Respawned directly beside player at {}",spawnPos);
                return;
            }
        }

        // If adjacent spawn failed or wasn't attempted, try normal spawn
        spawnPos = findSpawnLocationNearPlayer(serverWorld, nearestPlayer);

        if (spawnPos != null) {
            this.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Respawned successfully at {}", spawnPos);

            // Make sure the entity isn't stuck in blocks after teleporting
            if (this.level.getBlockState(this.blockPosition()).getMaterial().isSolid()) {
                // Try to place it on top of any solid block
                BlockPos elevatedPos = this.blockPosition().above();
                while (this.level.getBlockState(elevatedPos).getMaterial().isSolid() &&
                        elevatedPos.getY() < this.level.getMaxBuildHeight() - 2) {
                    elevatedPos = elevatedPos.above();
                }
                this.teleportTo(elevatedPos.getX() + 0.5, elevatedPos.getY(), elevatedPos.getZ() + 0.5);
                if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Adjusted position upward to {}",elevatedPos);
            }
        } else {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.warn("[It Follows] Failed to find spawn position - falling back to vanilla spawn placement");
            
            // As a fallback, use vanilla mob spawn logic directly
            vanillaSpawnNearPlayer(serverWorld, nearestPlayer);
        }
    }

    /**
     * Attempts to place the entity using vanilla-style spawn mechanics.
     * This is our fallback method when other approaches fail.
     * 
     * @param serverWorld The server world.
     * @param player      The player to spawn near.
     * @return True if the spawn was successful, false otherwise.
     */
    private void vanillaSpawnNearPlayer(ServerLevel serverWorld, Player player) {
        int spawnRadius = (int) ModConfig.MAXIMUM_SPAWN_DISTANCE.get();
        int minDistance = (int) ModConfig.MAXIMUM_SPAWN_DISTANCE.get() / 2; // Minimum distance from player
        int maxTries = 50;

        for (int attempt = 0; attempt < maxTries; attempt++) {
            // Get random position within spawn radius
            int x = player.blockPosition().getX() + random.nextInt(spawnRadius * 2) - spawnRadius;
            int z = player.blockPosition().getZ() + random.nextInt(spawnRadius * 2) - spawnRadius;

            // Check if it's within the minimum distance
            BlockPos candidatePos = new BlockPos(x, 0, z);
            if (candidatePos.distSqr(player.blockPosition()) < minDistance * minDistance) {
                continue; // Too close to player, try again
            }

            // Get the top valid position at this x,z coordinate
            BlockPos spawnPos = getSpawnablePos(serverWorld, candidatePos);

            if (spawnPos != null) {
                this.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Vanilla spawn succeeded at {}", spawnPos);
                return;
            }
        }

        if (ModConfig.ENABLE_LOGGING.get())  ItFollowsMod.LOGGER.error("[It Follows] All spawn attempts failed - entity willremain at current position");
    }

    /**
     * Gets a valid Y position for the given X,Z coordinates.
     * Tries both surface and cave positions.
     * 
     * @param world The world.
     * @param pos   The X,Z position.
     * @return A valid spawn position, or null if none found.
     */
    private BlockPos getSpawnablePos(ServerLevel world, BlockPos pos) {
        // First try to get surface position
        BlockPos surfacePos = getSurfaceSpawnPos(world, pos);
        if (surfacePos != null)
            return surfacePos;

        // If surface fails, try cave position
        return getCaveSpawnPos(world, pos);
    }

    /**
     * Gets a surface spawn position.
     */
    private BlockPos getSurfaceSpawnPos(ServerLevel world, BlockPos pos) {
        // Get the top non-leaves position
        BlockPos surfacePos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);

        // Check that there's a solid block below and space above
        if (!world.getBlockState(surfacePos.below()).getMaterial().isSolid()) {
            return null;
        }

        // Check for liquids
        if (world.getBlockState(surfacePos).getMaterial().isLiquid()) {
            return null;
        }

        // Make sure there's enough headroom (2 blocks)
        if (world.getBlockState(surfacePos).getMaterial().isSolid() ||
                world.getBlockState(surfacePos.above()).getMaterial().isSolid()) {
            return null;
        }

        return surfacePos;
    }

    /**
     * Gets a cave spawn position.
     * 
     * @param world The world.
     * @param pos   The X,Z position.
     * @return A valid spawn position, or null if none found.
     */
    private BlockPos getCaveSpawnPos(ServerLevel world, BlockPos pos) {
        // Start at surface height minus a bit
        int startY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 5;
        int minY = world.getMinBuildHeight() + 5;

        // Search downward for a valid cave position
        for (int y = startY; y > minY; y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());

            // Skip if we can see sky here (we want caves)
            if (world.canSeeSky(checkPos))
                continue;

            // Need a solid block below
            BlockPos below = checkPos.below();
            if (!world.getBlockState(below).getMaterial().isSolid())
                continue;

            // Check that the space is free
            if (world.getBlockState(checkPos).getMaterial().isSolid())
                continue;
            if (world.getBlockState(checkPos.above()).getMaterial().isSolid())
                continue;

            // Check for liquids
            if (world.getBlockState(checkPos).getMaterial().isLiquid())
                continue;
            if (world.getBlockState(below).getMaterial().isLiquid())
                continue;

            // Found a good spot!
            return checkPos;
        }

        return null;
    }

    /**
     * Find a spawn location near a player.
     * Divides the area into chunks and samples chunks for valid positions.
     * 
     * @param world  The world.
     * @param player The player.
     * @return A valid spawn position, or null if none found.
     */
    private BlockPos findSpawnLocationNearPlayer(ServerLevel world, Player player) {
        int chunkRange = 5; // 5 chunks radius
        BlockPos playerPos = player.blockPosition();

        // Convert player position to chunk coordinates
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        // Create a list of all chunk coordinates in range
        List<ChunkPos> chunkPositions = new ArrayList<>();
        for (int x = playerChunkX - chunkRange; x <= playerChunkX + chunkRange; x++) {
            for (int z = playerChunkZ - chunkRange; z <= playerChunkZ + chunkRange; z++) {
                // Calculate squared distance to ensure we're within a circle
                int dx = x - playerChunkX;
                int dz = z - playerChunkZ;
                if (dx * dx + dz * dz <= chunkRange * chunkRange) {
                    chunkPositions.add(new ChunkPos(x, z));
                }
            }
        }

        // Shuffle the list to randomize search order
        Collections.shuffle(chunkPositions, new Random());

        // Try up to 10 chunks
        int maxChunksToTry = Math.min(10, chunkPositions.size());
        for (int i = 0; i < maxChunksToTry; i++) {
            ChunkPos chunkPos = chunkPositions.get(i);

            // Try 10 random positions within each chunk
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = (chunkPos.x << 4) + random.nextInt(16);
                int z = (chunkPos.z << 4) + random.nextInt(16);

                BlockPos testPos = new BlockPos(x, 0, z);
                BlockPos spawnPos = getSpawnablePos(world, testPos);

                if (spawnPos != null) {
                    return spawnPos;
                }
            }
        }

        return null;
    }

    /**
     * Try to spawn the entity adjacent to the player.
     * 
     * @param world  The world.
     * @param player The player.
     * @return A valid spawn position, or null if none found.
     */
    private BlockPos trySpawnAdjacentToPlayer(ServerLevel world, Player player) {
        BlockPos playerPos = player.blockPosition();
        List<BlockPos> adjacentPositions = new ArrayList<>();

        // Add positions in a 5x5 area around player, but not directly on player
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0)
                    continue; // Skip player's position

                BlockPos pos = playerPos.offset(x, 0, z);
                BlockPos spawnPos = getSpawnablePos(world, pos);

                if (spawnPos != null) {
                    adjacentPositions.add(spawnPos);
                }
            }
        }

        if (!adjacentPositions.isEmpty()) {
            // Pick a random position from valid ones
            return adjacentPositions.get(random.nextInt(adjacentPositions.size()));
        }

        return null;
    }

    /**
     * Check the distance to the player and respawn the entity if too far away.
     */
    private void checkPlayerDistanceAndRespawn() {
        if (this.level.isClientSide)
            return;

        long currentTime = this.level.getGameTime();
        if (currentTime - lastDistanceCheckTime < 100)
            return; // Only check every 5 seconds

        Player nearestPlayer = this.level.getNearestPlayer(this, 1024.0); // Get the nearest player within range
        if (nearestPlayer == null)
            return;

        double distanceSquared = this.distanceToSqr(nearestPlayer);

        // Generate a random respawn threshold within 10-15 chunks
        int respawnDistance = ModConfig.MINIMUM_SPAWN_DISTANCE.get() + this.random
                .nextInt(ModConfig.MAXIMUM_SPAWN_DISTANCE.get() - ModConfig.MINIMUM_SPAWN_DISTANCE.get() + 1);

        if (distanceSquared > respawnDistance * respawnDistance) {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.info("[It Follows] Player is {} blocks away, respawning closer", Math.sqrt(distanceSquared));
            this.respawnNearby();
            lastRespawnTime = currentTime;
        }

        lastDistanceCheckTime = currentTime;
    }

    /**
     * Checks if the entity is stuck, with improved detection
     */
    private boolean isStuck() {
        // Only check if we're actually trying to move
        if (!this.getNavigation().isInProgress()) {
            return false;
        }

        // Check if we're stuck in a block
        boolean inSolid = this.level.getBlockState(this.blockPosition()).getMaterial().isSolid();

        // Check if we've made no progress for a while
        boolean navigationStuck = this.getNavigation().isStuck();

        // Check if we've been at the same position for multiple ticks
        BlockPos currentPos = this.blockPosition();
        boolean samePosition = false;

        if (lastRecordedPosition != null && lastRecordedPosition.equals(currentPos)) {
            stuckTicks++;
            if (stuckTicks > 40) { // 2 seconds of no movement
                samePosition = true;
            }
        } else {
            stuckTicks = 0;
            lastRecordedPosition = currentPos;
        }

        boolean stuck = inSolid || navigationStuck || samePosition;

        if (stuck && !lastReportedStuck) {
            if (ModConfig.ENABLE_LOGGING.get()) ItFollowsMod.LOGGER.warn("[It Follows] isStuck: Stalker is stuck at {}", this.blockPosition());
            lastReportedStuck = true;
        } else if (!stuck) {
            lastReportedStuck = false;
        }

        return stuck;
    }

    public boolean isClimbing() {
        BlockPos pos = this.blockPosition();
        BlockState state = this.level.getBlockState(pos);
        return state.is(BlockTags.CLIMBABLE);
    }

    @Override
    protected PathNavigation createNavigation(Level world) {
        return new WallClimberNavigation(this, world);
    }

    /**
     * Gets the entity's name, to be printed when the player dies as "the follower."
     */
    @Override
    public Component getTypeName() {
        return Component.translatable("death.itfollowsmod.stalker");
    }
}