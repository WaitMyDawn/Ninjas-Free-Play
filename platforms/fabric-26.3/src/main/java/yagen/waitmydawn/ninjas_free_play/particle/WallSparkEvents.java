package yagen.waitmydawn.ninjas_free_play.particle;

import net.minecraft.client.Minecraft;
import yagen.waitmydawn.ninjas_free_play.NinjasFreePlay;
import yagen.waitmydawn.ninjas_free_play.core.ParkourMode;
import yagen.waitmydawn.ninjas_free_play.player.MovementPhysics;

public final class WallSparkEvents {
    private WallSparkEvents() {
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) return;
        for (var player : minecraft.level.players()) {
            var state = MovementPhysics.controller(player).snapshot(player.tickCount).parkour();
            if (state.mode() != ParkourMode.WALL_LATCH || player.tickCount % 2 != 0 || player.distanceToSqr(minecraft.player) > 4096)
                continue;
            var wall = state.wall();
            var random = player.getRandom();
            for (int i = 0; i < 3; i++) {
                double lateral = (random.nextDouble() - .5) * player.getBbWidth();
                double speed = .1 + random.nextDouble() * .06;
                WallSparkParticle.emit(minecraft.level, player.getId(), player.getX() + wall.x() * .25 * player.getScale() + wall.z() * lateral,
                        player.getY() + player.getBbHeight() * (.3 + random.nextDouble() * .4), player.getZ() + wall.z() * .25 * player.getScale() - wall.x() * lateral,
                        -wall.x() * speed, (random.nextDouble() - .5) * .025, -wall.z() * speed);
            }
        }
    }
}
