package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Reorients a fixed-length limb endpoint onto a reachable plane without moving its joint pivot.
 */
public final class LimbPlaneConstraint {
    private LimbPlaneConstraint() {
    }

    public static Vector3 endpoint(Vector3 current, Vector3 outwardNormal, double planeFromPivot) {
        return endpoint(current, outwardNormal, planeFromPivot, current);
    }

    public static Vector3 endpoint(Vector3 current, Vector3 outwardNormal, double planeFromPivot, Vector3 preferred) {
        Vector3 normal = outwardNormal.normalized();
        double lengthSquared = dot(current, current);
        if (!Double.isFinite(planeFromPivot) || planeFromPivot * planeFromPivot > lengthSquared || lengthSquared < 1e-12)
            return current;
        Vector3 tangent = preferred.add(normal.scale(-dot(preferred, normal)));
        if (dot(tangent, tangent) < 1e-12) {
            Vector3 reference = Math.abs(normal.y()) < .9 ? new Vector3(0, 1, 0) : new Vector3(1, 0, 0);
            tangent = reference.add(normal.scale(-dot(reference, normal)));
        }
        return normal.scale(planeFromPivot).add(tangent.normalized().scale(Math.sqrt(Math.max(0, lengthSquared - planeFromPivot * planeFromPivot))));
    }

    private static double dot(Vector3 a, Vector3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }
}
