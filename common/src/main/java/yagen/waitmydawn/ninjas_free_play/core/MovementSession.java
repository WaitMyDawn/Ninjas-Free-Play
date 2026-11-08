package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Coordinates jump resources and ground/air posture without coupling either to animation playback.
 */
public final class MovementSession {
    public record Decision(boolean processed, JumpKind jump, boolean rollStarted, boolean slideStarted) {
        public Decision(boolean processed, JumpKind jump, boolean rollStarted) {
            this(processed, jump, rollStarted, false);
        }
    }

    private final JumpController jumps = new JumpController();
    private final PostureController posture = new PostureController();
    private final ParkourController parkour = new ParkourController();
    private long lastInputTick;

    public PostureController posture() {
        return posture;
    }

    public ParkourController parkour() {
        return parkour;
    }

    public AirResources resources() {
        return jumps.resources();
    }

    public boolean crouchHeld() {
        return posture.crouching();
    }

    public boolean canRepeatWallJump() {
        return jumps.canRepeatWallJump();
    }

    public boolean bulletJumping() {
        return jumps.bulletJumping();
    }

    /**
     * Collision follows the semantic action, independently of the cosmetic spin clip's duration.
     */
    public boolean compactAction(long tick) {
        return parkour.mode().compact() || bulletJumping() && !posture.rolling(tick);
    }

    public void observe(boolean eligible, boolean grounded, boolean canStand, long tick) {
        if (compactAction(tick)) posture.keepLow();
        jumps.observe(eligible, grounded);
        posture.observe(eligible, grounded, canStand, tick);
        if (!eligible) {
            parkour.stop(tick);
            parkour.slideMomentum().reset();
        }
    }

    public Decision accept(MovementInput input, boolean eligible, boolean grounded, boolean canStand, long tick, MovementValues values) {
        return accept(input, eligible, grounded, canStand, canStand || RollKind.fromInput(input.strafe(), input.forward()) == RollKind.FORWARD, tick, values);
    }

    public Decision accept(MovementInput input, boolean eligible, boolean grounded, boolean canStand, boolean canRoll, long tick, MovementValues values) {
        return accept(input, eligible, grounded, canStand, canRoll, tick, values, ParkourFrame.EMPTY);
    }

    public Decision accept(MovementInput input, boolean eligible, boolean grounded, boolean canStand, boolean canRoll, long tick, MovementValues values, ParkourFrame frame) {
        if (input.sequence() <= jumps.snapshot(tick).acknowledgedSequence())
            return new Decision(false, JumpKind.NONE, false);
        long previousMode = parkour.snapshot(tick, resources()).instance();
        boolean jumpPressed = input.jump() && !jumps.snapshot(tick).jumpHeld();
        boolean wallJump = eligible && !grounded && !input.crouch() && input.jump()
                && (jumpPressed || jumps.canRepeatWallJump()) && frame.wall().present() && parkour.canWallJump(tick);
        if (compactAction(tick)) posture.keepLow();
        boolean roll = posture.accept(input, eligible, grounded, canStand, canRoll, jumpPressed || wallJump, tick, values);
        // Sprint cancels the compact posture, not the crouch-key intent used by jump combinations.
        var filtered = new MovementInput(input.sequence(), input.crouch(), input.jump(), input.yaw(), input.pitch());
        var decision = jumps.accept(filtered, eligible, grounded, tick, values, wallJump);
        if (decision.jump() == JumpKind.WALL) parkour.wallJump(tick, values, WallDashPlan.resolve(input, frame.wall()));
        parkour.input(input, eligible, grounded, posture.rolling(tick), decision.jump() != JumpKind.NONE, tick, values, frame, resources());
        retireReplacedBullet(tick);
        lastInputTick = tick;
        return new Decision(decision.processed(), decision.jump(), roll,
                parkour.mode() == ParkourMode.SLIDE && parkour.snapshot(tick, resources()).instance() != previousMode);
    }

    public void expire(long tick) {
        jumps.expire(tick);
        if (tick - lastInputTick > 40) {
            posture.expire();
            parkour.stop(tick);
        }
    }

    public void update(boolean eligible, boolean grounded, boolean canStand, long tick, MovementValues values, ParkourFrame frame) {
        observe(eligible, grounded, canStand, tick);
        expire(tick);
        parkour.update(eligible, grounded, posture.rolling(tick), tick, values, frame, resources(), true);
        parkour.slideMomentum().observe(eligible, grounded && parkour.mode() == ParkourMode.NONE
                && !posture.rolling(tick) && !posture.low(tick) && !parkour.gliding() && !jumps.crouchHeld(), tick, frame);
        retireReplacedBullet(tick);
    }

    private void retireReplacedBullet(long tick) {
        // A compact collision request must not reappear after its replacing action has ended.
        if (posture.rolling(tick) || parkour.mode() != ParkourMode.NONE || parkour.gliding()) jumps.interruptBullet();
    }

    public MovementSnapshot snapshot(long tick) {
        return jumps.snapshot(tick).withPosture(posture.snapshot(tick)).withParkour(parkour.snapshot(tick, resources()));
    }

    public void restore(MovementSnapshot state, long tick) {
        jumps.restore(state, tick);
        posture.restore(state.posture(), tick);
        parkour.restore(state.parkour(), tick);
        lastInputTick = tick;
    }
}
