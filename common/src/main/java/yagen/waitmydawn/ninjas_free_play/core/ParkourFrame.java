package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Measured previous horizontal displacement, current velocity and world collision facts.
 */
public record ParkourFrame(double speedX, double speedZ, double velocityX, double velocityY, double velocityZ,
                           WallContact wall,
                           double ordinaryGroundSpeed) {
    public ParkourFrame(double speedX, double speedZ, double velocityX, double velocityY, double velocityZ, WallContact wall) {
        this(speedX, speedZ, velocityX, velocityY, velocityZ, wall, MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK);
    }

    public static final ParkourFrame EMPTY = new ParkourFrame(0, 0, 0, 0, 0, WallContact.NONE);

    public ParkourFrame {
        if (!Double.isFinite(speedX) || !Double.isFinite(speedZ) || !Double.isFinite(velocityX)
                || !Double.isFinite(velocityY) || !Double.isFinite(velocityZ) || wall == null
                || !Double.isFinite(ordinaryGroundSpeed) || ordinaryGroundSpeed < 0)
            throw new IllegalArgumentException("Invalid movement facts");
    }

    public double speed() {
        return Math.hypot(speedX, speedZ);
    }
}
