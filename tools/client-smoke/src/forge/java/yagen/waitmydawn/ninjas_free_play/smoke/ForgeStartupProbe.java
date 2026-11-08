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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.camera.client.CameraAccess;
import yagen.waitmydawn.ninjas_free_play.camera.client.ViewCommands;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.player.MovementCollisionAccess;

/**
 * Test-only subscriber, installed by the isolated smoke run rather than packaged in the runtime mod.
 */
@Mod.EventBusSubscriber(modid = NinjasFreePlay.MODID, value = Dist.CLIENT)
public final class ForgeStartupProbe {
    private static int ticks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var minecraft = Minecraft.getInstance();
        if (++ticks > 1200) {
            NinjasFreePlay.LOGGER.error("FORGE_CLIENT_SMOKE_TIMEOUT");
            minecraft.stop();
            return;
        }
        if (minecraft.getOverlay() != null) return;
        if (minecraft.screen instanceof TitleScreen || minecraft.screen instanceof AccessibilityOnboardingScreen) {
            if (!(minecraft.gameRenderer.getMainCamera() instanceof CameraAccess) || !ClientConfigs.SPEC.isLoaded()
                    || !MovementCollisionAccess.class.isAssignableFrom(Entity.class))
                throw new IllegalStateException("Camera/collision bridge or config did not load");
            verifyModelsAndResources(minecraft);
            verifyViewCommands();
            NinjasFreePlay.LOGGER.info("FORGE_CLIENT_SMOKE_PASS: camera, collision, projected HUD and perspective mixins loaded; screen={}",
                    minecraft.screen.getClass().getSimpleName());
            minecraft.stop();
        }
    }

    private static void verifyModelsAndResources(Minecraft minecraft) {
        if (yagen.waitmydawn.ninjas_free_play.render.AnimationResources.INSTANCE.ids().size() != 20
                || !yagen.waitmydawn.ninjas_free_play.player.MovementAccess.class.isAssignableFrom(net.minecraft.client.player.LocalPlayer.class))
            throw new IllegalStateException("Animations or real-input movement bridge did not load");
        for (boolean slim : new boolean[]{false, true}) {
            var layer = slim ? net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM : net.minecraft.client.model.geom.ModelLayers.PLAYER;
            var model = new net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>(minecraft.getEntityModels().bakeLayer(layer), slim);
            var joint = (yagen.waitmydawn.ninjas_free_play.render.BendablePart) (Object) model.rightArm;
            joint.ninjas$setBend(0.8, 6);
            model.rightSleeve.copyFrom(model.rightArm);
            if (((yagen.waitmydawn.ninjas_free_play.render.BendablePart) (Object) model.rightSleeve).ninjas$getBend() != 0.8)
                throw new IllegalStateException("The skin overlay did not copy the bent joint");
            var pose = new com.mojang.blaze3d.vertex.PoseStack();
            model.translateToHand(net.minecraft.world.entity.HumanoidArm.RIGHT, pose);
            var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(4096);
            buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY);
            model.rightArm.render(new com.mojang.blaze3d.vertex.PoseStack(), buffer, 0xF000F0, 0, 1, 1, 1, 1);
            var rendered = buffer.end();
            try {
                var vertices = rendered.vertexBuffer();
                int stride = com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY.getVertexSize();
                if (vertices.remaining() == 0) throw new IllegalStateException("The bent model emitted no vertices");
                for (int offset = 0; offset < vertices.remaining(); offset += stride)
                    for (int axis = 0; axis < 3; axis++)
                        if (!Float.isFinite(vertices.getFloat(offset + axis * 4)))
                            throw new IllegalStateException("The bent model emitted a non-finite position");
            } finally {
                rendered.release();
            }
        }
        NinjasFreePlay.LOGGER.info("FORGE_MODEL_SMOKE_PASS: standard/slim skin layers, bent vertices and item-hand transform");
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
