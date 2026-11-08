package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTransitionTest {
    private static final double STEP = 1e-6;

    private static KeyframeTrack track(Vector3 from, Vector3 to) {
        return new KeyframeTrack(List.of(new KeyframeTrack.Keyframe(0, from), new KeyframeTrack.Keyframe(1, to)),
                KeyframeTrack.Interpolation.LINEAR);
    }

    private static AnimationClip clip(double offset, double speed, boolean loop) {
        return new AnimationClip(1, loop, Map.of(Bone.LEFT_ARM, new AnimationClip.Track(
                track(new Vector3(offset, 0, 0), new Vector3(offset + speed, 0, 0)),
                track(new Vector3(0, offset / 10, 0), new Vector3(0, (offset + speed) / 10, 0)),
                track(new Vector3(offset / 30, 0, 0), new Vector3((offset + speed) / 30, 0, 0)))));
    }

    private static Vector3 values(AnimationPlayer player, double time) {
        BonePose arm = player.sample(time).pose().bone(Bone.LEFT_ARM);
        return new Vector3(arm.position().x(), arm.rotation().y(), arm.bend());
    }

    private static void near(Vector3 expected, Vector3 actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
    }

    private static Vector3 slope(Vector3 a, Vector3 b) { return b.add(a.scale(-1)).scale(1 / STEP); }

    private static void continuous(DoubleFunction<Vector3> sample, double time, double tolerance) {
        Vector3 center = sample.apply(time);
        near(slope(sample.apply(time - STEP), center), slope(center, sample.apply(time + STEP)), tolerance);
    }

    @Test
    void interruptionPreservesPositionAngularAndBendVelocityAtBothEnds() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(clip(0, 4, true), 0, 0.2);
        Vector3 previous = values(player, 0.5), before = values(player, 0.5 - STEP);
        player.play(clip(10, -3, true), 0.5, 0.2);
        near(previous, values(player, 0.5), 1e-9);
        near(slope(before, previous), slope(previous, values(player, 0.5 + STEP)), 0.002);
        continuous(time -> values(player, time), 0.7, 0.002);
        near(new Vector3(9.4, 0.94, 9.4 / 30), values(player, 0.7), 1e-8);
    }

    @Test
    void interruptedFadeAndResumePreserveVisiblePoseAndWeightVelocity() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(clip(2, 4, true), 0, 0.2);
        for (double time : new double[]{0.08, 0.11}) {
            double weight = player.sample(time).weight();
            double velocity = (weight - player.sample(time - STEP).weight()) / STEP;
            Vector3 visible = values(player, time).scale(weight);
            Vector3 before = values(player, time - STEP).scale(player.sample(time - STEP).weight());
            if (time == 0.08) player.stop(time);
            else player.play(clip(-3, 7, true), time, 0.2);
            assertEquals(weight, player.sample(time).weight(), 1e-9);
            assertEquals(velocity, (player.sample(time + STEP).weight() - weight) / STEP, 0.002);
            near(visible, values(player, time).scale(player.sample(time).weight()), 1e-9);
            near(slope(before, visible), slope(visible, values(player, time + STEP).scale(player.sample(time + STEP).weight())), 0.005);
        }
    }

    @Test
    void stopKeepsMotionAliveDuringFadeAndReleasesToEmpty() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(clip(0, 4, true), 0, 0.2);
        player.stop(0.5);
        continuous(time -> values(player, time).scale(player.sample(time).weight()), 0.5, 0.002);
        assertEquals(2.4, values(player, 0.6).x(), 1e-8);
        assertEquals(0.5, player.sample(0.6).weight(), 1e-8);
        assertEquals(AnimationPlayer.Sample.EMPTY, player.sample(0.71));
    }

    @Test
    void naturalEndCarriesTerminalVelocityThroughFade() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(clip(0, 4, false), 0, 0.2);
        continuous(time -> values(player, time).scale(player.sample(time).weight()), 1, 0.002);
        assertTrue(values(player, 1.05).x() > 4);
        assertEquals(AnimationPlayer.Sample.EMPTY, player.sample(1.21));
    }

    @Test
    void rapidRepeatedInterruptionsStayFiniteBoundedAndIndependentOfRenderCount() {
        AnimationPlayer sparse = new AnimationPlayer(), dense = new AnimationPlayer();
        for (int index = 0; index < 1000; index++) {
            double time = index * 0.013;
            AnimationClip clip = clip(Math.sin(index) * 2, Math.cos(index) * 3, true);
            sparse.play(clip, time, 0.15);
            dense.play(clip, time, 0.15);
            if (index % 7 == 0) { sparse.stop(time + 0.005); dense.stop(time + 0.005); }
            for (int frame = 0; frame < 13; frame++) dense.sample(time + frame * 0.001);
            AnimationPlayer.Sample sample = sparse.sample(time + 0.012);
            assertTrue(sample.weight() >= 0 && sample.weight() <= 1);
            near(values(sparse, time + 0.012), values(dense, time + 0.012), 1e-10);
            assertEquals(sample.weight(), dense.sample(time + 0.012).weight(), 1e-12);
        }
    }

    private static AnimationClip turn(double turns, boolean loop) {
        return new AnimationClip(1, loop, Map.of(Bone.ROOT, new AnimationClip.Track(null,
                track(Vector3.ZERO, new Vector3(0, 0, turns * Math.PI * 2)), null)));
    }

    private static Vector3 direction(AnimationPlayer player, double time) {
        return Rotation.fromEuler(player.sample(time).weightedRoot().rotation()).apply(new Vector3(1, 0, 0));
    }

    @Test
    void weightedWholeTurnsDoNotFlipAtThePrincipalAngleBoundary() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(turn(1, false), 0, 0.8);
        continuous(time -> direction(player, time), 0.5, 0.002);
        assertTrue(player.sample(0.5).weightedRoot().rotation().z() > 2);
    }

    @Test
    void windingRemainsContinuousAcrossLoopSeamsDuringFadeIn() {
        AnimationClip loop = turn(1, true);
        assertEquals(Math.PI * 2.5, loop.sample(1.25).bone(Bone.ROOT).rotation().z(), 1e-9);
        AnimationPlayer player = new AnimationPlayer();
        player.play(loop, 0, 2);
        continuous(time -> direction(player, time), 1, 0.002);
        assertEquals(0.5, player.sample(1).weight(), 1e-9);
    }

    @Test
    void fullTurnEndAndExplicitStopRebaseOnlyAtFullInfluence() {
        AnimationPlayer natural = new AnimationPlayer();
        natural.play(turn(1, false), 0, 0.15);
        continuous(time -> direction(natural, time), 1, 0.005);
        assertTrue(Math.abs(natural.sample(1.05).weightedRoot().rotation().z()) < 0.5);
        AnimationPlayer stopped = new AnimationPlayer();
        stopped.play(turn(2, false), 0, 0.15);
        Vector3 current = direction(stopped, 0.7), before = direction(stopped, 0.7 - STEP);
        stopped.stop(0.7);
        near(current, direction(stopped, 0.7), 1e-9);
        near(slope(before, current), slope(current, direction(stopped, 0.7 + STEP)), 0.01);
    }

    @Test
    void interruptingAtAWholeTurnRebasePreservesPhysicalAngularVelocity() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(turn(1, false), 0, 0.15);
        Vector3 current = direction(player, 1), before = direction(player, 1 - STEP);
        player.play(turn(0, true), 1, 0.15);
        near(current, direction(player, 1), 1e-9);
        near(slope(before, current), slope(current, direction(player, 1 + STEP)), 0.005);
    }

    @Test
    void stoppingAcrossNaturalEndDoesNotRebaseDuringPartialInfluence() {
        AnimationPlayer player = new AnimationPlayer();
        player.play(turn(1, false), 0, 0.2);
        player.stop(0.9);
        continuous(time -> direction(player, time), 1, 0.005);
    }

    @Test
    void stillPoseCanTransitionAndFadeWithoutAnImmediateReset() {
        AnimationPose held = new AnimationPose(Map.of(Bone.LEFT_ARM, new BonePose(new Vector3(3, 0, 0), Vector3.ZERO, 0.4)));
        AnimationPlayer player = new AnimationPlayer();
        player.play(AnimationClip.still(held), 0, 0);
        assertEquals(1, player.sample(1).weight());
        near(new Vector3(3, 0, 0.4), values(player, 1), 1e-9);
        player.stop(1, 0.2);
        assertEquals(0.5, player.sample(1.1).weight(), 1e-9);
        assertEquals(AnimationPlayer.Sample.EMPTY, player.sample(1.21));
    }

    @Test
    void zeroDurationIsAnExplicitCutAndInvalidCommandsDoNotMutatePlayback() {
        AnimationPlayer player = new AnimationPlayer();
        AnimationClip clip = clip(3, 0, true);
        player.play(clip, 0, 0);
        assertThrows(NullPointerException.class, () -> player.play(null, 1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> player.play(clip, 1, Double.NaN));
        assertEquals(1, player.sample(1).weight());
        assertEquals(3, values(player, 1).x());
        player.stop(1, 0);
        assertEquals(AnimationPlayer.Sample.EMPTY, player.sample(1));
    }

    @Test
    void authoredPrototypePairsPreserveLocalMotionWhenInterrupted() throws Exception {
        var clips = new ArrayList<AnimationClip>();
        for (String name : new String[]{"slide", "bullet_jump", "slide_to_bullet_jump"}) {
            try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                    "/assets/ninjas_free_play/ninja_animations/" + name + ".json")), StandardCharsets.UTF_8)) {
                clips.add(AnimationJsonCodec.read(reader));
            }
        }
        for (AnimationClip first : clips) for (AnimationClip second : clips) {
            for (double time : new double[]{0.07, 0.34, 0.65}) {
                AnimationPlayer player = new AnimationPlayer();
                player.play(first, 0, 0.15);
                AnimationPose previous = player.sample(time).pose(), before = player.sample(time - STEP).pose();
                player.play(second, time, 0.15);
                AnimationPose at = player.sample(time).pose(), after = player.sample(time + STEP).pose();
                for (Bone bone : Bone.values()) {
                    near(previous.bone(bone).position(), at.bone(bone).position(), 1e-8);
                    near(previous.bone(bone).rotation(), at.bone(bone).rotation(), 1e-8);
                    near(slope(before.bone(bone).position(), previous.bone(bone).position()),
                            slope(at.bone(bone).position(), after.bone(bone).position()), 0.05);
                    near(slope(before.bone(bone).rotation(), previous.bone(bone).rotation()),
                            slope(at.bone(bone).rotation(), after.bone(bone).rotation()), 0.05);
                    double oldBendVelocity = (previous.bone(bone).bend() - before.bone(bone).bend()) / STEP;
                    double newBendVelocity = (after.bone(bone).bend() - at.bone(bone).bend()) / STEP;
                    assertEquals(oldBendVelocity, newBendVelocity, 0.05);
                }
            }
        }
    }
}
