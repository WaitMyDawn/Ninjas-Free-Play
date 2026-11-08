package yagen.waitmydawn.ninjas_free_play.camera;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Camera queries select a facing target; actual interaction remains the reach-limited player-eye query.
 */
public final class CameraTargeting {
    private CameraTargeting() {
    }

    public static HitResult pick(Entity source, Vec3 origin, Vec3 direction, double distance) {
        Vec3 end = origin.add(direction.scale(distance));
        var block = source.level().clip(new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, source));
        double limit = origin.distanceToSqr(block.getLocation());
        var actor = ProjectileUtil.getEntityHitResult(source, origin, end, new AABB(origin, end).inflate(1),
                other -> !other.isSpectator() && other.isPickable(), limit);
        return actor != null && origin.distanceToSqr(actor.getLocation()) < limit ? actor : block;
    }
}
