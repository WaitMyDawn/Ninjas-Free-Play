package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.api.movement.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParkourControllerTest {
    private static final MovementValues VALUES=new MovementValues(MovementAttribute::defaultValue);
    private static final WallContact WALL=new WallContact(0,-1,.2,true);
    private static MovementInput input(long seq,boolean crouch,boolean jump,boolean aim) {
        return new MovementInput(seq,crouch,jump,0,-45,false,true,0,1,aim);
    }
    private static ParkourFrame frame(double speed,WallContact wall) { return new ParkourFrame(0,speed,0,0,speed,wall); }
    private static void update(MovementSession state,long tick,boolean ground,WallContact wall) {
        state.update(true,ground,true,tick,VALUES,frame(.3,wall));
    }
    @Test void walkingFullSpeedStartsSlideWithoutRequiringSprint() {
        var state=new MovementSession();
        var input=new MovementInput(0,true,false,0,0,false,false,0,1,false);
        state.accept(input,true,true,true,true,0,VALUES,frame(.21585,WallContact.NONE));
        assertEquals(ParkourMode.SLIDE,state.parkour().mode());
        var low=new MovementSession();
        low.accept(input,true,true,true,true,0,VALUES,frame(.2,WallContact.NONE));
        assertEquals(ParkourMode.NONE,low.parkour().mode());
        assertTrue(low.posture().crouching());
    }
    @Test void airborneCrouchEdgeStartsOneKickAndLandingTransfersToSlide() {
        var state=new MovementSession();
        state.accept(input(0,true,false,false),true,false,true,true,0,VALUES,frame(.4,WallContact.NONE));
        assertEquals(ParkourMode.FLYING_KICK,state.parkour().mode());
        long strike=state.snapshot(0).parkour().instance();
        state.accept(input(1,true,false,false),true,false,true,true,1,VALUES,frame(.4,WallContact.NONE));
        assertEquals(strike,state.snapshot(1).parkour().instance());
        update(state,2,true,WallContact.NONE);
        assertEquals(ParkourMode.SLIDE,state.parkour().mode());
    }
    @Test void aVerticalFallCanStartAKickWithoutAHorizontalMovementButton() {
        var state=new MovementSession();
        state.accept(new MovementInput(0,true,false,0,0),true,false,true,true,0,VALUES,
                new ParkourFrame(0,0,0,-.5,0,WallContact.NONE));
        assertEquals(ParkourMode.FLYING_KICK,state.parkour().mode());
    }
    @Test void bulletJumpDoesNotTurnTheHeldCrouchKeyIntoAKick() {
        var state=new MovementSession();
        assertEquals(JumpKind.BULLET,state.accept(input(0,true,true,false),true,true,true,true,0,VALUES,frame(.4,WallContact.NONE)).jump());
        update(state,1,false,WallContact.NONE);
        assertEquals(ParkourMode.NONE,state.parkour().mode());
    }
    @Test void glideAndLatchConsumeTheSameFourSecondBudget() {
        var state=new MovementSession();
        state.accept(input(0,false,false,true),true,false,true,true,0,VALUES,frame(.3,WallContact.NONE));
        for(int tick=0;tick<80;tick++) {
            if(tick==20 || tick==40 || tick==60) state.accept(input(tick,false,false,true),true,false,true,true,tick,VALUES,frame(.3,WALL));
            update(state,tick,false,tick<20?WallContact.NONE:WALL);
            update(state,tick,false,tick<20?WallContact.NONE:WALL);
            assertTrue(state.parkour().mode().assisted());
        }
        assertEquals(4,state.resources().assistedSecondsUsed(),1e-9);
        state.accept(input(81,false,false,true),true,false,true,true,80,VALUES,frame(.3,WALL));
        update(state,80,false,WALL);
        assertEquals(ParkourMode.NONE,state.parkour().mode());
    }
    @Test void releasingAimDoesNotRefillTimeAndUnsupportedWallsOnlyGlide() {
        var state=new MovementSession();
        state.accept(input(0,false,false,true),true,false,true,true,0,VALUES,frame(.3,WALL));
        update(state,0,false,WALL);
        state.accept(input(1,false,false,false),true,false,true,true,1,VALUES,frame(.3,WALL));
        assertEquals(.05,state.resources().assistedSecondsUsed(),1e-9);
        state.accept(input(2,false,false,true),true,false,true,true,2,VALUES,frame(.3,new WallContact(0,-1,.2,false)));
        update(state,2,false,new WallContact(0,-1,.2,false));
        assertEquals(ParkourMode.AIM_GLIDE,state.parkour().mode());
        assertEquals(.1,state.resources().assistedSecondsUsed(),1e-9);
    }
    @Test void wallDashRefreshesThenSpendsOneJumpAndRespectsItsInterval() {
        var state=new MovementSession();
        state.resources().consumeBulletJump(VALUES);
        state.resources().consumeJump(VALUES);
        state.resources().consumeAssistedSeconds(VALUES,3.5);
        assertEquals(JumpKind.WALL,state.accept(input(0,false,true,true),true,false,true,true,0,VALUES,frame(.3,WALL)).jump());
        assertEquals(1,state.resources().jumpsUsed());
        assertFalse(state.resources().bulletJumpUsed());
        assertEquals(0,state.resources().assistedSecondsUsed());
        assertEquals(JumpKind.NONE,state.accept(input(1,false,true,true),true,false,true,true,1,VALUES,frame(.3,WALL)).jump());
        assertEquals(JumpKind.WALL,state.accept(input(2,false,true,true),true,false,true,true,7,VALUES,frame(.3,WALL)).jump());
        assertEquals(1,state.resources().jumpsUsed());
    }
    @Test void holdingJumpAwayFromWallsCannotCreateWallDashes() {
        var state=new MovementSession();
        state.accept(input(0,false,true,false),true,false,true,true,0,VALUES,frame(.3,WALL));
        assertEquals(JumpKind.NONE,state.accept(input(1,false,true,false),true,false,true,true,10,VALUES,frame(.3,WallContact.NONE)).jump());
        assertEquals(1,state.resources().jumpsUsed());
    }
    @Test void authoritativeRestorePreservesSpentBudgetOnAnotherClock() {
        var state=new MovementSession();
        state.accept(input(0,false,false,true),true,false,true,true,100,VALUES,frame(.3,WALL));
        update(state,100,false,WALL);
        var restored=new MovementSession();
        restored.restore(state.snapshot(100),500);
        assertEquals(state.snapshot(100).parkour(),restored.snapshot(500).parkour());
        assertEquals(.05,restored.resources().assistedSecondsUsed(),1e-9);
        restored.expire(541);
        assertEquals(ParkourMode.NONE,restored.parkour().mode());
    }
    @Test void wallCoverageRejectsSingleBlocksAndGapsWhileAcceptingTiledFaces() {
        assertFalse(WallCoverage.covers(List.of(new WallCoverage.Rectangle(-1,1,0,1)),-.3,.3,.02,1.35));
        assertFalse(WallCoverage.covers(List.of(new WallCoverage.Rectangle(-1,1,0,.6),new WallCoverage.Rectangle(-1,1,.61,2)),-.3,.3,.02,1.35));
        assertFalse(WallCoverage.covers(List.of(new WallCoverage.Rectangle(-1,-.01,0,2),new WallCoverage.Rectangle(.01,1,0,2)),-.3,.3,.02,1.35));
        assertTrue(WallCoverage.covers(List.of(new WallCoverage.Rectangle(-1,0,0,2),new WallCoverage.Rectangle(0,1,0,1),new WallCoverage.Rectangle(0,1,1,2)),-.3,.3,.02,1.35));
    }
    @Test void lifecyclePublishesAllParkourActionsWithoutResurrectingOldJumpActions() {
        var tracker=new MovementActionTracker();
        var state=new MovementSession();
        state.accept(input(0,true,false,false),true,false,true,true,0,VALUES,frame(.3,WallContact.NONE));
        assertEquals(MovementAction.FLYING_KICK,tracker.reconcile(state.snapshot(0),true,0).get(0).action());
        state.accept(input(1,false,false,true),true,false,true,true,1,VALUES,frame(.3,WALL));
        var transitions=tracker.reconcile(state.snapshot(1),true,1);
        assertTrue(transitions.stream().anyMatch(s -> s.action()==MovementAction.WALL_LATCH&&s.phase()==ActionPhase.START));
        state.accept(input(2,false,true,true),true,false,true,true,2,VALUES,frame(.3,WALL));
        transitions=tracker.reconcile(state.snapshot(2),true,2);
        assertTrue(transitions.stream().anyMatch(s -> s.action()==MovementAction.WALL_DASH&&s.phase()==ActionPhase.START));
    }
}
