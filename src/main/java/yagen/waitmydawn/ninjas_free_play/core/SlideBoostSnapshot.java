package yagen.waitmydawn.ninjas_free_play.core;

/**
 * A carried-speed reference and consecutive ordinary-ground samples, independent of clock origin.
 */
public record SlideBoostSnapshot(boolean active, double referenceSpeed, int recoveryTicks) {
    public static final SlideBoostSnapshot EMPTY = new SlideBoostSnapshot(false, 0, 0);

    public SlideBoostSnapshot {
        if (!Double.isFinite(referenceSpeed) || referenceSpeed < 0 || recoveryTicks < 0
                || recoveryTicks >= SlideMomentum.RECOVERY_TICKS || !active && (referenceSpeed != 0 || recoveryTicks != 0)) {
            throw new IllegalArgumentException("Invalid slide boost state");
        }
    }
}
