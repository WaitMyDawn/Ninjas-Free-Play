package yagen.waitmydawn.ninjas_free_play.camera;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.camera.core.*;
import yagen.waitmydawn.ninjas_free_play.core.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraOrbitTest {
    @Test void aimingAtAPointDoesNotFeedBackIntoMouseOrbit() {
        var orbit=new CameraOrbit();orbit.initialize(38,-25);
        var original=orbit.angles();var eye=new Vector3(2,3,4);
        var basis=CameraGeometry.basis(original.yaw(),original.pitch());
        var camera=eye.add(CameraGeometry.offset(basis,3.2,2,.25));
        for(double distance:new double[]{3,8,64,4,64,3}) {
            var point=camera.add(basis.forward().scale(distance));
            var facing=CameraOrbit.aim(eye,point,original.yaw());
            var direction=CameraGeometry.basis(facing.yaw(),facing.pitch()).forward();
            assertEquals(1,CameraGeometry.dot(direction,point.add(eye.scale(-1)))/CameraGeometry.length(point.add(eye.scale(-1))),1e-9);
            assertEquals(original,orbit.angles());
        }
        orbit.turn(10,-20);
        assertEquals(39.5,orbit.angles().yaw(),1e-9);assertEquals(-28,orbit.angles().pitch(),1e-9);
    }
    @Test void virtualFarFacingDoesNotDependOnClippedCameraPositionOrWorldTranslation() {
        for(double yaw:new double[]{-180,-45,0,73,270}) for(double pitch:new double[]{-90,-45,0,45,90}) {
            var settings=new CameraSettings(3.2,2,.25,0,0,0,.12);var angles=new CameraGeometry.Angles(yaw,pitch);
            CameraGeometry.Angles expected=null;
            for(double scale:new double[]{.1,1,8}) {
                expected=null;
                for(double clip:new double[]{1,.5,.1,0}) for(var eye:new Vector3[]{Vector3.ZERO,new Vector3(120,80,-37)}) {
                    var rig=new CameraRig();var back=CameraGeometry.offset(CameraGeometry.basis(yaw,pitch),3.2,2,.25).scale(scale);
                    var frame=rig.sample(0,eye,yaw,pitch,back,scale,settings,(a,b,r) -> clip);
                    var far=CameraOrbit.virtualTarget(eye,angles,frame.composition(),scale,64);
                    var facing=CameraOrbit.aim(eye,far,yaw);
                    if(expected==null) expected=facing;
                    assertEquals(expected.yaw(),facing.yaw(),1e-9);assertEquals(expected.pitch(),facing.pitch(),1e-9);
                    assertEquals(yaw,frame.angles().yaw());assertEquals(pitch,frame.angles().pitch());
                }
            }
        }
    }
    @Test void virtualTargetRemainsOnTheOpenCameraCenterLineAndAheadOfTheEyeAtEveryScale() {
        var eye=new Vector3(2,3,4);
        for(double yaw:new double[]{0,56,180}) for(double pitch:new double[]{-90,-30,0,90})
            for(double side:new double[]{-2,0,2}) for(double scale:new double[]{.1,1,8,32}) {
                var angles=new CameraGeometry.Angles(yaw,pitch);var basis=CameraGeometry.basis(yaw,pitch);
                var composition=new Vector3(12,side,.25);
                var camera=eye.add(CameraGeometry.offset(basis,composition.x(),composition.y(),composition.z()).scale(scale));
                var far=CameraOrbit.virtualTarget(eye,angles,composition,scale,16);
                var ray=far.add(camera.scale(-1));
                assertEquals(0,CameraGeometry.dot(ray,basis.right()),1e-9);
                assertEquals(0,CameraGeometry.dot(ray,basis.up()),1e-9);
                assertEquals(16,CameraGeometry.dot(far.add(eye.scale(-1)),basis.forward()),1e-9);
                assertTrue(CameraGeometry.dot(ray,basis.forward())>0);
            }
    }
    @Test void virtualFarFacingPreservesDirectionalWallDashesOnEveryWallAndBothShoulders() {
        for(var wall:new WallContact[]{new WallContact(0,-1,.1,true),new WallContact(1,0,.1,true),
                new WallContact(0,1,.1,true),new WallContact(-1,0,.1,true)}) {
            double toward=Math.toDegrees(Math.atan2(wall.x(),-wall.z()));
            for(double side:new double[]{-2,2}) for(int sector=0;sector<3;sector++) {
                double yaw=toward+(sector==0?0:sector==1?90:-90);
                var far=CameraOrbit.virtualTarget(Vector3.ZERO,new CameraGeometry.Angles(yaw,0),new Vector3(3.2,side,.25),1,64);
                var facing=CameraOrbit.aim(Vector3.ZERO,far,yaw);
                var expected=switch(sector) {
                    case 0 -> new WallDashKind[]{WallDashKind.UP,WallDashKind.LEFT,WallDashKind.AWAY,WallDashKind.RIGHT};
                    case 1 -> new WallDashKind[]{WallDashKind.RIGHT,WallDashKind.UP,WallDashKind.LEFT,WallDashKind.AWAY};
                    default -> new WallDashKind[]{WallDashKind.LEFT,WallDashKind.AWAY,WallDashKind.RIGHT,WallDashKind.UP};
                };
                float[][] keys={{0,1},{1,0},{0,-1},{-1,0}};
                for(int i=0;i<keys.length;i++) assertEquals(expected[i],WallDashPlan.resolve(new MovementInput(0,false,true,
                        (float)facing.yaw(),(float)facing.pitch(),false,false,keys[i][0],keys[i][1],false),wall).kind());
            }
        }
    }
    @Test void virtualTargetRejectsInvalidScaleAndDistance() {
        var angles=new CameraGeometry.Angles(0,0);var composition=new Vector3(3.2,2,.25);
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,() -> CameraOrbit.virtualTarget(Vector3.ZERO,angles,composition,value,64));
            assertThrows(IllegalArgumentException.class,() -> CameraOrbit.virtualTarget(Vector3.ZERO,angles,composition,1,value));
        }
    }
    @Test void mousePitchClampsAtBothPolesAndResetDoesNotReusePreviousWorldState() {
        var orbit=new CameraOrbit();assertThrows(IllegalStateException.class,orbit::angles);
        orbit.initialize(100,0);orbit.turn(0,1000);assertEquals(90,orbit.angles().pitch());
        orbit.turn(0,-2000);assertEquals(-90,orbit.angles().pitch());
        orbit.reset();assertFalse(orbit.initialized());
        orbit.initialize(-50,30);assertEquals(-50,orbit.angles().yaw());assertEquals(30,orbit.angles().pitch());
    }
    @Test void eyeToCenterConvergenceHasStableYawAtVerticalAndCoincidentTargets() {
        var eye=new Vector3(4,5,6);
        assertEquals(new CameraGeometry.Angles(73,-90),CameraOrbit.aim(eye,eye.add(new Vector3(0,5,0)),73));
        assertEquals(new CameraGeometry.Angles(73,90),CameraOrbit.aim(eye,eye.add(new Vector3(0,-5,0)),73));
        assertEquals(new CameraGeometry.Angles(73,0),CameraOrbit.aim(eye,eye,73));
    }
}
