package com.builderbot.builder;

import com.builderbot.placement.SchematicPlacement;
import com.builderbot.schematic.BlockEntry;
import com.builderbot.schematic.BuildLayer;
import com.builderbot.schematic.TutorialSchematic;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Executes the building process layer by layer.
 * Manages the fake player and coordinates the entire build.
 */
public class BuildExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");
    
    // Speed presets (ticks between blocks)
    private static final int[] SPEED_DELAYS = {
        40, 35, 30, 25, 20, 15, 12, 8, 4, 2
    };
    
    // Blocks that can be safely replaced
    private static final Set<String> REPLACEABLE_BLOCKS = new HashSet<>();
    static {
        REPLACEABLE_BLOCKS.add("minecraft:air");
        REPLACEABLE_BLOCKS.add("minecraft:cave_air");
        REPLACEABLE_BLOCKS.add("minecraft:void_air");
        REPLACEABLE_BLOCKS.add("minecraft:grass");
        REPLACEABLE_BLOCKS.add("minecraft:short_grass");
        REPLACEABLE_BLOCKS.add("minecraft:tall_grass");
        REPLACEABLE_BLOCKS.add("minecraft:fern");
        REPLACEABLE_BLOCKS.add("minecraft:large_fern");
        REPLACEABLE_BLOCKS.add("minecraft:dead_bush");
        REPLACEABLE_BLOCKS.add("minecraft:seagrass");
        REPLACEABLE_BLOCKS.add("minecraft:tall_seagrass");
        REPLACEABLE_BLOCKS.add("minecraft:dandelion");
        REPLACEABLE_BLOCKS.add("minecraft:poppy");
        REPLACEABLE_BLOCKS.add("minecraft:blue_orchid");
        REPLACEABLE_BLOCKS.add("minecraft:allium");
        REPLACEABLE_BLOCKS.add("minecraft:azure_bluet");
        REPLACEABLE_BLOCKS.add("minecraft:red_tulip");
        REPLACEABLE_BLOCKS.add("minecraft:orange_tulip");
        REPLACEABLE_BLOCKS.add("minecraft:white_tulip");
        REPLACEABLE_BLOCKS.add("minecraft:pink_tulip");
        REPLACEABLE_BLOCKS.add("minecraft:oxeye_daisy");
        REPLACEABLE_BLOCKS.add("minecraft:cornflower");
        REPLACEABLE_BLOCKS.add("minecraft:lily_of_the_valley");
        REPLACEABLE_BLOCKS.add("minecraft:sunflower");
        REPLACEABLE_BLOCKS.add("minecraft:lilac");
        REPLACEABLE_BLOCKS.add("minecraft:rose_bush");
        REPLACEABLE_BLOCKS.add("minecraft:peony");
        REPLACEABLE_BLOCKS.add("minecraft:snow");
        REPLACEABLE_BLOCKS.add("minecraft:vine");
        REPLACEABLE_BLOCKS.add("minecraft:lily_pad");
        REPLACEABLE_BLOCKS.add("minecraft:kelp");
        REPLACEABLE_BLOCKS.add("minecraft:kelp_plant");
        REPLACEABLE_BLOCKS.add("minecraft:water");
        REPLACEABLE_BLOCKS.add("minecraft:lava");
    }
    
    public enum BuildState {
        IDLE,
        SPAWNING,
        WALKING,
        BUILDING,
        BREAKING,
        LAYER_COMPLETE,
        PAUSED,
        FINISHED,
        ERROR
    }
    
    private final FakePlayerBuilder builder;
    private TutorialSchematic schematic;
    private SchematicPlacement placement;
    private ServerWorld world;
    
    // Build state
    private BuildState state = BuildState.IDLE;
    private int speed = 5; // 1-10, default medium
    private int tickCounter = 0;
    
    // Progress tracking
    private List<BuildLayer> sortedLayers;
    private int currentLayerIndex = 0;
    private int currentBlockIndex = 0;
    private BlockEntry currentBlock;
    private int totalBlocksBuilt = 0;
    
    // Callbacks
    private Consumer<String> messageCallback;
    private Runnable layerCompleteCallback;
    private Runnable buildCompleteCallback;
    
    public BuildExecutor() {
        this.builder = new FakePlayerBuilder();
    }
    
    public BuildExecutor(String builderName) {
        this.builder = new FakePlayerBuilder(builderName);
    }
    
    /**
     * Initializes the executor with schematic and placement.
     */
    public void initialize(TutorialSchematic schematic, SchematicPlacement placement, ServerWorld world) {
        this.schematic = schematic;
        this.placement = placement;
        this.world = world;
        this.sortedLayers = schematic.getLayersSorted();
        this.currentLayerIndex = 0;
        this.currentBlockIndex = 0;
        this.totalBlocksBuilt = 0;
        this.state = BuildState.IDLE;
        
        LOGGER.info("BuildExecutor initialized: {} layers, {} total blocks",
            sortedLayers.size(), schematic.getTotalBlocks());
    }
    
    /**
     * Starts the building process.
     */
    public boolean start() {
        if (schematic == null || placement == null || world == null) {
            LOGGER.error("Cannot start: not initialized");
            return false;
        }
        
        if (!placement.isConfirmed()) {
            LOGGER.error("Cannot start: placement not confirmed");
            sendMessage("§cОшибка: сначала подтвердите размещение схемы (/build confirm)");
            return false;
        }
        
        if (state == BuildState.BUILDING || state == BuildState.WALKING) {
            LOGGER.warn("Build already in progress");
            return false;
        }
        
        // Spawn the fake player at the start position
        BlockPos startPos = placement.getOrigin();
        if (!builder.spawn(world, startPos)) {
            LOGGER.error("Failed to spawn builder");
            sendMessage("§cОшибка: не удалось создать строителя");
            state = BuildState.ERROR;
            return false;
        }
        
        state = BuildState.SPAWNING;
        tickCounter = 0;
        
        sendMessage("§aНачинаю строительство: " + schematic.getName());
        sendMessage("§7Слоёв: " + sortedLayers.size() + ", блоков: " + schematic.getTotalBlocks());
        
        LOGGER.info("Build started");
        return true;
    }
    
    /**
     * Pauses the building process.
     */
    public void pause() {
        if (state == BuildState.BUILDING || state == BuildState.WALKING || 
            state == BuildState.BREAKING) {
            state = BuildState.PAUSED;
            sendMessage("§eСтроительство приостановлено");
            LOGGER.info("Build paused");
        }
    }
    
    /**
     * Resumes the building process.
     */
    public void resume() {
        if (state == BuildState.PAUSED) {
            state = BuildState.BUILDING;
            sendMessage("§aСтроительство возобновлено");
            LOGGER.info("Build resumed");
        }
    }
    
    /**
     * Stops and cleans up the building process.
     */
    public void stop() {
        builder.despawn();
        state = BuildState.IDLE;
        sendMessage("§cСтроительство остановлено");
        LOGGER.info("Build stopped");
    }
    
    /**
     * Skips the current layer.
     */
    public void skipLayer() {
        if (state == BuildState.IDLE || state == BuildState.FINISHED) {
            return;
        }
        
        if (currentLayerIndex < sortedLayers.size() - 1) {
            BuildLayer skippedLayer = sortedLayers.get(currentLayerIndex);
            currentLayerIndex++;
            currentBlockIndex = 0;
            sendMessage("§eПропущен слой: " + skippedLayer.getName());
            LOGGER.info("Skipped layer: {}", skippedLayer.getName());
        }
    }
    
    /**
     * Goes to a specific layer by order number.
     */
    public void gotoLayer(int order) {
        for (int i = 0; i < sortedLayers.size(); i++) {
            if (sortedLayers.get(i).getOrder() == order) {
                currentLayerIndex = i;
                currentBlockIndex = 0;
                sendMessage("§aПерешёл к слою: " + sortedLayers.get(i).getName());
                LOGGER.info("Goto layer: {} (index {})", sortedLayers.get(i).getName(), i);
                return;
            }
        }
        sendMessage("§cСлой с порядком " + order + " не найден");
    }
    
    /**
     * Sets the building speed (1-10).
     */
    public void setSpeed(int speed) {
        this.speed = Math.max(1, Math.min(10, speed));
        sendMessage("§aСкорость: " + this.speed + "/10");
    }
    
    public int getSpeed() {
        return speed;
    }
    
    /**
     * Gets the current build state.
     */
    public BuildState getState() {
        return state;
    }
    
    /**
     * Gets current progress info.
     */
    public String getProgressInfo() {
        if (schematic == null || sortedLayers == null || sortedLayers.isEmpty()) {
            return "Не загружено";
        }
        
        BuildLayer currentLayer = currentLayerIndex < sortedLayers.size() 
            ? sortedLayers.get(currentLayerIndex) : null;
        
        String layerName = currentLayer != null ? currentLayer.getName() : "???";
        int layerBlocks = currentLayer != null ? currentLayer.getBlockCount() : 0;
        
        return String.format("%s - Слой: %s (%d/%d) | Блок: %d/%d | Всего: %d/%d",
            state.name(),
            layerName,
            currentLayerIndex + 1,
            sortedLayers.size(),
            currentBlockIndex,
            layerBlocks,
            totalBlocksBuilt,
            schematic.getTotalBlocks());
    }
    
    /**
     * Gets the fake player builder.
     */
    public FakePlayerBuilder getBuilder() {
        return builder;
    }
    
    /**
     * Main tick method - call every game tick.
     */
    public void tick() {
        if (state == BuildState.IDLE || state == BuildState.FINISHED || 
            state == BuildState.ERROR || state == BuildState.PAUSED) {
            return;
        }
        
        tickCounter++;
        
        switch (state) {
            case SPAWNING:
                // Wait a bit after spawning
                if (tickCounter > 20) {
                    startNextLayer();
                }
                break;
                
            case WALKING:
                tickWalking();
                break;
                
            case BUILDING:
                tickBuilding();
                break;
                
            case BREAKING:
                tickBreaking();
                break;
                
            case LAYER_COMPLETE:
                // Pause between layers
                if (tickCounter > 60) {
                    startNextLayer();
                }
                break;
        }
    }
    
    private void startNextLayer() {
        if (currentLayerIndex >= sortedLayers.size()) {
            // All layers complete
            finishBuild();
            return;
        }
        
        BuildLayer layer = sortedLayers.get(currentLayerIndex);
        currentBlockIndex = 0;
        
        sendMessage("§b▶ Слой " + (currentLayerIndex + 1) + "/" + sortedLayers.size() + ": " + layer.getName());
        
        if (layer.getBlocks().isEmpty()) {
            // Empty layer, skip
            currentLayerIndex++;
            startNextLayer();
            return;
        }
        
        state = BuildState.BUILDING;
        tickCounter = 0;
    }
    
    private void tickBuilding() {
        int delay = SPEED_DELAYS[speed - 1];
        
        if (tickCounter < delay) {
            return;
        }
        
        tickCounter = 0;
        
        // Get current layer and block
        if (currentLayerIndex >= sortedLayers.size()) {
            finishBuild();
            return;
        }
        
        BuildLayer layer = sortedLayers.get(currentLayerIndex);
        
        if (currentBlockIndex >= layer.getBlocks().size()) {
            // Layer complete
            completeLayer(layer);
            return;
        }
        
        BlockEntry entry = layer.getBlocks().get(currentBlockIndex);
        BlockPos worldPos = placement.toWorldPos(entry);
        
        // Check if we need to break an existing block
        BlockState existingState = world.getBlockState(worldPos);
        String existingId = existingState.getBlock().toString();
        
        if (!existingState.isAir() && !isReplaceable(existingId)) {
            // Need to break the block first
            currentBlock = entry;
            state = BuildState.BREAKING;
            tickCounter = 0;
            return;
        }
        
        // Check if we need to walk closer
        if (!builder.canReach(worldPos)) {
            BlockPos standPos = builder.getPositionToReach(worldPos);
            builder.walkTo(standPos);
            currentBlock = entry;
            state = BuildState.WALKING;
            return;
        }
        
        // Place the block
        BlockState targetState = entry.getBlockState();
        targetState = placement.rotateBlockState(targetState);
        
        if (builder.placeBlock(worldPos, targetState)) {
            totalBlocksBuilt++;
        }
        
        currentBlockIndex++;
    }
    
    private void tickWalking() {
        if (builder.tickMovement()) {
            // Arrived at destination
            state = BuildState.BUILDING;
            tickCounter = 0;
        }
    }
    
    private void tickBreaking() {
        int delay = SPEED_DELAYS[speed - 1] / 2;
        
        if (tickCounter < delay) {
            // Animation tick
            builder.swingHand();
            return;
        }
        
        if (currentBlock != null) {
            BlockPos worldPos = placement.toWorldPos(currentBlock);
            builder.breakBlock(worldPos);
        }
        
        state = BuildState.BUILDING;
        tickCounter = 0;
    }
    
    private void completeLayer(BuildLayer layer) {
        sendMessage("§a✓ Слой завершён: " + layer.getName() + 
            " (" + layer.getBlockCount() + " блоков)");
        
        if (layerCompleteCallback != null) {
            layerCompleteCallback.run();
        }
        
        currentLayerIndex++;
        state = BuildState.LAYER_COMPLETE;
        tickCounter = 0;
        
        LOGGER.info("Layer complete: {} ({} blocks)", layer.getName(), layer.getBlockCount());
    }
    
    private void finishBuild() {
        sendMessage("§a§l✓ Строительство завершено!");
        sendMessage("§7Построено блоков: " + totalBlocksBuilt);
        
        builder.despawn();
        state = BuildState.FINISHED;
        
        if (buildCompleteCallback != null) {
            buildCompleteCallback.run();
        }
        
        LOGGER.info("Build complete: {} blocks", totalBlocksBuilt);
    }
    
    private boolean isReplaceable(String blockId) {
        // Remove "Block{" and "}" wrapper if present
        if (blockId.startsWith("Block{")) {
            blockId = blockId.substring(6, blockId.length() - 1);
        }
        return REPLACEABLE_BLOCKS.contains(blockId) || blockId.contains("air");
    }
    
    private void sendMessage(String message) {
        if (messageCallback != null) {
            messageCallback.accept(message);
        }
    }
    
    // === Callbacks ===
    
    public void setMessageCallback(Consumer<String> callback) {
        this.messageCallback = callback;
    }
    
    public void setLayerCompleteCallback(Runnable callback) {
        this.layerCompleteCallback = callback;
    }
    
    public void setBuildCompleteCallback(Runnable callback) {
        this.buildCompleteCallback = callback;
    }
}
