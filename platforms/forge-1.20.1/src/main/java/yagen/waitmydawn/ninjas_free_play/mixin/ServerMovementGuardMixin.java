package yagen.waitmydawn.ninjas_free_play.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import yagen.waitmydawn.ninjas_free_play.player.VanillaMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerMovementGuardMixin {
    @Shadow
    public ServerPlayer player;
    @Shadow
    private boolean clientIsFloating;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void ninjas$beginMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (player.server.isSameThread() && packet.hasPosition()) VanillaMovement.beforeMovePacket(player);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void ninjas$acceptedMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (packet.hasPosition()) VanillaMovement.afterMovePacket(player);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ninjas$validatedAirMode(CallbackInfo ci) {
        if (MovementPhysics.eligible(player) && MovementPhysics.controller(player).parkour().assisted())
            clientIsFloating = false;
    }
}
