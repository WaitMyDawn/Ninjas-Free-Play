package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(GameRenderer.class)
public abstract class ShoulderRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ninjas$prepareFixedCamera(DeltaTracker tracker, CallbackInfo ci) {
        ShoulderCamera.prepareFixed(tracker.getGameTimeDeltaPartialTick(true));
    }

    @Inject(method = "pick(F)V", at = @At("HEAD"))
    private void ninjas$alignFixedAim(float partial, CallbackInfo ci) {
        ShoulderCamera.alignFixed(partial);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void ninjas$stableShoulderProjection(GameRenderer renderer, PoseStack pose, float partial, Operation<Void> original) {
        if (!ShoulderCamera.active()) original.call(renderer, pose, partial);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"))
    private void ninjas$projectInteractionPoint(GameRenderer renderer, Matrix4f projection, Operation<Void> original) {
        original.call(renderer, projection);
        ShoulderCamera.captureProjection(renderer.getMainCamera(), projection);
    }
}
