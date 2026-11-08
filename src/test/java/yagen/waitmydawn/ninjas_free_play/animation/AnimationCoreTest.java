package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimationCoreTest {
    private static final double EPSILON = 1e-6;

    private static KeyframeTrack track(Vector3 from, Vector3 to) {
        return new KeyframeTrack(List.of(new KeyframeTrack.Keyframe(0, from), new KeyframeTrack.Keyframe(1, to)), KeyframeTrack.Interpolation.LINEAR);
    }

    private static void vector(Vector3 expected, Vector3 actual) {
        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }

    @Test
    void quaternionCompositionUsesParentThenLocalAxes() {
        Rotation x = Rotation.fromEuler(new Vector3(Math.PI / 2, 0, 0));
        Rotation z = Rotation.fromEuler(new Vector3(0, 0, Math.PI / 2));
        vector(new Vector3(0, 0, 1), x.apply(new Vector3(0, 1, 0)));
        vector(new Vector3(0, 0, 1), z.multiply(x).apply(new Vector3(0, 1, 0)));
        Vector3 angles = new Vector3(0.37, -0.51, 1.27);
        vector(angles, Rotation.fromEuler(angles).toEuler());
    }

    @Test
    void rigRotatesShouldersAboutHipAndPreservesSlimOffset() {
        AnimationPose pose = new AnimationPose(Map.of(Bone.BODY,
                new BonePose(Vector3.ZERO, new Vector3(0, 0, Math.PI / 2), 0)));
        var normal = HumanoidRig.evaluateBody(pose, false);
        vector(new Vector3(10, 17, 0), normal.get(Bone.LEFT_ARM).position());
        vector(new Vector3(0, 12, 0), normal.get(Bone.BODY).transform(new Vector3(0, 12, 0)));
        vector(new Vector3(1.9, 12, 0), normal.get(Bone.LEFT_LEG).position());
        vector(new Vector3(9.5, 17, 0), HumanoidRig.evaluateBody(pose, true).get(Bone.LEFT_ARM).position());
    }

    @Test
    void authoredRotationTracksRetainWholeTurns() {
        KeyframeTrack turns = track(Vector3.ZERO, new Vector3(0, 0, Math.PI * 4));
        assertEquals(Math.PI, turns.sample(0.25).z(), EPSILON);
        assertEquals(Math.PI * 2, turns.sample(0.5).z(), EPSILON);
        assertEquals(Math.PI * 4, turns.sample(2).z(), EPSILON);
    }

    @Test
    void poseTransitionsChooseEquivalentShortRotation() {
        BonePose fullTurn = new BonePose(Vector3.ZERO, new Vector3(0, 0, Math.PI * 2), 0);
        assertEquals(0, BonePose.IDENTITY.blend(fullTurn, 0.5).rotation().z(), EPSILON);
    }

    @Test
    void bezierTimeHandlesPreserveSmoothstepAndNonUniformTiming() {
        var first = new KeyframeTrack.Keyframe(0, Vector3.ZERO, KeyframeTrack.Interpolation.BEZIER,
                Vector3.ZERO, Vector3.ZERO, new Vector3(1.0 / 3, 1.0 / 3, 1.0 / 3), Vector3.ZERO);
        var last = new KeyframeTrack.Keyframe(1, new Vector3(8, 4, 2), KeyframeTrack.Interpolation.BEZIER,
                new Vector3(-1.0 / 3, -1.0 / 3, -1.0 / 3), Vector3.ZERO, Vector3.ZERO, Vector3.ZERO);
        var smooth = new KeyframeTrack(List.of(first, last), KeyframeTrack.Interpolation.LINEAR);
        vector(new Vector3(1.25, 0.625, 0.3125), smooth.sample(0.25));
        first = new KeyframeTrack.Keyframe(0, Vector3.ZERO, KeyframeTrack.Interpolation.BEZIER,
                Vector3.ZERO, Vector3.ZERO, new Vector3(0.1, 0, 0), new Vector3(4, 0, 0));
        last = new KeyframeTrack.Keyframe(1, new Vector3(8, 0, 0), KeyframeTrack.Interpolation.BEZIER,
                new Vector3(-0.2, 0, 0), new Vector3(-1, 0, 0), Vector3.ZERO, Vector3.ZERO);
        assertEquals(2.796875, new KeyframeTrack(List.of(first, last), KeyframeTrack.Interpolation.LINEAR).sample(0.1703125).x(), EPSILON);
    }

    @Test
    void stepKeyframeTakesPrecedenceOverIncomingBezierHandle() {
        var first = new KeyframeTrack.Keyframe(0, Vector3.ZERO, KeyframeTrack.Interpolation.STEP,
                Vector3.ZERO, Vector3.ZERO, Vector3.ZERO, Vector3.ZERO);
        var last = new KeyframeTrack.Keyframe(1, new Vector3(8, 0, 0), KeyframeTrack.Interpolation.BEZIER,
                Vector3.ZERO, Vector3.ZERO, Vector3.ZERO, Vector3.ZERO);
        var mixed = new KeyframeTrack(List.of(first, last), KeyframeTrack.Interpolation.LINEAR);
        assertEquals(0, mixed.sample(0.999).x(), EPSILON);
        assertEquals(8, mixed.sample(1).x(), EPSILON);
    }

    @Test
    void clipLoopsAndClampsAtExplicitBoundaries() {
        var channels = Map.of(Bone.ROOT, new AnimationClip.Track(track(Vector3.ZERO, new Vector3(4, 0, 0)), null, null));
        AnimationClip loop = new AnimationClip(1, true, channels);
        AnimationClip once = new AnimationClip(1, false, channels);
        assertEquals(0, loop.sample(1).bone(Bone.ROOT).position().x(), EPSILON);
        assertEquals(1, loop.sample(1.25).bone(Bone.ROOT).position().x(), EPSILON);
        assertEquals(4, once.sample(4).bone(Bone.ROOT).position().x(), EPSILON);
        assertEquals(0, once.sample(-1).bone(Bone.ROOT).position().x(), EPSILON);
    }

    @Test
    void rejectsInvalidTracksAndNonFiniteInputs() {
        assertThrows(IllegalArgumentException.class, () -> new Vector3(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Rotation(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> track(Vector3.ZERO, Vector3.ZERO).sample(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new KeyframeTrack(List.of(new KeyframeTrack.Keyframe(0, Vector3.ZERO),
                new KeyframeTrack.Keyframe(0, Vector3.ZERO)), KeyframeTrack.Interpolation.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> new AnimationClip(0.5, false,
                Map.of(Bone.ROOT, new AnimationClip.Track(track(Vector3.ZERO, Vector3.ZERO), null, null))));
    }

    @Test
    void zeroBendIsIdentityAndBandEdgesAreContinuous() {
        vector(new Vector3(1, 8, -2), new LimbDeformer(0, 4, 10).position(new Vector3(1, 8, -2)));
        LimbDeformer bend = new LimbDeformer(1.7, 6, 10);
        for (double edge : new double[]{1, 11}) {
            Vector3 before = bend.position(new Vector3(0, edge - 1e-8, 2));
            Vector3 after = bend.position(new Vector3(0, edge + 1e-8, 2));
            vector(before, after);
        }
        assertEquals(0, bend.angleAt(0), EPSILON);
        assertEquals(1.7, bend.angleAt(12), EPSILON);
    }

    @Test
    void normalUsesInverseTransposeForCurvedSurface() {
        LimbDeformer bend = new LimbDeformer(1.4, 6, 10);
        Vector3 point = new Vector3(1, 5, 2);
        Vector3 normal = bend.normal(point, new Vector3(0, 0, 1));
        Vector3 start = bend.position(point);
        Vector3 next = bend.position(point.add(new Vector3(0, 1e-6, 0)));
        Vector3 tangent = next.add(start.scale(-1)).normalized();
        assertEquals(0, normal.x() * tangent.x() + normal.y() * tangent.y() + normal.z() * tangent.z(), EPSILON);
        assertThrows(IllegalArgumentException.class, () -> new LimbDeformer(2, 6, 1).normal(new Vector3(1, 6, 2), new Vector3(0, 0, 1)));
    }

    @Test
    void renderSamplingDoesNotAdvancePlaybackAndStopFades() {
        var clip = new AnimationClip(1, true, Map.of(Bone.ROOT,
                new AnimationClip.Track(track(Vector3.ZERO, new Vector3(4, 0, 0)), null, null)));
        AnimationPlayer player = new AnimationPlayer();
        player.play(clip, 10, 0.2);
        assertEquals(0, player.sample(10).weight(), EPSILON);
        assertEquals(0.5, player.sample(10.1).weight(), EPSILON);
        for (int frame = 0; frame < 1000; frame++) player.sample(10 + frame / 2000.0);
        assertEquals(2, player.sample(10.5).pose().bone(Bone.ROOT).position().x(), EPSILON);
        player.stop(10.5);
        assertEquals(0.5, player.sample(10.6).weight(), EPSILON);
        assertEquals(0, player.sample(10.7).weight(), EPSILON);
    }

    @Test
    void interruptingPlaybackStartsAtThePreviousPose() {
        AnimationPlayer player = new AnimationPlayer();
        var first = new AnimationClip(1, true, Map.of(Bone.ROOT,
                new AnimationClip.Track(track(Vector3.ZERO, new Vector3(4, 0, 0)), null, null)));
        var second = new AnimationClip(1, true, Map.of(Bone.ROOT,
                new AnimationClip.Track(track(new Vector3(10, 0, 0), new Vector3(20, 0, 0)), null, null)));
        player.play(first, 0, 0.2);
        player.play(second, 0.5, 0.2);
        assertEquals(2, player.sample(0.5).pose().bone(Bone.ROOT).position().x(), EPSILON);
        assertEquals(6.85, player.sample(0.6).pose().bone(Bone.ROOT).position().x(), EPSILON);
    }
}
