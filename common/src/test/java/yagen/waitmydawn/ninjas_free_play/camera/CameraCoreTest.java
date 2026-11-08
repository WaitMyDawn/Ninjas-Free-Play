package yagen.waitmydawn.ninjas_free_play.camera;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.camera.core.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraCoreTest {
    private static final Vector3 EYE=new Vector3(0,1.62,0),BACK=new Vector3(0,0,-4);
    private static final CameraSettings IMMEDIATE=new CameraSettings(3.2,1,.25,0,0,0,.12);
    private static final CameraSettings SMOOTH=new CameraSettings(3.2,1,.25,.08,.2,.12,.12);
    private static final CameraRig.Sweep CLEAR=(from,to,radius) -> 1;
    private static void near(Vector3 expected,Vector3 actual) {
        assertEquals(expected.x(),actual.x(),1e-9);assertEquals(expected.y(),actual.y(),1e-9);assertEquals(expected.z(),actual.z(),1e-9);
    }

    @Test void enabledCycleAddsBothNfpViewsAndReturnsToFirstPerson() {
        var view=CameraView.FIRST_PERSON;
        for(var expected:new CameraView[]{CameraView.THIRD_PERSON_BACK,CameraView.SHOULDER_AUTO,CameraView.SHOULDER_FIXED,CameraView.THIRD_PERSON_FRONT,CameraView.FIRST_PERSON}) {
            view=view.next(true);assertEquals(expected,view);
        }
    }
    @Test void disabledCycleRetainsTheThreeVanillaViews() {
        var view=CameraView.FIRST_PERSON;
        for(var expected:new CameraView[]{CameraView.THIRD_PERSON_BACK,CameraView.THIRD_PERSON_FRONT,CameraView.FIRST_PERSON}) {
            view=view.next(false);assertEquals(expected,view);
        }
        assertEquals(CameraView.THIRD_PERSON_FRONT,CameraView.SHOULDER_AUTO.next(false));
    }
    @Test void cameraBasisIsOrthonormalIncludingBothVerticalPoles() {
        for(double yaw:new double[]{-810,0,45,90,180,275,810}) for(double pitch:new double[]{-90,-89.99,-45,0,45,89.99,90}) {
            var b=CameraGeometry.basis(yaw,pitch);
            for(var axis:new Vector3[]{b.forward(),b.right(),b.up()}) assertEquals(1,CameraGeometry.length(axis),1e-9);
            assertEquals(0,CameraGeometry.dot(b.forward(),b.right()),1e-9);
            assertEquals(0,CameraGeometry.dot(b.forward(),b.up()),1e-9);
            assertEquals(0,CameraGeometry.dot(b.up(),b.right()),1e-9);
        }
    }
    @Test void positiveOffsetsPlaceThePlayerBelowAndLeftOfTheAimedScreenCenter() {
        for(double yaw:new double[]{0,70,180,275}) for(double fov:new double[]{30,70,110}) for(double aspect:new double[]{4./3,16./9,32./9}) {
            var basis=CameraGeometry.basis(yaw,0);var rig=new CameraRig();
            var frame=rig.sample(0,EYE,yaw,0,basis.forward().scale(-4),1,IMMEDIATE,CLEAR);
            var view=CameraGeometry.basis(frame.angles().yaw(),frame.angles().pitch());
            var player=new Vector3(0,.9,0).add(frame.position().scale(-1));
            double depth=CameraGeometry.dot(player,view.forward());assertTrue(depth>0);
            double x=CameraGeometry.dot(player,view.right())/(depth*Math.tan(Math.toRadians(fov)/2)*aspect);
            double y=CameraGeometry.dot(player,view.up())/(depth*Math.tan(Math.toRadians(fov)/2));
            assertTrue(x<0&&y<0,"Screen composition must be left and below, independently of world heading");
        }
    }
    @Test void cameraPositionAndObstructionNeverSteerTheMouseControlledOrientation() {
        for(double yaw:new double[]{-175,0,123}) for(double pitch:new double[]{-90,-89.99,-45,0,45,89.99,90}) {
            var rig=new CameraRig();
            for(int step=0;step<5;step++) {
                double clearance=step%2==0?1:.2;
                var frame=rig.sample(step*.05,EYE.add(new Vector3(.05*step,0,0)),yaw,pitch,BACK,1,SMOOTH,(a,b,r) -> clearance);
                assertEquals(yaw,frame.angles().yaw(),1e-9);assertEquals(pitch,frame.angles().pitch(),1e-9);
            }
        }
    }
    @Test void playerScaleChangesDistancesWithoutChangingTheComposition() {
        Vector3 reference=null;
        for(double scale:new double[]{.5,1,2}) {
            var eye=EYE.scale(scale);
            var frame=new CameraRig().sample(0,eye,0,0,BACK.scale(scale),scale,IMMEDIATE,CLEAR);
            var normal=frame.position().scale(1/scale);
            if(reference==null) reference=normal;else near(reference,normal);
        }
    }
    @Test void entryStartsAtVanillaBackAndBlendsToTheConfiguredComposition() {
        var rig=new CameraRig();
        near(EYE.add(BACK),rig.sample(0,EYE,0,0,BACK,1,SMOOTH,CLEAR).position());
        var middle=rig.sample(.1,EYE,0,0,BACK,1,SMOOTH,CLEAR).position();
        assertTrue(middle.x()<0&&middle.x()>-1);
        near(EYE.add(new Vector3(-1,.25,-3.2)),rig.sample(.2,EYE,0,0,BACK,1,SMOOTH,CLEAR).position());
    }
    @Test void positionFollowConvergesEquallyAtDifferentFrameRates() {
        Vector3 previous=null;
        for(int frames:new int[]{30,60,144}) {
            var rig=new CameraRig();var settings=new CameraSettings(3.2,1,.25,.08,0,0,.12);
            rig.sample(0,EYE,0,0,BACK,1,settings,CLEAR);
            var stepped=EYE.add(new Vector3(0,-.4,0));CameraRig.Frame last=null;
            for(int i=1;i<=frames;i++) last=rig.sample((double)i/frames,stepped,0,0,BACK,1,settings,CLEAR);
            if(previous==null) previous=last.position();else near(previous,last.position());
        }
    }
    @Test void obstructionRetractsImmediatelyAndOnlyRecoveryIsSmoothed() {
        var rig=new CameraRig();var settings=new CameraSettings(3.2,1,.25,0,0,.12,.12);
        rig.sample(0,EYE,0,0,BACK,1,settings,CLEAR);
        assertEquals(.25,rig.sample(.1,EYE,0,0,BACK,1,settings,(a,b,r) -> .25).collisionFraction(),1e-9);
        assertEquals(.25,rig.sample(.1,EYE,0,0,BACK,1,settings,CLEAR).collisionFraction(),1e-9);
        double recovering=rig.sample(.2,EYE,0,0,BACK,1,settings,CLEAR).collisionFraction();
        assertTrue(recovering>.25&&recovering<1);
        assertEquals(.1,rig.sample(.201,EYE,0,0,BACK,1,settings,(a,b,r) -> .1).collisionFraction(),1e-9);
    }
    @Test void collisionAlwaysQueriesTheActualEyeAndTheCompleteSmoothedOffset() {
        var rig=new CameraRig();var settings=new CameraSettings(3.2,1,.25,.2,0,0,.12);
        rig.sample(0,EYE,0,0,BACK,1,settings,CLEAR);
        var eye=EYE.add(new Vector3(.4,0,0));var end=new Vector3[1];
        var frame=rig.sample(.01,eye,0,0,BACK,1,settings,(from,to,radius) -> {
            near(eye,from);assertTrue(to.x()<eye.x()&&to.y()>eye.y()&&to.z()<eye.z());
            assertEquals(.12,radius,1e-9);end[0]=to;return .5;
        });
        near(eye.lerp(end[0],.5),frame.position());
    }
    @Test void teleportResetAndExplicitContextResetDoNotInterpolateAcrossOldWorldPositions() {
        var rig=new CameraRig();rig.sample(10,EYE,0,0,BACK,1,SMOOTH,CLEAR);
        var next=EYE.add(new Vector3(100,20,-50));
        near(next.add(BACK),rig.sample(11,next,0,0,BACK,1,SMOOTH,CLEAR).position());
        rig.reset();
        near(EYE.add(BACK),rig.sample(0,EYE,0,0,BACK,1,SMOOTH,CLEAR).position());
    }
    @Test void mouseTurningIsImmediateEvenWhilePositionFollowingIsSmoothed() {
        var rig=new CameraRig();rig.sample(0,EYE,123,-90,BACK,1,SMOOTH,CLEAR);
        var turned=rig.sample(.001,EYE,-85,34,BACK,1,SMOOTH,CLEAR);
        assertEquals(-85,turned.angles().yaw(),1e-9);assertEquals(34,turned.angles().pitch(),1e-9);
    }
    @Test void invalidSettingsSamplesAndCollisionFractionsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new CameraSettings(0,1,0,0,0,0,.1));
        assertThrows(IllegalArgumentException.class,() -> new CameraSettings(3,Double.NaN,0,0,0,0,.1));
        assertThrows(IllegalArgumentException.class,() -> new CameraRig().sample(0,EYE,0,0,BACK,0,IMMEDIATE,CLEAR));
        assertThrows(IllegalArgumentException.class,() -> new CameraRig().sample(0,EYE,0,0,BACK,1,IMMEDIATE,(a,b,r) -> Double.NaN));
        assertThrows(IllegalArgumentException.class,() -> new CameraRig().sample(0,EYE,0,0,BACK,1,IMMEDIATE,(a,b,r) -> 1.1));
    }
    @Test void shoulderReversalBlendsCompositionWithoutResettingOrbitAndStillSweepsCollision() {
        var positive=new CameraSettings(3.2,2,.25,0,.2,0,.12);
        var negative=new CameraSettings(3.2,-2,.25,0,.2,0,.12);
        var rig=new CameraRig();
        rig.sample(0,EYE,0,0,new Vector3(-2,.25,-3.2),1,positive,CLEAR);
        var start=rig.sample(1,EYE,0,0,BACK,1,positive,CLEAR);
        var change=rig.sample(1,EYE,0,0,BACK,1,negative,CLEAR);near(start.position(),change.position());
        var middle=rig.sample(1.1,EYE,0,0,BACK,1,negative,(a,b,r) -> .5);
        assertEquals(0,middle.composition().y(),1e-9);
        near(EYE.add(new Vector3(0,.125,-1.6)),middle.position());
        assertEquals(0,middle.angles().yaw());assertEquals(0,middle.angles().pitch());
        var end=rig.sample(1.2,EYE,0,0,BACK,1,negative,CLEAR);
        near(EYE.add(new Vector3(2,.25,-3.2)),end.position());
    }
    @Test void reversingAgainDuringTheTransitionStartsFromTheVisibleComposition() {
        var positive=new CameraSettings(3.2,2,.25,0,.2,0,.12);
        var negative=new CameraSettings(3.2,-2,.25,0,.2,0,.12);var rig=new CameraRig();
        rig.sample(0,EYE,0,0,new Vector3(-2,.25,-3.2),1,positive,CLEAR);
        rig.sample(1,EYE,0,0,BACK,1,negative,CLEAR);
        var previous=rig.sample(1.08,EYE,0,0,BACK,1,negative,CLEAR);
        var interrupted=rig.sample(1.08,EYE,0,0,BACK,1,positive,CLEAR);
        near(previous.position(),interrupted.position());near(previous.composition(),interrupted.composition());
        near(EYE.add(new Vector3(-2,.25,-3.2)),rig.sample(1.28,EYE,0,0,BACK,1,positive,CLEAR).position());
    }
}
