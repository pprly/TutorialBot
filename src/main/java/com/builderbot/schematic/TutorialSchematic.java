package com.builderbot.schematic;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Main class representing a tutorial schematic loaded from .ltutorial file.
 * Contains metadata, layers, and bounding box information.
 */
public class TutorialSchematic {
    // Metadata
    private String formatVersion = "1.0";
    private String name;
    private String description;
    private String author;
    private String minecraftVersion;
    private String createdAt;
    private String modifiedAt;
    
    // Statistics
    private int totalBlocks;
    private int estimatedBuildTimeMinutes;
    
    // Layers
    private final List<BuildLayer> layers;
    
    // Bounding box
    private BlockPos minPos;
    private BlockPos maxPos;
    
    public TutorialSchematic() {
        this.layers = new ArrayList<>();
        this.minPos = BlockPos.ORIGIN;
        this.maxPos = BlockPos.ORIGIN;
    }
    
    // === Metadata getters/setters ===
    
    public String getFormatVersion() {
        return formatVersion;
    }
    
    public void setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
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
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        this.author = author;
    }
    
    public String getMinecraftVersion() {
        return minecraftVersion;
    }
    
    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getModifiedAt() {
        return modifiedAt;
    }
    
    public void setModifiedAt(String modifiedAt) {
        this.modifiedAt = modifiedAt;
    }
    
    // === Statistics ===
    
    public int getTotalBlocks() {
        if (totalBlocks == 0) {
            totalBlocks = layers.stream().mapToInt(BuildLayer::getBlockCount).sum();
        }
        return totalBlocks;
    }
    
    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = totalBlocks;
    }
    
    public int getEstimatedBuildTimeMinutes() {
        return estimatedBuildTimeMinutes;
    }
    
    public void setEstimatedBuildTimeMinutes(int estimatedBuildTimeMinutes) {
        this.estimatedBuildTimeMinutes = estimatedBuildTimeMinutes;
    }
    
    // === Layers ===
    
    public List<BuildLayer> getLayers() {
        return layers;
    }
    
    /**
     * Returns layers sorted by their build order.
     */
    public List<BuildLayer> getLayersSorted() {
        List<BuildLayer> sorted = new ArrayList<>(layers);
        sorted.sort(Comparator.comparingInt(BuildLayer::getOrder));
        return sorted;
    }
    
    public void addLayer(BuildLayer layer) {
        layers.add(layer);
    }
    
    public BuildLayer getLayerById(int id) {
        return layers.stream()
            .filter(l -> l.getId() == id)
            .findFirst()
            .orElse(null);
    }
    
    public BuildLayer getLayerByOrder(int order) {
        return layers.stream()
            .filter(l -> l.getOrder() == order)
            .findFirst()
            .orElse(null);
    }
    
    public int getLayerCount() {
        return layers.size();
    }
    
    // === Bounding Box ===
    
    public BlockPos getMinPos() {
        return minPos;
    }
    
    public void setMinPos(BlockPos minPos) {
        this.minPos = minPos;
    }
    
    public BlockPos getMaxPos() {
        return maxPos;
    }
    
    public void setMaxPos(BlockPos maxPos) {
        this.maxPos = maxPos;
    }
    
    public void setBoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minPos = new BlockPos(minX, minY, minZ);
        this.maxPos = new BlockPos(maxX, maxY, maxZ);
    }
    
    public BlockBox getBoundingBox() {
        return new BlockBox(
            minPos.getX(), minPos.getY(), minPos.getZ(),
            maxPos.getX(), maxPos.getY(), maxPos.getZ()
        );
    }
    
    /**
     * Returns dimensions of the schematic [width, height, depth].
     */
    public int[] getDimensions() {
        return new int[] {
            maxPos.getX() - minPos.getX() + 1,
            maxPos.getY() - minPos.getY() + 1,
            maxPos.getZ() - minPos.getZ() + 1
        };
    }
    
    /**
     * Recalculates bounding box from all layer blocks.
     */
    public void recalculateBoundingBox() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BuildLayer layer : layers) {
            for (BlockEntry block : layer.getBlocks()) {
                minX = Math.min(minX, block.getX());
                minY = Math.min(minY, block.getY());
                minZ = Math.min(minZ, block.getZ());
                maxX = Math.max(maxX, block.getX());
                maxY = Math.max(maxY, block.getY());
                maxZ = Math.max(maxZ, block.getZ());
            }
        }
        
        if (minX != Integer.MAX_VALUE) {
            this.minPos = new BlockPos(minX, minY, minZ);
            this.maxPos = new BlockPos(maxX, maxY, maxZ);
        }
    }
    
    @Override
    public String toString() {
        return String.format("TutorialSchematic{name='%s', layers=%d, blocks=%d}", 
            name, layers.size(), getTotalBlocks());
    }
}
