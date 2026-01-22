package com.builderbot.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Optional;

/**
 * Represents a single block entry in a schematic layer.
 * Contains position (relative to schematic origin) and block state.
 */
public class BlockEntry {
    private final int x;
    private final int y;
    private final int z;
    private final String blockId;
    private final String blockStateString;
    private BlockState cachedState;
    
    public BlockEntry(int x, int y, int z, String blockId, String blockStateString) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
        this.blockStateString = blockStateString;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getZ() {
        return z;
    }
    
    public BlockPos getRelativePos() {
        return new BlockPos(x, y, z);
    }
    
    public String getBlockId() {
        return blockId;
    }
    
    public String getBlockStateString() {
        return blockStateString;
    }
    
    /**
     * Parses and returns the BlockState for this entry.
     * Caches the result for performance.
     */
    public BlockState getBlockState() {
        if (cachedState != null) {
            return cachedState;
        }
        
        // Get block from registry
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            id = Identifier.of("minecraft", blockId);
        }
        
        Block block = Registries.BLOCK.get(id);
        BlockState state = block.getDefaultState();
        
        // Parse blockstate properties
        if (blockStateString != null && !blockStateString.isEmpty() && !blockStateString.equals("{}")) {
            state = parseBlockState(state, blockStateString);
        }
        
        cachedState = state;
        return state;
    }
    
    /**
     * Parses blockstate string like "{facing:north,half:bottom}" into actual properties.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private BlockState parseBlockState(BlockState baseState, String stateString) {
        // Remove braces
        String props = stateString.trim();
        if (props.startsWith("{")) {
            props = props.substring(1);
        }
        if (props.endsWith("}")) {
            props = props.substring(0, props.length() - 1);
        }
        
        if (props.isEmpty()) {
            return baseState;
        }
        
        BlockState result = baseState;
        
        // Split by comma
        String[] pairs = props.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length != 2) continue;
            
            String propName = keyValue[0].trim();
            String propValue = keyValue[1].trim();
            
            // Find property
            for (Property<?> property : result.getProperties()) {
                if (property.getName().equals(propName)) {
                    Optional<?> value = property.parse(propValue);
                    if (value.isPresent()) {
                        result = result.with((Property) property, (Comparable) value.get());
                    }
                    break;
                }
            }
        }
        
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("BlockEntry{pos=[%d,%d,%d], block=%s}", x, y, z, blockId);
    }
}
