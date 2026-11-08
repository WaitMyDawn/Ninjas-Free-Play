package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WallDashPlanTest {
    private static final MovementValues VALUES=new MovementValues(MovementAttribute::defaultValue);
    private static MovementInput input(long sequence,float yaw,float strafe,float forward) {
        return new MovementInput(sequence,false,true,yaw,0,false,false,strafe,forward,false);
    }
    private static void keys(WallContact wall,float yaw,WallDashKind w,WallDashKind a,WallDashKind s,WallDashKind d) {
        assertEquals(w,WallDashPlan.resolve(input(0,yaw,0,1),wall).kind(),"W yaw="+yaw);
        assertEquals(a,WallDashPlan.resolve(input(0,yaw,1,0),wall).kind(),"A yaw="+yaw);
        assertEquals(s,WallDashPlan.resolve(input(0,yaw,0,-1),wall).kind(),"S yaw="+yaw);
        assertEquals(d,WallDashPlan.resolve(input(0,yaw,-1,0),wall).kind(),"D yaw="+yaw);
    }

    @Test void facingWallAndBothParallelSectorsUseCameraRelativeKeysOnEveryCardinalWall() {
        for (WallContact wall:new WallContact[]{new WallContact(0,-1,.1,true),new WallContact(1,0,.1,true),
                new WallContact(0,1,.1,true),new WallContact(-1,0,.1,true)}) {
            float facing=(float)Math.toDegrees(Math.atan2(wall.x(),-wall.z()));
            for(float delta:new float[]{-59,0,59}) keys(wall,facing+delta,WallDashKind.UP,WallDashKind.LEFT,WallDashKind.AWAY,WallDashKind.RIGHT);
            for(float delta:new float[]{60,90,120}) keys(wall,facing+delta,WallDashKind.RIGHT,WallDashKind.UP,WallDashKind.LEFT,WallDashKind.AWAY);
            for(float delta:new float[]{-60,-90,-120}) keys(wall,facing+delta,WallDashKind.LEFT,WallDashKind.AWAY,WallDashKind.RIGHT,WallDashKind.UP);
        }
    }

    @Test void launchVectorsHaveDistinctUpAlongAndAwayComponentsAndUnitMagnitude() {
        for (WallDashKind kind:new WallDashKind[]{WallDashKind.UP,WallDashKind.LEFT,WallDashKind.RIGHT,WallDashKind.AWAY}) {
            var direction=new WallDashPlan(kind,0,-1).direction();
            assertEquals(1,Math.sqrt(direction.x()*direction.x()+direction.y()*direction.y()+direction.z()*direction.z()),1e-9);
            assertTrue(direction.y()>0);
            if (kind==WallDashKind.UP) assertTrue(direction.y()>.99 && direction.z()>0);
            if (kind==WallDashKind.LEFT) assertTrue(direction.x()>.9 && direction.z()>0);
            if (kind==WallDashKind.RIGHT) assertTrue(direction.x()<-.9 && direction.z()>0);
            if (kind==WallDashKind.AWAY) assertTrue(direction.z()<-.8);
        }
    }

    @Test void touchingAWallOnTheGroundCannotReplaceNormalOrBulletTakeoff() {
        var wall=new WallContact(0,-1,.1,true);
        var frame=new ParkourFrame(0,.3,0,0,.3,wall);
        for(boolean crouch:new boolean[]{false,true}) {
            var session=new MovementSession();
            var input=new MovementInput(0,crouch,true,0,0,false,false,0,1,false);
            assertEquals(crouch?JumpKind.BULLET:JumpKind.GROUND,session.accept(input,true,true,true,true,0,VALUES,frame).jump());
            assertEquals(WallDashPlan.NONE,session.snapshot(0).parkour().wallDash());
        }
        var jumps=new JumpController();
        assertEquals(JumpKind.GROUND,jumps.accept(input(0,0,0,1),true,true,0,VALUES,true).jump());
    }

    @Test void launchPlanSurvivesLostContactAndAuthoritativeClockRestore() {
        var session=new MovementSession();
        var wall=new WallContact(0,-1,.1,true);
        session.accept(input(0,-90,0,1),true,false,true,true,10,VALUES,new ParkourFrame(0,0,0,0,0,wall));
        session.update(true,false,true,11,VALUES,ParkourFrame.EMPTY);
        assertEquals(WallDashKind.LEFT,session.snapshot(11).parkour().wallDash().kind());
        var restored=new MovementSession();restored.restore(session.snapshot(11),500);
        assertEquals(session.snapshot(11).parkour().wallDash(),restored.snapshot(500).parkour().wallDash());
        assertEquals(JumpKind.WALL,restored.snapshot(500).action());
    }

    @Test void aHeldGroundTakeoffCannotBecomeAWallChainAfterAirborneUpdatesOrRestore() {
        var wall=new WallContact(0,-1,.1,true);var frame=new ParkourFrame(0,.3,0,0,.3,wall);
        for(boolean crouch:new boolean[]{false,true}) {
            var session=new MovementSession();
            assertEquals(crouch?JumpKind.BULLET:JumpKind.GROUND,session.accept(
                    new MovementInput(0,crouch,true,0,0),true,true,true,true,0,VALUES,frame).jump());
            for(int tick=1;tick<=10;tick++) {
                session.update(true,false,true,tick,VALUES,frame);
                assertEquals(JumpKind.NONE,session.accept(input(tick,0,0,1),true,false,true,true,tick,VALUES,frame).jump());
                assertFalse(session.canRepeatWallJump());
            }
            assertEquals(1,session.resources().jumpsUsed());
            var restored=new MovementSession();restored.restore(session.snapshot(10),100);
            assertEquals(JumpKind.NONE,restored.accept(input(11,0,0,1),true,false,true,true,101,VALUES,frame).jump());
            restored.accept(new MovementInput(12,false,false,0,0),true,false,true,true,102,VALUES,frame);
            assertEquals(JumpKind.WALL,restored.accept(input(13,0,0,1),true,false,true,true,103,VALUES,frame).jump());
            assertTrue(restored.canRepeatWallJump());
            assertEquals(JumpKind.WALL,restored.accept(input(14,0,0,1),true,false,true,true,110,VALUES,frame).jump());
        }
    }

    @Test void crouchJumpTakesTheAvailableBulletSlotBeforeAnyWallRefresh() {
        var session=new MovementSession();session.resources().consumeAssistedSeconds(VALUES,2);
        var frame=new ParkourFrame(0,.3,0,0,.3,new WallContact(0,-1,.1,true));
        assertEquals(JumpKind.BULLET,session.accept(new MovementInput(0,true,true,0,0),true,false,true,true,0,VALUES,frame).jump());
        assertEquals(2,session.resources().jumpsUsed());
        assertTrue(session.resources().bulletJumpUsed());
        assertEquals(2,session.resources().assistedSecondsUsed());
        assertEquals(WallDashPlan.NONE,session.snapshot(0).parkour().wallDash());
    }

    @Test void unavailableBulletEligibilityCannotFallThroughToWallResourceRefill() {
        for(boolean exhausted:new boolean[]{false,true}) {
            var session=new MovementSession();session.resources().consumeBulletJump(VALUES);
            if(exhausted) session.resources().consumeJump(VALUES);
            session.resources().consumeAssistedSeconds(VALUES,2);
            var frame=new ParkourFrame(0,.3,0,0,.3,new WallContact(0,-1,.1,true));
            assertEquals(exhausted?JumpKind.NONE:JumpKind.AIR,session.accept(
                    new MovementInput(0,true,true,0,0),true,false,true,true,0,VALUES,frame).jump());
            assertEquals(2,session.resources().jumpsUsed());
            assertEquals(2,session.resources().assistedSecondsUsed());
            assertEquals(WallDashPlan.NONE,session.snapshot(0).parkour().wallDash());
        }
    }

    @Test void directWallHintsRespectPressEdgesAndCrouchPriority() {
        var jumps=new JumpController();
        assertEquals(JumpKind.GROUND,jumps.accept(input(0,0,0,1),true,true,0,VALUES,true).jump());
        assertEquals(JumpKind.NONE,jumps.accept(input(1,0,0,1),true,false,1,VALUES,true).jump());
        jumps.accept(new MovementInput(2,false,false,0,0),true,false,2,VALUES,false);
        assertEquals(JumpKind.BULLET,jumps.accept(new MovementInput(3,true,true,0,0),true,false,3,VALUES,true).jump());
    }
}
