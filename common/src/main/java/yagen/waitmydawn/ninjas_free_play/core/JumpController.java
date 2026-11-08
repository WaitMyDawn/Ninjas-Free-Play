package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Tick observations and sequenced input use the same resource rules on the server and predicting client.
 */
public final class JumpController {
    public record Decision(boolean processed, JumpKind jump) {
    }

    private final AirResources resources = new AirResources();
    private long sequence = -1;
    private long lastInputTick;
    private long lastJumpTick = Long.MIN_VALUE;
    private long actionSequence = -1;
    private JumpKind action = JumpKind.NONE;
    private float yaw;
    private float pitch;
    private boolean crouchHeld;
    // Retain the sampled jump level across timeouts; an explicit release is required to rearm its edge.
    private boolean jumpHeld;
    private boolean grounded;

    public AirResources resources() {
        return resources;
    }

    public boolean crouchHeld() {
        return crouchHeld;
    }

    public boolean bulletJumping() {
        return action == JumpKind.BULLET;
    }

    public boolean canRepeatWallJump() {
        return jumpHeld && action == JumpKind.WALL;
    }

    public void interruptBullet() {
        if (action == JumpKind.BULLET) action = JumpKind.NONE;
    }

    public void observe(boolean eligible, boolean onGround) {
        if (!eligible) {
            crouchHeld = false;
            action = JumpKind.NONE;
            return;
        }
        if (onGround && !grounded) {
            resources.landed();
            action = JumpKind.NONE;
        } else if (!onGround) {
            resources.leftGround();
        }
        grounded = onGround;
    }

    public Decision accept(MovementInput input, boolean eligible, boolean onGround, long tick, MovementValues values) {
        return accept(input, eligible, onGround, tick, values, false);
    }

    public Decision accept(MovementInput input, boolean eligible, boolean onGround, long tick, MovementValues values, boolean wallJump) {
        if (input.sequence() <= sequence) return new Decision(false, JumpKind.NONE);
        observe(eligible, onGround);
        boolean pressed = input.jump() && !jumpHeld;
        // A held ground/air jump cannot become a wall chain after leaving the ground or reaching a wall.
        wallJump = wallJump && !onGround && !input.crouch() && (pressed || canRepeatWallJump());
        sequence = input.sequence();
        lastInputTick = tick;
        crouchHeld = eligible && input.crouch();
        jumpHeld = input.jump();
        if (!eligible || !(pressed || wallJump && input.jump()) || tick == lastJumpTick)
            return new Decision(true, JumpKind.NONE);
        if (wallJump) resources.landed();
        boolean bullet = crouchHeld && !resources.bulletJumpUsed();
        if (wallJump) bullet = false;
        if (!(bullet ? resources.consumeBulletJump(values) : resources.consumeJump(values))) {
            return new Decision(true, JumpKind.NONE);
        }
        action = wallJump ? JumpKind.WALL : bullet ? JumpKind.BULLET : onGround ? JumpKind.GROUND : JumpKind.AIR;
        actionSequence = sequence;
        lastJumpTick = tick;
        yaw = input.yaw();
        pitch = input.pitch();
        return new Decision(true, action);
    }

    public void expire(long tick) {
        if (tick - lastInputTick > 40) crouchHeld = false;
    }

    public MovementSnapshot snapshot(long tick) {
        int age = lastJumpTick == Long.MIN_VALUE ? 0 : (int) Math.min(6000, Math.max(0, tick - lastJumpTick));
        return new MovementSnapshot(sequence, resources.jumpsUsed(), resources.bulletJumpUsed(), crouchHeld,
                jumpHeld, grounded, actionSequence, action, yaw, pitch, age);
    }

    public void restore(MovementSnapshot snapshot, long tick) {
        resources.restore(snapshot.jumpsUsed(), snapshot.bulletUsed(), snapshot.parkour().assistedSeconds());
        sequence = snapshot.acknowledgedSequence();
        crouchHeld = snapshot.crouchHeld();
        jumpHeld = snapshot.jumpHeld();
        grounded = snapshot.grounded();
        actionSequence = snapshot.actionSequence();
        action = snapshot.action();
        yaw = snapshot.yaw();
        pitch = snapshot.pitch();
        lastJumpTick = actionSequence < 0 ? Long.MIN_VALUE : tick - snapshot.actionAge();
        lastInputTick = tick;
    }
}
