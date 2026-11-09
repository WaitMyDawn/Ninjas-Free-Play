package yagen.waitmydawn.ninjas_free_play.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import yagen.waitmydawn.ninjas_free_play.player.ServerMovementEvents;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(MovementInputPayload.TYPE, MovementInputPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(MovementStatePayload.TYPE, MovementStatePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MovementInputPayload.TYPE, (payload, context) ->
                ServerMovementEvents.receive(payload, context.player()));
    }

    public static boolean requireClient(ServerGamePacketListenerImpl connection) {
        if (ServerPlayNetworking.canSend(connection.player, MovementStatePayload.TYPE)) return true;
        connection.disconnect(Component.literal("This server requires a matching Ninjas Free Play Fabric build."));
        return false;
    }

    public static void sendToPlayer(ServerPlayer player, MovementStatePayload payload) {
        if (ServerPlayNetworking.canSend(player, MovementStatePayload.TYPE)) ServerPlayNetworking.send(player, payload);
    }

    public static void sendToPlayersTrackingEntity(ServerPlayer player, MovementStatePayload payload) {
        for (ServerPlayer observer : PlayerLookup.tracking(player)) {
            if (observer != player) sendToPlayer(observer, payload);
        }
    }

    public static void sendToPlayersTrackingEntityAndSelf(ServerPlayer player, MovementStatePayload payload) {
        sendToPlayer(player, payload);
        sendToPlayersTrackingEntity(player, payload);
    }
}
