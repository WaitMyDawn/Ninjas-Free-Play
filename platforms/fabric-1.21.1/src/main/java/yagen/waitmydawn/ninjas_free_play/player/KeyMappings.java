package yagen.waitmydawn.ninjas_free_play.player;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyMappings {
    public static final KeyMapping CROUCH = new KeyMapping("key.ninjas_free_play.crouch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.ninjas_free_play");
    public static final KeyMapping ROLL = new KeyMapping("key.ninjas_free_play.roll", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_CAPS_LOCK, "key.categories.ninjas_free_play");

    private KeyMappings() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(CROUCH);
        KeyBindingHelper.registerKeyBinding(ROLL);
    }
}
