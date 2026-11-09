package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.render.AvatarAnimationState;
import yagen.waitmydawn.ninjas_free_play.render.PlayerPoseAdapter;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void ninjas$applyPose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarAnimationState animated && animated.ninjas$animation() != null) {
            PlayerPoseAdapter.apply((HumanoidModel<?>) (Object) this, animated.ninjas$animation());
        }
    }
}
