package yagen.waitmydawn.ninjas_free_play;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yagen.waitmydawn.ninjas_free_play.player.ParkourPhysics;
import yagen.waitmydawn.ninjas_free_play.player.VanillaMovement;

@GameTestHolder(NinjasFreePlay.MODID)
@PrefixGameTestTemplate(false)
public final class MovementSamplingGameTests {
    private static ServerPlayer player(GameTestHelper helper) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "sampling-test"));
        player.setPos(helper.absoluteVec(new Vec3(2.5, 4, 2.5)));
        return player;
    }

    @GameTest(template = "empty")
    public static void motionSamplingKeepsPerPacketSpeedWhenPacketsShareOneTick(GameTestHelper helper) {
        var player = player(helper);
        player.tickCount = 10;
        VanillaMovement.beforeMovePacket(player);
        player.setPos(player.position().add(.1, .2, .3));
        VanillaMovement.afterMovePacket(player);
        VanillaMovement.beforeMovePacket(player);
        player.setPos(player.position().add(.2, -.1, .15));
        VanillaMovement.afterMovePacket(player);
        player.setDeltaMovement(7, 7, 7);
        helper.assertTrue(VanillaMovement.knownMovement(player).distanceTo(new Vec3(.2, -.1, .15)) < 1e-6,
                "Motion qualification must use one accepted position step, excluding packet batching and entity velocity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void samplingRejectsTeleportsAndExpiresStaleMotion(GameTestHelper helper) {
        var player = player(helper);
        player.tickCount = 5;
        VanillaMovement.beforeMovePacket(player);
        player.setPos(player.position().add(.4, 0, 0));
        VanillaMovement.afterMovePacket(player);
        player.tickCount = 9;
        helper.assertTrue(VanillaMovement.knownMovement(player).equals(Vec3.ZERO), "Old movement cannot keep qualifying actions");
        VanillaMovement.beforeMovePacket(player);
        player.setPos(player.position().add(40, 0, 0));
        VanillaMovement.afterMovePacket(player);
        helper.assertTrue(VanillaMovement.knownMovement(player).equals(Vec3.ZERO), "Teleport displacement cannot qualify as movement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void runtimeResetDropsMotionAndStartsTheNextPacketFromItsNewOrigin(GameTestHelper helper) {
        var player = player(helper);
        player.tickCount = 20;
        VanillaMovement.beforeMovePacket(player);
        player.setPos(player.position().add(.4, 0, 0));
        VanillaMovement.afterMovePacket(player);
        ParkourPhysics.runtime(player).reset();
        player.setPos(player.position().add(100, 0, 0));
        VanillaMovement.beforeMovePacket(player);
        player.setPos(player.position().add(0, .1, -.2));
        VanillaMovement.afterMovePacket(player);
        helper.assertTrue(VanillaMovement.knownMovement(player).distanceTo(new Vec3(0, .1, -.2)) < 1e-6,
                "The reset origin must exclude movement in the previous world or session");
        helper.succeed();
    }
}
