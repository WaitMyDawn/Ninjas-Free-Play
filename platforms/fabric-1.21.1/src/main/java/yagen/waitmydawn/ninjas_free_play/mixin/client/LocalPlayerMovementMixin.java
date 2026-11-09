package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.ninjas_free_play.player.ClientMovementEvents;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.player.KeyMappings;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMovementMixin {
    @Shadow
    private int autoJumpTime;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void ninjas$clearQueuedAutoJump(CallbackInfo ci) {
        if (ninjas$blocksAutoJump()) autoJumpTime = 0;
    }

    @Inject(method = "canAutoJump", at = @At("HEAD"), cancellable = true)
    private void ninjas$preventCompactAutoJump(CallbackInfoReturnable<Boolean> cir) {
        if (ninjas$blocksAutoJump()) cir.setReturnValue(false);
    }

    @Unique
    private boolean ninjas$blocksAutoJump() {
        LocalPlayer player = (LocalPlayer) (Object) this;
        return MovementPhysics.eligible(player) && (KeyMappings.CROUCH.isDown() || MovementPhysics.low(player)
                || MovementPhysics.controller(player).posture().rolling(player.tickCount));
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void ninjas$adjustMovementInput(CallbackInfo ci) {
        ClientMovementEvents.movementInput((LocalPlayer) (Object) this);
    }

    @Inject(method = "serverAiStep", at = @At("TAIL"))
    private void ninjas$beforeTravel(CallbackInfo ci) {
        if (ClientMovementEvents.beforeTravel((LocalPlayer) (Object) this)) {
            ((LivingEntityInputAccessor) this).ninjas$setJumping(false);
        }
    }

    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void ninjas$crouchVisual(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (MovementPhysics.eligible(player) && MovementPhysics.controller(player).crouchHeld()) {
            cir.setReturnValue(player.hasPose(Pose.CROUCHING));
        }
    }
}
