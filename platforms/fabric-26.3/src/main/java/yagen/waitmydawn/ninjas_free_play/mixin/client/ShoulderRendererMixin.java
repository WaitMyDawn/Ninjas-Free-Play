package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(GameRenderer.class)
public abstract class ShoulderRendererMixin {
    @Invoker("bobHurt")
    protected abstract void ninjas$bobHurt(CameraRenderState camera, PoseStack pose);

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void ninjas$stableShoulderProjection(GameRenderer renderer, CameraRenderState camera, PoseStack pose, Operation<Void> original) {
        if (!ShoulderCamera.active()) original.call(renderer, camera, pose);
    }

    @Inject(method = "extract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V"))
    private void ninjas$projectInteractionPoint(DeltaTracker tracker, boolean renderLevel, CallbackInfo ci) {
        var game = ((GameRenderer) (Object) this).gameRenderState();
        if (!game.shouldRenderLevel || !ShoulderCamera.projectsReticle()) return;
        var level = game.levelRenderState;
        var camera = level.cameraRenderState;
        var player = level.playerRenderState;
        var hurt = new PoseStack();
        ninjas$bobHurt(camera, hurt);
        var projection = new Matrix4f(camera.projectionMatrix).mul(hurt.last().pose());

        // The HUD is extracted before renderLevel applies vanilla's portal/nausea projection warp.
        float scale = game.optionsRenderState.screenEffectScale;
        float intensity = Math.max(player.portalEffectIntensity, player.nauseaEffectIntensity) * scale * scale;
        if (intensity > 0) {
            float squeeze = 5 / (intensity * intensity + 5) - intensity * .04f;
            float angle = player.spinningEffectAngle * Mth.DEG_TO_RAD;
            float diagonal = Mth.SQRT_OF_TWO / 2;
            projection.rotate(angle, 0, diagonal, diagonal)
                    .scale(1 / (squeeze * squeeze), 1, 1)
                    .rotate(-angle, 0, diagonal, diagonal);
        }
        ShoulderCamera.captureProjection(camera, projection);
    }
}
