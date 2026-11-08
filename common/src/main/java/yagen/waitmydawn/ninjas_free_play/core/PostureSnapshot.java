package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Tick durations are relative to the receiver; no absolute client/server clocks are exchanged.
 */
public record PostureSnapshot(boolean crouching, boolean blockedLow, boolean crouchCancelled,
                              boolean rollHeld, boolean airRollUsed, long rollSequence, float rollYaw,
                              int rollAge, int rollRemaining, int cooldownRemaining, int rollDuration,
                              RollKind rollKind, float bodyYaw, double rollSpeed) {
    public PostureSnapshot(boolean crouching, boolean blockedLow, boolean crouchCancelled, boolean rollHeld,
                           boolean airRollUsed, long rollSequence, float rollYaw, int rollAge, int rollRemaining,
                           int cooldownRemaining, int rollDuration) {
        this(crouching, blockedLow, crouchCancelled, rollHeld, airRollUsed, rollSequence, rollYaw,
                rollAge, rollRemaining, cooldownRemaining, rollDuration, RollKind.FORWARD, rollYaw, 0);
    }

    public static final PostureSnapshot EMPTY = new PostureSnapshot(false, false, false, false, false, -1, 0, 0, 0, 0, 0);

    public PostureSnapshot {
        if (rollSequence < -1 || !Float.isFinite(rollYaw) || rollAge < 0 || rollAge > 6000
                || rollRemaining < 0 || rollRemaining > 40 || cooldownRemaining < 0 || cooldownRemaining > 100
                || rollDuration < 0 || rollDuration > 40 || rollKind == null || !Float.isFinite(bodyYaw)
                || !Double.isFinite(rollSpeed) || rollSpeed < 0 || rollSpeed > 5)
            throw new IllegalArgumentException("Invalid posture snapshot");
    }
}
