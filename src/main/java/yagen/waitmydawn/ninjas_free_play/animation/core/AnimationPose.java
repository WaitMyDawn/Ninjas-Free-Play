package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class AnimationPose {
    public static final AnimationPose IDENTITY = new AnimationPose(Map.of());
    private final Map<Bone, BonePose> bones;

    public AnimationPose(Map<Bone, BonePose> bones) {
        EnumMap<Bone, BonePose> copy = new EnumMap<>(Bone.class);
        bones.forEach((bone, pose) -> copy.put(Objects.requireNonNull(bone), Objects.requireNonNull(pose)));
        this.bones = Map.copyOf(copy);
    }

    public BonePose bone(Bone bone) {
        return bones.getOrDefault(bone, BonePose.IDENTITY);
    }

    public AnimationPose blend(AnimationPose other, double weight) {
        EnumMap<Bone, BonePose> result = new EnumMap<>(Bone.class);
        for (Bone bone : Bone.values()) {
            result.put(bone, bone(bone).blend(other.bone(bone), weight));
        }
        return new AnimationPose(result);
    }
}
