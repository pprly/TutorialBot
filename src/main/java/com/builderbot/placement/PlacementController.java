package com.builderbot.placement;

import com.builderbot.schematic.TutorialSchematic;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlacementController {
    private static final Logger LOGGER = LoggerFactory.getLogger("BuilderBot");

    private SchematicPlacement placement;
    private boolean buildingStarted;
    private int moveStep = 1;

    public PlacementController() {
        this.placement = null;
        this.buildingStarted = false;
    }

    public void startPlacement(TutorialSchematic schematic) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null) {
            sendMessage("§cОшибка: игрок не найден");
            return;
        }

        BlockPos playerPos = client.player.getBlockPos();
        this.placement = new SchematicPlacement(schematic, playerPos);
        this.buildingStarted = false;

        sendMessage("§a✓ Режим размещения активирован");
    }

    public void setPlacement(SchematicPlacement placement) {
        this.placement = placement;
        this.buildingStarted = false;
    }

    public SchematicPlacement getPlacement() {
        return placement;
    }

    /**
     * FIX: Can move/rotate until building starts, even after confirm.
     */
    public boolean isPlacementMode() {
        return placement != null && !buildingStarted;
    }

    public boolean hasActivePlacement() {
        return placement != null && placement.isConfirmed();
    }

    public boolean hasPlacement() {
        return placement != null;
    }

    /**
     * Locks placement when building starts.
     */
    public void lockForBuilding() {
        this.buildingStarted = true;
        LOGGER.info("Placement locked for building");
    }

    public void move(Direction direction) {
        move(direction, moveStep);
    }

    public void move(Direction direction, int amount) {
        if (placement == null) {
            sendMessage("§cОшибка: размещение не создано");
            return;
        }

        if (buildingStarted) {
            sendMessage("§cОшибка: строительство уже началось");
            return;
        }

        placement.move(direction, amount);
        sendMessage("§7Позиция: " + placement.getOrigin().toShortString());
    }

    public void move(int dx, int dy, int dz) {
        if (placement != null && !buildingStarted) {
            placement.move(dx, dy, dz);
        }
    }

    public void moveTo(BlockPos pos) {
        if (placement != null && !buildingStarted) {
            placement.setOrigin(pos);
        }
    }

    public void rotate() {
        if (placement != null && !buildingStarted) {
            placement.rotate90();
            sendMessage("§7Поворот: " + placement.getRotation() + "°");
        }
    }

    public void setRotation(int degrees) {
        if (placement != null && !buildingStarted) {
            placement.setRotation(degrees);
            sendMessage("§7Поворот: " + placement.getRotation() + "°");
        }
    }

    /**
     * FIX: Confirm doesn't lock movement anymore.
     */
    public boolean confirm() {
        if (placement == null) {
            sendMessage("§cОшибка: размещение не создано");
            return false;
        }

        if (buildingStarted) {
            sendMessage("§eСтроительство уже началось");
            return false;
        }

        placement.setConfirmed(true);
        sendMessage("§a✓ Позиция подтверждена!");
        sendMessage("§7Можно корректировать до /build start");

        return true;
    }

    public void cancel() {
        if (placement != null && !buildingStarted) {
            placement = null;
            sendMessage("§cРазмещение отменено");
        }
    }

    public void clear() {
        placement = null;
        buildingStarted = false;
    }

    public int getMoveStep() {
        return moveStep;
    }

    public void setMoveStep(int moveStep) {
        this.moveStep = Math.max(1, moveStep);
    }

    public boolean handleKeyPress(int keyCode) {
        if (!isPlacementMode()) {
            return false;
        }

        switch (keyCode) {
            case 262: move(Direction.EAST); return true;
            case 263: move(Direction.WEST); return true;
            case 264: move(Direction.SOUTH); return true;
            case 265: move(Direction.NORTH); return true;
            case 266: move(Direction.UP); return true;
            case 267: move(Direction.DOWN); return true;
            case 82: rotate(); return true;
            case 257: confirm(); return true;
            case 256: cancel(); return true;
        }

        return false;
    }

    private void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true);
        }
    }
}