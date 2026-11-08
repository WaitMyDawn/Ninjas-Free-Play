package yagen.waitmydawn.ninjas_free_play.smoke;

import net.minecraft.client.Minecraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.camera.client.CameraAccess;
import yagen.waitmydawn.ninjas_free_play.camera.client.ViewCommands;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.player.MovementCollisionAccess;

/**
 * Test-only subscriber, installed by the isolated smoke run rather than packaged in the runtime mod.
 */
@EventBusSubscriber(modid = NinjasFreePlay.MODID, value = Dist.CLIENT)
public final class CameraStartupProbe {
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (++ticks > 1200) {
            NinjasFreePlay.LOGGER.error("C1_CLIENT_SMOKE_TIMEOUT");
            minecraft.stop();
            return;
        }
        if (minecraft.getOverlay() != null) return;
        if (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen) {
            if (!(minecraft.gameRenderer.getMainCamera() instanceof CameraAccess) || !ClientConfigs.SPEC.isLoaded()
                    || !MovementCollisionAccess.class.isAssignableFrom(Entity.class))
                throw new IllegalStateException("Camera/collision bridge or config did not load");
            verifyViewCommands();
            NinjasFreePlay.LOGGER.info("C1_CLIENT_SMOKE_PASS: camera, collision, projected HUD and perspective mixins loaded; screen={}",
                    minecraft.screen.getClass().getSimpleName());
            minecraft.stop();
        }
    }

    private static void verifyViewCommands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ViewCommands.register(dispatcher);
        var source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 0, "camera_smoke",
                Component.literal("camera_smoke"), null, null);
        String previous = ClientConfigs.CAMERA_VIEW_CYCLE.get();
        double automatic = ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get(), fixed = ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get();
        try {
            if (!dispatcher.getRoot().getChild("nfp").canUse(source)
                    || !dispatcher.getRoot().getChild("nfp").getChild("view").canUse(source)
                    || dispatcher.execute("nfp view 10110", source) != 1
                    || !ClientConfigs.CAMERA_VIEW_CYCLE.get().equals("10110"))
                throw new IllegalStateException("View command must work at permission level zero");
            try {
                dispatcher.execute("nfp view 00000", source);
                throw new IllegalStateException("Empty view cycle was accepted");
            } catch (CommandSyntaxException expected) {
                if (!ClientConfigs.CAMERA_VIEW_CYCLE.get().equals("10110"))
                    throw new IllegalStateException("Rejected command changed the config");
            }
            dispatcher.execute("nfp view 00100", source);
            if (!ClientConfigs.CAMERA_VIEW_CYCLE.get().equals("00100") || dispatcher.execute("nfp view", source) != 1)
                throw new IllegalStateException("Leading zeroes or view query failed");
            NinjasFreePlay.LOGGER.info("VIEW_COMMAND_SMOKE_PASS: permission=0, invalid-mask rejection, leading zeroes, query and config saving; horizontal={}",
                    ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get());
            verifyReverse(dispatcher, source);
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("View command failed", exception);
        } finally {
            ClientConfigs.CAMERA_VIEW_CYCLE.set(previous);
            ClientConfigs.CAMERA_HORIZONTAL_OFFSET.set(automatic);
            ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.set(fixed);
            ClientConfigs.SPEC.save();
        }
    }

    private static void verifyReverse(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source) throws CommandSyntaxException {
        if (!dispatcher.getRoot().getChild("nfp").getChild("reverse").canUse(source))
            throw new IllegalStateException("Reverse command must work at permission level zero");
        ClientConfigs.CAMERA_HORIZONTAL_OFFSET.set(1.4);
        ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.set(-2.3);
        dispatcher.execute("nfp reverse auto", source);
        if (ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get() != -1.4 || ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get() != -2.3)
            throw new IllegalStateException("Automatic reversal must not change the fixed offset");
        dispatcher.execute("nfp reverse fix", source);
        if (ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get() != -1.4 || ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get() != 2.3)
            throw new IllegalStateException("Fixed reversal must not change the automatic offset");
        dispatcher.execute("nfp reverse", source);
        if (ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get() != 1.4 || ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get() != -2.3
                || !ClientConfigs.CAMERA_VIEW_CYCLE.get().equals("00100"))
            throw new IllegalStateException("Combined reversal must invert both sides and preserve the cycle");
        dispatcher.execute("nfp reverse", source);
        dispatcher.execute("nfp reverse", source);
        if (ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get() != 1.4 || ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get() != -2.3)
            throw new IllegalStateException("Repeated reversal must preserve custom magnitudes");
        ClientConfigs.CAMERA_HORIZONTAL_OFFSET.set(0.0);
        dispatcher.execute("nfp reverse auto", source);
        if (ClientConfigs.CAMERA_HORIZONTAL_OFFSET.get() != 0 || ClientConfigs.CAMERA_FIXED_HORIZONTAL_OFFSET.get() != -2.3)
            throw new IllegalStateException("A centered view must remain centered without changing the other mode");
        NinjasFreePlay.LOGGER.info("REVERSE_COMMAND_SMOKE_PASS: permission=0, independent/combined reversal, custom magnitudes, repeatability and config saving");
    }
}
