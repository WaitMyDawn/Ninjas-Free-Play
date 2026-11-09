package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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

    @ModifyExpressionValue(method = "modifyInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isMovingSlowly()Z"))
    private boolean ninjas$scaleCompactInput(boolean vanillaSlow) {
        return vanillaSlow || ClientMovementEvents.movementInputMultiplier((LocalPlayer) (Object) this, -1) >= 0;
    }

    @ModifyExpressionValue(method = "modifyInput", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double ninjas$compactMultiplier(double vanillaMultiplier) {
        return ClientMovementEvents.movementInputMultiplier((LocalPlayer) (Object) this, vanillaMultiplier);
    }

    @Inject(method = "applyInput", at = @At("TAIL"))
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
