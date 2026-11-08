package yagen.waitmydawn.ninjas_free_play.camera;

import org.junit.jupiter.api.Test;
import yagen.waitmydawn.ninjas_free_play.camera.core.*;
import static org.junit.jupiter.api.Assertions.*;

class ViewCycleTest {
    @Test void allMasksSkipDisabledViewsAndReconcileRemovedCurrentViewsInOrder() {
        for(int bits=1;bits<32;bits++) {
            String mask=String.format("%5s",Integer.toBinaryString(bits)).replace(' ','0');
            var cycle=new ViewCycle(mask);
            for(boolean available:new boolean[]{true,false}) for(int index=0;index<5;index++) {
                var current=ViewCycle.ORDER.get(index);CameraView expected=CameraView.FIRST_PERSON;
                for(int step=1;step<=5;step++) {
                    int next=(index+step)%5;
                    if(mask.charAt(next)=='1' && (available || next!=2 && next!=3)) {
                        expected=ViewCycle.ORDER.get(next);break;
                    }
                }
                assertEquals(expected,cycle.next(current,available),mask+" from "+current);
                boolean keep=mask.charAt(index)=='1' && (available || index!=2 && index!=3);
                assertEquals(keep?current:expected,cycle.reconcile(current,available));
                assertEquals(mask,cycle.mask());
            }
        }
    }

    @Test void singleViewCyclesRemainSelectedWithoutRequiringAnotherView() {
        for(int i=0;i<5;i++) {
            String mask="0".repeat(i)+"1"+"0".repeat(4-i);var view=ViewCycle.ORDER.get(i);
            assertEquals(view,new ViewCycle(mask).next(view,true));
        }
    }

    @Test void invalidMasksCannotCreateAnEmptyOrAmbiguousCycle() {
        assertFalse(ViewCycle.valid(null));assertThrows(IllegalArgumentException.class,() -> new ViewCycle(null));
        for(String mask:new String[]{"","00000","1111","111111","1011a","10 10","１1111","-1111"}) {
            assertFalse(ViewCycle.valid(mask));assertThrows(IllegalArgumentException.class,() -> new ViewCycle(mask));
        }
    }
}
