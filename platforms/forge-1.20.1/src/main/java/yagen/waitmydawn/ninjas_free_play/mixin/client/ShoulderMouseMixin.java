package yagen.waitmydawn.ninjas_free_play.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(MouseHandler.class)
public abstract class ShoulderMouseMixin {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void ninjas$turnIndependentOrbit(LocalPlayer player, double horizontal, double vertical, Operation<Void> original) {
        if (!ShoulderCamera.turn(player, horizontal, vertical)) original.call(player, horizontal, vertical);
    }
}
