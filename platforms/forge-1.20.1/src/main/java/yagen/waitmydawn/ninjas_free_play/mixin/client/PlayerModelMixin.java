package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.render.BendablePart;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void ninjas$applyHandJoint(HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        ((BendablePart) (Object) (side == HumanoidArm.LEFT ? model.leftArm : model.rightArm)).ninjas$translateToHand(poseStack);
    }
}
