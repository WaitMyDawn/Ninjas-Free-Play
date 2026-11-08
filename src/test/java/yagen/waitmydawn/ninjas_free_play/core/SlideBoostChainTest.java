package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;

import static org.junit.jupiter.api.Assertions.*;

class SlideBoostChainTest {
    private static final MovementValues DEFAULTS=new MovementValues(MovementAttribute::defaultValue);
    private static ParkourFrame frame(double speed) {return new ParkourFrame(0,speed,0,0,speed,WallContact.NONE);}
    private static Vector3 launch(SlideMomentum momentum,double speed) {
        return momentum.launch(frame(speed),new Vector3(0,.1,speed),DEFAULTS);
    }
    private static MovementInput input(long sequence,boolean crouch,boolean jump,boolean roll,boolean aim,float strafe) {
        return new MovementInput(sequence,crouch,jump,0,0,roll,false,strafe,1,aim);
    }
    private static MovementSession sliding() {
        var session=new MovementSession();
        assertTrue(session.accept(input(0,true,false,false,false,0),true,true,true,true,0,DEFAULTS,frame(.3)).slideStarted());
        launch(session.parkour().slideMomentum(),.3);
        return session;
    }

    @Test void oneHundredEntriesShareTheFirstReferenceAcrossDifferentSpeedsAndDirections() {
        for(double initial:new double[]{.21585,.37,1.7}) {
            var momentum=new SlideMomentum();
            double speed=launch(momentum,initial).z();
            for(int entry=0;entry<100;entry++) {
                double angle=entry*.7;
                double x=Math.sin(angle)*speed,z=Math.cos(angle)*speed;
                var result=momentum.launch(new ParkourFrame(x,z,x,0,z,WallContact.NONE),new Vector3(x,.1,z),DEFAULTS);
                assertEquals(initial*1.5,Math.hypot(result.x(),result.z()),1e-9);
                assertEquals(.1,result.y(),1e-9);
                assertEquals(x/speed,result.x()/(initial*1.5),1e-9);
                speed=Math.hypot(result.x(),result.z())*.97;
            }
            assertEquals(initial,momentum.snapshot().referenceSpeed(),1e-9);
        }
    }

    @Test void decayCanBeReplenishedButFasterExternalMomentumIsNeverClampedOrMultipliedAgain() {
        var momentum=new SlideMomentum();launch(momentum,.3);
        assertEquals(.45,launch(momentum,.32).z(),1e-9);
        assertEquals(.375,launch(momentum,.25).z(),1e-9);
        assertEquals(1.2,launch(momentum,1.2).z(),1e-9);
        assertEquals(.3,momentum.snapshot().referenceSpeed(),1e-9);
        var reversed=momentum.launch(frame(.4),new Vector3(-.8,.2,0),DEFAULTS);
        assertEquals(-.8,reversed.x(),1e-9);assertEquals(0,reversed.z(),1e-9);
    }

    @Test void recoveryRequiresSixConsecutiveNormalGroundSamplesAndCountsEachTickOnce() {
        var momentum=new SlideMomentum();launch(momentum,.3);
        for(int tick=0;tick<5;tick++) {
            momentum.observe(true,true,tick,frame(.1));
            momentum.observe(true,true,tick,frame(.1));
        }
        assertEquals(5,momentum.snapshot().recoveryTicks());
        momentum.observe(true,true,5,frame(.4));
        assertEquals(0,momentum.snapshot().recoveryTicks());
        for(int tick=6;tick<11;tick++) momentum.observe(true,true,tick,frame(.1));
        assertTrue(momentum.snapshot().active());
        momentum.observe(true,true,11,frame(.1));
        assertEquals(SlideBoostSnapshot.EMPTY,momentum.snapshot());
        assertEquals(.6,launch(momentum,.4).z(),1e-9);
    }

    @Test void airborneHighSpeedAndSkippedSamplesCannotRearmTheReference() {
        var momentum=new SlideMomentum();launch(momentum,.3);
        for(int tick=0;tick<100;tick++) momentum.observe(true,tick%2==0,tick,frame(tick%2==0?.5:0));
        assertTrue(momentum.snapshot().active());
        assertEquals(0,momentum.snapshot().recoveryTicks());
        momentum.observe(true,true,200,frame(0));momentum.observe(true,true,210,frame(0));
        assertEquals(1,momentum.snapshot().recoveryTicks());
        assertEquals(.45,launch(momentum,.45).z(),1e-9);
    }

    @Test void ordinaryGroundLimitTracksMovementAttributesRatherThanTheFixedSlideEntryThreshold() {
        var momentum=new SlideMomentum();launch(momentum,.6);
        var fastWalk=new ParkourFrame(0,.4,0,0,.2,WallContact.NONE,.4317);
        for(int tick=0;tick<6;tick++) momentum.observe(true,true,tick,fastWalk);
        assertFalse(momentum.snapshot().active());
        launch(momentum,.3);
        for(int tick=6;tick<20;tick++) momentum.observe(true,true,tick,frame(.4));
        assertTrue(momentum.snapshot().active());
    }

    @Test void snapshotRestorePreservesReferenceAndPartialRecoveryAcrossClockOrigins() {
        var momentum=new SlideMomentum();launch(momentum,.3);
        for(int tick=0;tick<3;tick++) momentum.observe(true,true,tick,frame(.1));
        var state=momentum.snapshot();var restored=new SlideMomentum();restored.restore(state,1000);
        restored.observe(true,true,1000,frame(.1));
        assertEquals(state,restored.snapshot());
        restored.observe(true,true,1001,frame(.1));restored.observe(true,true,1002,frame(.1));
        assertTrue(restored.snapshot().active());
        restored.observe(true,true,1003,frame(.1));assertFalse(restored.snapshot().active());
        restored.restore(state,2000);
        assertEquals(.45,launch(restored,.45).z(),1e-9);
    }

    @Test void attributeChangesUseTheOriginalReferenceAndNonBoostMultipliersKeepTheirScalingMeaning() {
        var momentum=new SlideMomentum();launch(momentum,.3);
        for(double multiplier:new double[]{2,1.5,1,.5,0}) {
            var values=new MovementValues(a -> a==MovementAttribute.SLIDE_SPEED_MULTIPLIER?multiplier:a.defaultValue());
            var result=momentum.launch(frame(.4),new Vector3(0,.2,.4),values);
            assertEquals(multiplier>1?.3*multiplier:.4*multiplier,result.z(),1e-9);
            assertEquals(.3,momentum.snapshot().referenceSpeed(),1e-9);
            assertEquals(.2,result.y(),1e-9);
        }
    }

    @Test void jumpLandingAndAirborneActionChangesKeepTheSameReference() {
        var session=sliding();
        session.accept(input(1,true,true,false,false,0),true,true,true,true,1,DEFAULTS,frame(.45));
        for(int tick=2;tick<12;tick++) session.update(true,false,true,tick,DEFAULTS,frame(.45));
        session.accept(input(2,false,false,false,false,0),true,true,true,true,12,DEFAULTS,frame(.45));
        session.update(true,true,true,12,DEFAULTS,frame(.45));
        assertTrue(session.accept(input(3,true,false,false,false,0),true,true,true,true,13,DEFAULTS,frame(.45)).slideStarted());
        assertEquals(.45,launch(session.parkour().slideMomentum(),.45).z(),1e-9);
        var restored=new MovementSession();restored.restore(session.snapshot(13),100);
        assertEquals(session.snapshot(13).parkour().slideBoost(),restored.snapshot(100).parkour().slideBoost());
    }

    @Test void sideRollAndGlideDoNotCountAsOrdinaryGroundRecovery() {
        for(boolean roll:new boolean[]{true,false}) {
            var session=sliding();
            var decision=session.accept(input(1,false,false,roll,!roll,roll?1:0),true,roll,true,true,1,DEFAULTS,frame(.1));
            assertEquals(roll,decision.rollStarted());
            for(int tick=2;tick<10;tick++) session.update(true,roll,true,tick,DEFAULTS,frame(.1));
            assertTrue(session.parkour().slideMomentum().snapshot().active());
            assertEquals(0,session.parkour().slideMomentum().snapshot().recoveryTicks());
        }
    }

    @Test void ineligibleMovementEndsTheChainButInputTimeoutAloneDoesNot() {
        var session=sliding();session.expire(100);
        assertTrue(session.parkour().slideMomentum().snapshot().active());
        session.observe(false,false,true,101);
        assertEquals(SlideBoostSnapshot.EMPTY,session.parkour().slideMomentum().snapshot());
    }

    @Test void invalidSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new SlideBoostSnapshot(true,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,() -> new SlideBoostSnapshot(true,-1,0));
        assertThrows(IllegalArgumentException.class,() -> new SlideBoostSnapshot(false,.3,0));
        assertThrows(IllegalArgumentException.class,() -> new SlideBoostSnapshot(false,0,1));
        assertThrows(IllegalArgumentException.class,() -> new SlideBoostSnapshot(true,.3,6));
    }
}
