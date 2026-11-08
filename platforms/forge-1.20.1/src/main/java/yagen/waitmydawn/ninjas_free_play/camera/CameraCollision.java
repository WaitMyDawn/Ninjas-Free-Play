package yagen.waitmydawn.ninjas_free_play.camera;

import net.minecraft.world.entity.Entity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/**
 * World-query adapter with no client classes; usable by dedicated-server geometry tests.
 */
public final class CameraCollision {
    private CameraCollision() {
    }

    public static double fraction(BlockGetter level, Entity entity, Vector3 from, Vector3 to, double radius) {
        if (!Double.isFinite(radius) || radius < 0 || radius > 16)
            throw new IllegalArgumentException("Invalid camera radius");
        var start = new Vec3(from.x(), from.y(), from.z());
        var delta = new Vec3(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        double length = delta.length();
        if (length < 1e-8) return 1;
        var visited = new LongOpenHashSet();
        var position = new BlockPos.MutableBlockPos();
        var context = CollisionContext.of(entity);
        double[] allowed = {1};
        int neighbors = (int) Math.ceil(radius) + 1;
        // Expand the actual visual boxes, rather than sampling rays that can miss a thin shape between corners.
        // Neighbor cells also cover vanilla shapes protruding beyond their owning block.
        BlockGetter.traverseBlocks(start, start.add(delta), visited, (checked, center) -> {
            for (int x = center.getX() - neighbors; x <= center.getX() + neighbors; x++)
                for (int y = center.getY() - neighbors; y <= center.getY() + neighbors; y++)
                    for (int z = center.getZ() - neighbors; z <= center.getZ() + neighbors; z++) {
                        if (!checked.add(BlockPos.asLong(x, y, z))) continue;
                        position.set(x, y, z);
                        var shape = level.getBlockState(position).getVisualShape(level, position, context);
                        for (var local : shape.toAabbs()) {
                            var box = local.move(position).inflate(radius);
                            if (box.contains(start)) {
                                allowed[0] = 0;
                                return Double.valueOf(0);
                            }
                            var hit = box.clip(start, start.add(delta));
                            if (hit.isPresent()) {
                                double along = hit.get().subtract(start).dot(delta) / (length * length);
                                allowed[0] = Math.min(allowed[0], Math.max(0, along - .02 / length));
                            }
                        }
                    }
            return null;
        }, checked -> allowed[0]);
        return Math.max(0, Math.min(1, allowed[0]));
    }
}
