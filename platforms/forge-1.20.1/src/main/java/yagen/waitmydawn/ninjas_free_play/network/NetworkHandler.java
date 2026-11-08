package yagen.waitmydawn.ninjas_free_play.network;

import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.player.ServerMovementEvents;

public final class NetworkHandler {
    private static final String PROTOCOL = "10";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NinjasFreePlay.MODID, "movement"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static Consumer<MovementStatePayload> clientReceiver = payload -> {};

    private NetworkHandler() {}

    public static void register() {
        CHANNEL.messageBuilder(MovementInputPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> MovementInputPayload.write(buffer, payload))
                .decoder(MovementInputPayload::read)
                .consumerMainThread((payload, context) -> {
                    ServerMovementEvents.receive(payload, context.get().getSender());
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(MovementStatePayload.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> MovementStatePayload.write(buffer, payload))
                .decoder(MovementStatePayload::read)
                .consumerMainThread((payload, context) -> {
                    clientReceiver.accept(payload);
                    context.get().setPacketHandled(true);
                }).add();
    }

    public static void installClientReceiver(Consumer<MovementStatePayload> receiver) {
        clientReceiver = receiver;
    }

    public static void sendToServer(MovementInputPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, MovementStatePayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToPlayersTrackingEntity(ServerPlayer player, MovementStatePayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), payload);
    }

    public static void sendToPlayersTrackingEntityAndSelf(ServerPlayer player, MovementStatePayload payload) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), payload);
    }
}
