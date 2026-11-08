package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.api.movement.*;
import static org.junit.jupiter.api.Assertions.*;

class GlideLayerTest {
    private static final MovementValues VALUES=new MovementValues(MovementAttribute::defaultValue);
    private static final ParkourFrame FRAME=new ParkourFrame(0,.5,0,-.2,.5,WallContact.NONE);
    private static MovementInput input(long seq,boolean crouch,boolean jump,boolean roll,boolean aim) {
        return new MovementInput(seq,crouch,jump,0,0,roll,false,0,1,aim);
    }
    private static void update(MovementSession state,long tick) {state.update(true,false,true,tick,VALUES,FRAME);}

    @Test void rollAndGlidePublishIndependentConcurrentLifecyclesWithoutRefillingTime() {
        var state=new MovementSession();var tracker=new MovementActionTracker();
        state.accept(input(0,false,false,true,true),true,false,true,true,0,VALUES,FRAME);
        update(state,0);
        tracker.reconcile(state.snapshot(0),true,0);
        assertTrue(tracker.state(MovementAction.ROLL,0).startedThisTick());
        assertTrue(tracker.state(MovementAction.AIM_GLIDE,0).startedThisTick());
        long instance=state.snapshot(0).parkour().glide().instance();
        for(int tick=1;tick<=12;tick++) update(state,tick);
        assertFalse(state.posture().rolling(12));
        assertTrue(state.parkour().gliding());
        assertEquals(instance,state.snapshot(12).parkour().glide().instance());
        assertEquals(.65,state.resources().assistedSecondsUsed(),1e-9);
        tracker.reconcile(state.snapshot(12),true,12);
        assertEquals(ActionPhase.ACTIVE,tracker.state(MovementAction.AIM_GLIDE,12).phase());
    }

    @Test void aimingDuringKickPreservesTheStrikeAndReleaseEndsOnlyGlide() {
        var state=new MovementSession();var tracker=new MovementActionTracker();
        state.accept(input(0,true,false,false,false),true,false,true,true,0,VALUES,FRAME);
        long strike=state.snapshot(0).parkour().instance();
        state.accept(input(1,true,false,false,true),true,false,true,true,1,VALUES,FRAME);update(state,1);
        tracker.reconcile(state.snapshot(1),true,1);
        assertTrue(tracker.state(MovementAction.FLYING_KICK,1).isActive());
        assertTrue(tracker.state(MovementAction.AIM_GLIDE,1).isActive());
        state.accept(input(2,true,false,false,false),true,false,true,true,2,VALUES,FRAME);update(state,2);
        tracker.reconcile(state.snapshot(2),true,2);
        assertEquals(ParkourMode.FLYING_KICK,state.parkour().mode());
        assertEquals(strike,state.snapshot(2).parkour().instance());
        assertEquals(ActionEndReason.RELEASED,tracker.state(MovementAction.AIM_GLIDE,2).endReason());
        assertTrue(tracker.state(MovementAction.FLYING_KICK,2).isActive());
        assertEquals(.05,state.resources().assistedSecondsUsed(),1e-9);
    }

    @Test void glideInterruptsBulletPresentationWithoutRefundingJumpOrBulletEligibility() {
        var state=new MovementSession();var tracker=new MovementActionTracker();
        state.accept(input(0,true,true,false,false),true,true,true,true,0,VALUES,FRAME);
        tracker.reconcile(state.snapshot(0),true,0);
        state.accept(input(1,true,false,false,true),true,false,true,true,1,VALUES,FRAME);
        tracker.reconcile(state.snapshot(1),true,1);
        assertEquals(JumpKind.NONE,state.snapshot(1).action());
        assertTrue(state.parkour().gliding());
        assertEquals(1,state.resources().jumpsUsed());
        assertTrue(state.resources().bulletJumpUsed());
        assertTrue(tracker.state(MovementAction.BULLET_JUMP,1).endedThisTick());
        assertTrue(tracker.state(MovementAction.AIM_GLIDE,1).startedThisTick());
        state.accept(input(2,false,false,false,false),true,false,true,true,2,VALUES,FRAME);
        assertEquals(JumpKind.NONE,state.snapshot(2).action());
    }

    @Test void restoringAnInterruptedBulletRetainsTheSameTickJumpGuard() {
        var state=new MovementSession();
        state.accept(input(0,true,true,false,false),true,true,true,true,50,VALUES,FRAME);
        state.accept(input(1,false,false,false,true),true,false,true,true,50,VALUES,FRAME);
        var restored=new MovementSession();restored.restore(state.snapshot(50),300);
        assertEquals(JumpKind.NONE,restored.accept(input(2,false,true,false,false),true,false,true,true,300,VALUES,FRAME).jump());
        assertEquals(1,restored.resources().jumpsUsed());
        restored.accept(input(3,false,false,false,false),true,false,true,true,301,VALUES,FRAME);
        assertEquals(JumpKind.AIR,restored.accept(input(4,false,true,false,false),true,false,true,true,301,VALUES,FRAME).jump());
    }

    @Test void restoredCombinedLayersKeepTheirClocksAndBudgetAcrossDifferentTickOrigins() {
        var state=new MovementSession();
        state.accept(input(0,true,false,false,true),true,false,true,true,100,VALUES,FRAME);update(state,100);update(state,101);
        var snapshot=state.snapshot(101);var restored=new MovementSession();restored.restore(snapshot,500);
        assertEquals(snapshot.parkour(),restored.snapshot(500).parkour());
        restored.accept(input(1,true,false,false,false),true,false,true,true,501,VALUES,FRAME);
        restored.accept(input(2,true,false,false,true),true,false,true,true,502,VALUES,FRAME);update(restored,502);
        assertEquals(.15,restored.resources().assistedSecondsUsed(),1e-9);
        assertTrue(restored.snapshot(502).parkour().glide().instance()>snapshot.parkour().glide().instance());
    }

    @Test void exhaustingGlideTimeDoesNotEndAnOngoingRollOrRefillTheSharedBudget() {
        var values=new MovementValues(a -> a==MovementAttribute.AIR_MANEUVER_DURATION?.1:a==MovementAttribute.ROLL_DURATION?2:a.defaultValue());
        var state=new MovementSession();var tracker=new MovementActionTracker();
        state.accept(input(0,false,false,true,true),true,false,true,true,0,values,FRAME);
        state.update(true,false,true,0,values,FRAME);tracker.reconcile(state.snapshot(0),true,0);
        state.update(true,false,true,1,values,FRAME);state.update(true,false,true,2,values,FRAME);
        tracker.reconcile(state.snapshot(2),true,2);
        assertTrue(state.posture().rolling(2));
        assertFalse(state.parkour().gliding());
        assertEquals(.1,state.resources().assistedSecondsUsed(),1e-9);
        assertEquals(ActionEndReason.COMPLETED,tracker.state(MovementAction.AIM_GLIDE,2).endReason());
        assertTrue(tracker.state(MovementAction.ROLL,2).isActive());
    }
}
