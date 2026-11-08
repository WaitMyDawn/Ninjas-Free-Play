package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.render.BendablePart;
import yagen.waitmydawn.ninjas_free_play.render.PlayerPoseAdapter;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void ninjas$applyPose(LivingEntity entity, float swing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        PlayerPoseAdapter.apply((HumanoidModel<?>) (Object) this, entity);
    }

    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void ninjas$applyHandJoint(HumanoidArm side, PoseStack poseStack, CallbackInfo ci) {
        ((BendablePart) (Object) (side == HumanoidArm.LEFT ? leftArm : rightArm)).ninjas$translateToHand(poseStack);
    }
}
