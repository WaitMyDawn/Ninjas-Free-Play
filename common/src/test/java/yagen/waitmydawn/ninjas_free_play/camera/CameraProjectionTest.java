package yagen.waitmydawn.ninjas_free_play.camera;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import yagen.waitmydawn.ninjas_free_play.camera.core.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraProjectionTest {
    private static double[] matrix(Matrix4f matrix) {
        float[] values=matrix.get(new float[16]);double[] result=new double[16];
        for(int i=0;i<16;i++) result[i]=values[i];return result;
    }
    @Test void perspectiveProjectionMatchesAnalyticScreenCoordinatesAcrossFovsAndAspectRatios() {
        for(double fov:new double[]{30,70,110}) for(double aspect:new double[]{4./3,16./9,32./9}) {
            var matrix=new Matrix4f().perspective((float)Math.toRadians(fov),(float)aspect,.05f,256);
            for(double depth:new double[]{2,4,64}) {
                var point=CameraProjection.project(new Vector3(-.25,.1,-depth),matrix(matrix));
                assertTrue(point.visible());
                assertEquals(-.25/(depth*Math.tan(Math.toRadians(fov)/2)*aspect),point.x(),1e-6);
                assertEquals(.1/(depth*Math.tan(Math.toRadians(fov)/2)),point.y(),1e-6);
            }
        }
    }
    @Test void switchingNearAndFarTargetsMovesTheReticleWithoutSteeringTheCamera() {
        var eye=new Vector3(0,1.62,0);var rig=new CameraRig();
        var settings=new CameraSettings(3.2,1.35,.25,0,0,0,.12);
        var frame=rig.sample(0,eye,0,0,new Vector3(0,0,-4),1,settings,(a,b,r) -> 1);
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,256)
                .rotate(new Quaternionf().rotationYXZ((float)Math.PI,0,0).conjugate());
        double nearX=0,farX=0;
        for(double distance:new double[]{64,.2,5,.5,64}) {
            var aim=eye.add(new Vector3(0,0,distance));
            var point=CameraProjection.project(aim.add(frame.position().scale(-1)),matrix(projection));
            assertTrue(point.visible());assertTrue(point.x()<0&&point.y()<0);
            assertEquals(0,frame.angles().yaw(),1e-9);assertEquals(0,frame.angles().pitch(),1e-9);
            if(distance==.2) nearX=point.x();if(distance==64) farX=point.x();
        }
        assertTrue(nearX<farX-.1,"Close-range parallax must be reflected by the reticle rather than hidden by camera rotation");
    }
    @Test void effectiveRenderMatrixIncludingViewAndProjectionEffectsMatchesLibraryProjection() {
        var transform=new Matrix4f().perspective((float)Math.toRadians(82),1.5f,.05f,256)
                .rotateZ(.15f).translate(.03f,-.02f,0).rotateY(.4f);
        var point=new Vector3(.3,.2,-4);
        var expected=new Vector4f((float)point.x(),(float)point.y(),(float)point.z(),1).mul(transform);
        var actual=CameraProjection.project(point,matrix(transform));
        assertTrue(actual.visible());
        assertEquals(expected.x/expected.w,actual.x(),1e-6);assertEquals(expected.y/expected.w,actual.y(),1e-6);
    }
    @Test void behindCameraAndOffscreenPointsDoNotProduceAMisleadingCenteredReticle() {
        var projection=matrix(new Matrix4f().perspective((float)Math.toRadians(70),1,.05f,256));
        assertFalse(CameraProjection.project(new Vector3(0,0,1),projection).visible());
        assertFalse(CameraProjection.project(new Vector3(100,0,-1),projection).visible());
        assertFalse(CameraProjection.project(Vector3.ZERO,projection).visible());
    }
    @Test void onlyTheLegacyHorizontalDefaultMigratesAndCustomSettingsRemainOwnedByTheConfig() {
        assertEquals(1.75,CameraSettings.DEFAULT.horizontal(),1e-9);
        assertEquals(1.75,CameraSettings.migrateHorizontal(1,0),1e-9);
        assertEquals(1.75,CameraSettings.migrateHorizontal(1.35,0),1e-9);
        assertEquals(1.75,CameraSettings.migrateHorizontal(1.35,1),1e-9);
        for(double value:new double[]{-.6,0,.8,1.2,2.4}) assertEquals(value,CameraSettings.migrateHorizontal(value,0),1e-9);
        assertEquals(1,CameraSettings.migrateHorizontal(1,1),1e-9);
        for(double value:new double[]{1,1.35,1.75,2}) assertEquals(value,CameraSettings.migrateHorizontal(value,2),1e-9);
    }
    @Test void malformedMatricesAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> CameraProjection.project(Vector3.ZERO,new double[15]));
        var matrix=new double[16];matrix[0]=Double.NaN;
        assertThrows(IllegalArgumentException.class,() -> CameraProjection.project(Vector3.ZERO,matrix));
    }
}
