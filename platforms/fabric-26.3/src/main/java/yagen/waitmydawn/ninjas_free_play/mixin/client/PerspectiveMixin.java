package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(Minecraft.class)
public abstract class PerspectiveMixin {
    @Inject(method = "pick", at = @At("HEAD"))
    private void ninjas$alignFixedAim(float partial, CallbackInfo ci) {
        ShoulderCamera.alignFixed(partial);
    }

    @Inject(method = "pick", at = @At("TAIL"))
    private void ninjas$updateAimTarget(float partial, CallbackInfo ci) {
        ShoulderCamera.updateAimTarget(partial);
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"))
    private CameraType ninjas$cyclePerspective(CameraType current, Operation<CameraType> original) {
        return ShoulderCamera.cycle(current, original.call(current));
    }
}
