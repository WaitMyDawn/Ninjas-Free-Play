package yagen.waitmydawn.ninjas_free_play.camera.client;

import net.minecraft.world.phys.Vec3;

public interface CameraAccess {
    void ninjas$setCameraPosition(Vec3 position);

    void ninjas$setCameraRotation(float yaw, float pitch, float roll);
}
