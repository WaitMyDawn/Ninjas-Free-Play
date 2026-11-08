package yagen.waitmydawn.ninjas_free_play.core;

public record ParkourSnapshot(ParkourMode mode, long instance, int age, boolean aim, boolean crouch,
                              WallContact wall, int wallCooldown, double assistedSeconds, WallDashPlan wallDash,
                              GlideSnapshot glide,
                              SlideBoostSnapshot slideBoost) {
    public ParkourSnapshot(ParkourMode mode, long instance, int age, boolean aim, boolean crouch,
                           WallContact wall, int wallCooldown, double assistedSeconds, WallDashPlan wallDash, GlideSnapshot glide) {
        this(mode, instance, age, aim, crouch, wall, wallCooldown, assistedSeconds, wallDash, glide, SlideBoostSnapshot.EMPTY);
    }

    public ParkourSnapshot(ParkourMode mode, long instance, int age, boolean aim, boolean crouch,
                           WallContact wall, int wallCooldown, double assistedSeconds, WallDashPlan wallDash) {
        this(mode, instance, age, aim, crouch, wall, wallCooldown, assistedSeconds, wallDash,
                new GlideSnapshot(mode == ParkourMode.AIM_GLIDE, instance, age));
    }

    public boolean assisted() {
        return glide.active() || mode == ParkourMode.WALL_LATCH;
    }

    public ParkourSnapshot(ParkourMode mode, long instance, int age, boolean aim, boolean crouch,
                           WallContact wall, int wallCooldown, double assistedSeconds) {
        this(mode, instance, age, aim, crouch, wall, wallCooldown, assistedSeconds, WallDashPlan.NONE);
    }

    public static final ParkourSnapshot EMPTY = new ParkourSnapshot(ParkourMode.NONE, 0, 0, false, false, WallContact.NONE, 0, 0);

    public ParkourSnapshot {
        if (mode == null || instance < 0 || age < 0 || age > 6000 || wall == null || wallDash == null || glide == null || slideBoost == null || wallCooldown < 0 || wallCooldown > 200
                || !Double.isFinite(assistedSeconds) || assistedSeconds < 0 || assistedSeconds > 120)
            throw new IllegalArgumentException("Invalid parkour state");
    }
}
