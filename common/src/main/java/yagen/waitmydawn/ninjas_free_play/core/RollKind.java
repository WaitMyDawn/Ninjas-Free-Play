package yagen.waitmydawn.ninjas_free_play.core;

public enum RollKind {
    FORWARD, LEFT, RIGHT, BACKWARD;

    public static RollKind fromInput(float strafe, float forward) {
        if (Math.abs(strafe) > Math.abs(forward)) return strafe > 0 ? LEFT : RIGHT;
        return forward < 0 ? BACKWARD : FORWARD;
    }

    public float bodyYaw(float travelYaw) {
        return (float) Math.IEEEremainder(travelYaw + switch (this) {
            case FORWARD -> 0;
            case LEFT -> 90;
            case RIGHT -> -90;
            case BACKWARD -> 180;
        }, 360);
    }
}
