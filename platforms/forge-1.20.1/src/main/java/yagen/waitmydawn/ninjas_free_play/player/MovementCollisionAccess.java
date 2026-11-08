package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.world.phys.Vec3;

/**
 * Read-only access to the version adapter's vanilla movement/step resolution.
 */
public interface MovementCollisionAccess {
    Vec3 ninjas$resolveMovement(Vec3 movement);
}
