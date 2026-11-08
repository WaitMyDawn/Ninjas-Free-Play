package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * A bounded-size, velocity-preserving correction evaluated against the incoming clip.
 */
final class PoseTransition {
    private record Offset(Vector3 position, Vector3 positionVelocity, Vector3 rotation,
                          Vector3 rotationVelocity, Vector3 rotationBranch, double bend, double bendVelocity) {
    }

    private final Map<Bone, Offset> offsets = new EnumMap<>(Bone.class);
    private final double duration;

    PoseTransition(AnimationPose previous, AnimationPose previousBefore, AnimationPose next,
                   AnimationPose nextAfter, double step, double duration) {
        this.duration = duration;
        for (Bone bone : Bone.values()) {
            BonePose a = previous.bone(bone), before = previousBefore.bone(bone);
            BonePose b = next.bone(bone), after = nextAfter.bone(bone);
            Vector3 difference = subtract(a.rotation(), b.rotation());
            Vector3 rotation = principal(difference);
            offsets.put(bone, new Offset(subtract(a.position(), b.position()),
                    subtract(subtract(a.position(), before.position()), subtract(after.position(), b.position())).scale(1 / step),
                    rotation,
                    subtract(principal(subtract(a.rotation(), before.rotation())), subtract(after.rotation(), b.rotation())).scale(1 / step),
                    subtract(difference, rotation), a.bend() - b.bend(),
                    (a.bend() - before.bend() - after.bend() + b.bend()) / step));
        }
    }

    AnimationPose apply(AnimationPose incoming, double elapsed) {
        double amount = duration == 0 ? 1 : Math.max(0, Math.min(1, elapsed / duration));
        double positionBasis = 1 - amount * amount * (3 - 2 * amount);
        double velocityBasis = duration * amount * (1 - amount) * (1 - amount);
        EnumMap<Bone, BonePose> result = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) {
            BonePose target = incoming.bone(bone);
            Offset offset = offsets.get(bone);
            result.put(bone, new BonePose(target.position().add(offset.position().scale(positionBasis))
                    .add(offset.positionVelocity().scale(velocityBasis)),
                    target.rotation().add(offset.rotationBranch()).add(offset.rotation().scale(positionBasis))
                            .add(offset.rotationVelocity().scale(velocityBasis)),
                    boundedBend(target.bend() + offset.bend() * positionBasis + offset.bendVelocity() * velocityBasis)));
        }
        return new AnimationPose(result);
    }

    static AnimationPose continueEndpoint(AnimationPose end, AnimationPose before, double step, double elapsed, double duration) {
        double amount = duration == 0 ? 1 : Math.max(0, Math.min(1, elapsed / duration));
        double distance = duration * (amount - amount * amount / 2) / step;
        EnumMap<Bone, BonePose> result = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) {
            BonePose a = end.bone(bone), b = before.bone(bone);
            result.put(bone, new BonePose(a.position().add(subtract(a.position(), b.position()).scale(distance)),
                    a.rotation().add(subtract(a.rotation(), b.rotation()).scale(distance)),
                    boundedBend(a.bend() + (a.bend() - b.bend()) * distance)));
        }
        return new AnimationPose(result);
    }

    static AnimationPose shiftRoot(AnimationPose pose, Vector3 shift) {
        EnumMap<Bone, BonePose> result = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) result.put(bone, pose.bone(bone));
        BonePose root = pose.bone(Bone.ROOT);
        result.put(Bone.ROOT, new BonePose(root.position(), subtract(root.rotation(), shift), root.bend()));
        return new AnimationPose(result);
    }

    static Vector3 wholeTurns(Vector3 rotation) {
        return subtract(rotation, principal(rotation));
    }

    private static Vector3 principal(Vector3 rotation) {
        return new Vector3(Math.IEEEremainder(rotation.x(), Math.PI * 2),
                Math.IEEEremainder(rotation.y(), Math.PI * 2), Math.IEEEremainder(rotation.z(), Math.PI * 2));
    }

    private static Vector3 subtract(Vector3 a, Vector3 b) {
        return a.add(b.scale(-1));
    }

    private static double boundedBend(double bend) {
        // Hard anatomical limits take precedence over extrapolated velocity at a constraint boundary.
        return Math.max(-BonePose.MAX_BEND, Math.min(BonePose.MAX_BEND, bend));
    }
}
