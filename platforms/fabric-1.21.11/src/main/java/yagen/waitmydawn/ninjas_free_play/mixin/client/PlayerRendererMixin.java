package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import yagen.waitmydawn.ninjas_free_play.render.BendablePart;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.render.AvatarAnimationState;
import yagen.waitmydawn.ninjas_free_play.render.PlayerAnimationContext;
import yagen.waitmydawn.ninjas_free_play.render.PlayerAnimations;
import yagen.waitmydawn.ninjas_free_play.render.PlayerPoseAdapter;

@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void ninjas$extractAnimation(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        PlayerAnimationContext animation = null;
        boolean compact = false;
        if (avatar instanceof AbstractClientPlayer player) {
            compact = MovementPhysics.low(player);
            if (PlayerAnimations.isActive(player, partialTick)) animation = new PlayerAnimationContext(player, partialTick);
        }
        ((AvatarAnimationState) state).ninjas$setAnimation(animation, compact);
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void ninjas$compactOffset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        if (((AvatarAnimationState) state).ninjas$compact()) cir.setReturnValue(Vec3.ZERO);
    }

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void ninjas$resetFirstPersonSleeve(PoseStack pose, SubmitNodeCollector collector, int light, Identifier texture,
                                               ModelPart arm, boolean showSleeve, CallbackInfo ci) {
        PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
        ((BendablePart) (Object) (arm == model.leftArm ? model.leftSleeve : model.rightSleeve)).ninjas$setBend(0, 0);
    }

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("TAIL"))
    private void ninjas$applyRoot(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        var animation = ((AvatarAnimationState) state).ninjas$animation();
        if (animation != null) PlayerPoseAdapter.applyRoot(animation, poseStack);
    }
}
