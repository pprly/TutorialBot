package com.builderbot;

import com.builderbot.builder.BuildManager;
import com.builderbot.commands.BuildCommands;
import com.builderbot.render.BuilderHUD;
import com.builderbot.render.SchematicRenderer;
import com.builderbot.schematic.SchematicLoader;
import com.builderbot.util.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod class for Builder Bot.
 * 
 * Builder Bot is an automated building system that:
 * 1. Loads .ltutorial schematic files
 * 2. Places a preview "ghost" in the world
 * 3. Creates a fake player builder
 * 4. Automatically builds the schematic layer by layer
 * 
 * Part of the Minecraft tutorial automation system.
 */
public class BuilderBotMod implements ClientModInitializer, ModInitializer {
    
    public static final String MOD_ID = "builderbot";
    public static final String MOD_NAME = "Builder Bot";
    public static final String VERSION = "1.0.0";
    
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    
    @Override
    public void onInitialize() {
        // Common initialization (both client and server)
        LOGGER.info("{} v{} - Common initialization", MOD_NAME, VERSION);
        
        // Ensure schematics folder exists
        SchematicLoader.ensureFolderExists();
    }
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("{} v{} - Client initialization", MOD_NAME, VERSION);
        
        // Register commands
        BuildCommands.register();
        LOGGER.info("Commands registered");
        
        // Register key bindings
        KeyBindings.register();
        LOGGER.info("Key bindings registered");
        
        // Register world renderer for schematic preview
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SchematicRenderer::render);
        LOGGER.info("World renderer registered");
        
        // Register HUD renderer
        BuilderHUD.register();
        LOGGER.info("HUD renderer registered");
        
        // Register tick event for build execution
        ClientTickEvents.END_CLIENT_TICK.register(BuildManager::tick);
        LOGGER.info("Tick handler registered");
        
        LOGGER.info("{} initialization complete!", MOD_NAME);
        LOGGER.info("Use /build help for command list");
    }
}
