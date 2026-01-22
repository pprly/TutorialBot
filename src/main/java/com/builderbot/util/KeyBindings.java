package com.builderbot.util;

import com.builderbot.builder.BuildManager;
import com.builderbot.placement.PlacementController;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * Handles keyboard input for schematic placement and building control.
 */
public class KeyBindings {
    
    // Key bindings
    private static KeyBinding keyRotate;
    private static KeyBinding keyConfirm;
    private static KeyBinding keyCancel;
    private static KeyBinding keyMoveNorth;
    private static KeyBinding keyMoveSouth;
    private static KeyBinding keyMoveEast;
    private static KeyBinding keyMoveWest;
    private static KeyBinding keyMoveUp;
    private static KeyBinding keyMoveDown;
    
    // Key states for edge detection
    private static boolean wasRotatePressed = false;
    private static boolean wasConfirmPressed = false;
    private static boolean wasCancelPressed = false;
    
    // Debounce for movement keys
    private static int moveCooldown = 0;
    
    /**
     * Registers all key bindings.
     */
    public static void register() {
        // Placement control keys
        keyRotate = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.rotate",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.builderbot"
        ));
        
        keyConfirm = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.confirm",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            "category.builderbot"
        ));
        
        keyCancel = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.cancel",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_ESCAPE,
            "category.builderbot"
        ));
        
        // Movement keys (arrow keys by default)
        keyMoveNorth = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.move_north",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            "category.builderbot"
        ));
        
        keyMoveSouth = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.move_south",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            "category.builderbot"
        ));
        
        keyMoveEast = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.move_east",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT,
            "category.builderbot"
        ));
        
        keyMoveWest = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.move_west",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT,
            "category.builderbot"
        ));
        
        keyMoveUp = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.move_up",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            "category.builderbot"
        ));
        
        keyMoveDown = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.builderbot.move_down",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            "category.builderbot"
        ));
        
        // Register tick handler
        ClientTickEvents.END_CLIENT_TICK.register(KeyBindings::onTick);
    }
    
    /**
     * Called every client tick to check key states.
     */
    private static void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        
        // Decrease cooldown
        if (moveCooldown > 0) {
            moveCooldown--;
        }
        
        PlacementController controller = BuildManager.getInstance().getPlacementController();
        
        // Debug: Check placement state
        if (!controller.isPlacementMode()) {
            // Reset states when not in placement mode
            wasRotatePressed = false;
            wasConfirmPressed = false;
            wasCancelPressed = false;
            return;
        }
        
        // Don't process when a screen is open (except for our HUD)
        if (client.currentScreen != null) {
            return;
        }
        
        // Rotate (edge triggered)
        if (keyRotate.isPressed()) {
            if (!wasRotatePressed) {
                controller.rotate();
                sendMessage(client, "§7Поворот: " + controller.getPlacement().getRotation() + "°");
                wasRotatePressed = true;
            }
        } else {
            wasRotatePressed = false;
        }
        
        // Confirm (edge triggered)
        if (keyConfirm.isPressed()) {
            if (!wasConfirmPressed) {
                controller.confirm();
                wasConfirmPressed = true;
            }
        } else {
            wasConfirmPressed = false;
        }
        
        // Cancel (edge triggered)
        if (keyCancel.isPressed()) {
            if (!wasCancelPressed) {
                controller.cancel();
                wasCancelPressed = true;
            }
        } else {
            wasCancelPressed = false;
        }
        
        // Movement keys with cooldown to prevent too fast movement
        if (moveCooldown == 0) {
            boolean moved = false;
            
            if (keyMoveNorth.isPressed()) {
                controller.move(Direction.NORTH);
                moved = true;
            }
            if (keyMoveSouth.isPressed()) {
                controller.move(Direction.SOUTH);
                moved = true;
            }
            if (keyMoveEast.isPressed()) {
                controller.move(Direction.EAST);
                moved = true;
            }
            if (keyMoveWest.isPressed()) {
                controller.move(Direction.WEST);
                moved = true;
            }
            if (keyMoveUp.isPressed()) {
                controller.move(Direction.UP);
                moved = true;
            }
            if (keyMoveDown.isPressed()) {
                controller.move(Direction.DOWN);
                moved = true;
            }
            
            if (moved) {
                moveCooldown = 3; // 3 ticks cooldown (150ms)
                sendMessage(client, "§7Позиция: " + controller.getPlacement().getOrigin().toShortString());
            }
        }
    }
    
    private static void sendMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), true); // true = action bar
        }
    }
}
