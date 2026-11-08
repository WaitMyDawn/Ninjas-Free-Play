package yagen.waitmydawn.ninjas_free_play.player;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;

@Mod.EventBusSubscriber(modid = NinjasFreePlay.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KeyMappings {
    public static final KeyMapping CROUCH = new KeyMapping("key.ninjas_free_play.crouch", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.ninjas_free_play");
    public static final KeyMapping ROLL = new KeyMapping("key.ninjas_free_play.roll", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_CAPS_LOCK, "key.categories.ninjas_free_play");

    private KeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CROUCH);
        event.register(ROLL);
    }
}
