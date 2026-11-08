package yagen.waitmydawn.ninjas_free_play.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yagen.waitmydawn.ninjas_free_play.player.ServerMovementEvents;

import java.util.function.Consumer;

public final class NetworkHandler {
    private static Consumer<MovementStatePayload> clientReceiver = payload -> {
    };

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("9");
        registrar.playToServer(MovementInputPayload.TYPE, MovementInputPayload.STREAM_CODEC, (payload, context) ->
                ServerMovementEvents.receive(payload, context));
        registrar.playToClient(MovementStatePayload.TYPE, MovementStatePayload.STREAM_CODEC, (payload, context) ->
                clientReceiver.accept(payload));
    }

    public static void installClientReceiver(Consumer<MovementStatePayload> receiver) {
        clientReceiver = receiver;
    }
}
