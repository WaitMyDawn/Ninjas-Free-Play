package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactActionTest {
    private static final MovementValues VALUES=new MovementValues(MovementAttribute::defaultValue);
    private static MovementInput input(long sequence,boolean crouch,boolean jump,boolean roll,boolean aim) {
        return new MovementInput(sequence,crouch,jump,0,0,roll,false,0,1,aim);
    }
    private static MovementSession bullet() {
        var session=new MovementSession();
        assertEquals(JumpKind.BULLET,session.accept(input(0,true,true,false,false),true,true,true,0,VALUES).jump());
        session.observe(true,false,true,1);
        session.accept(input(1,false,false,false,false),true,false,true,1,VALUES);
        return session;
    }

    @Test void releasedCrouchAndElapsedAnimationTimeDoNotRemoveBulletCollision() {
        var session=bullet();
        for(int tick=2;tick<100;tick++) session.update(true,false,true,tick,VALUES,ParkourFrame.EMPTY);
        assertFalse(session.crouchHeld());
        assertTrue(session.bulletJumping());assertTrue(session.compactAction(100));
        assertEquals(1,session.resources().jumpsUsed());assertTrue(session.resources().bulletJumpUsed());
    }

    @Test void snapshotRestoreKeepsCollisionWithoutASeparateNetworkFlag() {
        var source=bullet();var restored=new MovementSession();
        restored.restore(source.snapshot(10),1000);
        assertTrue(restored.compactAction(1000));
        restored.update(true,false,true,1001,VALUES,ParkourFrame.EMPTY);
        assertTrue(restored.compactAction(1001));
    }

    @Test void landingRetainsLowCeilingHistoryAndClearsItAfterLeavingTheCeiling() {
        var session=bullet();
        session.observe(true,true,false,2);
        assertFalse(session.compactAction(2));assertFalse(session.bulletJumping());
        assertTrue(session.snapshot(2).posture().blockedLow());
        var observer=new MovementSession();observer.restore(session.snapshot(2),100);
        assertTrue(observer.posture().low(100));
        observer.observe(true,true,true,101);
        assertFalse(observer.posture().low(101));
        assertEquals(0,session.resources().jumpsUsed());
    }

    @Test void anAirJumpEndsBulletCollisionWithoutForgettingTheLowCeiling() {
        var session=bullet();
        assertEquals(JumpKind.AIR,session.accept(input(2,false,true,false,false),true,false,false,2,VALUES).jump());
        assertFalse(session.compactAction(2));assertTrue(session.posture().low(2));
        assertEquals(2,session.resources().jumpsUsed());
        assertTrue(session.resources().bulletJumpUsed());
    }

    @Test void rollKickAndGlideCannotReviveOldBulletCollisionAfterEnding() {
        for(int kind=0;kind<3;kind++) {
            var session=bullet();
            var frame=new ParkourFrame(0,.5,0,0,.5,WallContact.NONE);
            var start=kind==0?new MovementInput(2,false,false,0,0,true,false,1,0,false)
                    :input(2,kind==1,false,false,kind==2);
            session.accept(start,true,false,true,true,2,VALUES,frame);
            assertFalse(session.bulletJumping());
            if(kind==0) assertFalse(session.compactAction(2));
            if(kind==1) assertTrue(session.compactAction(2));
            session.accept(input(3,false,false,false,false),true,false,true,true,30,VALUES,ParkourFrame.EMPTY);
            session.update(true,false,true,31,VALUES,ParkourFrame.EMPTY);
            assertFalse(session.compactAction(31));assertFalse(session.bulletJumping());
            assertTrue(session.resources().bulletJumpUsed());
        }
    }

    @Test void ordinaryJumpsAndIneligibleMovementDoNotReceiveBulletCollision() {
        var session=new MovementSession();
        session.accept(input(0,false,true,false,false),true,true,true,0,VALUES);
        assertFalse(session.compactAction(0));
        session=bullet();session.observe(false,false,true,2);
        assertFalse(session.compactAction(2));assertFalse(session.bulletJumping());
    }
}
