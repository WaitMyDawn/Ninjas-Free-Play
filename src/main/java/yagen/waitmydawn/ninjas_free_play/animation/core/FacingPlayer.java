package yagen.waitmydawn.ninjas_free_play.animation.core;

import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * Inertializes a world-space orientation layer without mixing or discarding authored local turns.
 */
public final class FacingPlayer {
    private static final double STEP = 1e-4;
    private DoubleFunction<Rotation> target;
    private Vector3 offset = Vector3.ZERO;
    private Vector3 velocity = Vector3.ZERO;
    private double startedAt;
    private double duration;

    public FacingPlayer(DoubleFunction<Rotation> initial) {
        target = Objects.requireNonNull(initial);
    }

    public void play(DoubleFunction<Rotation> next, double now, double transition) {
        requireTime(now);
        if (!Double.isFinite(transition) || transition < 0)
            throw new IllegalArgumentException("Invalid facing transition");
        Objects.requireNonNull(next);
        Rotation currentOffset = sample(now).multiply(Objects.requireNonNull(next.apply(now)).inverse());
        if (currentOffset.w() < 0) currentOffset = currentOffset.negated();
        Rotation beforeOffset = sample(now - STEP).multiply(Objects.requireNonNull(next.apply(now - STEP)).inverse());
        if (beforeOffset.dot(currentOffset) < 0) beforeOffset = beforeOffset.negated();
        Vector3 nextOffset = currentOffset.rotationVector();
        Vector3 nextVelocity = nextOffset.add(beforeOffset.rotationVector().scale(-1)).scale(1 / STEP);
        target = next;
        startedAt = now;
        duration = transition;
        offset = nextOffset;
        velocity = nextVelocity;
    }

    public Rotation sample(double now) {
        requireTime(now);
        Rotation incoming = Objects.requireNonNull(target.apply(now));
        if (duration == 0 || now >= startedAt + duration) return incoming;
        double s = Math.max(0, (now - startedAt) / duration);
        Vector3 residual = offset.scale(1 - s * s * (3 - 2 * s))
                .add(velocity.scale(duration * s * (1 - s) * (1 - s)));
        return Rotation.fromRotationVector(residual).multiply(incoming);
    }

    private static void requireTime(double now) {
        if (!Double.isFinite(now)) throw new IllegalArgumentException("Facing clock must be finite");
    }
}
