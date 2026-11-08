package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RollDriveTest {
    @Test void driveReplacesFrictionLossWithoutErasingPerpendicularMotion() {
        var velocity = RollDrive.sustain(0.2, 0.6 * 0.546, 0, 0.6, 0.3);
        assertEquals(0.2, velocity.x(), 1e-9);
        assertEquals(0.6, velocity.z(), 1e-9);
    }
    @Test void fasterMomentumIsRetainedAndTheEndingDriveTapers() {
        assertEquals(1.2, RollDrive.sustain(0, 1.2, 0, 0.6, 0.3).z(), 1e-9);
        assertEquals(0.18, RollDrive.sustain(0, 0, 0, 0.6, 1).z(), 1e-9);
    }
    @Test void variantsKeepTravelAndBodyFacingSeparate() {
        assertEquals(0, RollKind.LEFT.bodyYaw(-90), 1e-6);
        assertEquals(0, RollKind.RIGHT.bodyYaw(90), 1e-6);
        assertEquals(0, RollKind.BACKWARD.bodyYaw(180), 1e-6);
        assertEquals(RollKind.LEFT, RollKind.fromInput(1, 0.3f));
        assertEquals(RollKind.BACKWARD, RollKind.fromInput(0.2f, -1));
    }
    @Test void fastIncomingMomentumSurvivesSidewaysAndBackwardRolls() {
        for (double yaw : new double[]{0, -90, 90, 180}) {
            var velocity = RollDrive.sustain(0.3, 1.2, yaw, 0.6, 0.2);
            assertEquals(0.3, velocity.x(), 1e-9);
            assertEquals(1.2, velocity.z(), 1e-9);
        }
    }
    @Test void sideAndBackwardActionsDoNotUseTheHalfHeightPosture() {
        var state = new MovementSession();
        var values = new MovementValues(MovementAttribute::defaultValue);
        var input = new MovementInput(0, true, false, 0, 0, true, false, 1, 0);
        assertTrue(state.accept(input, true, true, true, true, 0, values).rollStarted());
        assertFalse(state.posture().low(1));
        assertEquals(RollKind.LEFT, state.snapshot(1).posture().rollKind());
    }
    @Test void insufficientAcrobaticsClearanceDoesNotSpendTheAirAllowance() {
        var state = new MovementSession();
        var input = new MovementInput(0, false, false, 0, 0, true, false, 0, -1);
        assertFalse(state.accept(input, true, false, true, false, 0, new MovementValues(MovementAttribute::defaultValue)).rollStarted());
        assertFalse(state.snapshot(0).posture().airRollUsed());
    }
}
