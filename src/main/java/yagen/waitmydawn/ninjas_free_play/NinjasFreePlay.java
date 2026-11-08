package yagen.waitmydawn.ninjas_free_play;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.config.ServerConfigs;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.player.MovementAttributeEvents;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.particle.NParticles;

@Mod(NinjasFreePlay.MODID)
public class NinjasFreePlay {
    public static final String MODID = "ninjas_free_play";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NinjasFreePlay(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(NinjasFreePlay::commonSetup);
        modEventBus.addListener(MovementAttributeEvents::onConfigLoading);
        modEventBus.addListener(MovementAttributeEvents::onConfigReloading);
        modEventBus.addListener(ClientConfigs::onLoading);
        modEventBus.addListener(ClientConfigs::onReloading);
        NAttributes.register(modEventBus);
        NParticles.register(modEventBus);
        modEventBus.addListener(NetworkHandler::register);

        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfigs.SPEC, String.format("%s/%s-client.toml", NinjasFreePlay.MODID, NinjasFreePlay.MODID));
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfigs.SPEC, String.format("%s/%s-server.toml", NinjasFreePlay.MODID, NinjasFreePlay.MODID));
    }

    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
        });
    }
}
