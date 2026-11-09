package yagen.waitmydawn.ninjas_free_play.mixin;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.ninjas_free_play.player.MovementAccess;

@Mixin(Avatar.class)
public abstract class AvatarDimensionsMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void ninjas$compactDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        Avatar avatar = (Avatar) (Object) this;
        if (pose == Pose.CROUCHING && avatar instanceof MovementAccess movement && movement.ninjas$lowHeightRatio() > 0) {
            cir.setReturnValue(avatar.getDefaultDimensions(Pose.STANDING).scale(1, movement.ninjas$lowHeightRatio()));
        }
    }
}
