package yagen.waitmydawn.ninjas_free_play.core;

public enum ParkourMode {
    NONE, SLIDE, FLYING_KICK, AIM_GLIDE, WALL_LATCH;

    public boolean compact() {
        return this == SLIDE || this == FLYING_KICK;
    }

    public boolean assisted() {
        return this == AIM_GLIDE || this == WALL_LATCH;
    }
}
