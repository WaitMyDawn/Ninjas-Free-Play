package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Analytic critically damped response; frame partitioning does not alter a stationary-target result.
 */
public final class VectorSmoother {
    private Vector3 value = Vector3.ZERO, velocity = Vector3.ZERO;
    private double previous = Double.NaN;

    public Vector3 sample(double now, Vector3 target, double duration) {
        if (!Double.isFinite(now) || !Double.isFinite(duration) || duration < 0)
            throw new IllegalArgumentException("Invalid smoothing clock");
        if (duration == 0) {
            value = target;
            velocity = Vector3.ZERO;
            previous = now;
            return value;
        }
        if (Double.isNaN(previous)) {
            previous = now;
            return value;
        }
        double dt = Math.max(0, now - previous), omega = 4 / duration;
        Vector3 error = value.add(target.scale(-1)), c = velocity.add(error.scale(omega));
        double decay = Math.exp(-omega * dt);
        value = target.add(error.add(c.scale(dt)).scale(decay));
        velocity = velocity.add(c.scale(-omega * dt)).scale(decay);
        previous = now;
        return value;
    }
}
