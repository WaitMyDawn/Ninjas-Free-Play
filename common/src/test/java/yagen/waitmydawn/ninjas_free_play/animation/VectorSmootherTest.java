package yagen.waitmydawn.ninjas_free_play.animation;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.animation.core.*;
import static org.junit.jupiter.api.Assertions.*;

class VectorSmootherTest {
    @Test void responseDoesNotDependOnRenderFramePartition() {
        var a=new VectorSmoother(); var b=new VectorSmoother(); var target=new Vector3(.3,0,.1);
        a.sample(0,target,.15);b.sample(0,target,.15);
        for(int i=1;i<60;i++) b.sample(i/60.,target,.15);
        assertEquals(a.sample(1,target,.15).x(),b.sample(1,target,.15).x(),1e-9);
    }
    @Test void changingTheTargetDoesNotSnapPosition() {
        var player=new VectorSmoother();var target=new Vector3(.3,0,0);
        player.sample(0,target,.15); var before=player.sample(.1,target,.15);
        assertEquals(before,player.sample(.1,Vector3.ZERO,.15));
        assertTrue(player.sample(.100001,Vector3.ZERO,.15).x()>before.x());
        assertTrue(player.sample(1,Vector3.ZERO,.15).x()<1e-8);
    }
}
