package yagen.waitmydawn.ninjas_free_play.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.ninjas_free_play.camera.client.CameraAccess;
import yagen.waitmydawn.ninjas_free_play.camera.client.ShoulderCamera;

@Mixin(Camera.class)
public abstract class CameraMixin implements CameraAccess {
    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Override
    public void ninjas$setCameraPosition(Vec3 position) {
        setPosition(position);
    }

    @Override
    public void ninjas$setCameraRotation(float yaw, float pitch, float roll) {
        setRotation(yaw, pitch);
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void ninjas$shoulderView(Level level, Entity entity, boolean detached, boolean mirrored, float partial, CallbackInfo ci) {
        ShoulderCamera.setup((Camera) (Object) this, level, entity, detached, mirrored, partial);
    }
}
