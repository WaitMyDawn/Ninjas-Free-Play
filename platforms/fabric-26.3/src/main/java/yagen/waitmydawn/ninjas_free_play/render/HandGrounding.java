package yagen.waitmydawn.ninjas_free_play.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.config.ClientConfigs;

import java.util.Map;
import java.util.WeakHashMap;

public final class HandGrounding {
    private record State(GroundContactSolver solver, ContactOffsetPlayer offset) {
    }

    private static final Map<AbstractClientPlayer, State> SOLVERS = new WeakHashMap<>();

    private HandGrounding() {
    }

    public static void clear() {
        SOLVERS.clear();
    }

    public static void clear(AbstractClientPlayer entity) {
        SOLVERS.remove(entity);
    }

    public static Vector3 offset(PlayerAnimationContext context, float partialTick, Rotation base) {
        var weights = PlayerAnimations.contacts(context.entity, partialTick);
        var frames = HumanoidRig.evaluateBody(context.sample.pose(), context.slim);
        Vec3 origin = context.entity.getPosition(partialTick);
        var root = context.sample.weightedRoot();
        GroundContactSolver.Hand left = hand(context, origin, frames.get(Bone.LEFT_ARM), Bone.LEFT_ARM, weights.left(), root, base);
        GroundContactSolver.Hand right = hand(context, origin, frames.get(Bone.RIGHT_ARM), Bone.RIGHT_ARM, weights.right(), root, base);
        var state = SOLVERS.computeIfAbsent(context.entity, entity -> new State(new GroundContactSolver(), new ContactOffsetPlayer()));
        double scale = context.entity.getScale();
        Vector3 target = state.solver().solve(left, right, scale);
        boolean supported = left.weight() > 0 && left.surface() != null || right.weight() > 0 && right.surface() != null;
        Vector3 offset = state.offset().sample((context.entity.tickCount + (double) partialTick) / 20,
                target, supported, ClientConfigs.ANIMATION_TRANSITION_SECONDS.get());
        double horizontal = Math.hypot(offset.x(), offset.z());
        double bound = horizontal > 0.45 * scale ? 0.45 * scale / horizontal : 1;
        return new Vector3(offset.x() * bound, Math.max(-0.8 * scale, Math.min(0.8 * scale, offset.y())), offset.z() * bound);
    }

    private static GroundContactSolver.Hand hand(PlayerAnimationContext context, Vec3 origin, HumanoidRig.Frame frame,
                                                 Bone bone, double weight, BonePose root, Rotation base) {
        double bend = context.sample.pose().bone(bone).bend();
        var deform = new LimbDeformer(bend, HumanoidRig.bendCenter(bone), 10);
        Vector3 wrist = frame.transform(deform.position(HumanoidRig.palmAnchor(bone, context.slim)));
        Vector3 relative = PlayerModelSpace.offset(wrist, root.position(), context.rootLocalRotation, base,
                context.heightScale, context.entity.getScale());
        Vector3 position = new Vector3(origin.x + relative.x(), origin.y + relative.y(), origin.z + relative.z());
        weight *= context.sample.weight();
        Vector3 surface = null;
        if (weight > 0) {
            double scale = context.entity.getScale();
            Vec3 start = new Vec3(position.x(), position.y() + 0.6 * scale, position.z());
            Vec3 end = new Vec3(position.x(), position.y() - 1.0 * scale, position.z());
            var hit = context.entity.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context.entity));
            double y = hit.getLocation().y;
            if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP
                    && y <= origin.y + 0.25 * scale && y >= origin.y - 1.25 * scale
                    && Math.abs(y - position.y()) <= 0.8 * scale) {
                surface = new Vector3(hit.getLocation().x, y + 0.015 * scale, hit.getLocation().z);
            }
        }
        return new GroundContactSolver.Hand(position, surface, weight);
    }
}
