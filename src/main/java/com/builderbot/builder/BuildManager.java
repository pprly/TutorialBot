package com.builderbot.builder;

import com.builderbot.placement.PlacementController;
import com.builderbot.placement.SchematicPlacement;
import com.builderbot.schematic.SchematicLoader;
import com.builderbot.schematic.TutorialSchematic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton manager for the entire build system.
 * Coordinates schematic loading, placement, and execution.
 */
public class BuildManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");
    private static BuildManager INSTANCE;
    
    private TutorialSchematic currentSchematic;
    private PlacementController placementController;
    private BuildExecutor buildExecutor;
    
    private BuildManager() {
        this.placementController = new PlacementController();
        this.buildExecutor = new BuildExecutor();
        
        // Set up message callback
        buildExecutor.setMessageCallback(this::sendChatMessage);
    }
    
    public static BuildManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BuildManager();
        }
        return INSTANCE;
    }
    
    // === Schematic Loading ===
    
    /**
     * Loads a schematic from file and enters placement mode.
     */
    public boolean loadSchematic(String filename) {
        TutorialSchematic schematic = SchematicLoader.load(filename);
        
        if (schematic == null) {
            sendChatMessage("§cОшибка: не удалось загрузить схему '" + filename + "'");
            LOGGER.error("Failed to load schematic: {}", filename);
            return false;
        }
        
        currentSchematic = schematic;
        
        // Start placement - this creates the SchematicPlacement
        placementController.startPlacement(schematic);
        
        // Verify placement was created
        if (!placementController.hasPlacement()) {
            sendChatMessage("§cОшибка: не удалось создать размещение");
            LOGGER.error("Placement was not created!");
            return false;
        }
        
        if (!placementController.isPlacementMode()) {
            sendChatMessage("§cОшибка: не удалось войти в режим размещения");
            LOGGER.error("Not in placement mode!");
            return false;
        }
        
        sendChatMessage("§a✓ Схема загружена: " + schematic.getName());
        sendChatMessage("§7Слоёв: " + schematic.getLayerCount() + 
            ", блоков: " + schematic.getTotalBlocks());
        sendChatMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendChatMessage("§eУправление:");
        sendChatMessage("§7  /build move <north|south|east|west|up|down> [кол-во]");
        sendChatMessage("§7  /build rotate [90|180|270]");
        sendChatMessage("§7  /build confirm §e- подтвердить позицию");
        sendChatMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        LOGGER.info("Schematic loaded successfully: {}", schematic.getName());
        LOGGER.info("Placement mode: {}", placementController.isPlacementMode());
        LOGGER.info("Has placement: {}", placementController.hasPlacement());
        
        // DO NOT AUTO-CONFIRM! User must do it manually with /build confirm
        
        return true;
    }
    
    /**
     * Unloads the current schematic.
     */
    public void unloadSchematic() {
        if (buildExecutor.getState() != BuildExecutor.BuildState.IDLE &&
            buildExecutor.getState() != BuildExecutor.BuildState.FINISHED) {
            buildExecutor.stop();
        }
        
        currentSchematic = null;
        placementController.clear();
        
        sendChatMessage("§7Схема выгружена");
        LOGGER.info("Schematic unloaded");
    }
    
    /**
     * Gets info about the current schematic.
     */
    public String getSchematicInfo() {
        if (currentSchematic == null) {
            return "Схема не загружена";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== ").append(currentSchematic.getName()).append(" ===\n");
        
        if (currentSchematic.getAuthor() != null) {
            sb.append("§7Автор: §f").append(currentSchematic.getAuthor()).append("\n");
        }
        if (currentSchematic.getDescription() != null && !currentSchematic.getDescription().isEmpty()) {
            sb.append("§7Описание: §f").append(currentSchematic.getDescription()).append("\n");
        }
        
        sb.append("§7Слоёв: §f").append(currentSchematic.getLayerCount()).append("\n");
        sb.append("§7Блоков: §f").append(currentSchematic.getTotalBlocks()).append("\n");
        
        int[] dims = currentSchematic.getDimensions();
        sb.append("§7Размер: §f").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2]).append("\n");
        
        if (currentSchematic.getEstimatedBuildTimeMinutes() > 0) {
            sb.append("§7Время: §f~").append(currentSchematic.getEstimatedBuildTimeMinutes()).append(" мин\n");
        }
        
        SchematicPlacement placement = placementController.getPlacement();
        if (placement != null) {
            sb.append("§7Позиция: §f").append(placement.getOrigin().toShortString()).append("\n");
            sb.append("§7Поворот: §f").append(placement.getRotation()).append("°\n");
            sb.append("§7Статус: §f").append(placement.isConfirmed() ? "§aподтверждено" : "§eразмещение").append("\n");
        } else {
            sb.append("§cРазмещение не создано!\n");
        }
        
        // Debug info
        sb.append("§8[Debug] Placement mode: ").append(placementController.isPlacementMode()).append("\n");
        sb.append("§8[Debug] Has placement: ").append(placementController.hasPlacement()).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Gets the current schematic.
     */
    public TutorialSchematic getCurrentSchematic() {
        return currentSchematic;
    }
    
    /**
     * Checks if a schematic is loaded.
     */
    public boolean hasSchematic() {
        return currentSchematic != null;
    }
    
    // === Placement ===
    
    public PlacementController getPlacementController() {
        return placementController;
    }
    
    /**
     * Rotates the schematic placement.
     */
    public void rotate(int degrees) {
        if (!placementController.isPlacementMode()) {
            sendChatMessage("§cСхема не в режиме размещения");
            LOGGER.warn("Not in placement mode! hasPlacement={}, isPlacementMode={}", 
                placementController.hasPlacement(), placementController.isPlacementMode());
            return;
        }
        
        placementController.setRotation(
            placementController.getPlacement().getRotation() + degrees);
        sendChatMessage("§7Поворот: " + placementController.getPlacement().getRotation() + "°");
    }
    
    /**
     * Confirms the placement.
     */
    public boolean confirmPlacement() {
        LOGGER.info("confirmPlacement() called from: {}", 
            Thread.currentThread().getStackTrace()[2].toString());
        LOGGER.info("Attempting to confirm placement...");
        LOGGER.info("  Has placement: {}", placementController.hasPlacement());
        LOGGER.info("  Placement mode: {}", placementController.isPlacementMode());
        
        if (!placementController.hasPlacement()) {
            sendChatMessage("§cОшибка: размещение не создано");
            LOGGER.error("Cannot confirm: no placement exists");
            return false;
        }
        
        if (!placementController.isPlacementMode()) {
            sendChatMessage("§cОшибка: не в режиме размещения");
            LOGGER.error("Cannot confirm: not in placement mode");
            return false;
        }
        
        SchematicPlacement placement = placementController.getPlacement();
        if (placement.isConfirmed()) {
            sendChatMessage("§eРазмещение уже подтверждено");
            LOGGER.warn("Placement already confirmed");
            return false;
        }
        
        if (placementController.confirm()) {
            sendChatMessage("§a✓ Позиция подтверждена!");
            sendChatMessage("§7Используйте §f/build start §7для начала строительства");
            LOGGER.info("Placement confirmed successfully");
            return true;
        }
        
        sendChatMessage("§cНе удалось подтвердить размещение");
        LOGGER.error("Failed to confirm placement");
        return false;
    }
    
    // === Building ===
    
    public BuildExecutor getBuildExecutor() {
        return buildExecutor;
    }
    
    /**
     * Starts building the schematic.
     */
    public boolean startBuild() {
        if (currentSchematic == null) {
            sendChatMessage("§cСхема не загружена");
            return false;
        }
        
        SchematicPlacement placement = placementController.getPlacement();
        if (placement == null || !placement.isConfirmed()) {
            sendChatMessage("§cСначала подтвердите размещение (/build confirm)");
            return false;
        }
        
        // Get server world
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() == null) {
            sendChatMessage("§cТребуется одиночная игра или локальный сервер");
            return false;
        }
        
        ServerWorld world = client.getServer().getOverworld();
        if (world == null) {
            sendChatMessage("§cМир не найден");
            return false;
        }
        
        buildExecutor.initialize(currentSchematic, placement, world);
        return buildExecutor.start();
    }
    
    /**
     * Pauses building.
     */
    public void pauseBuild() {
        buildExecutor.pause();
    }
    
    /**
     * Resumes building.
     */
    public void resumeBuild() {
        buildExecutor.resume();
    }
    
    /**
     * Stops building.
     */
    public void stopBuild() {
        buildExecutor.stop();
    }
    
    /**
     * Sets build speed.
     */
    public void setSpeed(int speed) {
        buildExecutor.setSpeed(speed);
    }
    
    /**
     * Skips current layer.
     */
    public void skipLayer() {
        buildExecutor.skipLayer();
    }
    
    /**
     * Goes to specific layer.
     */
    public void gotoLayer(int order) {
        buildExecutor.gotoLayer(order);
    }
    
    // === Tick ===
    
    /**
     * Called every client tick.
     */
    public static void tick(MinecraftClient client) {
        if (INSTANCE != null) {
            INSTANCE.buildExecutor.tick();
        }
    }
    
    // === Utility ===
    
    private void sendChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}
