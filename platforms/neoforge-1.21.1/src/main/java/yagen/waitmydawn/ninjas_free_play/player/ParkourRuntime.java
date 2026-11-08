package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.level.Level;
import yagen.waitmydawn.ninjas_free_play.core.WallContact;

/**
 * Platform-only sampling and per-strike hit identity; never accepted from network input.
 */
public final class ParkourRuntime {
    public Vec3 travelStart = Vec3.ZERO;
    public Vec3 displacement = Vec3.ZERO;
    public double glideSpeedCeiling;
    public Vec3 combatPosition;
    public long strike = -1;
    public final Set<UUID> hitTargets = new HashSet<>();
    public long wallTick = Long.MIN_VALUE;
    public Vec3 wallPosition;
    public Level wallLevel;
    public float wallWidth;
    public WallContact wall = WallContact.NONE;

    public void reset() {
        displacement = Vec3.ZERO;
        combatPosition = null;
        strike = -1;
        hitTargets.clear();
        wallTick = Long.MIN_VALUE;
        wallPosition = null;
        wallLevel = null;
        wall = WallContact.NONE;
    }
}
