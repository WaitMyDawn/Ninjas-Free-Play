package yagen.waitmydawn.ninjas_free_play.setup;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.render.AnimationResources;
import yagen.waitmydawn.ninjas_free_play.render.HandContactResources;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.player.ClientMovementEvents;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import yagen.waitmydawn.ninjas_free_play.particle.NParticles;
import yagen.waitmydawn.ninjas_free_play.particle.WallSparkParticle;

@Mod.EventBusSubscriber(modid = NinjasFreePlay.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
