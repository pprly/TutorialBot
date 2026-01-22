package com.builderbot.render;

import com.builderbot.builder.BuildExecutor;
import com.builderbot.builder.BuildManager;
import com.builderbot.placement.PlacementController;
import com.builderbot.placement.SchematicPlacement;
import com.builderbot.schematic.TutorialSchematic;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Renders HUD overlay with schematic and build information.
 */
public class BuilderHUD {
    
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_COLOR = 0x80000000; // Semi-transparent black
    private static final int BORDER_COLOR = 0xFF444444;
    
    /**
     * Registers the HUD render callback.
     */
    public static void register() {
        HudRenderCallback.EVENT.register(BuilderHUD::render);
    }
    
    /**
     * Main render method.
     */
    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        BuildManager manager = BuildManager.getInstance();
        PlacementController placementController = manager.getPlacementController();
        
        if (!placementController.hasPlacement()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        
        SchematicPlacement placement = placementController.getPlacement();
        TutorialSchematic schematic = placement.getSchematic();
        BuildExecutor executor = manager.getBuildExecutor();
        
        // Determine what info to show
        if (placementController.isPlacementMode()) {
            renderPlacementHUD(context, textRenderer, schematic, placement);
        } else if (executor.getState() != BuildExecutor.BuildState.IDLE && 
                   executor.getState() != BuildExecutor.BuildState.FINISHED) {
            renderBuildingHUD(context, textRenderer, schematic, executor);
        } else if (placement.isConfirmed()) {
            renderConfirmedHUD(context, textRenderer, schematic, placement);
        }
    }
    
    /**
     * Renders HUD during placement mode.
     */
    private static void renderPlacementHUD(DrawContext context, TextRenderer textRenderer,
                                           TutorialSchematic schematic, SchematicPlacement placement) {
        String[] lines = {
            "§6Размещение схемы",
            "§fСхема: §7" + schematic.getName(),
            "§fПозиция: §7" + placement.getOrigin().toShortString(),
            "§fПоворот: §7" + placement.getRotation() + "°",
            "",
            "§7[Стрелки] - перемещение",
            "§7[PageUp/Down] - вверх/вниз",
            "§7[R] - повернуть",
            "§7[Enter] - подтвердить",
            "§7[Esc] - отмена"
        };
        
        renderInfoBox(context, textRenderer, lines, 10, 10);
    }
    
    /**
     * Renders HUD when placement is confirmed but not building.
     */
    private static void renderConfirmedHUD(DrawContext context, TextRenderer textRenderer,
                                           TutorialSchematic schematic, SchematicPlacement placement) {
        String[] lines = {
            "§aСхема размещена",
            "§fСхема: §7" + schematic.getName(),
            "§fПозиция: §7" + placement.getOrigin().toShortString(),
            "",
            "§7/build start - начать"
        };
        
        renderInfoBox(context, textRenderer, lines, 10, 10);
    }
    
    /**
     * Renders HUD during building.
     */
    private static void renderBuildingHUD(DrawContext context, TextRenderer textRenderer,
                                          TutorialSchematic schematic, BuildExecutor executor) {
        
        String stateText = switch (executor.getState()) {
            case BUILDING -> "§aСтроит";
            case WALKING -> "§eИдёт";
            case BREAKING -> "§cЛомает";
            case PAUSED -> "§6Пауза";
            case LAYER_COMPLETE -> "§bСлой готов";
            default -> executor.getState().name();
        };
        
        String[] lines = {
            "§6Строительство",
            "§fСхема: §7" + schematic.getName(),
            "§fСтатус: " + stateText,
            "§fПрогресс: §7" + executor.getProgressInfo(),
            "§fСкорость: §7" + executor.getSpeed() + "/10",
            "",
            "§7/build pause - пауза",
            "§7/build stop - остановить"
        };
        
        renderInfoBox(context, textRenderer, lines, 10, 10);
    }
    
    /**
     * Renders an info box with the given lines.
     */
    private static void renderInfoBox(DrawContext context, TextRenderer textRenderer,
                                       String[] lines, int x, int y) {
        // Calculate box dimensions
        int maxWidth = 0;
        for (String line : lines) {
            int width = textRenderer.getWidth(stripFormatting(line));
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        
        int boxWidth = maxWidth + PADDING * 2;
        int boxHeight = lines.length * LINE_HEIGHT + PADDING * 2;
        
        // Draw background
        context.fill(x, y, x + boxWidth, y + boxHeight, BOX_COLOR);
        
        // Draw border
        context.fill(x, y, x + boxWidth, y + 1, BORDER_COLOR);
        context.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, BORDER_COLOR);
        context.fill(x, y, x + 1, y + boxHeight, BORDER_COLOR);
        context.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, BORDER_COLOR);
        
        // Draw text
        int textY = y + PADDING;
        for (String line : lines) {
            if (!line.isEmpty()) {
                context.drawText(textRenderer, line, x + PADDING, textY, 0xFFFFFF, true);
            }
            textY += LINE_HEIGHT;
        }
    }
    
    /**
     * Strips Minecraft formatting codes from a string for width calculation.
     */
    private static String stripFormatting(String text) {
        return text.replaceAll("§.", "");
    }
}
