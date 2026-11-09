package yagen.waitmydawn.ninjas_free_play.setup;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;
import yagen.waitmydawn.ninjas_free_play.camera.client.ViewCommands;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.network.MovementStatePayload;
import yagen.waitmydawn.ninjas_free_play.particle.NParticles;
import yagen.waitmydawn.ninjas_free_play.particle.WallSparkEvents;
import yagen.waitmydawn.ninjas_free_play.particle.WallSparkParticle;
import yagen.waitmydawn.ninjas_free_play.player.ClientAnimationEvents;
import yagen.waitmydawn.ninjas_free_play.player.ClientMovementEvents;
import yagen.waitmydawn.ninjas_free_play.player.KeyMappings;
import yagen.waitmydawn.ninjas_free_play.render.AnimationResources;
import yagen.waitmydawn.ninjas_free_play.render.HandContactResources;
import yagen.waitmydawn.ninjas_free_play.render.MovementAnimations;

public final class ClientSetupEvents implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfigs.load(FabricLoader.getInstance().getConfigDir().resolve("ninjas_free_play/ninjas_free_play-client.toml"));
        KeyMappings.register();
        ClientPlayNetworking.registerGlobalReceiver(MovementStatePayload.TYPE, (payload, context) ->
                ClientMovementEvents.receive(payload));
        var resources = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        resources.registerReloader(NinjasFreePlay.id("animations"), AnimationResources.INSTANCE);
        resources.registerReloader(NinjasFreePlay.id("hand_contacts"), HandContactResources.INSTANCE);
        ParticleFactoryRegistry.getInstance().register(NParticles.WALL_SPARK, WallSparkParticle.Provider::new);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            ViewCommands.register(dispatcher);
            ClientAnimationEvents.register(dispatcher);
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            ShoulderCamera.tick();
            ClientMovementEvents.tick();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientMovementEvents.actionTick();
            ClientAnimationEvents.onClientTick();
            MovementAnimations.cleanup();
            WallSparkEvents.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientMovementEvents.logout();
            ClientAnimationEvents.onLogout();
            ShoulderCamera.logout();
        });
    }
}
