package yagen.waitmydawn.ninjas_free_play.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/** Accepted position packets represent client simulation steps, even when several arrive in one server tick. */
public final class MovementSamples {
    public static final int MAX_AGE_TICKS = 3;
    private Vector3 velocity = Vector3.ZERO;
    private long sampledAt = Long.MIN_VALUE;

    public void record(Vector3 before, Vector3 after, long tick) {
        double x = after.x() - before.x(), y = after.y() - before.y(), z = after.z() - before.z();
        velocity = x * x + y * y + z * z < 64 ? new Vector3(x, y, z) : Vector3.ZERO;
        sampledAt = tick;
    }

    public Vector3 sample(long tick) {
        return sampledAt != Long.MIN_VALUE && tick >= sampledAt && tick - sampledAt <= MAX_AGE_TICKS
                ? velocity : Vector3.ZERO;
    }

    public void reset() {
        velocity = Vector3.ZERO;
        sampledAt = Long.MIN_VALUE;
    }
}
