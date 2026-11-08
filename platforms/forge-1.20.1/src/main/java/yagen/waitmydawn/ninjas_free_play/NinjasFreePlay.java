package yagen.waitmydawn.ninjas_free_play;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.player.MovementAttributeEvents;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;
import yagen.waitmydawn.ninjas_free_play.config.ServerConfigs;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.particle.NParticles;

@Mod(NinjasFreePlay.MODID)
public final class NinjasFreePlay {
    public static final String MODID="ninjas_free_play";
    public static final Logger LOGGER=LogUtils.getLogger();

    public NinjasFreePlay() {
        var bus=FMLJavaModLoadingContext.get().getModEventBus();
        NAttributes.register(bus);
        NParticles.register(bus);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(NetworkHandler::register));
        var container=ModLoadingContext.get();
        container.registerConfig(ModConfig.Type.CLIENT,ClientConfigs.SPEC,MODID+"/"+MODID+"-client.toml");
        container.registerConfig(ModConfig.Type.SERVER,ServerConfigs.SPEC,MODID+"/"+MODID+"-server.toml");
        bus.addListener(ClientConfigs::onLoading);
        bus.addListener(ClientConfigs::onReloading);
        bus.addListener(MovementAttributeEvents::onConfigLoading);
        bus.addListener(MovementAttributeEvents::onConfigReloading);
    }
}
