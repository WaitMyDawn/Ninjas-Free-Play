package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Fits the curved arm to a head-relative mouth target, leaving the torso and neck pivots intact.
 */
public final class ConsumptionArmPose {
    private ConsumptionArmPose() {
    }

    public static BonePose solve(AnimationPose pose, Bone arm, Rotation headAim, boolean slim, double motion) {
        if (arm != Bone.LEFT_ARM && arm != Bone.RIGHT_ARM)
            throw new IllegalArgumentException("Consumption requires an arm");
        var frames = HumanoidRig.evaluateBody(pose, slim);
        Vector3 mouth = frames.get(Bone.HEAD).transform(Vector3.ZERO).add(headAim.apply(new Vector3(0, -3.4, -5.1 + motion)));
        Vector3 direction = mouth.add(frames.get(arm).position().scale(-1));
        double distance = length(direction);
        Vector3 palm = HumanoidRig.palmAnchor(arm, slim);
        double low = 0, high = BonePose.MAX_BEND;
        for (int i = 0; i < 40; i++) {
            double middle = (low + high) / 2;
            if (length(new LimbDeformer(-middle, 4, 10).position(palm)) > distance) low = middle;
            else high = middle;
        }
        double bend = -(low + high) / 2;
        Vector3 curved = new LimbDeformer(bend, 4, 10).position(palm);
        Vector3 local = frames.get(Bone.BODY).rotation().inverse().apply(direction);
        double side = arm == Bone.LEFT_ARM ? 1 : -1;
        Rotation preferred = Rotation.fromEuler(new Vector3(-.8, 0, -side * .25));
        Rotation rotation = Rotation.between(preferred.apply(curved), local).multiply(preferred);
        return new BonePose(pose.bone(arm).position(), frames.get(Bone.BODY).rotation().multiply(rotation).toEuler(), bend);
    }

    private static double length(Vector3 v) {
        return Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
    }
}
