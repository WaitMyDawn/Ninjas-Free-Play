package yagen.waitmydawn.ninjas_free_play.player;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;

public final class KeyMappings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(NinjasFreePlay.id("movement"));
    public static final KeyMapping CROUCH = new KeyMapping("key.ninjas_free_play.crouch", InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping ROLL = new KeyMapping("key.ninjas_free_play.roll", InputConstants.KEY_CAPSLOCK, CATEGORY);

    private KeyMappings() {
    }

    public static void register() {
        KeyMappingHelper.registerKeyMapping(CROUCH);
        KeyMappingHelper.registerKeyMapping(ROLL);
    }
}
