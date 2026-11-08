package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Continuous modes share one air-time ledger; input sequence replay does not consume simulation time.
 */
public final class ParkourController {
    private ParkourMode mode = ParkourMode.NONE;
    private boolean aim, crouch;
    private long instance, started, lastCharged = Long.MIN_VALUE, wallUntil;
    private WallContact wall = WallContact.NONE;
    private WallDashPlan wallDash = WallDashPlan.NONE;
    private boolean gliding;
    private long glideInstance, glideStarted;
    private final SlideMomentum slideMomentum = new SlideMomentum();

    public ParkourMode mode() {
        return mode;
    }

    public SlideMomentum slideMomentum() {
        return slideMomentum;
    }

    public boolean aim() {
        return aim;
    }

    public boolean gliding() {
        return gliding;
    }

    public boolean assisted() {
        return gliding || mode == ParkourMode.WALL_LATCH;
    }

    public boolean canWallJump(long tick) {
        return tick >= wallUntil;
    }

    public void wallJump(long tick, MovementValues values, WallDashPlan plan) {
        wallDash = plan;
        wallUntil = tick + (long) Math.ceil(values.get(MovementAttribute.WALL_DASH_INTERVAL) * 20);
        change(ParkourMode.NONE, tick);
    }

    public void input(MovementInput input, boolean eligible, boolean grounded, boolean rolling, boolean jumped,
                      long tick, MovementValues values, ParkourFrame frame, AirResources resources) {
        boolean pressed = input.crouch() && !crouch;
        wall = frame.wall();
        aim = input.aim();
        crouch = input.crouch();
        if (jumped || rolling) change(ParkourMode.NONE, tick);
        double entrySpeed = grounded ? frame.speed() : Math.sqrt(frame.speed() * frame.speed() + frame.velocityY() * frame.velocityY());
        if (!jumped && !rolling && eligible && pressed && entrySpeed + 1e-4 >= MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK
                && (!grounded || input.strafe() != 0 || input.forward() != 0))
            change(grounded ? ParkourMode.SLIDE : ParkourMode.FLYING_KICK, tick);
        update(eligible, grounded, rolling, tick, values, frame, resources, false);
    }

    public void update(boolean eligible, boolean grounded, boolean rolling, long tick, MovementValues values,
                       ParkourFrame frame, AirResources resources, boolean consumeTime) {
        wall = frame.wall();
        if (!eligible) {
            change(ParkourMode.NONE, tick);
            glide(false, tick);
            return;
        }
        if (rolling) change(ParkourMode.NONE, tick);
        if (mode == ParkourMode.FLYING_KICK && grounded && crouch) change(ParkourMode.SLIDE, tick);
        if (mode == ParkourMode.SLIDE && (!crouch || !grounded || frame.speed() < MovementValues.SLIDE_ENTER_BLOCKS_PER_TICK * 0.65))
            change(ParkourMode.NONE, tick);
        if (mode == ParkourMode.FLYING_KICK && (!crouch || grounded || tick - started >= 20))
            change(ParkourMode.NONE, tick);
        boolean available = resources.remainingAssistedSeconds(values) > 1e-9 || assisted() && lastCharged == tick;
        boolean active = !grounded && aim && available;
        if (!mode.compact() && !rolling) {
            if (active)
                change(tick >= wallUntil && wall.latchable() ? ParkourMode.WALL_LATCH : ParkourMode.AIM_GLIDE, tick);
            else change(ParkourMode.NONE, tick);
        }
        glide(active && mode != ParkourMode.WALL_LATCH, tick);
        if (consumeTime && assisted() && tick != lastCharged) {
            resources.consumeAssistedSeconds(values, 0.05);
            lastCharged = tick;
        }
    }

    public void stop(long tick) {
        aim = false;
        crouch = false;
        change(ParkourMode.NONE, tick);
        glide(false, tick);
    }

    private void glide(boolean active, long tick) {
        if (active == gliding) return;
        gliding = active;
        glideInstance++;
        glideStarted = tick;
    }

    private void change(ParkourMode next, long tick) {
        if (next == mode) return;
        mode = next;
        instance++;
        started = tick;
    }

    public ParkourSnapshot snapshot(long tick, AirResources resources) {
        return new ParkourSnapshot(mode, instance, (int) Math.min(6000, Math.max(0, tick - started)), aim, crouch, wall,
                (int) Math.max(0, wallUntil - tick), resources.assistedSecondsUsed(), wallDash,
                new GlideSnapshot(gliding, glideInstance, (int) Math.min(6000, Math.max(0, tick - glideStarted))), slideMomentum.snapshot());
    }

    public void restore(ParkourSnapshot state, long tick) {
        mode = state.mode();
        instance = state.instance();
        started = tick - state.age();
        aim = state.aim();
        crouch = state.crouch();
        wall = state.wall();
        wallDash = state.wallDash();
        wallUntil = tick + state.wallCooldown();
        lastCharged = tick;
        gliding = state.glide().active();
        glideInstance = state.glide().instance();
        glideStarted = tick - state.glide().age();
        slideMomentum.restore(state.slideBoost(), tick);
    }
}
