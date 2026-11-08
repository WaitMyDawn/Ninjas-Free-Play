package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.api.movement.*;
import static org.junit.jupiter.api.Assertions.*;

class MovementActionTrackerTest {
    private static final MovementValues VALUES = new MovementValues(MovementAttribute::defaultValue);
    private final MovementSession movement = new MovementSession();
    private final MovementActionTracker tracker = new MovementActionTracker();
    private MovementInput roll(long sequence, boolean held) {
        return new MovementInput(sequence, false, false, 0, 0, held, true, 0, 1);
    }
    private java.util.List<ActionState> sync(long tick) { return tracker.reconcile(movement.snapshot(tick), true, tick); }

    @Test void aRollHasOneStartOneExecutionPerTickAndOneEnd() {
        movement.accept(roll(0,true),true,true,true,0,VALUES);
        var start=sync(0);
        assertEquals(1,start.size());
        assertEquals(ActionPhase.START,start.get(0).phase());
        assertTrue(sync(0).isEmpty());
        assertTrue(tracker.tick(0).isEmpty());
        assertEquals(ActionPhase.ACTIVE,tracker.tick(1).get(0).phase());
        assertTrue(tracker.tick(1).isEmpty());
        var end=sync(VALUES.rollTicks());
        assertEquals(ActionPhase.END,end.get(0).phase());
        assertEquals(ActionEndReason.COMPLETED,end.get(0).endReason());
        assertEquals(start.get(0).instanceId(),end.get(0).instanceId());
        assertTrue(sync(VALUES.rollTicks()).isEmpty());
        assertEquals(ActionPhase.IDLE,tracker.state(MovementAction.ROLL,20).phase());
        assertEquals(VALUES.rollTicks(),tracker.state(MovementAction.ROLL,20).elapsedTicks());
    }

    @Test void rejectedAndRetransmittedInputCannotCreateAnotherStart() {
        movement.accept(roll(0,true),true,false,true,false,0,VALUES);
        assertTrue(sync(0).isEmpty());
        movement.accept(roll(1,false),true,false,true,1,VALUES);
        movement.accept(roll(2,true),true,false,true,2,VALUES);
        assertEquals(1,sync(2).size());
        assertFalse(movement.accept(roll(2,true),true,false,true,2,VALUES).processed());
        assertTrue(sync(2).isEmpty());
    }

    @Test void aSecondJumpEndsTheFirstInstanceEvenWhenItsKindMatches() {
        var values = new MovementValues(attribute -> attribute==MovementAttribute.JUMP_COUNT ? 4 : attribute.defaultValue());
        movement.accept(new MovementInput(0,false,true,0,0),true,false,true,0,values);
        var first=sync(0).get(0);
        movement.accept(new MovementInput(1,false,false,0,0),true,false,true,1,values);
        movement.accept(new MovementInput(2,false,true,0,0),true,false,true,2,values);
        var changed=sync(2);
        assertEquals(2,changed.size());
        assertEquals(ActionPhase.END,changed.get(0).phase());
        assertEquals(ActionEndReason.REPLACED,changed.get(0).endReason());
        assertEquals(ActionPhase.START,changed.get(1).phase());
        assertNotEquals(first.instanceId(),changed.get(1).instanceId());
    }

    @Test void landingEndsTheJumpEvenWhenCrouchRemainsActive() {
        movement.accept(new MovementInput(0,true,true,0,0),true,true,true,0,VALUES);
        sync(0);
        movement.observe(true,false,true,1);
        movement.observe(true,true,true,2);
        var end=sync(2).stream().filter(state -> state.action()==MovementAction.BULLET_JUMP).findFirst().orElseThrow();
        assertEquals(ActionEndReason.LANDED,end.endReason());
        assertTrue(tracker.state(MovementAction.CROUCH,2).isActive());
    }

    @Test void aCompletedRollCannotRestartThePreviousBulletJump() {
        movement.accept(new MovementInput(0,true,true,0,0),true,true,true,0,VALUES);
        sync(0);
        movement.accept(roll(1,true),true,false,true,1,VALUES);
        var transitions=sync(1);
        assertTrue(transitions.stream().anyMatch(state -> state.action()==MovementAction.BULLET_JUMP && state.phase()==ActionPhase.END));
        sync(20);
        assertFalse(tracker.state(MovementAction.BULLET_JUMP,20).isActive());
        assertFalse(tracker.state(MovementAction.ROLL,20).isActive());
    }

    @Test void stoppingForAWorldLifecycleBoundaryIsIdempotent() {
        movement.accept(roll(0,true),true,false,true,0,VALUES);
        sync(0);
        var ended=tracker.stopAll(1,ActionEndReason.DIMENSION_CHANGED);
        assertEquals(1,ended.size());
        assertEquals(ActionEndReason.DIMENSION_CHANGED,ended.get(0).endReason());
        assertTrue(tracker.stopAll(1,ActionEndReason.DISCONNECTED).isEmpty());
    }

    @Test void readOnlyQueriesDoNotConsumeTheStartOrEndPhase() {
        movement.accept(roll(0,true),true,true,true,0,VALUES);
        sync(0);
        assertEquals(tracker.state(MovementAction.ROLL,0),tracker.state(MovementAction.ROLL,0));
        assertTrue(tracker.state(MovementAction.ROLL,0).startedThisTick());
        movement.posture().stopRoll(1);
        sync(1);
        assertTrue(tracker.state(MovementAction.ROLL,1).endedThisTick());
        assertEquals(ActionEndReason.INTERRUPTED,tracker.state(MovementAction.ROLL,1).endReason());
        assertTrue(sync(1).isEmpty());
    }

    @Test void ineligibleMovementEndsAllActionsWithoutRefiring() {
        movement.accept(roll(0,true),true,false,true,0,VALUES);
        sync(0);
        var ended=tracker.reconcile(movement.snapshot(1),false,1);
        assertEquals(ActionEndReason.INELIGIBLE,ended.get(0).endReason());
        assertTrue(tracker.reconcile(movement.snapshot(2),false,2).isEmpty());
    }
}
