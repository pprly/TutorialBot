package com.builderbot.placement;

import com.builderbot.schematic.TutorialSchematic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for interactive schematic placement.
 * Handles keyboard/mouse input for positioning the schematic.
 */
public class PlacementController {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");
    
    private SchematicPlacement placement;
    private boolean placementMode;
    private int moveStep = 1; // Blocks to move per key press
    
    public PlacementController() {
        this.placement = null;
        this.placementMode = false;
    }
    
    /**
     * Starts placement mode for a schematic at player's position.
     */
    public void startPlacement(TutorialSchematic schematic) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            LOGGER.error("Cannot start placement: no player");
            return;
        }
        
        BlockPos playerPos = client.player.getBlockPos();
        this.placement = new SchematicPlacement(schematic, playerPos);
        this.placementMode = true;
        
        LOGGER.info("Started placement mode for '{}' at {}", 
            schematic.getName(), playerPos.toShortString());
    }
    
    /**
     * Loads an existing placement (e.g., from save).
     */
    public void setPlacement(SchematicPlacement placement) {
        this.placement = placement;
        this.placementMode = placement != null && !placement.isConfirmed();
    }
    
    /**
     * Gets the current placement.
     */
    public SchematicPlacement getPlacement() {
        return placement;
    }
    
    /**
     * Checks if placement mode is active.
     */
    public boolean isPlacementMode() {
        return placementMode && placement != null && !placement.isConfirmed();
    }
    
    /**
     * Checks if there is an active (confirmed) placement.
     */
    public boolean hasActivePlacement() {
        return placement != null && placement.isConfirmed();
    }
    
    /**
     * Checks if there is any placement (confirmed or not).
     */
    public boolean hasPlacement() {
        return placement != null;
    }
    
    /**
     * Moves the schematic in a direction.
     */
    public void move(Direction direction) {
        move(direction, moveStep);
    }
    
    public void move(Direction direction, int amount) {
        if (placement != null && isPlacementMode()) {
            placement.move(direction, amount);
            LOGGER.debug("Moved schematic {} by {}", direction, amount);
        }
    }
    
    /**
     * Moves the schematic by raw offset.
     */
    public void move(int dx, int dy, int dz) {
        if (placement != null && isPlacementMode()) {
            placement.move(dx, dy, dz);
        }
    }
    
    /**
     * Moves schematic to specific position.
     */
    public void moveTo(BlockPos pos) {
        if (placement != null && isPlacementMode()) {
            placement.setOrigin(pos);
            LOGGER.info("Moved schematic to {}", pos.toShortString());
        }
    }
    
    /**
     * Rotates the schematic by 90 degrees.
     */
    public void rotate() {
        if (placement != null && isPlacementMode()) {
            placement.rotate90();
            LOGGER.info("Rotated schematic to {}°", placement.getRotation());
        }
    }
    
    /**
     * Sets specific rotation.
     */
    public void setRotation(int degrees) {
        if (placement != null && isPlacementMode()) {
            placement.setRotation(degrees);
            LOGGER.info("Set schematic rotation to {}°", placement.getRotation());
        }
    }
    
    /**
     * Confirms the current placement.
     * After confirmation, the schematic position is locked.
     */
    public boolean confirm() {
        if (placement == null) {
            LOGGER.warn("Cannot confirm: no placement");
            return false;
        }
        
        if (placement.isConfirmed()) {
            LOGGER.warn("Placement already confirmed");
            return false;
        }
        
        placement.setConfirmed(true);
        placementMode = false;
        LOGGER.info("Confirmed placement at {} with {}° rotation", 
            placement.getOrigin().toShortString(), placement.getRotation());
        
        return true;
    }
    
    /**
     * Cancels the current placement.
     */
    public void cancel() {
        if (placement != null && !placement.isConfirmed()) {
            LOGGER.info("Cancelled placement");
            placement = null;
            placementMode = false;
        }
    }
    
    /**
     * Clears the placement completely (including confirmed).
     */
    public void clear() {
        placement = null;
        placementMode = false;
        LOGGER.info("Cleared placement");
    }
    
    /**
     * Re-enters placement mode for adjustment (if was confirmed).
     */
    public void editPlacement() {
        if (placement != null && placement.isConfirmed()) {
            placement.setConfirmed(false);
            placementMode = true;
            LOGGER.info("Re-entered placement mode for editing");
        }
    }
    
    /**
     * Gets the move step (blocks per key press).
     */
    public int getMoveStep() {
        return moveStep;
    }
    
    /**
     * Sets the move step.
     */
    public void setMoveStep(int moveStep) {
        this.moveStep = Math.max(1, moveStep);
    }
    
    /**
     * Handles keyboard input for placement.
     * Returns true if the key was handled.
     */
    public boolean handleKeyPress(int keyCode) {
        if (!isPlacementMode()) {
            return false;
        }
        
        // Arrow keys for X/Z movement
        // 262 = RIGHT, 263 = LEFT, 264 = DOWN, 265 = UP (GLFW key codes)
        switch (keyCode) {
            case 262: // RIGHT
                move(Direction.EAST);
                return true;
            case 263: // LEFT
                move(Direction.WEST);
                return true;
            case 264: // DOWN (arrow)
                move(Direction.SOUTH);
                return true;
            case 265: // UP (arrow)
                move(Direction.NORTH);
                return true;
            case 266: // PAGE UP
                move(Direction.UP);
                return true;
            case 267: // PAGE DOWN
                move(Direction.DOWN);
                return true;
            case 82: // R key
                rotate();
                return true;
            case 257: // ENTER
                confirm();
                return true;
            case 256: // ESCAPE
                cancel();
                return true;
        }
        
        return false;
    }
}
