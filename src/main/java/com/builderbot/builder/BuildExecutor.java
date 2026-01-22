package com.builderbot.builder;

import com.builderbot.placement.SchematicPlacement;
import com.builderbot.schematic.BlockEntry;
import com.builderbot.schematic.BuildLayer;
import com.builderbot.schematic.TutorialSchematic;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Executes building WITHOUT fake player - places blocks directly.
 * Simple, reliable, works in 1.21+
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
        BUILDING,
        PAUSED,
        LAYER_COMPLETE,
        FINISHED,
        ERROR
    }

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
    private int totalBlocksBuilt = 0;

    // Callbacks
    private Consumer<String> messageCallback;
    private Runnable layerCompleteCallback;
    private Runnable buildCompleteCallback;

    public BuildExecutor() {
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
            sendMessage("§cОшибка: сначала подтвердите размещение (/build confirm)");
            return false;
        }

        if (state == BuildState.BUILDING) {
            LOGGER.warn("Build already in progress");
            return false;
        }

        state = BuildState.BUILDING;
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
        if (state == BuildState.BUILDING) {
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
     * Checks if currently building (for renderer to hide preview).
     */
    public boolean isBuilding() {
        return state == BuildState.BUILDING ||
                state == BuildState.LAYER_COMPLETE ||
                state == BuildState.PAUSED;
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

        return String.format("Слой: %s (%d/%d) | Блок: %d/%d | Всего: %d/%d",
                layerName,
                currentLayerIndex + 1,
                sortedLayers.size(),
                currentBlockIndex,
                layerBlocks,
                totalBlocksBuilt,
                schematic.getTotalBlocks());
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

        int delay = SPEED_DELAYS[speed - 1];

        if (tickCounter < delay) {
            return;
        }

        tickCounter = 0;

        // Process next block
        processNextBlock();
    }

    /**
     * Processes the next block in the build queue.
     */
    private void processNextBlock() {
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

        if (!existingState.isAir() && !isReplaceable(existingState)) {
            // Break the block first
            breakBlock(worldPos, existingState);
        }

        // Place the block
        BlockState targetState = entry.getBlockState();
        targetState = placement.rotateBlockState(targetState);

        if (placeBlock(worldPos, targetState)) {
            totalBlocksBuilt++;
        }

        currentBlockIndex++;
    }

    /**
     * Places a block directly (no fake player needed).
     */
    private boolean placeBlock(BlockPos pos, BlockState state) {
        try {
            // Place block on server thread
            world.getServer().execute(() -> {
                boolean success = world.setBlockState(pos, state, 3);

                if (success) {
                    // Play place sound
                    world.playSound(null, pos,
                            state.getSoundGroup().getPlaceSound(),
                            SoundCategory.BLOCKS, 1.0f, 1.0f);
                }
            });

            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to place block at {}: {}", pos, e.getMessage());
            return false;
        }
    }

    /**
     * Breaks a block directly.
     */
    private void breakBlock(BlockPos pos, BlockState state) {
        try {
            world.getServer().execute(() -> {
                // Play break sound
                world.playSound(null, pos,
                        state.getSoundGroup().getBreakSound(),
                        SoundCategory.BLOCKS, 1.0f, 1.0f);

                // Break the block
                world.breakBlock(pos, false);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to break block at {}: {}", pos, e.getMessage());
        }
    }

    /**
     * Completes the current layer.
     */
    private void completeLayer(BuildLayer layer) {
        sendMessage("§a✓ Слой завершён: " + layer.getName() +
                " (" + layer.getBlockCount() + " блоков)");

        if (layerCompleteCallback != null) {
            layerCompleteCallback.run();
        }

        currentLayerIndex++;
        currentBlockIndex = 0;

        LOGGER.info("Layer complete: {} ({} blocks)", layer.getName(), layer.getBlockCount());

        // Small pause between layers
        state = BuildState.LAYER_COMPLETE;
        tickCounter = -30; // 1.5 second pause
    }

    /**
     * Finishes the entire build.
     */
    private void finishBuild() {
        sendMessage("§a§l✓ Строительство завершено!");
        sendMessage("§7Построено блоков: " + totalBlocksBuilt);

        state = BuildState.FINISHED;

        if (buildCompleteCallback != null) {
            buildCompleteCallback.run();
        }

        LOGGER.info("Build complete: {} blocks", totalBlocksBuilt);
    }

    /**
     * Checks if a block state is replaceable.
     */
    private boolean isReplaceable(BlockState state) {
        String blockId = state.getBlock().toString();

        // Remove "Block{" and "}" wrapper if present
        if (blockId.startsWith("Block{")) {
            blockId = blockId.substring(6, blockId.length() - 1);
        }

        return REPLACEABLE_BLOCKS.contains(blockId) ||
                blockId.contains("air") ||
                state.isReplaceable();
    }

    /**
     * Sends a message via callback.
     */
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