package yagen.waitmydawn.ninjas_free_play.animation.core;

public record Rotation(double x, double y, double z, double w) {
    public static final Rotation IDENTITY = new Rotation(0, 0, 0, 1);

    public Rotation {
        double norm = x * x + y * y + z * z + w * w;
        if (!Double.isFinite(norm) || Math.abs(norm - 1) > 1e-6) {
            throw new IllegalArgumentException("Rotation must be a unit quaternion");
        }
    }

    public static Rotation fromEuler(Vector3 radians) {
        double sx = Math.sin(radians.x() / 2), cx = Math.cos(radians.x() / 2);
        double sy = Math.sin(radians.y() / 2), cy = Math.cos(radians.y() / 2);
        double sz = Math.sin(radians.z() / 2), cz = Math.cos(radians.z() / 2);
        return new Rotation(sx * cy * cz - cx * sy * sz, cx * sy * cz + sx * cy * sz,
                cx * cy * sz - sx * sy * cz, cx * cy * cz + sx * sy * sz);
    }

    public Rotation multiply(Rotation other) {
        return new Rotation(w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z);
    }

    public Rotation inverse() {
        return new Rotation(-x, -y, -z, w);
    }

    public Rotation negated() {
        return new Rotation(-x, -y, -z, -w);
    }

    public double dot(Rotation other) {
        return x * other.x + y * other.y + z * other.z + w * other.w;
    }

    public static Rotation between(Vector3 from, Vector3 to) {
        Vector3 a = from.normalized(), b = to.normalized();
        double dot = Math.max(-1, Math.min(1, a.x() * b.x() + a.y() * b.y() + a.z() * b.z()));
        if (dot > 1 - 1e-12) return IDENTITY;
        Vector3 cross = new Vector3(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x());
        if (dot < -1 + 1e-12) {
            Vector3 reference = Math.abs(a.x()) < 0.8 ? new Vector3(1, 0, 0) : new Vector3(0, 1, 0);
            cross = new Vector3(a.y() * reference.z() - a.z() * reference.y(),
                    a.z() * reference.x() - a.x() * reference.z(), a.x() * reference.y() - a.y() * reference.x());
            return fromRotationVector(cross.normalized().scale(Math.PI));
        }
        return fromRotationVector(cross.normalized().scale(Math.acos(dot)));
    }

    public static Rotation fromRotationVector(Vector3 vector) {
        double angle = Math.sqrt(vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
        if (angle < 1e-12) return new Rotation(vector.x() / 2, vector.y() / 2, vector.z() / 2, 1);
        double factor = Math.sin(angle / 2) / angle;
        return new Rotation(vector.x() * factor, vector.y() * factor, vector.z() * factor, Math.cos(angle / 2));
    }

    /**
     * The caller chooses the quaternion hemisphere when a continuous logarithm branch is required.
     */
    public Vector3 rotationVector() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-12) return new Vector3(x * 2, y * 2, z * 2);
        double factor = 2 * Math.atan2(length, w) / length;
        return new Vector3(x * factor, y * factor, z * factor);
    }

    public Vector3 apply(Vector3 point) {
        double tx = 2 * (y * point.z() - z * point.y());
        double ty = 2 * (z * point.x() - x * point.z());
        double tz = 2 * (x * point.y() - y * point.x());
        return new Vector3(point.x() + w * tx + y * tz - z * ty,
                point.y() + w * ty + z * tx - x * tz,
                point.z() + w * tz + x * ty - y * tx);
    }

    public Vector3 toEuler() {
        return new Vector3(Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y)),
                Math.asin(Math.max(-1, Math.min(1, 2 * (w * y - z * x)))),
                Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z)));
    }
}
