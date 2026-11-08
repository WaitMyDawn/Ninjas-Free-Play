package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Speed controls the body axis; the authored limb pose remains in the source animation.
 */
public final class GlideOrientation {
    private GlideOrientation() {
    }

    public static double inclinationDegrees(double blocksPerSecond) {
        if (!Double.isFinite(blocksPerSecond) || blocksPerSecond < 0)
            throw new IllegalArgumentException("Invalid glide speed");
        return 30 + 30 * Math.pow(2, -blocksPerSecond / 8);
    }

    public static Rotation correction(AnimationPose source, double blocksPerSecond) {
        Rotation root = Rotation.fromEuler(source.bone(Bone.ROOT).rotation());
        Rotation body = Rotation.fromEuler(source.bone(Bone.BODY).rotation());
        Vector3 axis = root.multiply(body).apply(new Vector3(0, -1, 0));
        double angle = Math.toRadians(inclinationDegrees(blocksPerSecond));
        return Rotation.between(axis, new Vector3(0, -Math.sin(angle), -Math.cos(angle)));
    }
}
