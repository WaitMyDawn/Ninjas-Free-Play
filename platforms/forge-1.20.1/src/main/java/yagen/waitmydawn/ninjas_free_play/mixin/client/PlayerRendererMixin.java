package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;
import yagen.waitmydawn.ninjas_free_play.render.PlayerAnimationContext;
import yagen.waitmydawn.ninjas_free_play.render.PlayerAnimations;
import yagen.waitmydawn.ninjas_free_play.render.PlayerPoseAdapter;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void ninjas$compactOffset(AbstractClientPlayer entity, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (MovementPhysics.low(entity)) cir.setReturnValue(Vec3.ZERO);
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void ninjas$renderScope(AbstractClientPlayer entity, float yaw, float partialTick, PoseStack poseStack,
                                    MultiBufferSource buffer, int light, Operation<Void> original) {
        PlayerRenderer renderer = (PlayerRenderer) (Object) this;
        if (!PlayerAnimations.isActive(entity, partialTick)) {
            original.call(entity, yaw, partialTick, poseStack, buffer, light);
            return;
        }
        try (PlayerAnimationContext ignored = new PlayerAnimationContext(entity, renderer.getModel(), partialTick)) {
            original.call(entity, yaw, partialTick, poseStack, buffer, light);
        }
    }

    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
    private void ninjas$applyRoot(AbstractClientPlayer player, PoseStack poseStack, float partialTick, CallbackInfo ci) {
        PlayerPoseAdapter.applyRoot(player, poseStack);
    }
}
