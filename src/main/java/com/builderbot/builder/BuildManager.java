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
            return false;
        }
        
        currentSchematic = schematic;
        placementController.startPlacement(schematic);
        
        sendChatMessage("§aСхема загружена: " + schematic.getName());
        sendChatMessage("§7Слоёв: " + schematic.getLayerCount() + 
            ", блоков: " + schematic.getTotalBlocks());
        sendChatMessage("§eИспользуйте стрелки для перемещения, R для поворота");
        sendChatMessage("§eEnter или /build confirm для подтверждения");
        
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
            sb.append("§7Статус: §f").append(placement.isConfirmed() ? "подтверждено" : "размещение").append("\n");
        }
        
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
        if (placementController.isPlacementMode()) {
            placementController.setRotation(
                placementController.getPlacement().getRotation() + degrees);
            sendChatMessage("§7Поворот: " + placementController.getPlacement().getRotation() + "°");
        } else {
            sendChatMessage("§cСхема не в режиме размещения");
        }
    }
    
    /**
     * Confirms the placement.
     */
    public boolean confirmPlacement() {
        if (placementController.confirm()) {
            sendChatMessage("§aПозиция подтверждена!");
            sendChatMessage("§7Используйте /build start для начала строительства");
            return true;
        }
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
