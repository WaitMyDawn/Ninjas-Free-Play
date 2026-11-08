package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(Minecraft.class)
public abstract class PerspectiveMixin {
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"))
    private CameraType ninjas$cyclePerspective(CameraType current, Operation<CameraType> original) {
        return ShoulderCamera.cycle(current, original.call(current));
    }
}
