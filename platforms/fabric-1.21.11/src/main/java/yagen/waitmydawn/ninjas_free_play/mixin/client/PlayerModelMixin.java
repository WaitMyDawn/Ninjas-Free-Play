package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.render.BendablePart;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void ninjas$bendSkinLayers(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        // Outer skin parts already inherit their parent's transform.
        ninjas$copyBend(model.leftSleeve, model.leftArm);
        ninjas$copyBend(model.rightSleeve, model.rightArm);
        ninjas$copyBend(model.leftPants, model.leftLeg);
        ninjas$copyBend(model.rightPants, model.rightLeg);
    }

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void ninjas$applyHandJoint(AvatarRenderState state, HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        ((BendablePart) (Object) (side == HumanoidArm.LEFT ? model.leftArm : model.rightArm)).ninjas$translateToHand(poseStack);
    }

    @Unique
    private static void ninjas$copyBend(ModelPart target, ModelPart source) {
        ((BendablePart) (Object) target).ninjas$copyBend((BendablePart) (Object) source);
    }
}
