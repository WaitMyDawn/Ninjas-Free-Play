package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.EnumMap;

/**
 * Contact offsets fade independently of clip playback, including when their owning action is interrupted.
 */
public final class PoseCorrectionSmoother {
    private record Offset(VectorSmoother position, RotationSmoother rotation, VectorSmoother bend) {
        Offset() {
            this(new VectorSmoother(), new RotationSmoother(), new VectorSmoother());
        }
    }

    private final EnumMap<Bone, Offset> offsets = new EnumMap<>(Bone.class);

    public AnimationPose sample(double now, AnimationPose source, AnimationPose target, double duration) {
        EnumMap<Bone, BonePose> result = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) {
            BonePose from = source.bone(bone), to = target.bone(bone);
            if (bone == Bone.ROOT) {
                result.put(bone, from);
                continue;
            }
            Offset offset = offsets.computeIfAbsent(bone, key -> new Offset());
            Vector3 position = offset.position.sample(now, to.position().add(from.position().scale(-1)), duration);
            Rotation delta = Rotation.fromEuler(to.rotation()).multiply(Rotation.fromEuler(from.rotation()).inverse());
            Rotation angular = offset.rotation.sample(now, delta, duration);
            Vector3 euler = angular.multiply(Rotation.fromEuler(from.rotation())).toEuler();
            // Equivalent Euler branches must retain the source track's authored winding.
            Vector3 continuous = new Vector3(near(euler.x(), from.rotation().x()), near(euler.y(), from.rotation().y()), near(euler.z(), from.rotation().z()));
            double bend = from.bend() + offset.bend.sample(now, new Vector3(to.bend() - from.bend(), 0, 0), duration).x();
            result.put(bone, new BonePose(from.position().add(position), continuous, Math.max(-BonePose.MAX_BEND, Math.min(BonePose.MAX_BEND, bend))));
        }
        return new AnimationPose(result);
    }

    private static double near(double angle, double reference) {
        return reference + Math.IEEEremainder(angle - reference, 2 * Math.PI);
    }
}
