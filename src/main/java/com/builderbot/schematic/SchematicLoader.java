package com.builderbot.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads and parses .ltutorial schematic files.
 */
public class SchematicLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");
    private static final String SCHEMATIC_EXTENSION = ".ltutorial";
    
    /**
     * Gets the schematics folder path.
     */
    public static Path getSchematicsFolder() {
        return FabricLoader.getInstance().getGameDir()
            .resolve("schematics")
            .resolve("tutorials");
    }
    
    /**
     * Ensures the schematics folder exists.
     */
    public static void ensureFolderExists() {
        Path folder = getSchematicsFolder();
        if (!Files.exists(folder)) {
            try {
                Files.createDirectories(folder);
                LOGGER.info("Created schematics folder: {}", folder);
            } catch (IOException e) {
                LOGGER.error("Failed to create schematics folder", e);
            }
        }
    }
    
    /**
     * Lists all available .ltutorial files.
     */
    public static List<String> listSchematics() {
        List<String> result = new ArrayList<>();
        Path folder = getSchematicsFolder();
        
        if (!Files.exists(folder)) {
            return result;
        }
        
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.toString().endsWith(SCHEMATIC_EXTENSION))
                .forEach(p -> {
                    String name = p.getFileName().toString();
                    result.add(name.substring(0, name.length() - SCHEMATIC_EXTENSION.length()));
                });
        } catch (IOException e) {
            LOGGER.error("Failed to list schematics", e);
        }
        
        return result;
    }
    
    /**
     * Loads a schematic from file.
     * @param filename Name without extension (e.g., "medieval_house")
     * @return Loaded schematic or null if failed
     */
    public static TutorialSchematic load(String filename) {
        Path path = getSchematicsFolder().resolve(filename + SCHEMATIC_EXTENSION);
        
        if (!Files.exists(path)) {
            LOGGER.error("Schematic file not found: {}", path);
            return null;
        }
        
        try {
            String json = Files.readString(path);
            return parseSchematic(json);
        } catch (IOException e) {
            LOGGER.error("Failed to read schematic file: {}", path, e);
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to parse schematic: {}", filename, e);
            return null;
        }
    }
    
    /**
     * Parses schematic from JSON string.
     */
    public static TutorialSchematic parseSchematic(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        
        TutorialSchematic schematic = new TutorialSchematic();
        
        // Parse metadata
        if (root.has("format_version")) {
            schematic.setFormatVersion(root.get("format_version").getAsString());
        }
        if (root.has("name")) {
            schematic.setName(root.get("name").getAsString());
        }
        if (root.has("description")) {
            schematic.setDescription(root.get("description").getAsString());
        }
        if (root.has("author")) {
            schematic.setAuthor(root.get("author").getAsString());
        }
        if (root.has("minecraft_version")) {
            schematic.setMinecraftVersion(root.get("minecraft_version").getAsString());
        }
        if (root.has("created_at")) {
            schematic.setCreatedAt(root.get("created_at").getAsString());
        }
        if (root.has("modified_at")) {
            schematic.setModifiedAt(root.get("modified_at").getAsString());
        }
        
        // Parse metadata statistics
        if (root.has("metadata")) {
            JsonObject metadata = root.getAsJsonObject("metadata");
            if (metadata.has("total_blocks")) {
                schematic.setTotalBlocks(metadata.get("total_blocks").getAsInt());
            }
            if (metadata.has("estimated_build_time_minutes")) {
                schematic.setEstimatedBuildTimeMinutes(metadata.get("estimated_build_time_minutes").getAsInt());
            }
        }
        
        // Parse layers
        if (root.has("layers")) {
            JsonArray layersArray = root.getAsJsonArray("layers");
            for (JsonElement layerElem : layersArray) {
                BuildLayer layer = parseLayer(layerElem.getAsJsonObject());
                schematic.addLayer(layer);
            }
        }
        
        // Parse bounding box
        if (root.has("bounding_box")) {
            JsonObject bb = root.getAsJsonObject("bounding_box");
            if (bb.has("min") && bb.has("max")) {
                JsonObject min = bb.getAsJsonObject("min");
                JsonObject max = bb.getAsJsonObject("max");
                schematic.setBoundingBox(
                    min.get("x").getAsInt(),
                    min.get("y").getAsInt(),
                    min.get("z").getAsInt(),
                    max.get("x").getAsInt(),
                    max.get("y").getAsInt(),
                    max.get("z").getAsInt()
                );
            }
        } else {
            // Recalculate if not provided
            schematic.recalculateBoundingBox();
        }
        
        LOGGER.info("Loaded schematic: {} ({} layers, {} blocks)", 
            schematic.getName(), schematic.getLayerCount(), schematic.getTotalBlocks());
        
        return schematic;
    }
    
    /**
     * Parses a single layer from JSON.
     */
    private static BuildLayer parseLayer(JsonObject json) {
        int id = json.has("id") ? json.get("id").getAsInt() : 0;
        BuildLayer layer = new BuildLayer(id);
        
        if (json.has("name")) {
            layer.setName(json.get("name").getAsString());
        }
        if (json.has("description")) {
            layer.setDescription(json.get("description").getAsString());
        }
        if (json.has("order")) {
            layer.setOrder(json.get("order").getAsInt());
        }
        if (json.has("color")) {
            layer.setColorHex(json.get("color").getAsString());
        }
        
        // Parse blocks
        if (json.has("blocks")) {
            JsonArray blocksArray = json.getAsJsonArray("blocks");
            for (JsonElement blockElem : blocksArray) {
                JsonObject blockJson = blockElem.getAsJsonObject();
                
                int x = blockJson.get("x").getAsInt();
                int y = blockJson.get("y").getAsInt();
                int z = blockJson.get("z").getAsInt();
                String blockId = blockJson.get("block").getAsString();
                String blockState = blockJson.has("blockstate") 
                    ? blockJson.get("blockstate").getAsString() 
                    : "{}";
                
                layer.addBlock(new BlockEntry(x, y, z, blockId, blockState));
            }
        }
        
        // Parse statistics
        if (json.has("statistics")) {
            JsonObject stats = json.getAsJsonObject("statistics");
            if (stats.has("unique_block_types")) {
                layer.setUniqueBlockTypes(stats.get("unique_block_types").getAsInt());
            }
            if (stats.has("estimated_time_seconds")) {
                layer.setEstimatedTimeSeconds(stats.get("estimated_time_seconds").getAsInt());
            }
        }
        
        return layer;
    }
}
