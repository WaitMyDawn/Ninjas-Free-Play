package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import yagen.waitmydawn.ninjas_free_play.animation.resource.AnimationJsonCodec;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class LaunchOrientationTest {
    private static void near(Vector3 expected, Vector3 actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
        assertEquals(expected.z(), actual.z(), tolerance);
    }

    @Test
    void bodyAxisImpulseAndViewDirectionAgreeAcrossYawAndVerticalPitch() {
        for (double yaw : new double[]{0, 40, 90, 180, 270, -315}) {
            for (double pitch : new double[]{0, -30, -45, -60, -89.9, -90, 90}) {
                var orientation = LaunchOrientation.fromDegrees(yaw, pitch);
                near(orientation.direction(), orientation.worldModelRotation().apply(new Vector3(0, -1, 0)), 1e-9);
                near(orientation.direction().scale(0.9), orientation.impulsePerTick(0.9), 1e-9);
                for (double turn : new double[]{0, 0.7, Math.PI, Math.PI * 2, Math.PI * 5}) {
                    Rotation combined = orientation.worldModelRotation().multiply(Rotation.fromEuler(new Vector3(0, turn, 0)));
                    near(orientation.direction(), combined.apply(new Vector3(0, -1, 0)), 1e-9);
                }
            }
        }
    }

    @Test
    void verticalLimitRetainsStableRightAxisAndRejectsInvalidInputs() {
        var almost = LaunchOrientation.fromDegrees(47, -89.99999);
        var vertical = LaunchOrientation.fromDegrees(47, -90);
        near(almost.worldModelRotation().apply(new Vector3(1, 0, 0)), vertical.worldModelRotation().apply(new Vector3(1, 0, 0)), 1e-6);
        assertThrows(IllegalArgumentException.class, () -> LaunchOrientation.fromDegrees(0, -91));
        assertThrows(IllegalArgumentException.class, () -> LaunchOrientation.fromDegrees(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> vertical.impulsePerTick(-1));
    }

    @Test
    void facingInterruptionPreservesPhysicalAngularVelocityWithMovingTargets() {
        FacingPlayer player = new FacingPlayer(t -> Rotation.fromEuler(new Vector3(0, t * 0.4, 0)));
        player.play(t -> Rotation.fromEuler(new Vector3(0.6, t, 0.3)), 0, 0.3);
        double time = 0.12, step = 1e-6;
        Vector3 before = player.sample(time - step).apply(new Vector3(1, 0.7, 0.2));
        Vector3 current = player.sample(time).apply(new Vector3(1, 0.7, 0.2));
        Vector3 velocity = current.add(before.scale(-1)).scale(1 / step);
        player.play(t -> Rotation.fromEuler(new Vector3(-0.2, t * -0.7, -0.5)), time, 0.2);
        near(current, player.sample(time).apply(new Vector3(1, 0.7, 0.2)), 1e-9);
        near(velocity, player.sample(time + step).apply(new Vector3(1, 0.7, 0.2)).add(current.scale(-1)).scale(1 / step), 0.02);
        Rotation destination = Rotation.fromEuler(new Vector3(-0.2, 0.32 * -0.7, -0.5));
        near(destination.apply(new Vector3(1, 0, 0)), player.sample(0.32).apply(new Vector3(1, 0, 0)), 1e-9);
    }

    @Test
    void facingPiBranchAndRepeatedSamplingStayContinuous() {
        FacingPlayer player = new FacingPlayer(t -> Rotation.fromEuler(new Vector3(0, Math.PI - 0.001 + t, 0)));
        player.play(t -> Rotation.IDENTITY, 0.001, 0.15);
        Vector3 initial = player.sample(0.001).apply(new Vector3(1, 0, 0));
        near(initial, player.sample(0.00100001).apply(new Vector3(1, 0, 0)), 1e-6);
        for (int i = 0; i < 500; i++) player.sample(i / 1000.0);
        near(new Vector3(1, 0, 0), player.sample(0.2).apply(new Vector3(1, 0, 0)), 1e-9);
    }

    @Test
    void editableLaunchAssetTwistsAboutItsFlightAxis() throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/assets/ninjas_free_play/ninja_animations/bullet_jump.json")), StandardCharsets.UTF_8)) {
            AnimationClip clip = AnimationJsonCodec.read(reader);
            assertEquals(AnimationClip.CoordinateSpace.LAUNCH, clip.coordinateSpace());
            assertEquals(Math.PI * 2, clip.sample(clip.duration()).bone(Bone.ROOT).rotation().y(), 1e-6);
            for (double pitch : new double[]{0, -45, -90}) for (int index = 0; index <= 120; index++) {
                var orientation = LaunchOrientation.fromDegrees(33, pitch);
                Rotation full = orientation.worldModelRotation().multiply(Rotation.fromEuler(
                        clip.sample(clip.duration() * index / 120).bone(Bone.ROOT).rotation()));
                near(orientation.direction(), full.apply(new Vector3(0, -1, 0)), 1e-6);
            }
        }
    }

    @Test
    void aimCorrectionRetainsWorldDirectionThroughAFullBodyRoll() {
        Vector3 forward = new Vector3(0, 0, -1);
        Vector3 look = LaunchOrientation.fromDegrees(20, -35).direction();
        for (double roll : new double[]{0, 1.4, Math.PI, Math.PI * 2}) {
            Rotation root = LaunchOrientation.fromDegrees(60, -70).worldModelRotation()
                    .multiply(Rotation.fromEuler(new Vector3(0, roll, 0)));
            Rotation local = Rotation.between(forward, root.inverse().apply(look));
            near(look, root.multiply(local).apply(forward), 1e-8);
        }
        near(new Vector3(0, 0, 1), Rotation.between(forward, new Vector3(0, 0, 1)).apply(forward), 1e-9);
    }
}
