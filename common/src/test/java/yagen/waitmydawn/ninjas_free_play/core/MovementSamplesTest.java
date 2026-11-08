package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import static org.junit.jupiter.api.Assertions.*;

class MovementSamplesTest {
    @Test void batchingTwoPositionPacketsDoesNotDoubleTheSimulationSpeed() {
        var samples = new MovementSamples();
        samples.record(Vector3.ZERO, new Vector3(.2, .1, .3), 5);
        samples.record(new Vector3(.2, .1, .3), new Vector3(.4, .2, .6), 5);
        assertEquals(new Vector3(.2, .1, .3), samples.sample(5));
        assertEquals(samples.sample(5), samples.sample(7));
        assertEquals(Vector3.ZERO, samples.sample(9));
    }

    @Test void aRealStopClearsMotionAndTeleportsCannotBecomeMovementQualifications() {
        var samples = new MovementSamples();
        samples.record(Vector3.ZERO, new Vector3(.4, 0, 0), 5);
        samples.record(new Vector3(.4, 0, 0), new Vector3(.4, 0, 0), 6);
        assertEquals(Vector3.ZERO, samples.sample(6));
        samples.record(Vector3.ZERO, new Vector3(100, 5, -100), 7);
        assertEquals(Vector3.ZERO, samples.sample(7));
        samples.record(Vector3.ZERO, new Vector3(0, -.2, -.3), 8);
        samples.reset();
        assertEquals(Vector3.ZERO, samples.sample(8));
    }

    @Test void slidePredictionToleratesSamplingNoiseButCorrectsLargeVelocityDifferences() {
        assertFalse(SlidePrediction.needsVelocityCorrection(.324, .315));
        assertFalse(SlidePrediction.needsVelocityCorrection(2.05, 2));
        assertTrue(SlidePrediction.needsVelocityCorrection(.6, .324));
        assertTrue(SlidePrediction.needsVelocityCorrection(.1, .324));
        assertThrows(IllegalArgumentException.class, () -> SlidePrediction.needsVelocityCorrection(Double.NaN, .3));
    }
}
