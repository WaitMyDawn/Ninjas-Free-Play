package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Foot contact uses the visible mesh extremity rather than an ankle or sole center.
 */
public final class FootSupport {
    private FootSupport() {
    }

    public static Vector3 point(AnimationPose pose, Bone leg, Vector3 localNormal, double inflation) {
        if (leg != Bone.LEFT_LEG && leg != Bone.RIGHT_LEG)
            throw new IllegalArgumentException("Foot support requires a leg");
        var frame = HumanoidRig.evaluateBody(pose, false).get(leg);
        var deformer = new LimbDeformer(pose.bone(leg).bend(), 6, 10);
        Vector3 result = null;
        double minimum = Double.POSITIVE_INFINITY;
        for (double x : new double[]{-2 - inflation, 2 + inflation})
            for (double z : new double[]{-2 - inflation, 2 + inflation}) {
                Vector3 candidate = frame.transform(deformer.position(new Vector3(x, 12 + inflation, z)));
                double projection = candidate.x() * localNormal.x() + candidate.y() * localNormal.y() + candidate.z() * localNormal.z();
                if (projection < minimum) {
                    minimum = projection;
                    result = candidate;
                }
            }
        return result;
    }

    public static AnimationPose fit(AnimationPose pose, Bone leg, Vector3 normal, double plane, double inflation) {
        var bones = new java.util.EnumMap<Bone, BonePose>(Bone.class);
        for (Bone bone : Bone.values()) bones.put(bone, pose.bone(bone));
        for (int i = 0; i < 5; i++) {
            var current = new AnimationPose(bones);
            var frame = HumanoidRig.evaluateBody(current, false).get(leg);
            Vector3 end = point(current, leg, normal, inflation).add(frame.position().scale(-1));
            double distance = plane - frame.position().x() * normal.x() - frame.position().y() * normal.y() - frame.position().z() * normal.z();
            Vector3 target = LimbPlaneConstraint.endpoint(end, normal, distance);
            var part = bones.get(leg);
            Rotation rotation = Rotation.between(end, target).multiply(frame.rotation());
            bones.put(leg, new BonePose(part.position(), rotation.toEuler(), part.bend()));
        }
        return new AnimationPose(bones);
    }
}
