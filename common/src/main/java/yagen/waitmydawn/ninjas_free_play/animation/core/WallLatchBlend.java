package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.EnumMap;

/**
 * A cyclic directional blend retains clip entry corrections and does not restart the breathing loop.
 */
public final class WallLatchBlend {
    private WallLatchBlend() {
    }

    public static AnimationPose adapt(AnimationPose source, AnimationPose front, AnimationPose left,
                                      AnimationPose back, AnimationPose right, double yaw) {
        double wrapped = Math.IEEEremainder(yaw, 2 * Math.PI);
        double sector = Math.abs(wrapped) / (Math.PI / 2);
        AnimationPose side = wrapped < 0 ? left : right;
        AnimationPose from = sector <= 1 ? front : side, to = sector <= 1 ? side : back;
        double weight = sector <= 1 ? sector : sector - 1;
        weight = weight * weight * (3 - 2 * weight);
        EnumMap<Bone, BonePose> bones = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) {
            var actual = source.bone(bone);
            var reference = front.bone(bone);
            if (bone == Bone.ROOT) {
                bones.put(bone, actual);
                continue;
            }
            var a = from.bone(bone);
            var b = to.bone(bone);
            Rotation start = Rotation.fromEuler(a.rotation()), end = Rotation.fromEuler(b.rotation());
            Rotation delta = end.multiply(start.inverse());
            if (delta.w() < 0) delta = delta.negated();
            Rotation desired = Rotation.fromRotationVector(delta.rotationVector().scale(weight)).multiply(start);
            Rotation corrected = desired.multiply(Rotation.fromEuler(reference.rotation()).inverse()).multiply(Rotation.fromEuler(actual.rotation()));
            Vector3 position = actual.position().add(a.position().lerp(b.position(), weight)).add(reference.position().scale(-1));
            double bend = actual.bend() + a.bend() + (b.bend() - a.bend()) * weight - reference.bend();
            bones.put(bone, new BonePose(position, corrected.toEuler(), Math.max(-BonePose.MAX_BEND, Math.min(BonePose.MAX_BEND, bend))));
        }
        return new AnimationPose(bones);
    }
}
