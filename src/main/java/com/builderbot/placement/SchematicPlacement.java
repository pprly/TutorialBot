package com.builderbot.placement;

import com.builderbot.schematic.BlockEntry;
import com.builderbot.schematic.TutorialSchematic;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Manages the placement of a schematic in the world.
 * Handles position offset, rotation, and coordinate transformations.
 */
public class SchematicPlacement {
    private final TutorialSchematic schematic;
    private BlockPos origin;
    private int rotation; // 0, 90, 180, 270 degrees
    private boolean confirmed;
    
    public SchematicPlacement(TutorialSchematic schematic, BlockPos origin) {
        this.schematic = schematic;
        this.origin = origin;
        this.rotation = 0;
        this.confirmed = false;
    }
    
    public TutorialSchematic getSchematic() {
        return schematic;
    }
    
    public BlockPos getOrigin() {
        return origin;
    }
    
    public void setOrigin(BlockPos origin) {
        this.origin = origin;
    }
    
    public int getRotation() {
        return rotation;
    }
    
    public void setRotation(int rotation) {
        this.rotation = rotation % 360;
        if (this.rotation < 0) {
            this.rotation += 360;
        }
    }
    
    public void rotate90() {
        setRotation(rotation + 90);
    }
    
    public void rotate180() {
        setRotation(rotation + 180);
    }
    
    public void rotate270() {
        setRotation(rotation + 270);
    }
    
    public boolean isConfirmed() {
        return confirmed;
    }
    
    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
    
    /**
     * Moves the schematic origin by the given offset.
     */
    public void move(int dx, int dy, int dz) {
        origin = origin.add(dx, dy, dz);
    }
    
    public void move(Direction direction, int amount) {
        origin = origin.offset(direction, amount);
    }
    
    /**
     * Converts relative schematic coordinates to world coordinates.
     * Applies rotation transformation around the origin.
     */
    public BlockPos toWorldPos(int relX, int relY, int relZ) {
        // First, offset from schematic's internal origin (bounding box min)
        BlockPos schematicMin = schematic.getMinPos();
        int offsetX = relX - schematicMin.getX();
        int offsetY = relY - schematicMin.getY();
        int offsetZ = relZ - schematicMin.getZ();
        
        // Apply rotation
        int rotatedX = offsetX;
        int rotatedZ = offsetZ;
        
        switch (rotation) {
            case 90:
                rotatedX = -offsetZ;
                rotatedZ = offsetX;
                break;
            case 180:
                rotatedX = -offsetX;
                rotatedZ = -offsetZ;
                break;
            case 270:
                rotatedX = offsetZ;
                rotatedZ = -offsetX;
                break;
        }
        
        // Add world origin
        return origin.add(rotatedX, offsetY, rotatedZ);
    }
    
    /**
     * Converts relative schematic coordinates to world coordinates.
     */
    public BlockPos toWorldPos(BlockPos relPos) {
        return toWorldPos(relPos.getX(), relPos.getY(), relPos.getZ());
    }
    
    /**
     * Converts relative schematic coordinates to world coordinates.
     */
    public BlockPos toWorldPos(BlockEntry entry) {
        return toWorldPos(entry.getX(), entry.getY(), entry.getZ());
    }
    
    /**
     * Rotates a BlockState according to the placement rotation.
     * Handles directional blocks like stairs, doors, etc.
     */
    public BlockState rotateBlockState(BlockState state) {
        if (rotation == 0) {
            return state;
        }
        
        BlockRotation blockRotation = getBlockRotation();
        return state.rotate(blockRotation);
    }
    
    /**
     * Gets the Minecraft BlockRotation enum for current rotation.
     */
    public BlockRotation getBlockRotation() {
        return switch (rotation) {
            case 90 -> BlockRotation.CLOCKWISE_90;
            case 180 -> BlockRotation.CLOCKWISE_180;
            case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }
    
    /**
     * Gets the world-space bounding box of the schematic.
     */
    public BlockBox getWorldBoundingBox() {
        BlockPos schematicMin = schematic.getMinPos();
        BlockPos schematicMax = schematic.getMaxPos();
        
        // Transform all corners and find new bounds
        BlockPos[] corners = new BlockPos[] {
            toWorldPos(schematicMin.getX(), schematicMin.getY(), schematicMin.getZ()),
            toWorldPos(schematicMax.getX(), schematicMin.getY(), schematicMin.getZ()),
            toWorldPos(schematicMin.getX(), schematicMin.getY(), schematicMax.getZ()),
            toWorldPos(schematicMax.getX(), schematicMin.getY(), schematicMax.getZ()),
            toWorldPos(schematicMin.getX(), schematicMax.getY(), schematicMin.getZ()),
            toWorldPos(schematicMax.getX(), schematicMax.getY(), schematicMin.getZ()),
            toWorldPos(schematicMin.getX(), schematicMax.getY(), schematicMax.getZ()),
            toWorldPos(schematicMax.getX(), schematicMax.getY(), schematicMax.getZ())
        };
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos corner : corners) {
            minX = Math.min(minX, corner.getX());
            minY = Math.min(minY, corner.getY());
            minZ = Math.min(minZ, corner.getZ());
            maxX = Math.max(maxX, corner.getX());
            maxY = Math.max(maxY, corner.getY());
            maxZ = Math.max(maxZ, corner.getZ());
        }
        
        return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Gets center position of the schematic in world coordinates.
     */
    public BlockPos getCenter() {
        BlockBox box = getWorldBoundingBox();
        return new BlockPos(
            (box.getMinX() + box.getMaxX()) / 2,
            (box.getMinY() + box.getMaxY()) / 2,
            (box.getMinZ() + box.getMaxZ()) / 2
        );
    }
    
    @Override
    public String toString() {
        return String.format("SchematicPlacement{origin=%s, rotation=%d°, confirmed=%b}",
            origin.toShortString(), rotation, confirmed);
    }
}
