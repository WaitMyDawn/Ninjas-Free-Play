package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PostureControllerTest {
    private static final MovementValues VALUES = new MovementValues(MovementAttribute::defaultValue);
    private static MovementInput input(long sequence, boolean crouch, boolean jump, boolean roll, boolean sprint, float x, float z) {
        return new MovementInput(sequence, crouch, jump, 0, 0, roll, sprint, x, z);
    }

    @Test void sprintCancelsCrouchUntilTheCrouchKeyIsReleased() {
        var state = new MovementSession();
        state.accept(input(0, true, false, false, false, 0, 1), true, true, true, 0, VALUES);
        assertTrue(state.crouchHeld());
        state.accept(input(1, true, false, false, true, 0, 1), true, true, true, 1, VALUES);
        assertFalse(state.crouchHeld());
        state.accept(input(2, true, false, false, false, 0, 1), true, true, true, 2, VALUES);
        assertFalse(state.crouchHeld());
        state.accept(input(3, false, false, false, false, 0, 0), true, true, true, 3, VALUES);
        state.accept(input(4, true, false, false, false, 0, 0), true, true, true, 4, VALUES);
        assertTrue(state.crouchHeld());
    }

    @Test void sprintCannotStandIntoACeiling() {
        var state = new MovementSession();
        state.accept(input(0, true, false, false, false, 0, 1), true, true, false, 0, VALUES);
        state.accept(input(1, true, false, false, true, 0, 1), true, true, false, 1, VALUES);
        assertTrue(state.crouchHeld());
        state.accept(input(2, false, false, false, false, 0, 0), true, true, false, 2, VALUES);
        assertTrue(state.posture().low(2));
        state.observe(true, true, true, 3);
        assertFalse(state.posture().low(3));
    }

    @Test void sprintCancelsOnlyThePoseAndKeepsTheBulletJumpCombination() {
        var state = new MovementSession();
        state.accept(input(0, true, false, false, true, 0, 1), true, true, true, 0, VALUES);
        assertFalse(state.crouchHeld());
        var jump = state.accept(input(1, true, true, false, true, 0, 1), true, true, true, 1, VALUES);
        assertEquals(JumpKind.BULLET, jump.jump());
        assertFalse(state.posture().crouching());
        assertTrue(state.snapshot(1).crouchHeld());
    }

    @Test void sprintDoesNotRejectOrStopARoll() {
        var state = new MovementSession();
        assertTrue(state.accept(input(0, true, false, true, true, 0, 1), true, true, true, 0, VALUES).rollStarted());
        state.accept(input(1, true, false, false, true, 0, 1), true, true, true, 1, VALUES);
        assertTrue(state.posture().rolling(1));
    }

    @Test void rollDoesNotSpendJumpsAndHoldingTheKeyCannotRepeatIt() {
        var state = new MovementSession();
        assertTrue(state.accept(input(0, false, false, true, false, 0, 1), true, true, true, 0, VALUES).rollStarted());
        assertEquals(0, state.resources().jumpsUsed());
        assertFalse(state.accept(input(1, false, false, true, false, 0, 1), true, true, true, 30, VALUES).rollStarted());
        assertEquals(0, state.snapshot(30).posture().rollSequence());
    }

    @Test void eachAirborneCycleHasOnlyOneAirRoll() {
        var state = new MovementSession();
        assertTrue(state.accept(input(0, false, false, true, false, 1, 0), true, false, true, 0, VALUES).rollStarted());
        assertEquals(1, state.resources().jumpsUsed());
        state.accept(input(1, false, false, false, false, 0, 0), true, false, true, 20, VALUES);
        assertFalse(state.accept(input(2, false, false, true, false, 0, 1), true, false, true, 21, VALUES).rollStarted());
        state.observe(true, true, true, 22);
        state.accept(input(3, false, false, false, false, 0, 0), true, true, true, 23, VALUES);
        assertTrue(state.accept(input(4, false, false, true, false, 0, 1), true, false, true, 24, VALUES).rollStarted());
    }

    @Test void aGroundRollThatLeavesTheLedgeUsesTheAirRollAllowance() {
        var state = new MovementSession();
        state.accept(input(0, false, false, true, false, 0, 1), true, true, true, 0, VALUES);
        state.observe(true, false, true, 1);
        assertTrue(state.snapshot(1).posture().airRollUsed());
        state.accept(input(1, false, false, false, false, 0, 0), true, false, true, 20, VALUES);
        assertFalse(state.accept(input(2, false, false, true, false, 0, 1), true, false, true, 21, VALUES).rollStarted());
    }

    @Test void movementInputDeterminesRollDirectionAndIdleFallsBackToView() {
        for (float[] direction : new float[][]{{1, 0, -90}, {-1, 0, 90}, {0, -1, 180}, {0, 0, 0}}) {
            var state = new MovementSession();
            state.accept(input(0, false, false, true, false, direction[0], direction[1]), true, true, true, 0, VALUES);
            assertEquals(0, Math.IEEEremainder(state.snapshot(0).posture().rollYaw() - direction[2], 360), 1e-5);
        }
    }

    @Test void aJumpCanInterruptARollWithoutWaitingForTheAnimation() {
        var state = new MovementSession();
        state.accept(input(0, false, false, true, false, 0, 1), true, true, true, 0, VALUES);
        var jump = state.accept(input(1, true, true, false, false, 0, 1), true, true, true, 1, VALUES);
        assertEquals(JumpKind.BULLET, jump.jump());
        assertFalse(state.posture().rolling(1));
    }

    @Test void rollExpiryPreservesTheLowPoseUntilStandingSpaceIsAvailable() {
        var state = new MovementSession();
        state.accept(input(0, false, false, true, false, 0, 1), true, true, true, 0, VALUES);
        state.observe(true, true, false, VALUES.rollTicks());
        assertFalse(state.posture().rolling(VALUES.rollTicks()));
        assertTrue(state.posture().low(VALUES.rollTicks()));
        state.observe(true, true, true, VALUES.rollTicks() + 1);
        assertFalse(state.posture().low(VALUES.rollTicks() + 1));
    }

    @Test void relativeTimersSurviveAnAuthoritativeClockChange() {
        var original = new MovementSession();
        original.accept(input(0, false, false, true, false, 0, 1), true, false, true, 100, VALUES);
        var restored = new MovementSession();
        restored.restore(original.snapshot(104), 999);
        assertTrue(restored.posture().rolling(1006));
        assertFalse(restored.posture().rolling(1007));
        assertTrue(restored.snapshot(999).posture().airRollUsed());
    }

    @Test void invalidModesCancelRollsWithoutRefundingTheAirAllowance() {
        var state = new MovementSession();
        state.accept(input(0, false, false, true, false, 0, 1), true, false, true, 0, VALUES);
        state.observe(false, false, true, 1);
        assertFalse(state.posture().rolling(1));
        assertTrue(state.snapshot(1).posture().airRollUsed());
    }
}
