package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Preserves faster incoming horizontal vectors; low-speed rolls supply the missing along-path drive.
 */
public final class RollDrive {
    public record Velocity(double x, double z) {
    }

    private RollDrive() {
    }

    public static Velocity sustain(double x, double z, double yawDegrees, double speedPerTick, double phase) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(yawDegrees)
                || !Double.isFinite(speedPerTick) || speedPerTick < 0 || !Double.isFinite(phase)) {
            throw new IllegalArgumentException("Invalid roll drive state");
        }
        double yaw = Math.toRadians(yawDegrees), dx = -Math.sin(yaw), dz = Math.cos(yaw);
        double finish = Math.max(0, Math.min(1, (phase - 0.75) / 0.25));
        double target = speedPerTick * (1 - 0.7 * finish * finish * (3 - 2 * finish));
        // A fast incoming movement keeps its vector even when the animation rolls in another direction.
        if (Math.hypot(x, z) >= target) return new Velocity(x, z);
        double addition = Math.max(0, target - x * dx - z * dz);
        return new Velocity(x + dx * addition, z + dz * addition);
    }
}
