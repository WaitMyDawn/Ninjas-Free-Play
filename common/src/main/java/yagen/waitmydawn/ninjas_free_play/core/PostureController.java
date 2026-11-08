package yagen.waitmydawn.ninjas_free_play.core;

public final class PostureController {
    private boolean crouching;
    private boolean blockedLow;
    private boolean crouchCancelled;
    private boolean rollHeld;
    private boolean airRollUsed;
    private boolean wasLow;
    private long rollSequence = -1;
    private float rollYaw;
    private long rollStarted;
    private long rollUntil;
    private long cooldownUntil;
    private int rollDuration;
    private RollKind rollKind = RollKind.FORWARD;
    private float bodyYaw;
    private double rollSpeed;

    public boolean rolling(long tick) {
        return tick < rollUntil;
    }

    public boolean crouching() {
        return crouching;
    }

    public void keepLow() {
        wasLow = true;
    }

    public boolean low(long tick) {
        return rolling(tick) ? rollKind == RollKind.FORWARD : crouching || blockedLow;
    }

    public void stopRoll(long tick) {
        rollUntil = tick;
    }

    public void observe(boolean eligible, boolean grounded, boolean canStand, long tick) {
        if (!eligible) {
            crouching = false;
            blockedLow = false;
            rollUntil = tick;
            wasLow = false;
            return;
        }
        if (grounded) airRollUsed = false;
        else if (rolling(tick)) airRollUsed = true;
        blockedLow = !canStand && (wasLow || low(tick));
        wasLow = low(tick);
    }

    public boolean accept(MovementInput input, boolean eligible, boolean grounded, boolean canStand, boolean canRoll,
                          boolean jumpPressed, long tick, MovementValues values) {
        observe(eligible, grounded, canStand, tick);
        if (!input.crouch()) crouchCancelled = false;
        if (input.sprint() && input.forward() > 0 && canStand) crouchCancelled = true;
        crouching = eligible && input.crouch() && !crouchCancelled;
        wasLow |= crouching;
        boolean pressed = input.roll() && !rollHeld;
        rollHeld = input.roll();
        if (jumpPressed) rollUntil = tick;
        if (!eligible || !canRoll || jumpPressed || !pressed || rolling(tick) || tick < cooldownUntil || !grounded && airRollUsed)
            return false;
        RollKind requested = RollKind.fromInput(input.strafe(), input.forward());
        if (requested != RollKind.FORWARD && !canStand) return false;
        rollKind = requested;
        rollSequence = input.sequence();
        rollYaw = input.yaw();
        if (Math.hypot(input.strafe(), input.forward()) > 1e-5) {
            rollYaw += (float) Math.toDegrees(Math.atan2(-input.strafe(), input.forward()));
        }
        rollYaw = (float) Math.IEEEremainder(rollYaw, 360);
        bodyYaw = rollKind.bodyYaw(rollYaw);
        rollSpeed = values.rollPerTick();
        rollStarted = tick;
        rollDuration = values.rollTicks();
        rollUntil = tick + rollDuration;
        cooldownUntil = tick + values.rollCooldownTicks();
        if (!grounded) airRollUsed = true;
        blockedLow = false;
        wasLow = rollKind == RollKind.FORWARD;
        return true;
    }

    public void expire() {
        crouching = false;
    }

    public PostureSnapshot snapshot(long tick) {
        return new PostureSnapshot(crouching, blockedLow, crouchCancelled, rollHeld, airRollUsed, rollSequence, rollYaw,
                (int) Math.min(6000, Math.max(0, tick - rollStarted)), (int) Math.max(0, rollUntil - tick),
                (int) Math.max(0, cooldownUntil - tick), rollDuration, rollKind, bodyYaw, rollSpeed);
    }

    public void restore(PostureSnapshot state, long tick) {
        crouching = state.crouching();
        blockedLow = state.blockedLow();
        crouchCancelled = state.crouchCancelled();
        rollHeld = state.rollHeld();
        airRollUsed = state.airRollUsed();
        rollSequence = state.rollSequence();
        rollYaw = state.rollYaw();
        rollStarted = tick - state.rollAge();
        rollUntil = tick + state.rollRemaining();
        cooldownUntil = tick + state.cooldownRemaining();
        rollDuration = state.rollDuration();
        rollKind = state.rollKind();
        bodyYaw = state.bodyYaw();
        rollSpeed = state.rollSpeed();
        wasLow = low(tick);
    }
}
