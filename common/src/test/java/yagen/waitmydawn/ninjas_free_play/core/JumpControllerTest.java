package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JumpControllerTest {
    private static final MovementValues DEFAULTS = new MovementValues(MovementAttribute::defaultValue);

    private static JumpKind input(JumpController controller, long sequence, boolean crouch, boolean jump,
                                  boolean grounded, long tick) {
        return controller.accept(new MovementInput(sequence, crouch, jump, 30, -45), true, grounded, tick, DEFAULTS).jump();
    }

    @Test void groundJumpThenAirJumpExhaustsTheCycle() {
        var controller = new JumpController();
        assertEquals(JumpKind.GROUND, input(controller, 0, false, true, true, 0));
        input(controller, 1, false, false, false, 1);
        assertEquals(JumpKind.AIR, input(controller, 2, false, true, false, 2));
        input(controller, 3, false, false, false, 3);
        assertEquals(JumpKind.NONE, input(controller, 4, false, true, false, 4));
        assertEquals(2, controller.resources().jumpsUsed());
    }

    @Test void ordinaryJumpCanBeFollowedByOneAirBulletJump() {
        var controller = new JumpController();
        input(controller, 0, false, true, true, 0);
        input(controller, 1, true, false, false, 1);
        assertEquals(JumpKind.BULLET, input(controller, 2, true, true, false, 2));
        assertTrue(controller.resources().bulletJumpUsed());
        assertEquals(2, controller.resources().jumpsUsed());
    }

    @Test void holdingCrouchAfterBulletJumpFallsBackToAnOrdinaryAirJump() {
        var controller = new JumpController();
        assertEquals(JumpKind.BULLET, input(controller, 0, true, true, true, 0));
        input(controller, 1, true, false, false, 1);
        assertEquals(JumpKind.AIR, input(controller, 2, true, true, false, 2));
    }

    @Test void walkingOffSpendsTheGroundOpportunityButKeepsBulletEligibility() {
        var controller = new JumpController();
        controller.observe(true, true);
        controller.observe(true, false);
        assertEquals(1, controller.resources().jumpsUsed());
        assertEquals(JumpKind.BULLET, input(controller, 0, true, true, false, 1));
    }

    @Test void landingRefreshesBothResourcesWithoutCreatingAHeldKeyEdge() {
        var controller = new JumpController();
        input(controller, 0, true, true, true, 0);
        controller.observe(true, false);
        controller.observe(true, true);
        assertEquals(0, controller.resources().jumpsUsed());
        assertFalse(controller.resources().bulletJumpUsed());
        assertEquals(JumpKind.NONE, input(controller, 1, true, true, true, 20));
        input(controller, 2, true, false, true, 21);
        assertEquals(JumpKind.BULLET, input(controller, 3, true, true, true, 22));
    }

    @Test void repeatedAndOutOfOrderSequencesCannotCreateExtraActions() {
        var controller = new JumpController();
        input(controller, 8, true, true, true, 0);
        assertFalse(controller.accept(new MovementInput(8, false, false, 0, 0), true, false, 1, DEFAULTS).processed());
        input(controller, 7, false, false, false, 1);
        assertEquals(JumpKind.NONE, input(controller, 9, true, true, false, 2));
        assertEquals(1, controller.resources().jumpsUsed());
    }

    @Test void aPacketBurstCannotCreateMultipleJumpsInOneTick() {
        var controller = new JumpController();
        input(controller, 0, false, true, true, 1);
        input(controller, 1, false, false, false, 1);
        assertEquals(JumpKind.NONE, input(controller, 2, false, true, false, 1));
        assertEquals(1, controller.resources().jumpsUsed());
    }

    @Test void increasedCapacityStillAllowsOnlyOneBulletJump() {
        var controller = new JumpController();
        var values = new MovementValues(attribute -> attribute == MovementAttribute.JUMP_COUNT ? 4 : attribute.defaultValue());
        assertEquals(JumpKind.BULLET, controller.accept(new MovementInput(0, true, true, 0, 0), true, true, 0, values).jump());
        for (int i = 1; i < 4; i++) {
            controller.accept(new MovementInput(i * 2L - 1, true, false, 0, 0), true, false, i * 2L - 1, values);
            assertEquals(JumpKind.AIR, controller.accept(new MovementInput(i * 2L, true, true, 0, 0), true, false, i * 2L, values).jump());
        }
        assertEquals(4, controller.resources().jumpsUsed());
    }

    @Test void changingCapacityDoesNotRestoreTheSpentBulletJump() {
        var controller = new JumpController();
        input(controller, 0, true, true, true, 0);
        input(controller, 1, true, false, false, 1);
        var reduced = new MovementValues(attribute -> attribute == MovementAttribute.JUMP_COUNT ? 1 : attribute.defaultValue());
        assertEquals(JumpKind.NONE, controller.accept(new MovementInput(2, true, true, 0, 0), true, false, 2, reduced).jump());
        controller.accept(new MovementInput(3, true, false, 0, 0), true, false, 3, reduced);
        var increased = new MovementValues(attribute -> attribute == MovementAttribute.JUMP_COUNT ? 4 : attribute.defaultValue());
        assertEquals(JumpKind.AIR, controller.accept(new MovementInput(4, true, true, 0, 0), true, false, 4, increased).jump());
        assertTrue(controller.resources().bulletJumpUsed());
        assertEquals(2, controller.resources().jumpsUsed());
    }

    @Test void restorationAndReplayPreserveAJumpAfterAnOlderAcknowledgement() {
        var server = new JumpController();
        input(server, 0, false, true, true, 10);
        var snapshot = server.snapshot(11);
        var client = new JumpController();
        client.restore(snapshot, 200);
        input(client, 1, true, false, false, 201);
        assertEquals(JumpKind.BULLET, input(client, 2, true, true, false, 202));
        assertEquals(2, client.resources().jumpsUsed());
    }

    @Test void disabledModesAndInputTimeoutDoNotRefillResources() {
        var controller = new JumpController();
        input(controller, 0, true, true, true, 10);
        controller.observe(false, false);
        controller.expire(60);
        assertFalse(controller.crouchHeld());
        assertEquals(1, controller.resources().jumpsUsed());
        assertTrue(controller.resources().bulletJumpUsed());
    }

    @Test void delayedHeartbeatAfterTimeoutCannotManufactureANewJumpEdge() {
        var controller = new JumpController();
        input(controller, 0, true, true, true, 0);
        controller.observe(true, false);
        controller.expire(60);
        assertEquals(JumpKind.NONE, input(controller, 1, true, true, false, 61));
        assertEquals(1, controller.resources().jumpsUsed());
        input(controller, 2, true, false, false, 62);
        assertEquals(JumpKind.AIR, input(controller, 3, true, true, false, 63));
    }

    @Test void leavingAnUnsupportedModeStillRequiresANewJumpPress() {
        var controller = new JumpController();
        controller.accept(new MovementInput(0, false, true, 0, 0), false, false, 0, DEFAULTS);
        assertEquals(JumpKind.NONE, input(controller, 1, false, true, false, 1));
        input(controller, 2, false, false, false, 2);
        assertEquals(JumpKind.AIR, input(controller, 3, false, true, false, 3));
    }

    @Test void focusReturnRequiresAJumpRelease() {
        var gate = new InputGate();
        assertTrue(gate.sample(true, true, true).jump());
        assertFalse(gate.sample(false, true, true).crouch());
        assertFalse(gate.sample(true, true, true).jump());
        gate.sample(true, false, false);
        assertTrue(gate.sample(true, false, true).jump());
    }

    @Test void aJumpStartedWhileAScreenIsOpenMustNotFireOnReturn() {
        var gate = new InputGate();
        gate.sample(false, false, false);
        assertFalse(gate.sample(true, false, true).jump());
        gate.sample(true, false, false);
        assertTrue(gate.sample(true, false, true).jump());
    }
}
