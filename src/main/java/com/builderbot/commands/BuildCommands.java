package com.builderbot.commands;

import com.builderbot.builder.BuildExecutor;
import com.builderbot.builder.BuildManager;
import com.builderbot.placement.PlacementController;
import com.builderbot.schematic.BuildLayer;
import com.builderbot.schematic.SchematicLoader;
import com.builderbot.schematic.TutorialSchematic;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * Registers and handles all /build commands.
 */
public class BuildCommands {
    
    // Suggestion provider for schematic filenames
    private static final SuggestionProvider<FabricClientCommandSource> SCHEMATIC_SUGGESTIONS = 
        (context, builder) -> {
            List<String> files = SchematicLoader.listSchematics();
            for (String file : files) {
                builder.suggest(file);
            }
            return builder.buildFuture();
        };
    
    // Suggestion provider for layer IDs
    private static final SuggestionProvider<FabricClientCommandSource> LAYER_SUGGESTIONS =
        (context, builder) -> {
            TutorialSchematic schematic = BuildManager.getInstance().getCurrentSchematic();
            if (schematic != null) {
                for (BuildLayer layer : schematic.getLayersSorted()) {
                    builder.suggest(String.valueOf(layer.getOrder()), 
                        Text.literal(layer.getName()));
                }
            }
            return builder.buildFuture();
        };
    
    /**
     * Registers all commands.
     */
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(BuildCommands::registerCommands);
    }
    
    private static void registerCommands(
            CommandDispatcher<FabricClientCommandSource> dispatcher,
            CommandRegistryAccess registryAccess) {
        
        dispatcher.register(
            ClientCommandManager.literal("build")
                // /build load <filename>
                .then(ClientCommandManager.literal("load")
                    .then(ClientCommandManager.argument("filename", StringArgumentType.string())
                        .suggests(SCHEMATIC_SUGGESTIONS)
                        .executes(BuildCommands::loadSchematic)))
                
                // /build unload
                .then(ClientCommandManager.literal("unload")
                    .executes(BuildCommands::unloadSchematic))
                
                // /build list
                .then(ClientCommandManager.literal("list")
                    .executes(BuildCommands::listSchematics))
                
                // /build info
                .then(ClientCommandManager.literal("info")
                    .executes(BuildCommands::showInfo))
                
                // /build confirm
                .then(ClientCommandManager.literal("confirm")
                    .executes(BuildCommands::confirmPlacement))
                
                // /build rotate [degrees]
                .then(ClientCommandManager.literal("rotate")
                    .executes(ctx -> rotatePlacement(ctx, 90))
                    .then(ClientCommandManager.argument("degrees", IntegerArgumentType.integer(0, 270))
                        .executes(ctx -> rotatePlacement(ctx, IntegerArgumentType.getInteger(ctx, "degrees")))))
                
                // /build move <direction> [amount]
                .then(ClientCommandManager.literal("move")
                    .then(ClientCommandManager.literal("north")
                        .executes(ctx -> movePlacement(ctx, Direction.NORTH, 1))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> movePlacement(ctx, Direction.NORTH, IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(ClientCommandManager.literal("south")
                        .executes(ctx -> movePlacement(ctx, Direction.SOUTH, 1))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> movePlacement(ctx, Direction.SOUTH, IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(ClientCommandManager.literal("east")
                        .executes(ctx -> movePlacement(ctx, Direction.EAST, 1))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> movePlacement(ctx, Direction.EAST, IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(ClientCommandManager.literal("west")
                        .executes(ctx -> movePlacement(ctx, Direction.WEST, 1))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> movePlacement(ctx, Direction.WEST, IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(ClientCommandManager.literal("up")
                        .executes(ctx -> movePlacement(ctx, Direction.UP, 1))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> movePlacement(ctx, Direction.UP, IntegerArgumentType.getInteger(ctx, "amount")))))
                    .then(ClientCommandManager.literal("down")
                        .executes(ctx -> movePlacement(ctx, Direction.DOWN, 1))
                        .then(ClientCommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> movePlacement(ctx, Direction.DOWN, IntegerArgumentType.getInteger(ctx, "amount"))))))
                
                // /build start
                .then(ClientCommandManager.literal("start")
                    .executes(BuildCommands::startBuild))
                
                // /build pause
                .then(ClientCommandManager.literal("pause")
                    .executes(BuildCommands::pauseBuild))
                
                // /build resume
                .then(ClientCommandManager.literal("resume")
                    .executes(BuildCommands::resumeBuild))
                
                // /build stop
                .then(ClientCommandManager.literal("stop")
                    .executes(BuildCommands::stopBuild))
                
                // /build speed <1-10>
                .then(ClientCommandManager.literal("speed")
                    .then(ClientCommandManager.argument("speed", IntegerArgumentType.integer(1, 10))
                        .executes(BuildCommands::setSpeed)))
                
                // /build skip
                .then(ClientCommandManager.literal("skip")
                    .executes(BuildCommands::skipLayer))
                
                // /build goto <layer_order>
                .then(ClientCommandManager.literal("goto")
                    .then(ClientCommandManager.argument("layer", IntegerArgumentType.integer(1))
                        .suggests(LAYER_SUGGESTIONS)
                        .executes(BuildCommands::gotoLayer)))
                
                // /build status
                .then(ClientCommandManager.literal("status")
                    .executes(BuildCommands::showStatus))
                
                // /build layers
                .then(ClientCommandManager.literal("layers")
                    .executes(BuildCommands::listLayers))
                
                // /build help
                .then(ClientCommandManager.literal("help")
                    .executes(BuildCommands::showHelp))
                
                // Default: show help
                .executes(BuildCommands::showHelp)
        );
    }
    
    // === Command handlers ===
    
    private static int loadSchematic(CommandContext<FabricClientCommandSource> ctx) {
        String filename = StringArgumentType.getString(ctx, "filename");
        BuildManager manager = BuildManager.getInstance();
        
        if (manager.loadSchematic(filename)) {
            return 1;
        }
        return 0;
    }
    
    private static int unloadSchematic(CommandContext<FabricClientCommandSource> ctx) {
        BuildManager.getInstance().unloadSchematic();
        return 1;
    }
    
    private static int listSchematics(CommandContext<FabricClientCommandSource> ctx) {
        List<String> files = SchematicLoader.listSchematics();
        
        if (files.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal("§7Нет доступных схем"));
            ctx.getSource().sendFeedback(Text.literal("§7Папка: .minecraft/schematics/tutorials/"));
        } else {
            ctx.getSource().sendFeedback(Text.literal("§6Доступные схемы:"));
            for (String file : files) {
                ctx.getSource().sendFeedback(Text.literal("§7- §f" + file));
            }
        }
        return 1;
    }
    
    private static int showInfo(CommandContext<FabricClientCommandSource> ctx) {
        String info = BuildManager.getInstance().getSchematicInfo();
        for (String line : info.split("\n")) {
            ctx.getSource().sendFeedback(Text.literal(line));
        }
        return 1;
    }
    
    private static int confirmPlacement(CommandContext<FabricClientCommandSource> ctx) {
        if (BuildManager.getInstance().confirmPlacement()) {
            return 1;
        }
        ctx.getSource().sendFeedback(Text.literal("§cНе удалось подтвердить размещение"));
        return 0;
    }
    
    private static int rotatePlacement(CommandContext<FabricClientCommandSource> ctx, int degrees) {
        BuildManager manager = BuildManager.getInstance();
        PlacementController controller = manager.getPlacementController();
        
        if (!controller.isPlacementMode()) {
            ctx.getSource().sendFeedback(Text.literal("§cСхема не в режиме размещения"));
            return 0;
        }
        
        controller.setRotation(controller.getPlacement().getRotation() + degrees);
        ctx.getSource().sendFeedback(Text.literal("§7Поворот: " + 
            controller.getPlacement().getRotation() + "°"));
        return 1;
    }
    
    private static int movePlacement(CommandContext<FabricClientCommandSource> ctx, Direction dir, int amount) {
        PlacementController controller = BuildManager.getInstance().getPlacementController();
        
        if (!controller.isPlacementMode()) {
            ctx.getSource().sendFeedback(Text.literal("§cСхема не в режиме размещения"));
            return 0;
        }
        
        controller.move(dir, amount);
        ctx.getSource().sendFeedback(Text.literal("§7Позиция: " + 
            controller.getPlacement().getOrigin().toShortString()));
        return 1;
    }
    
    private static int startBuild(CommandContext<FabricClientCommandSource> ctx) {
        if (BuildManager.getInstance().startBuild()) {
            return 1;
        }
        return 0;
    }
    
    private static int pauseBuild(CommandContext<FabricClientCommandSource> ctx) {
        BuildManager.getInstance().pauseBuild();
        return 1;
    }
    
    private static int resumeBuild(CommandContext<FabricClientCommandSource> ctx) {
        BuildManager.getInstance().resumeBuild();
        return 1;
    }
    
    private static int stopBuild(CommandContext<FabricClientCommandSource> ctx) {
        BuildManager.getInstance().stopBuild();
        return 1;
    }
    
    private static int setSpeed(CommandContext<FabricClientCommandSource> ctx) {
        int speed = IntegerArgumentType.getInteger(ctx, "speed");
        BuildManager.getInstance().setSpeed(speed);
        return 1;
    }
    
    private static int skipLayer(CommandContext<FabricClientCommandSource> ctx) {
        BuildManager.getInstance().skipLayer();
        return 1;
    }
    
    private static int gotoLayer(CommandContext<FabricClientCommandSource> ctx) {
        int layer = IntegerArgumentType.getInteger(ctx, "layer");
        BuildManager.getInstance().gotoLayer(layer);
        return 1;
    }
    
    private static int showStatus(CommandContext<FabricClientCommandSource> ctx) {
        BuildExecutor executor = BuildManager.getInstance().getBuildExecutor();
        ctx.getSource().sendFeedback(Text.literal("§6Статус: §f" + executor.getProgressInfo()));
        return 1;
    }
    
    private static int listLayers(CommandContext<FabricClientCommandSource> ctx) {
        TutorialSchematic schematic = BuildManager.getInstance().getCurrentSchematic();
        
        if (schematic == null) {
            ctx.getSource().sendFeedback(Text.literal("§cСхема не загружена"));
            return 0;
        }
        
        ctx.getSource().sendFeedback(Text.literal("§6Слои схемы:"));
        for (BuildLayer layer : schematic.getLayersSorted()) {
            ctx.getSource().sendFeedback(Text.literal(String.format(
                "§7%d. §f%s §7(%d блоков)", 
                layer.getOrder(), 
                layer.getName(), 
                layer.getBlockCount())));
        }
        return 1;
    }
    
    private static int showHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.literal("§6=== Builder Bot - Справка ==="));
        ctx.getSource().sendFeedback(Text.literal(""));
        ctx.getSource().sendFeedback(Text.literal("§e/build load <file>§7 - Загрузить схему"));
        ctx.getSource().sendFeedback(Text.literal("§e/build unload§7 - Выгрузить схему"));
        ctx.getSource().sendFeedback(Text.literal("§e/build list§7 - Список схем"));
        ctx.getSource().sendFeedback(Text.literal("§e/build info§7 - Информация о схеме"));
        ctx.getSource().sendFeedback(Text.literal(""));
        ctx.getSource().sendFeedback(Text.literal("§e/build confirm§7 - Подтвердить размещение"));
        ctx.getSource().sendFeedback(Text.literal("§e/build rotate [90|180|270]§7 - Повернуть"));
        ctx.getSource().sendFeedback(Text.literal("§e/build move <dir> [n]§7 - Переместить"));
        ctx.getSource().sendFeedback(Text.literal(""));
        ctx.getSource().sendFeedback(Text.literal("§e/build start§7 - Начать строительство"));
        ctx.getSource().sendFeedback(Text.literal("§e/build pause§7 - Пауза"));
        ctx.getSource().sendFeedback(Text.literal("§e/build resume§7 - Продолжить"));
        ctx.getSource().sendFeedback(Text.literal("§e/build stop§7 - Остановить"));
        ctx.getSource().sendFeedback(Text.literal("§e/build speed <1-10>§7 - Скорость"));
        ctx.getSource().sendFeedback(Text.literal("§e/build skip§7 - Пропустить слой"));
        ctx.getSource().sendFeedback(Text.literal("§e/build goto <n>§7 - Перейти к слою"));
        ctx.getSource().sendFeedback(Text.literal("§e/build status§7 - Текущий статус"));
        ctx.getSource().sendFeedback(Text.literal("§e/build layers§7 - Список слоёв"));
        return 1;
    }
}
