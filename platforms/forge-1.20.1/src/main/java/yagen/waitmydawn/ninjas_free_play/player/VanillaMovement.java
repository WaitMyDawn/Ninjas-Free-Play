package yagen.waitmydawn.ninjas_free_play.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

/** Adapts movement facts that have no equivalent entity API in Minecraft 1.20.1. */
public final class VanillaMovement {
    private VanillaMovement() {}

    public static double sneakMultiplier(Player player) {
        return Mth.clamp(0.3f + EnchantmentHelper.getSneakingSpeedBonus(player), 0, 1);
    }

    public static Vec3 knownMovement(Player player) {
        if (!(player instanceof ServerPlayer)) return player.getDeltaMovement();
        var sample = ParkourPhysics.runtime(player).serverMotion.sample(player.tickCount);
        return new Vec3(sample.x(), sample.y(), sample.z());
    }

    public static void beforeMovePacket(ServerPlayer player) {
        ParkourPhysics.runtime(player).serverMotionOrigin = player.position();
    }

    public static void afterMovePacket(ServerPlayer player) {
        var runtime = ParkourPhysics.runtime(player);
        // Sample accepted displacement, never the unvalidated coordinates supplied by the client.
        var before = runtime.serverMotionOrigin;
        var after = player.position();
        runtime.serverMotion.record(new Vector3(before.x, before.y, before.z),
                new Vector3(after.x, after.y, after.z), player.tickCount);
    }
}
