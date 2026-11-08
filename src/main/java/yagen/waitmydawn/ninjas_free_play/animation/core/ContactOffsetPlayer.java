package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Releases a world-space contact offset with its incoming velocity when contact is interrupted.
 */
public final class ContactOffsetPlayer {
    private Vector3 value = Vector3.ZERO;
    private Vector3 velocity = Vector3.ZERO;
    private Vector3 releaseFrom = Vector3.ZERO;
    private Vector3 releaseVelocity = Vector3.ZERO;
    private double lastTime = Double.NaN;
    private double releaseAt;
    private boolean releasing;

    public Vector3 sample(double now, Vector3 target, boolean supported, double duration) {
        if (!Double.isFinite(now) || !Double.isFinite(duration) || duration < 0 || target == null) {
            throw new IllegalArgumentException("Invalid contact offset sample");
        }
        if (supported) {
            if (Double.isFinite(lastTime) && now > lastTime)
                velocity = target.add(value.scale(-1)).scale(1 / (now - lastTime));
            value = target;
            releasing = false;
        } else {
            if (!releasing) {
                releaseFrom = value;
                releaseVelocity = velocity;
                releaseAt = now;
                releasing = true;
            }
            double s = duration == 0 ? 1 : Math.max(0, Math.min(1, (now - releaseAt) / duration));
            value = releaseFrom.scale(1 - s * s * (3 - 2 * s))
                    .add(releaseVelocity.scale(duration * s * (1 - s) * (1 - s)));
            velocity = s == 1 ? Vector3.ZERO : releaseFrom.scale(-6 * s * (1 - s) / duration)
                    .add(releaseVelocity.scale(1 - 4 * s + 3 * s * s));
        }
        lastTime = now;
        return value;
    }
}
