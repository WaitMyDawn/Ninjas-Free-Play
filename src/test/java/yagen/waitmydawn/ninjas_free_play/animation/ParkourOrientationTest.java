package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import static org.junit.jupiter.api.Assertions.*;

class ParkourOrientationTest {
    @Test void glideInclinationHasTheRequestedExponentialAnchorsAndMonotonicLimit() {
        assertEquals(60,GlideOrientation.inclinationDegrees(0),1e-9);
        assertEquals(45,GlideOrientation.inclinationDegrees(8),1e-9);
        assertEquals(37.5,GlideOrientation.inclinationDegrees(16),1e-9);
        assertEquals(30,GlideOrientation.inclinationDegrees(10000),1e-9);
        for(int speed=1;speed<100;speed++) assertTrue(GlideOrientation.inclinationDegrees(speed)<GlideOrientation.inclinationDegrees(speed-1));
    }

    @Test void aReachablePlanePreservesLimbLengthAndTangentialDirection() {
        for(Vector3 normal:new Vector3[]{new Vector3(1,0,0),new Vector3(0,0,-1)}) {
            var current=new Vector3(.2,-.4,.3);
            var constrained=LimbPlaneConstraint.endpoint(current,normal,-.3);
            assertEquals(dot(current,current),dot(constrained,constrained),1e-9);
            assertEquals(-.3,dot(constrained,normal),1e-9);
        }
        var current=new Vector3(.1,.2,.3);
        assertEquals(current,LimbPlaneConstraint.endpoint(current,new Vector3(0,0,1),2));
    }

    @Test void aPerpendicularLimbHasAStablePlaneSolutionAtTheReachBoundary() {
        var normal=new Vector3(0,0,-1);
        var actual=LimbPlaneConstraint.endpoint(new Vector3(0,0,1),normal,-.5);
        assertEquals(1,dot(actual,actual),1e-9);
        assertEquals(-.5,dot(actual,normal),1e-9);
        assertEquals(new Vector3(0,0,1),LimbPlaneConstraint.endpoint(new Vector3(0,0,1),normal,-1));
    }
    private static double dot(Vector3 a,Vector3 b) {return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}

    @Test void interruptedContactCorrectionRetainsItsPoseAndVelocityWhileFading() {
        var target=new AnimationPose(java.util.Map.of(Bone.LEFT_ARM,new BonePose(new Vector3(2,0,0),new Vector3(.5,0,0),-1)));
        var layer=new PoseCorrectionSmoother();
        layer.sample(0,AnimationPose.IDENTITY,target,.06);
        double step=1e-6;
        var before=layer.sample(.04-step,AnimationPose.IDENTITY,target,.06).bone(Bone.LEFT_ARM);
        var center=layer.sample(.04,AnimationPose.IDENTITY,target,.06).bone(Bone.LEFT_ARM);
        var interrupted=layer.sample(.04,AnimationPose.IDENTITY,AnimationPose.IDENTITY,.06).bone(Bone.LEFT_ARM);
        var after=layer.sample(.04+step,AnimationPose.IDENTITY,AnimationPose.IDENTITY,.06).bone(Bone.LEFT_ARM);
        assertEquals(center.position().x(),interrupted.position().x(),1e-9);
        assertEquals(center.rotation().x(),interrupted.rotation().x(),1e-9);
        assertEquals(center.bend(),interrupted.bend(),1e-9);
        assertEquals((center.bend()-before.bend())/step,(after.bend()-center.bend())/step,.02);
        assertEquals((center.position().x()-before.position().x())/step,(after.position().x()-center.position().x())/step,.02);
        assertTrue(layer.sample(1,AnimationPose.IDENTITY,AnimationPose.IDENTITY,.06).bone(Bone.LEFT_ARM).position().x()<1e-8);
    }
}
