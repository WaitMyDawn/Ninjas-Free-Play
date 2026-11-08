package yagen.waitmydawn.ninjas_free_play.camera.core;

/**
 * Distances use unscaled blocks and smoothing constants use simulation seconds.
 */
public record CameraSettings(double distance, double horizontal, double vertical, double followSeconds,
                             double transitionSeconds, double recoverySeconds, double radius) {
    public static final CameraSettings DEFAULT = new CameraSettings(3.2, 1.75, .25, .08, .2, .12, .12);
    public static final double FIXED_HORIZONTAL_DEFAULT = 2.0;

    public static double migrateHorizontal(double current, int schema) {
        boolean legacy = schema == 0 && Double.compare(current, 1.0) == 0
                || schema < 2 && Double.compare(current, 1.35) == 0;
        return legacy ? DEFAULT.horizontal() : current;
    }

    public CameraSettings {
        for (double value : new double[]{distance, horizontal, vertical, followSeconds, transitionSeconds, recoverySeconds, radius}) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite camera setting");
        }
        if (distance <= 0 || followSeconds < 0 || transitionSeconds < 0 || recoverySeconds < 0 || radius < 0)
            throw new IllegalArgumentException("Invalid camera setting");
    }
}
