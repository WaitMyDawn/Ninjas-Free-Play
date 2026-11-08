package yagen.waitmydawn.ninjas_free_play.core;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.Vector3;
import static org.junit.jupiter.api.Assertions.*;

class SlideMomentumTest {
    private static final MovementValues DEFAULTS=new MovementValues(MovementAttribute::defaultValue);
    private static final ParkourFrame FRAME=new ParkourFrame(.12,.16,.048,-.08,.064,WallContact.NONE);

    @Test void entryRestoresActualWalkingVelocityThenMultipliesOnlyTheHorizontalComponents() {
        var launch=new SlideMomentum().launch(FRAME,new Vector3(.048,-.08,.064),DEFAULTS);
        assertEquals(.18,launch.x(),1e-9);assertEquals(.24,launch.z(),1e-9);assertEquals(-.08,launch.y(),1e-9);
    }
    @Test void fasterMomentumAndChangedExternalForceDirectionsAreRetained() {
        var faster=new SlideMomentum().launch(FRAME,new Vector3(.3,-.1,.4),DEFAULTS);
        assertEquals(.45,faster.x(),1e-9);assertEquals(.6,faster.z(),1e-9);assertEquals(-.1,faster.y(),1e-9);
        var reversed=new SlideMomentum().launch(FRAME,new Vector3(-.06,.2,-.08),DEFAULTS);
        assertEquals(-.09,reversed.x(),1e-9);assertEquals(-.12,reversed.z(),1e-9);
        var sideways=new SlideMomentum().launch(FRAME,new Vector3(-.1,.2,0),DEFAULTS);
        assertEquals(-.15,sideways.x(),1e-9);assertEquals(0,sideways.z(),1e-9);
    }
    @Test void configurableOneDisablesBoostAndZeroStopsHorizontalLaunch() {
        for(double multiplier:new double[]{0,1,2}) {
            var values=new MovementValues(a -> a==MovementAttribute.SLIDE_SPEED_MULTIPLIER?multiplier:a.defaultValue());
            var launch=new SlideMomentum().launch(FRAME,new Vector3(.048,.3,.064),values);
            assertEquals(.12*multiplier,launch.x(),1e-9);assertEquals(.16*multiplier,launch.z(),1e-9);
            assertEquals(.3,launch.y(),1e-9);
        }
    }
    @Test void heldDuplicateAndAcknowledgedInputsDoNotStartAnotherBoost() {
        var session=new MovementSession();
        var frame=new ParkourFrame(0,.3,0,0,.3,WallContact.NONE);
        var enter=new MovementInput(0,true,false,0,0,false,false,0,1,false);
        assertTrue(session.accept(enter,true,true,true,true,0,DEFAULTS,frame).slideStarted());
        assertFalse(session.accept(enter,true,true,true,true,0,DEFAULTS,frame).processed());
        var held=new MovementInput(1,true,false,0,0,false,false,0,1,false);
        assertFalse(session.accept(held,true,true,true,true,1,DEFAULTS,frame).slideStarted());
        var restored=new MovementSession();restored.restore(session.snapshot(1),100);
        assertFalse(restored.accept(new MovementInput(2,true,false,0,0,false,false,0,1,false),true,true,true,true,101,DEFAULTS,frame).slideStarted());
        restored.accept(new MovementInput(3,false,false,0,0,false,false,0,1,false),true,true,true,true,102,DEFAULTS,frame);
        assertTrue(restored.accept(new MovementInput(4,true,false,0,0,false,false,0,1,false),true,true,true,true,103,DEFAULTS,frame).slideStarted());
    }
}
