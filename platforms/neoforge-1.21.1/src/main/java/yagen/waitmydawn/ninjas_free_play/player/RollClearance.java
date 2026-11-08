package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Checks the handspring envelope along the same Y/major-axis/minor-axis path as vanilla travel.
 */
public final class RollClearance {
    private RollClearance() {
    }

    public static boolean canFollow(Player player, Vec3 resolved) {
        AABB box = player.getDimensions(Pose.STANDING).scale(1, 1.25f).makeBoundingBox(player.position());
        if (!clear(player, box.expandTowards(0, resolved.y, 0))) return false;
        box = box.move(0, resolved.y, 0);
        if (Math.abs(resolved.x) < Math.abs(resolved.z)) {
            if (!clear(player, box.expandTowards(0, 0, resolved.z))) return false;
            return clear(player, box.move(0, 0, resolved.z).expandTowards(resolved.x, 0, 0));
        }
        if (!clear(player, box.expandTowards(resolved.x, 0, 0))) return false;
        return clear(player, box.move(resolved.x, 0, 0).expandTowards(0, 0, resolved.z));
    }

    private static boolean clear(Player player, AABB box) {
        return player.level().noCollision(player, box.deflate(1e-7));
    }
}
