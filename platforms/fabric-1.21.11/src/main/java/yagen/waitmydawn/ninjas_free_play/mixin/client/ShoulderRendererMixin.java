package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(GameRenderer.class)
public abstract class ShoulderRendererMixin {
    @Inject(method = "pick(F)V", at = @At("HEAD"))
    private void ninjas$alignFixedAim(float partial, CallbackInfo ci) {
        ShoulderCamera.alignFixed(partial);
    }

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void ninjas$updateAimTarget(float partial, CallbackInfo ci) {
        ShoulderCamera.updateAimTarget(partial);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void ninjas$stableShoulderProjection(GameRenderer renderer, PoseStack pose, float partial, Operation<Void> original) {
        if (!ShoulderCamera.active()) original.call(renderer, pose, partial);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/PerspectiveProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice ninjas$projectInteractionPoint(PerspectiveProjectionMatrixBuffer buffer, Matrix4f projection,
                                                         Operation<GpuBufferSlice> original) {
        ShoulderCamera.captureProjection(((GameRenderer) (Object) this).getMainCamera(), projection);
        return original.call(buffer, projection);
    }
}
