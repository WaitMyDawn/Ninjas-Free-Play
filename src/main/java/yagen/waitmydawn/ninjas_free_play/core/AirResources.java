package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Spent resources are retained when capacity changes or assisted air modes are switched.
 */
public final class AirResources {
    private static final double TIME_EPSILON = 1e-9;
    private int jumpsUsed;
    private boolean bulletJumpUsed;
    private double assistedSecondsUsed;

    public int jumpsUsed() {
        return jumpsUsed;
    }

    public boolean bulletJumpUsed() {
        return bulletJumpUsed;
    }

    public double assistedSecondsUsed() {
        return assistedSecondsUsed;
    }

    public boolean consumeJump(MovementValues values) {
        if (jumpsUsed >= values.jumpCount()) return false;
        jumpsUsed++;
        return true;
    }

    public boolean consumeBulletJump(MovementValues values) {
        if (bulletJumpUsed || !consumeJump(values)) return false;
        bulletJumpUsed = true;
        return true;
    }

    public void leftGround() {
        // Walking off a ledge spends the ground opportunity without spending the cycle's bullet jump.
        jumpsUsed = Math.max(1, jumpsUsed);
    }

    public void restore(int jumps, boolean bulletUsed, double assistedSeconds) {
        if (jumps < 0 || jumps > 64 || !Double.isFinite(assistedSeconds) || assistedSeconds < 0 || assistedSeconds > 120) {
            throw new IllegalArgumentException("Invalid spent air resources");
        }
        jumpsUsed = jumps;
        bulletJumpUsed = bulletUsed;
        assistedSecondsUsed = assistedSeconds;
    }

    public double remainingAssistedSeconds(MovementValues values) {
        return Math.max(0, values.get(MovementAttribute.AIR_MANEUVER_DURATION) - assistedSecondsUsed);
    }

    public double consumeAssistedSeconds(MovementValues values, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) throw new IllegalArgumentException("Invalid assisted duration");
        double consumed = Math.min(seconds, remainingAssistedSeconds(values));
        assistedSecondsUsed += consumed;
        // Repeated 0.05-second steps must not leave a fractional remainder that grants an extra assisted tick.
        double capacity = values.get(MovementAttribute.AIR_MANEUVER_DURATION);
        if (capacity - assistedSecondsUsed < TIME_EPSILON)
            assistedSecondsUsed = Math.max(assistedSecondsUsed, capacity);
        return consumed;
    }

    public void landed() {
        jumpsUsed = 0;
        bulletJumpUsed = false;
        assistedSecondsUsed = 0;
    }
}
