package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.Objects;

/**
 * Local offsets use model pixels, ZYX Euler radians, and an X-axis limb bend.
 */
public record BonePose(Vector3 position, Vector3 rotation, double bend) {
    public static final BonePose IDENTITY = new BonePose(Vector3.ZERO, Vector3.ZERO, 0);
    public static final double MAX_BEND = Math.toRadians(120);

    public BonePose {
        Objects.requireNonNull(position);
        Objects.requireNonNull(rotation);
        if (!Double.isFinite(bend) || Math.abs(bend) > MAX_BEND + 1e-12) {
            throw new IllegalArgumentException("Limb bend must be within +/-120 degrees");
        }
        // Convex Bezier evaluation can exceed an exact endpoint limit by a few floating-point ULPs.
        bend = Math.max(-MAX_BEND, Math.min(MAX_BEND, bend));
    }

    public BonePose blend(BonePose other, double weight) {
        if (!Double.isFinite(weight) || weight < 0 || weight > 1) {
            throw new IllegalArgumentException("Blend weight must be within [0, 1]");
        }
        return new BonePose(position.lerp(other.position, weight), new Vector3(
                blendAngle(rotation.x(), other.rotation.x(), weight),
                blendAngle(rotation.y(), other.rotation.y(), weight),
                blendAngle(rotation.z(), other.rotation.z(), weight)), bend + (other.bend - bend) * weight);
    }

    private static double blendAngle(double from, double to, double weight) {
        double difference = Math.IEEEremainder(to - from, Math.PI * 2);
        return from + difference * weight;
    }
}
