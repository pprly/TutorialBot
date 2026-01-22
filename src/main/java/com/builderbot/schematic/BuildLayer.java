package com.builderbot.schematic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single build layer in a tutorial schematic.
 * Each layer has a name, order (build sequence), color, and list of blocks.
 */
public class BuildLayer {
    private final int id;
    private String name;
    private String description;
    private int order;
    private String colorHex;
    private final List<BlockEntry> blocks;
    
    // Statistics
    private int blockCount;
    private int uniqueBlockTypes;
    private int estimatedTimeSeconds;
    
    public BuildLayer(int id) {
        this.id = id;
        this.name = "Layer " + id;
        this.description = "";
        this.order = id;
        this.colorHex = "#FFFFFF";
        this.blocks = new ArrayList<>();
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
    
    public String getColorHex() {
        return colorHex;
    }
    
    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }
    
    public List<BlockEntry> getBlocks() {
        return blocks;
    }
    
    public void addBlock(BlockEntry block) {
        blocks.add(block);
    }
    
    public int getBlockCount() {
        return blocks.size();
    }
    
    public int getUniqueBlockTypes() {
        return uniqueBlockTypes;
    }
    
    public void setUniqueBlockTypes(int uniqueBlockTypes) {
        this.uniqueBlockTypes = uniqueBlockTypes;
    }
    
    public int getEstimatedTimeSeconds() {
        return estimatedTimeSeconds;
    }
    
    public void setEstimatedTimeSeconds(int estimatedTimeSeconds) {
        this.estimatedTimeSeconds = estimatedTimeSeconds;
    }
    
    /**
     * Returns color as float array [r, g, b] in range 0-1.
     */
    public float[] getColorComponents() {
        try {
            Color color = Color.decode(colorHex);
            return new float[] {
                color.getRed() / 255f,
                color.getGreen() / 255f,
                color.getBlue() / 255f
            };
        } catch (NumberFormatException e) {
            return new float[] {1f, 1f, 1f};
        }
    }
    
    /**
     * Returns color as integer (0xRRGGBB).
     */
    public int getColorInt() {
        try {
            return Color.decode(colorHex).getRGB() & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
    
    @Override
    public String toString() {
        return String.format("BuildLayer{id=%d, name='%s', order=%d, blocks=%d}", 
            id, name, order, blocks.size());
    }
}
