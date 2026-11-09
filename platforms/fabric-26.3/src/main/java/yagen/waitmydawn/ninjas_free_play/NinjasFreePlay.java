package yagen.waitmydawn.ninjas_free_play;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yagen.waitmydawn.ninjas_free_play.api.attribute.NAttributes;
import yagen.waitmydawn.ninjas_free_play.api.movement.ActionEndReason;
import yagen.waitmydawn.ninjas_free_play.api.movement.ContactDamageContext;
import yagen.waitmydawn.ninjas_free_play.network.NetworkHandler;
import yagen.waitmydawn.ninjas_free_play.particle.NParticles;
import yagen.waitmydawn.ninjas_free_play.player.MovementActionHooks;
import yagen.waitmydawn.ninjas_free_play.player.MovementAttributeEvents;
import yagen.waitmydawn.ninjas_free_play.player.ServerMovementEvents;

public final class NinjasFreePlay implements ModInitializer {
    public static final String MODID = "ninjas_free_play";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        NAttributes.register();
        NParticles.register();
        NetworkHandler.register();
        ServerLifecycleEvents.SERVER_STARTED.register(MovementAttributeEvents::reload);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) MovementAttributeEvents.reload(server);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (NetworkHandler.requireClient(handler)) MovementAttributeEvents.applyDefaults(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ServerMovementEvents.logout(handler.player));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                MovementAttributeEvents.copy(oldPlayer, newPlayer));
        ServerTickEvents.END_LEVEL_TICK.register(world -> world.players().forEach(ServerMovementEvents::tick));
        EntityTrackingEvents.START_TRACKING.register((entity, observer) -> {
            if (entity instanceof ServerPlayer target) ServerMovementEvents.tracking(observer, target);
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                ServerMovementEvents.changedDimension(player));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !ContactDamageContext.suppresses(entity, source));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) MovementActionHooks.stopAll(player, ActionEndReason.DEATH);
        });
    }
}
