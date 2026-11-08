package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(GameRenderer.class)
public abstract class ShoulderRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ninjas$prepareFixedCamera(float partial, long finishNanoTime, PoseStack pose, CallbackInfo ci) {
        ShoulderCamera.prepareFixed(partial);
    }

    @Inject(method = "pick(F)V", at = @At("HEAD"))
    private void ninjas$alignFixedAim(float partial, CallbackInfo ci) {
        ShoulderCamera.alignFixed(partial);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private void ninjas$stableShoulderProjection(GameRenderer renderer, PoseStack pose, float partial, Operation<Void> original) {
        if (!ShoulderCamera.active()) original.call(renderer, pose, partial);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V"))
    private void ninjas$projectInteractionPoint(LevelRenderer renderer, PoseStack pose, float partial, long finish,
                                               boolean outline, Camera camera, GameRenderer gameRenderer,
                                               LightTexture light, Matrix4f projection, Operation<Void> original) {
        // In 1.20.1 projection setup precedes Camera.setup, which clears the automatic reticle for the frame.
        ShoulderCamera.captureProjection(camera, projection, pose.last().pose());
        original.call(renderer, pose, partial, finish, outline, camera, gameRenderer, light, projection);
    }
}
