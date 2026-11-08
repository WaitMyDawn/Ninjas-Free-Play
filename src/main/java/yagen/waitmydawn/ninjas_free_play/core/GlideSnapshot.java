package yagen.waitmydawn.ninjas_free_play.core;

/**
 * Glide is an independent air-physics layer and can coexist with a roll or kick.
 */
public record GlideSnapshot(boolean active, long instance, int age) {
    public static final GlideSnapshot EMPTY = new GlideSnapshot(false, 0, 0);

    public GlideSnapshot {
        if (instance < 0 || age < 0 || age > 6000) throw new IllegalArgumentException("Invalid glide state");
    }
}
