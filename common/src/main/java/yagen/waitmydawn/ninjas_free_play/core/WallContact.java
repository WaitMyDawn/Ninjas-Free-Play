package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Outward cardinal normal and player-edge distance to an actual collision surface.
 */
public record WallContact(int x, int z, double distance, boolean latchable) {
    public static final WallContact NONE = new WallContact(0, 0, 0, false);

    public WallContact {
        if (x < -1 || x > 1 || z < -1 || z > 1 || Math.abs(x) + Math.abs(z) > 1 || !Double.isFinite(distance) || distance < 0 || distance > 64
                || latchable && x == 0 && z == 0) throw new IllegalArgumentException("Invalid wall contact");
    }

    public boolean present() {
        return x != 0 || z != 0;
    }
}
