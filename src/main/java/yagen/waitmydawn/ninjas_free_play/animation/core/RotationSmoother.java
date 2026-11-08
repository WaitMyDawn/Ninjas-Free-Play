package yagen.waitmydawn.ninjas_free_play.animation.core;

/**
 * Damps the error relative to the current quaternion, avoiding an absolute logarithm seam at 180 degrees.
 */
public final class RotationSmoother {
    private Rotation value = Rotation.IDENTITY;
    private Vector3 velocity = Vector3.ZERO;
    private double previous = Double.NaN;

    public Rotation sample(double now, Rotation target, double duration) {
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
        if (dt == 0) return value;
        Rotation error = value.multiply(target.inverse());
        if (error.w() < 0) error = error.negated();
        Vector3 offset = error.rotationVector(), c = velocity.add(offset.scale(omega));
        double decay = Math.exp(-omega * dt);
        value = Rotation.fromRotationVector(offset.add(c.scale(dt)).scale(decay)).multiply(target);
        velocity = velocity.add(c.scale(-omega * dt)).scale(decay);
        previous = now;
        return value;
    }
}
