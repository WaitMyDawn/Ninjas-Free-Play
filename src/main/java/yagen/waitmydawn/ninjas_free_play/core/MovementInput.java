package yagen.waitmydawn.ninjas_free_play.core;

/**
 * One sequenced input sample; angles follow Minecraft's world yaw and negative-up pitch convention.
 */
public record MovementInput(long sequence, boolean crouch, boolean jump, float yaw, float pitch,
                            boolean roll, boolean sprint, float strafe, float forward, boolean aim) {
    public MovementInput(long sequence, boolean crouch, boolean jump, float yaw, float pitch,
                         boolean roll, boolean sprint, float strafe, float forward) {
        this(sequence, crouch, jump, yaw, pitch, roll, sprint, strafe, forward, false);
    }

    public MovementInput(long sequence, boolean crouch, boolean jump, float yaw, float pitch) {
        this(sequence, crouch, jump, yaw, pitch, false, false, 0, 0);
    }

    public MovementInput {
        if (sequence < 0 || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(pitch) > 90
                || !Float.isFinite(strafe) || !Float.isFinite(forward) || Math.abs(strafe) > 1 || Math.abs(forward) > 1) {
            throw new IllegalArgumentException("Invalid movement input");
        }
        yaw = (float) Math.IEEEremainder(yaw, 360);
    }
}
