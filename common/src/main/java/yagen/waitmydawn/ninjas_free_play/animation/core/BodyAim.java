package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Aim changes torso twist without replacing the authored body axis or its inclination.
 */
public final class BodyAim {
    private BodyAim() {
    }

    public static Rotation align(Rotation body, Vector3 desiredForward) {
        Vector3 up = body.apply(new Vector3(0, -1, 0));
        Vector3 current = body.apply(new Vector3(0, 0, -1));
        Vector3 target = desiredForward.add(up.scale(-dot(desiredForward, up)));
        if (dot(target, target) < 1e-10) return body;
        target = target.normalized();
        Vector3 cross = new Vector3(current.y() * target.z() - current.z() * target.y(),
                current.z() * target.x() - current.x() * target.z(), current.x() * target.y() - current.y() * target.x());
        double angle = Math.atan2(dot(cross, up), dot(current, target));
        return Rotation.fromRotationVector(up.scale(angle)).multiply(body);
    }

    private static double dot(Vector3 a, Vector3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }
}
