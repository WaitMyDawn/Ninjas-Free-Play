package yagen.waitmydawn.ninjas_free_play.setup;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.render.AnimationResources;
import yagen.waitmydawn.ninjas_free_play.render.HandContactResources;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.player.ClientMovementEvents;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import yagen.waitmydawn.ninjas_free_play.particle.NParticles;
import yagen.waitmydawn.ninjas_free_play.particle.WallSparkParticle;

@EventBusSubscriber(modid = NinjasFreePlay.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetupEvents {
    private ClientSetupEvents() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> NetworkHandler.installClientReceiver(ClientMovementEvents::receive));
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(AnimationResources.INSTANCE);
        event.registerReloadListener(HandContactResources.INSTANCE);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(NParticles.WALL_SPARK.get(), WallSparkParticle.Provider::new);
    }
}
