package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(Gui.class)
public abstract class ShoulderCrosshairMixin {
    @Unique
    private boolean ninjas$reticleTranslated;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void ninjas$projectedCrosshair(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        ninjas$reticleTranslated = false;
        if (!ShoulderCamera.active()) return;
        var point = ShoulderCamera.reticle();
        if (!point.visible()) {
            ci.cancel();
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) (point.x() * graphics.guiWidth() / 2), (float) (-point.y() * graphics.guiHeight() / 2));
        ninjas$reticleTranslated = true;
    }

    @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean ninjas$showShoulderCrosshair(boolean firstPerson) {
        return firstPerson || ShoulderCamera.active();
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void ninjas$restoreCrosshairTransform(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo ci) {
        if (ninjas$reticleTranslated) {
            graphics.pose().popMatrix();
            ninjas$reticleTranslated = false;
        }
    }
}
