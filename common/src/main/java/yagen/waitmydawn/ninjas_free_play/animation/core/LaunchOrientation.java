package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Minecraft world direction uses Y-up, yaw zero toward +Z, and negative pitch upward.
 */
public record LaunchOrientation(double yawRadians, double pitchRadians) {
    public LaunchOrientation {
        if (!Double.isFinite(yawRadians) || !Double.isFinite(pitchRadians) || Math.abs(pitchRadians) > Math.PI / 2) {
            throw new IllegalArgumentException("Invalid launch orientation");
        }
        yawRadians = Math.IEEEremainder(yawRadians, Math.PI * 2);
    }

    public static LaunchOrientation fromDegrees(double yaw, double pitch) {
        return new LaunchOrientation(Math.toRadians(yaw), Math.toRadians(pitch));
    }

    public Vector3 direction() {
        double horizontal = Math.cos(pitchRadians);
        return new Vector3(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians), Math.cos(yawRadians) * horizontal);
    }

    public Vector3 impulsePerTick(double magnitude) {
        if (!Double.isFinite(magnitude) || magnitude < 0)
            throw new IllegalArgumentException("Invalid launch magnitude");
        return direction().scale(magnitude);
    }

    public static Rotation entityModelRotation(double bodyYawRadians) {
        return Rotation.fromEuler(new Vector3(0, Math.PI - bodyYawRadians, 0))
                .multiply(Rotation.fromEuler(new Vector3(0, 0, Math.PI)));
    }

    public Rotation worldModelRotation() {
        // Retaining yaw at vertical pitch supplies a stable twist reference without normalizing a zero projection.
        return entityModelRotation(yawRadians)
                .multiply(Rotation.fromEuler(new Vector3(Math.PI / 2 + pitchRadians, 0, 0)));
    }
}
