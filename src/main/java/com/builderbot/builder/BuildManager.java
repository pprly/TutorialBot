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

public class BuildManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");
    private static BuildManager INSTANCE;

    private TutorialSchematic currentSchematic;
    private PlacementController placementController;
    private BuildExecutor buildExecutor;

    private BuildManager() {
        this.placementController = new PlacementController();
        this.buildExecutor = new BuildExecutor();
        buildExecutor.setMessageCallback(this::sendChatMessage);
    }

    public static BuildManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BuildManager();
        }
        return INSTANCE;
    }

    public boolean loadSchematic(String filename) {
        TutorialSchematic schematic = SchematicLoader.load(filename);

        if (schematic == null) {
            sendChatMessage("§cОшибка: не удалось загрузить схему '" + filename + "'");
            return false;
        }

        currentSchematic = schematic;
        placementController.startPlacement(schematic);

        sendChatMessage("§a✓ Схема загружена: " + schematic.getName());
        sendChatMessage("§7Слоёв: " + schematic.getLayerCount() + ", блоков: " + schematic.getTotalBlocks());
        sendChatMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sendChatMessage("§eУправление:");
        sendChatMessage("§7  Стрелки / PageUp/Down - перемещение");
        sendChatMessage("§7  R - повернуть");
        sendChatMessage("§7  Enter - подтвердить");
        sendChatMessage("§7  /build confirm - подтвердить через команду");
        sendChatMessage("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        return true;
    }

    public void unloadSchematic() {
        if (buildExecutor.getState() != BuildExecutor.BuildState.IDLE &&
                buildExecutor.getState() != BuildExecutor.BuildState.FINISHED) {
            buildExecutor.stop();
        }

        currentSchematic = null;
        placementController.clear();
        sendChatMessage("§7Схема выгружена");
    }

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

        SchematicPlacement placement = placementController.getPlacement();
        if (placement != null) {
            sb.append("§7Позиция: §f").append(placement.getOrigin().toShortString()).append("\n");
            sb.append("§7Поворот: §f").append(placement.getRotation()).append("°\n");
            sb.append("§7Статус: §f").append(placement.isConfirmed() ? "§aподтверждено" : "§eразмещение").append("\n");
        }

        return sb.toString();
    }

    public TutorialSchematic getCurrentSchematic() {
        return currentSchematic;
    }

    public boolean hasSchematic() {
        return currentSchematic != null;
    }

    public PlacementController getPlacementController() {
        return placementController;
    }

    public void rotate(int degrees) {
        if (!placementController.isPlacementMode()) {
            sendChatMessage("§cСхема не в режиме размещения");
            return;
        }

        placementController.setRotation(
                placementController.getPlacement().getRotation() + degrees);
        sendChatMessage("§7Поворот: " + placementController.getPlacement().getRotation() + "°");
    }

    public boolean confirmPlacement() {
        if (!placementController.hasPlacement()) {
            sendChatMessage("§cОшибка: размещение не создано");
            return false;
        }

        return placementController.confirm();
    }

    public BuildExecutor getBuildExecutor() {
        return buildExecutor;
    }

    /**
     * Starts building - locks placement.
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

        // Lock placement - no more movement
        placementController.lockForBuilding();

        buildExecutor.initialize(currentSchematic, placement, world);
        return buildExecutor.start();
    }

    public void pauseBuild() {
        buildExecutor.pause();
    }

    public void resumeBuild() {
        buildExecutor.resume();
    }

    public void stopBuild() {
        buildExecutor.stop();
    }

    public void setSpeed(int speed) {
        buildExecutor.setSpeed(speed);
    }

    public void skipLayer() {
        buildExecutor.skipLayer();
    }

    public void gotoLayer(int order) {
        buildExecutor.gotoLayer(order);
    }

    public static void tick(MinecraftClient client) {
        if (INSTANCE != null) {
            INSTANCE.buildExecutor.tick();
        }
    }

    private void sendChatMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
}