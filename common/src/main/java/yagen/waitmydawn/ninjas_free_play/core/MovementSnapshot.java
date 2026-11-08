package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Authoritative resources and action phase, independent of either side's absolute tick counter.
 */
public record MovementSnapshot(long acknowledgedSequence, int jumpsUsed, boolean bulletUsed,
                               boolean crouchHeld, boolean jumpHeld, boolean grounded,
                               long actionSequence, JumpKind action, float yaw, float pitch, int actionAge,
                               PostureSnapshot posture, ParkourSnapshot parkour) {
    public MovementSnapshot(long acknowledgedSequence, int jumpsUsed, boolean bulletUsed, boolean crouchHeld,
                            boolean jumpHeld, boolean grounded, long actionSequence, JumpKind action,
                            float yaw, float pitch, int actionAge, PostureSnapshot posture) {
        this(acknowledgedSequence, jumpsUsed, bulletUsed, crouchHeld, jumpHeld, grounded, actionSequence, action, yaw, pitch, actionAge, posture, ParkourSnapshot.EMPTY);
    }

    public MovementSnapshot(long acknowledgedSequence, int jumpsUsed, boolean bulletUsed, boolean crouchHeld,
                            boolean jumpHeld, boolean grounded, long actionSequence, JumpKind action,
                            float yaw, float pitch, int actionAge) {
        this(acknowledgedSequence, jumpsUsed, bulletUsed, crouchHeld, jumpHeld, grounded, actionSequence,
                action, yaw, pitch, actionAge, PostureSnapshot.EMPTY);
    }

    public MovementSnapshot withPosture(PostureSnapshot state) {
        return new MovementSnapshot(acknowledgedSequence, jumpsUsed, bulletUsed, crouchHeld, jumpHeld,
                grounded, actionSequence, action, yaw, pitch, actionAge, state, parkour);
    }

    public MovementSnapshot withParkour(ParkourSnapshot state) {
        return new MovementSnapshot(acknowledgedSequence, jumpsUsed, bulletUsed, crouchHeld, jumpHeld, grounded,
                actionSequence, action, yaw, pitch, actionAge, posture, state);
    }

    public MovementSnapshot {
        if (acknowledgedSequence < -1 || jumpsUsed < 0 || jumpsUsed > 64 || actionSequence < -1
                || action == null || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90
                || actionAge < 0 || actionAge > 6000 || posture == null || parkour == null) {
            throw new IllegalArgumentException("Invalid movement snapshot");
        }
    }
}
