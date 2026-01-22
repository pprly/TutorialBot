package com.builderbot.builder;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Creates and manages a fake player entity that builds the schematic.
 * The fake player walks, looks around, and places blocks with animations.
 */
public class FakePlayerBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");
    
    private static final String DEFAULT_NAME = "BuilderBot";
    private static final double WALK_SPEED = 0.15; // blocks per tick
    private static final double REACH_DISTANCE = 4.5;
    
    private ServerPlayerEntity fakePlayer;
    private ServerWorld world;
    private String playerName;
    private boolean spawned;
    
    // Movement state
    private BlockPos targetPos;
    private boolean isWalking;
    
    public FakePlayerBuilder() {
        this(DEFAULT_NAME);
    }
    
    public FakePlayerBuilder(String playerName) {
        this.playerName = playerName;
        this.spawned = false;
    }
    
    /**
     * Spawns the fake player at the given position.
     */
    public boolean spawn(ServerWorld world, BlockPos pos) {
        if (spawned) {
            LOGGER.warn("Fake player already spawned");
            return false;
        }
        
        this.world = world;
        MinecraftServer server = world.getServer();
        
        // Create game profile
        GameProfile profile = new GameProfile(UUID.randomUUID(), playerName);
        
        try {
            // Create fake player entity
            fakePlayer = new ServerPlayerEntity(
                server,
                world,
                profile,
                ServerPlayerEntity.ClientSettings.DEFAULT
            );
            
            // Position the player
            fakePlayer.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            
            // Set creative mode (no resources needed)
            fakePlayer.changeGameMode(GameMode.CREATIVE);
            
            // Create fake network handler to prevent NPEs
            // This is a minimal implementation
            ClientConnection connection = new ClientConnection(NetworkSide.CLIENTBOUND);
            ConnectedClientData clientData = ConnectedClientData.createDefault(profile, false);
            fakePlayer.networkHandler = new ServerPlayNetworkHandler(server, connection, fakePlayer, clientData);
            
            // Add to world
            world.spawnEntity(fakePlayer);
            
            spawned = true;
            LOGGER.info("Spawned fake player '{}' at {}", playerName, pos.toShortString());
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to spawn fake player", e);
            return false;
        }
    }
    
    /**
     * Despawns the fake player.
     */
    public void despawn() {
        if (!spawned || fakePlayer == null) {
            return;
        }
        
        fakePlayer.discard();
        fakePlayer = null;
        spawned = false;
        isWalking = false;
        targetPos = null;
        
        LOGGER.info("Despawned fake player '{}'", playerName);
    }
    
    /**
     * Checks if the player is spawned.
     */
    public boolean isSpawned() {
        return spawned && fakePlayer != null && fakePlayer.isAlive();
    }
    
    /**
     * Gets the fake player entity.
     */
    public ServerPlayerEntity getPlayer() {
        return fakePlayer;
    }
    
    /**
     * Gets the current position.
     */
    public Vec3d getPosition() {
        return fakePlayer != null ? fakePlayer.getPos() : Vec3d.ZERO;
    }
    
    /**
     * Gets the current block position.
     */
    public BlockPos getBlockPos() {
        return fakePlayer != null ? fakePlayer.getBlockPos() : BlockPos.ORIGIN;
    }
    
    /**
     * Teleports the player to a position instantly.
     */
    public void teleportTo(BlockPos pos) {
        if (fakePlayer != null) {
            fakePlayer.teleport(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 
                fakePlayer.getYaw(), fakePlayer.getPitch());
        }
    }
    
    /**
     * Starts walking to target position.
     * Returns true if already at target.
     */
    public boolean walkTo(BlockPos target) {
        if (fakePlayer == null) return true;
        
        this.targetPos = target;
        Vec3d currentPos = fakePlayer.getPos();
        Vec3d targetVec = Vec3d.ofCenter(target);
        
        double distance = currentPos.horizontalDistanceTo(targetVec);
        
        if (distance <= 0.5) {
            isWalking = false;
            return true; // Already there
        }
        
        isWalking = true;
        return false;
    }
    
    /**
     * Called each tick to update movement.
     * Returns true when destination reached.
     */
    public boolean tickMovement() {
        if (fakePlayer == null || !isWalking || targetPos == null) {
            return true;
        }
        
        Vec3d currentPos = fakePlayer.getPos();
        Vec3d targetVec = Vec3d.ofCenter(targetPos);
        
        // Check horizontal distance
        double distance = currentPos.horizontalDistanceTo(targetVec);
        
        if (distance <= 0.3) {
            isWalking = false;
            fakePlayer.setVelocity(Vec3d.ZERO);
            return true;
        }
        
        // Calculate direction
        double dx = targetVec.x - currentPos.x;
        double dz = targetVec.z - currentPos.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        
        // Normalize and scale by speed
        double moveX = (dx / length) * WALK_SPEED;
        double moveZ = (dz / length) * WALK_SPEED;
        
        // Apply movement
        Vec3d movement = new Vec3d(moveX, 0, moveZ);
        fakePlayer.setVelocity(movement);
        fakePlayer.move(MovementType.SELF, movement);
        
        // Update rotation to face movement direction
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        fakePlayer.setYaw(lerpAngle(fakePlayer.getYaw(), targetYaw, 0.3f));
        fakePlayer.setHeadYaw(fakePlayer.getYaw());
        
        // Animate walking (swing limbs)
        fakePlayer.limbAnimator.updateLimbs(1.0f, 0.4f);
        
        return false;
    }
    
    /**
     * Makes the player look at a block position.
     */
    public void lookAt(BlockPos pos) {
        if (fakePlayer == null) return;
        
        Vec3d playerPos = fakePlayer.getEyePos();
        Vec3d targetPos = Vec3d.ofCenter(pos);
        
        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;
        
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) -(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI);
        
        fakePlayer.setYaw(yaw);
        fakePlayer.setPitch(pitch);
        fakePlayer.setHeadYaw(yaw);
    }
    
    /**
     * Smoothly looks at a block position over time.
     */
    public void smoothLookAt(BlockPos pos, float speed) {
        if (fakePlayer == null) return;
        
        Vec3d playerPos = fakePlayer.getEyePos();
        Vec3d targetPos = Vec3d.ofCenter(pos);
        
        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;
        
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) -(Math.atan2(dy, horizontalDist) * 180.0 / Math.PI);
        
        fakePlayer.setYaw(lerpAngle(fakePlayer.getYaw(), targetYaw, speed));
        fakePlayer.setPitch(lerp(fakePlayer.getPitch(), targetPitch, speed));
        fakePlayer.setHeadYaw(fakePlayer.getYaw());
    }
    
    /**
     * Swings the main hand (animation).
     */
    public void swingHand() {
        if (fakePlayer != null) {
            fakePlayer.swingHand(Hand.MAIN_HAND, true);
        }
    }
    
    /**
     * Places a block at the given position with animation.
     */
    public boolean placeBlock(BlockPos pos, BlockState state) {
        if (fakePlayer == null || world == null) {
            return false;
        }
        
        // Look at the block
        lookAt(pos);
        
        // Swing hand animation
        swingHand();
        
        // Place the block
        boolean success = world.setBlockState(pos, state);
        
        if (success) {
            // Play place sound
            world.playSound(null, pos, 
                state.getSoundGroup().getPlaceSound(), 
                SoundCategory.BLOCKS, 1.0f, 1.0f);
        }
        
        return success;
    }
    
    /**
     * Breaks a block at the given position with animation.
     */
    public boolean breakBlock(BlockPos pos) {
        if (fakePlayer == null || world == null) {
            return false;
        }
        
        BlockState currentState = world.getBlockState(pos);
        if (currentState.isAir()) {
            return true;
        }
        
        // Look at the block
        lookAt(pos);
        
        // Swing hand animation
        swingHand();
        
        // Play break sound
        world.playSound(null, pos, 
            currentState.getSoundGroup().getBreakSound(), 
            SoundCategory.BLOCKS, 1.0f, 1.0f);
        
        // Break the block
        return world.breakBlock(pos, false, fakePlayer);
    }
    
    /**
     * Checks if the player can reach the given position.
     */
    public boolean canReach(BlockPos pos) {
        if (fakePlayer == null) return false;
        
        Vec3d playerPos = fakePlayer.getEyePos();
        Vec3d blockPos = Vec3d.ofCenter(pos);
        
        return playerPos.distanceTo(blockPos) <= REACH_DISTANCE;
    }
    
    /**
     * Gets a good position to stand to reach the target block.
     */
    public BlockPos getPositionToReach(BlockPos target) {
        if (fakePlayer == null) {
            return target;
        }
        
        // Find a valid position within reach
        // Try positions around the target
        BlockPos[] candidates = new BlockPos[] {
            target.north(),
            target.south(),
            target.east(),
            target.west(),
            target.north().east(),
            target.north().west(),
            target.south().east(),
            target.south().west()
        };
        
        Vec3d currentPos = fakePlayer.getPos();
        BlockPos best = candidates[0];
        double bestDist = Double.MAX_VALUE;
        
        for (BlockPos candidate : candidates) {
            // Check if position is valid (not solid)
            if (world != null && !world.getBlockState(candidate).isAir()) {
                continue;
            }
            
            double dist = currentPos.squaredDistanceTo(Vec3d.ofCenter(candidate));
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        
        return best;
    }
    
    // === Utility methods ===
    
    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }
    
    private static float lerpAngle(float start, float end, float delta) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + diff * delta;
    }
}
