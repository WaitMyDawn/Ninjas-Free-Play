package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CompactAnimationTest {
    private AnimationClip load(String name) throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("build/generated/animation-resources/assets/ninjas_free_play/ninja_animations/" + name + ".json"))) {
            return AnimationJsonCodec.read(reader);
        }
    }

    @Test void rollRetimingPreservesBezierPhasesAndTheAuthoredWholeTurn() throws Exception {
        var source = load("roll");
        var retimed = source.retimed(1.37);
        for (int index = 0; index <= 100; index++) {
            double phase = index / 100.0;
            for (Bone bone : Bone.values()) {
                var expected = source.sample(source.duration() * phase).bone(bone);
                var actual = retimed.sample(retimed.duration() * phase).bone(bone);
                assertEquals(expected.bend(), actual.bend(), 1e-6);
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(expected.position().component(axis), actual.position().component(axis), 1e-6);
                    assertEquals(expected.rotation().component(axis), actual.rotation().component(axis), 1e-6);
                }
            }
        }
        assertEquals(Math.PI * 2, retimed.sample(1.37).bone(Bone.ROOT).rotation().x(), 1e-6);
    }

    @Test void compactLocomotionLoopsCloseTheirPosesAndKeepTheKneesBent() throws Exception {
        for (String id : new String[]{"crouch_idle", "crouch_walk"}) {
            var clip = load(id);
            assertTrue(clip.loop());
            for (Bone bone : Bone.values()) {
                var start = clip.sample(0).bone(bone);
                var end = clip.sample(Math.nextDown(clip.duration())).bone(bone);
                assertEquals(start.bend(), end.bend(), 1e-6);
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(start.position().component(axis), end.position().component(axis), 1e-6);
                    assertEquals(start.rotation().component(axis), end.rotation().component(axis), 1e-6);
                }
            }
            assertTrue(Math.abs(clip.sample(0.2).bone(Bone.LEFT_LEG).bend()) >= Math.PI / 2 - 1e-6);
        }
        var walk = load("crouch_walk");
        assertNotEquals(walk.sample(0.2).bone(Bone.LEFT_LEG), walk.sample(0.6).bone(Bone.LEFT_LEG));
    }

    @Test void rollingAndCrouchingCanInterruptTheAcceptedBulletAnimation() throws Exception {
        var clips = new AnimationClip[]{load("roll"), load("crouch_idle"), load("crouch_walk"), load("bullet_jump")};
        for (var from : clips) for (var to : clips) {
            var player = new AnimationPlayer();
            player.play(from, 0, 0.15);
            var before = player.sample(0.31);
            player.play(to, 0.31, 0.15);
            var after = player.sample(0.31);
            assertEquals(before.weight(), after.weight(), 1e-9);
            for (Bone bone : Bone.values()) {
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(before.pose().bone(bone).rotation().component(axis), after.pose().bone(bone).rotation().component(axis), 1e-6);
                    assertEquals(before.pose().bone(bone).position().component(axis), after.pose().bone(bone).position().component(axis), 1e-6);
                }
            }
        }
    }

    @Test void jointLimitsTolerateRoundingButRejectAnAuthoredOvershoot() {
        assertEquals(BonePose.MAX_BEND, new BonePose(Vector3.ZERO, Vector3.ZERO, BonePose.MAX_BEND + 1e-14).bend());
        assertThrows(IllegalArgumentException.class, () -> new BonePose(Vector3.ZERO, Vector3.ZERO, BonePose.MAX_BEND + 1e-5));
    }

    @Test void everyConfigurableRollTickDurationRetainsItsExactBoundary() throws Exception {
        var source = load("roll");
        for (int tick = 4; tick <= 40; tick++) {
            double seconds = tick / 20.0;
            var clip = source.retimed(seconds);
            assertEquals(seconds, clip.duration());
            assertEquals(Math.PI * 2, clip.sample(seconds).bone(Bone.ROOT).rotation().x(), 1e-6);
            assertEquals(source.sample(source.duration() * 0.4).bone(Bone.LEFT_LEG).bend(),
                    clip.sample(seconds * 0.4).bone(Bone.LEFT_LEG).bend(), 1e-6);
        }
    }
}
