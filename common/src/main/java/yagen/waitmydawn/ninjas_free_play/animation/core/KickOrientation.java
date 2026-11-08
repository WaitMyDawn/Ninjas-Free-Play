package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * A stable terminal foot axis keeps the wind-up free to retract while the strike points at its target.
 */
public final class KickOrientation {
    private KickOrientation() {
    }

    public static Bone leadingLeg(AnimationPose terminal) {
        return Math.abs(terminal.bone(Bone.LEFT_LEG).bend()) <= Math.abs(terminal.bone(Bone.RIGHT_LEG).bend())
                ? Bone.LEFT_LEG : Bone.RIGHT_LEG;
    }

    public static Vector3 footAxis(AnimationPose pose, Bone leg) {
        var deform = new LimbDeformer(pose.bone(leg).bend(), 6, 10);
        Vector3 tangent = deform.position(new Vector3(0, 12, 0)).add(deform.position(new Vector3(0, 11.9, 0)).scale(-1));
        return Rotation.fromEuler(pose.bone(Bone.ROOT).rotation())
                .multiply(HumanoidRig.evaluateBody(pose, false).get(leg).rotation()).apply(tangent).normalized();
    }

    public static Rotation facing(AnimationPose terminal, LaunchOrientation direction) {
        return direction.worldModelRotation().multiply(Rotation.between(footAxis(terminal, leadingLeg(terminal)), new Vector3(0, -1, 0)));
    }
}
