package yagen.waitmydawn.ninjas_free_play.core;

public final class InputGate {
    public record Buttons(boolean crouch, boolean jump, boolean roll, boolean sprint, float strafe, float forward,
                          boolean aim) {
        public Buttons(boolean crouch, boolean jump, boolean roll, boolean sprint, float strafe, float forward) {
            this(crouch, jump, roll, sprint, strafe, forward, false);
        }

        public Buttons(boolean crouch, boolean jump) {
            this(crouch, jump, false, false, 0, 0);
        }
    }

    private boolean jumpBlocked;
    private boolean rollBlocked;

    public Buttons sample(boolean allowed, boolean crouch, boolean jump) {
        return sample(allowed, crouch, jump, false, false, 0, 0);
    }

    public Buttons sample(boolean allowed, boolean crouch, boolean jump, boolean roll, boolean sprint, float strafe, float forward) {
        return sample(allowed, crouch, jump, roll, sprint, strafe, forward, false);
    }

    public Buttons sample(boolean allowed, boolean crouch, boolean jump, boolean roll, boolean sprint, float strafe, float forward, boolean aim) {
        if (!allowed) {
            jumpBlocked = true;
            rollBlocked = true;
            return new Buttons(false, false);
        }
        if (!jump) jumpBlocked = false;
        if (!roll) rollBlocked = false;
        return new Buttons(crouch, jump && !jumpBlocked, roll && !rollBlocked, sprint, strafe, forward, aim);
    }
}
