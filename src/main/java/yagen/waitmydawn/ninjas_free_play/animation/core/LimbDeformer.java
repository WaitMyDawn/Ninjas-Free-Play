package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Constant-curvature bend band with rigid upper and lower sections, measured in model pixels.
 */
public record LimbDeformer(double angle, double center, double width) {
    public LimbDeformer {
        if (!Double.isFinite(angle) || Math.abs(angle) > BonePose.MAX_BEND
                || !Double.isFinite(center) || !Double.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException("Invalid limb bend profile");
        }
    }

    public double angleAt(double y) {
        return angle * Math.max(0, Math.min(1, (y - center + width / 2) / width));
    }

    public Vector3 position(Vector3 point) {
        if (Math.abs(angle) < 1e-9 || point.y() <= center - width / 2) return point;
        double start = center - width / 2;
        double distance = Math.min(width, point.y() - start);
        double theta = angle * distance / width;
        double curvature = angle / width;
        double tail = Math.max(0, point.y() - start - width);
        double y = start + Math.sin(theta) / curvature + tail * Math.cos(theta);
        double z = (1 - Math.cos(theta)) / curvature + tail * Math.sin(theta);
        return new Vector3(point.x(), y - point.z() * Math.sin(theta), z + point.z() * Math.cos(theta));
    }

    public Vector3 normal(Vector3 position, Vector3 normal) {
        double theta = angleAt(position.y());
        double inBand = position.y() > center - width / 2 && position.y() < center + width / 2 ? angle / width : 0;
        double stretch = 1 - position.z() * inBand;
        if (stretch <= 0) throw new IllegalArgumentException("Bend band is too narrow for the mesh depth");
        double y = normal.y() / stretch;
        return new Vector3(normal.x(), y * Math.cos(theta) - normal.z() * Math.sin(theta),
                y * Math.sin(theta) + normal.z() * Math.cos(theta)).normalized();
    }
}
