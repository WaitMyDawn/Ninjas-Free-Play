package yagen.waitmydawn.ninjas_free_play.core;

import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/**
 * Repeated entries share an unboosted reference until ordinary grounded movement ends the chain.
 */
public final class SlideMomentum {
    public static final int RECOVERY_TICKS = 6;
    private boolean active;
    private double referenceSpeed;
    private int recoveryTicks;
    private long lastRecoveryTick = Long.MIN_VALUE;

    public Vector3 launch(ParkourFrame frame, Vector3 current, MovementValues values) {
        double speed = Math.hypot(current.x(), current.z()), actual = frame.speed();
        double dot = current.x() * frame.speedX() + current.z() * frame.speedZ();
        boolean carry = actual > speed && (speed < 1e-8 || dot >= speed * actual * .999);
        double x = carry ? frame.speedX() : current.x(), z = carry ? frame.speedZ() : current.z();
        double incoming = Math.hypot(x, z);
        if (!active) {
            active = true;
            referenceSpeed = incoming;
        }
        recoveryTicks = 0;
        double multiplier = values.get(MovementAttribute.SLIDE_SPEED_MULTIPLIER);
        double outgoing = multiplier <= 1 ? incoming * multiplier :
                Math.max(incoming, Math.min(incoming * multiplier, referenceSpeed * multiplier));
        double scale = incoming > 0 ? outgoing / incoming : 0;
        return new Vector3(x * scale, current.y(), z * scale);
    }

    public void observe(boolean eligible, boolean ordinaryGround, long tick, ParkourFrame frame) {
        if (!eligible) {
            reset();
            return;
        }
        if (!active) return;
        boolean normal = ordinaryGround && frame.speed() <= frame.ordinaryGroundSpeed() + 1e-4;
        if (!normal) {
            recoveryTicks = 0;
            lastRecoveryTick = tick;
            return;
        }
        if (tick == lastRecoveryTick) return;
        // Missing simulation samples cannot certify a continuous period of ordinary movement.
        recoveryTicks = tick == lastRecoveryTick + 1 ? recoveryTicks + 1 : 1;
        lastRecoveryTick = tick;
        if (recoveryTicks >= RECOVERY_TICKS) reset();
    }

    public void reset() {
        active = false;
        referenceSpeed = 0;
        recoveryTicks = 0;
        lastRecoveryTick = Long.MIN_VALUE;
    }

    public SlideBoostSnapshot snapshot() {
        return new SlideBoostSnapshot(active, referenceSpeed, recoveryTicks);
    }

    public void restore(SlideBoostSnapshot state, long tick) {
        active = state.active();
        referenceSpeed = state.referenceSpeed();
        recoveryTicks = state.recoveryTicks();
        lastRecoveryTick = tick;
    }
}
